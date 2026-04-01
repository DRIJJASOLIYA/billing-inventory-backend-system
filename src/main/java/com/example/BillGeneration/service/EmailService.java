package com.example.BillGeneration.service;

import com.example.BillGeneration.config.AppProperties;
import com.example.BillGeneration.exception.BadRequestException;
import com.example.BillGeneration.exception.ExternalServiceUnavailableException;
import com.example.BillGeneration.util.SimpleCircuitBreaker;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.time.Duration;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final SimpleCircuitBreaker mailCircuitBreaker;

    public EmailService(JavaMailSender mailSender, AppProperties appProperties) {
        this.mailSender = mailSender;
        this.mailCircuitBreaker = new SimpleCircuitBreaker(
                appProperties.getResilience().getFailureThreshold(),
                Duration.ofSeconds(appProperties.getResilience().getOpenStateSeconds())
        );
    }

    @Retryable(
            retryFor = RuntimeException.class,
            maxAttemptsExpression = "${app.retry.email.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${app.retry.email.delay-ms:500}")
    )
    public void sendMail(String to, String subject, String body) {
        validateRecipient(to);
        validateCircuitState();
        SimpleMailMessage message = createSimpleMessage(to, subject, body);
        sendSimpleMessage(message);
    }

    @Retryable(
            retryFor = RuntimeException.class,
            maxAttemptsExpression = "${app.retry.email.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${app.retry.email.delay-ms:500}")
    )
    public void sendMailWithAttachment(String to, String subject, String body, String fileName, byte[] fileBytes) {
        validateRecipient(to);
        validateCircuitState();
        MimeMessage mimeMessage = createMimeMessageWithAttachment(to, subject, body, fileName, fileBytes);
        sendMimeMessage(mimeMessage);
    }

    private void validateRecipient(String to) {
        if (to == null || to.isBlank()) {
            throw new BadRequestException("Email recipient is required");
        }
    }

    private SimpleMailMessage createSimpleMessage(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        return message;
    }

    private void sendSimpleMessage(SimpleMailMessage message) {
        executeWithCircuitBreaker(() -> mailSender.send(message));
    }

    private MimeMessage createMimeMessageWithAttachment(
            String to,
            String subject,
            String body,
            String fileName,
            byte[] fileBytes
    ) {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body);
            addAttachment(helper, fileName, fileBytes);
            return mimeMessage;
        } catch (MessagingException ex) {
            throw new RuntimeException("Failed to prepare email with attachment", ex);
        }
    }

    private void addAttachment(MimeMessageHelper helper, String fileName, byte[] fileBytes) throws MessagingException {
        helper.addAttachment(fileName, new ByteArrayResource(fileBytes));
    }

    private void sendMimeMessage(MimeMessage mimeMessage) {
        executeWithCircuitBreaker(() -> mailSender.send(mimeMessage));
    }

    private void validateCircuitState() {
        if (mailCircuitBreaker.isOpen()) {
            throw new ExternalServiceUnavailableException("Email service is temporarily unavailable");
        }
    }

    private void executeWithCircuitBreaker(Runnable operation) {
        try {
            operation.run();
            mailCircuitBreaker.recordSuccess();
        } catch (RuntimeException ex) {
            mailCircuitBreaker.recordFailure();
            throw ex;
        }
    }

    @Recover
    public void recoverMailSend(RuntimeException ex, String to, String subject, String body) {
        log.warn("Email send failed after retries to {}", to, ex);
        throw new ExternalServiceUnavailableException("Email service failed after retries", ex);
    }

    @Recover
    public void recoverMailAttachmentSend(RuntimeException ex, String to, String subject, String body, String fileName, byte[] fileBytes) {
        log.warn("Email with attachment failed after retries to {}", to, ex);
        throw new ExternalServiceUnavailableException("Email service failed after retries", ex);
    }
}
