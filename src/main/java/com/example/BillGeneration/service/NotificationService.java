package com.example.BillGeneration.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class NotificationService {

    private final EmailService emailService;
    private final SmsService smsService;

    public NotificationService(EmailService emailService, SmsService smsService) {
        this.emailService = emailService;
        this.smsService = smsService;
    }

    @Async("notificationExecutor")
    public CompletableFuture<Void> sendSms(String mobile, String msg) {
        smsService.sendSms(mobile, msg);
        return CompletableFuture.completedFuture(null);
    }

    @Async("notificationExecutor")
    public CompletableFuture<Void> sendEmail(String to, String subject, String body) {
        emailService.sendMail(to, subject, body);
        return CompletableFuture.completedFuture(null);
    }

    @Async("notificationExecutor")
    public CompletableFuture<Void> sendWhatsApp(String to, String msg) {
        smsService.sendWhatsApp(to, msg);
        return CompletableFuture.completedFuture(null);
    }

    public boolean isSmsConfigured() {
        return smsService.isSmsConfigured();
    }

    public boolean isWhatsAppConfigured() {
        return smsService.isWhatsAppConfigured();
    }
}
