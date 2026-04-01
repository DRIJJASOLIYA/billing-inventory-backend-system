package com.example.BillGeneration.config;

import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TwilioConfig {

    private static final Logger log = LoggerFactory.getLogger(TwilioConfig.class);

    private final String accountSid;
    private final String authToken;

    public TwilioConfig(
            @Value("${twilio.account.sid}") String accountSid,
            @Value("${twilio.auth.token}") String authToken
    ) {
        this.accountSid = accountSid;
        this.authToken = authToken;
    }

    @PostConstruct
    public void init() {
        if (accountSid == null || accountSid.isBlank() || authToken == null || authToken.isBlank()) {
            log.warn("Twilio credentials are not configured. SMS/WhatsApp features are disabled.");
            return;
        }
        initializeTwilioClient();
        logInitialization();
    }

    private void initializeTwilioClient() {
        Twilio.init(accountSid, authToken);
    }

    private void logInitialization() {
        log.info("Twilio initialized");
    }
}
