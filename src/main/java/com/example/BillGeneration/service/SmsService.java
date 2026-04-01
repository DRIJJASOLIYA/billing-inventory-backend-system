package com.example.BillGeneration.service;

import com.example.BillGeneration.config.AppProperties;
import com.example.BillGeneration.exception.BadRequestException;
import com.example.BillGeneration.exception.ExternalServiceUnavailableException;
import com.example.BillGeneration.util.SimpleCircuitBreaker;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);
    private static final String WHATSAPP_PREFIX = "whatsapp:";
    private static final String INDIA_COUNTRY_CODE = "+91";
    private static final String E164_PATTERN = "^\\+[1-9]\\d{7,14}$";

    private final String fromNumber;
    private final String whatsappFrom;
    private final String whatsappToDefault;
    private final SimpleCircuitBreaker smsCircuitBreaker;

    public SmsService(
            @Value("${twilio.from.number}") String fromNumber,
            @Value("${twilio.whatsapp.from}") String whatsappFrom,
            @Value("${twilio.whatsapp.to:}") String whatsappToDefault,
            AppProperties appProperties
    ) {
        this.fromNumber = fromNumber;
        this.whatsappFrom = whatsappFrom;
        this.whatsappToDefault = whatsappToDefault;
        this.smsCircuitBreaker = new SimpleCircuitBreaker(
                appProperties.getResilience().getFailureThreshold(),
                Duration.ofSeconds(appProperties.getResilience().getOpenStateSeconds())
        );
    }

    @Retryable(
            retryFor = RuntimeException.class,
            maxAttemptsExpression = "${app.retry.sms.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${app.retry.sms.delay-ms:500}")
    )
    public void sendSms(String mobile, String msg) {
        validateCircuitState();
        String recipient = normalizePhoneNumber(mobile);
        String sender = resolveSmsSender();
        MessageCreator message = createMessage(recipient, sender, msg);
        sendMessage("SMS", recipient, sender, message);
    }

    @Retryable(
            retryFor = RuntimeException.class,
            maxAttemptsExpression = "${app.retry.sms.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${app.retry.sms.delay-ms:500}")
    )
    public void sendWhatsApp(String to, String msg) {
        validateCircuitState();
        String recipient = resolveWhatsAppRecipient(to);
        String sender = resolveWhatsAppSender();
        MessageCreator message = createMessage(recipient, sender, msg);
        sendMessage("WHATSAPP", recipient, sender, message);
    }

    public boolean isSmsConfigured() {
        return !isBlank(fromNumber);
    }

    public boolean isWhatsAppConfigured() {
        return !isBlank(whatsappFrom);
    }

    private String resolveSmsSender() {
        return normalizeConfiguredNumber(fromNumber, "SMS sender number is not configured");
    }

    private String resolveWhatsAppRecipient(String to) {
        String configuredRecipient = resolveConfiguredWhatsAppRecipient(to);
        String normalized = normalizePhoneNumber(stripWhatsAppPrefix(configuredRecipient));
        return addWhatsAppPrefix(normalized);
    }

    private String resolveWhatsAppSender() {
        String configuredSender = resolveConfiguredValue(whatsappFrom, "WhatsApp sender number is not configured");
        String normalized = normalizePhoneNumber(stripWhatsAppPrefix(configuredSender));
        return addWhatsAppPrefix(normalized);
    }

    private String resolveConfiguredWhatsAppRecipient(String to) {
        String recipient = isBlank(whatsappToDefault) ? to : whatsappToDefault;
        if (isBlank(recipient)) {
            throw new BadRequestException("WhatsApp 'to' number is required");
        }
        return recipient;
    }

    private String normalizeConfiguredNumber(String value, String missingMessage) {
        String configured = resolveConfiguredValue(value, missingMessage);
        return normalizePhoneNumber(normalizeConfiguredPhoneNumber(configured));
    }

    private String resolveConfiguredValue(String value, String missingMessage) {
        if (isBlank(value)) {
            throw new BadRequestException(missingMessage);
        }
        return value;
    }

    private MessageCreator createMessage(String to, String from, String body) {
        return Message.creator(new PhoneNumber(to), new PhoneNumber(from), body);
    }

    private void sendMessage(String channel, String recipient, String sender, MessageCreator message) {
        executeWithCircuitBreaker(() -> {
            Message response = message.create();
            log.info("Twilio {} sent successfully sid={} to={} from={}", channel, response.getSid(), recipient, sender);
        });
    }

    private String normalizePhoneNumber(String phoneNumber) {
        String normalized = phoneNumber == null ? null : phoneNumber.trim();
        validatePhoneNumber(normalized);
        return normalized;
    }

    private String normalizeConfiguredPhoneNumber(String phoneNumber) {
        String normalized = phoneNumber == null ? null : phoneNumber.trim();
        if (normalized != null && normalized.matches("\\d{10}")) {
            return INDIA_COUNTRY_CODE + normalized;
        }
        return normalized;
    }

    private void validatePhoneNumber(String phoneNumber) {
        if (isBlank(phoneNumber)) {
            throw new BadRequestException("Mobile number is required");
        }
        if (!phoneNumber.startsWith("+")) {
            throw new BadRequestException("Mobile number must start with '+' and follow E.164 format");
        }
        if (!phoneNumber.matches(E164_PATTERN)) {
            throw new BadRequestException("Invalid mobile number format");
        }
    }

    private String stripWhatsAppPrefix(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        if (phoneNumber.startsWith(WHATSAPP_PREFIX)) {
            return phoneNumber.substring(WHATSAPP_PREFIX.length());
        }
        return phoneNumber;
    }

    private String addWhatsAppPrefix(String phoneNumber) {
        return WHATSAPP_PREFIX + phoneNumber;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void validateCircuitState() {
        if (smsCircuitBreaker.isOpen()) {
            throw new ExternalServiceUnavailableException("SMS service is temporarily unavailable");
        }
    }

    private void executeWithCircuitBreaker(Runnable operation) {
        try {
            operation.run();
            smsCircuitBreaker.recordSuccess();
        } catch (RuntimeException ex) {
            smsCircuitBreaker.recordFailure();
            throw ex;
        }
    }

    @Recover
    public void recoverSmsSend(RuntimeException ex, String mobile, String msg) {
        log.warn("Twilio SMS send failed after retries to={} errorType={} errorMessage={}",
                mobile, ex.getClass().getSimpleName(), ex.getMessage(), ex);
        throw new ExternalServiceUnavailableException("SMS service failed after retries", ex);
    }

    @Recover
    public void recoverWhatsAppSend(RuntimeException ex, String to, String msg) {
        log.warn("Twilio WhatsApp send failed after retries to={} errorType={} errorMessage={}",
                to, ex.getClass().getSimpleName(), ex.getMessage(), ex);
        throw new ExternalServiceUnavailableException("WhatsApp service failed after retries", ex);
    }
}
