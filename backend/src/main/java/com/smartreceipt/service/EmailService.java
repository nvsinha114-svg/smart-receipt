package com.smartreceipt.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final RestClient restClient;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${resend.api-key:}")
    private String resendApiKey;

    @Value("${resend.from:onboarding@resend.dev}")
    private String resendFrom;

    @Value("${app.email.mock-fallback-enabled:true}")
    private boolean mockFallbackEnabled;

    @Autowired(required = false)
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
        this.restClient = RestClient.builder().baseUrl("https://api.resend.com").build();
    }

    public String generate6DigitOtp() {
        int otp = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(otp);
    }

    public void sendOtpEmail(String recipientEmail, String otp) {
        String subject = "Smart Receipt - Email Verification Code";
        String text = "Welcome to Smart Receipt!\n\nYour 6-digit verification code is: " + otp + "\n\nThis code will expire in 5 minutes. Do not share this code with anyone.";
        sendEmail(recipientEmail, subject, text, otp, "REGISTRATION");
    }

    public void sendLoginOtpEmail(String recipientEmail, String otp) {
        String subject = "Smart Receipt - Login Verification Code";
        String text = "Hello,\n\nYou requested to sign in to Smart Receipt.\n\nYour 6-digit login verification code is: " + otp + "\n\nThis code will expire in 5 minutes. If you did not request this, please ignore this email and your account remains secure.\n\nDo not share this code with anyone.";
        sendEmail(recipientEmail, subject, text, otp, "LOGIN");
    }

    private void sendEmail(String recipientEmail, String subject, String text, String otp, String purpose) {
        boolean isProd = isProductionEnvironment();
        boolean effectiveMockFallback = mockFallbackEnabled && !isProd;

        // 1. Try Resend if configured
        if (resendApiKey != null && !resendApiKey.trim().isEmpty()) {
            log.info("Sending {} OTP email to: {} via Resend API", purpose, recipientEmail);
            try {
                ResendEmailRequest request = new ResendEmailRequest(resendFrom, recipientEmail, subject, text);
                ResponseEntity<String> response = restClient.post()
                        .uri("/emails")
                        .header("Authorization", "Bearer " + resendApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .toEntity(String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("Successfully sent {} email via Resend to {}", purpose, recipientEmail);
                    return;
                } else {
                    String errorBody = response.getBody();
                    log.error("Failed to send email via Resend. Status: {}, Response: {}", response.getStatusCode(), errorBody);
                    throw new RuntimeException("Resend API failed: " + response.getStatusCode() + " - " + errorBody);
                }
            } catch (Exception e) {
                log.error("Error sending {} email via Resend: {}", purpose, e.getMessage());
                throw new RuntimeException("Resend email delivery failed: " + e.getMessage(), e);
            }
        }

        // 2. Try SMTP if configured and Resend is not set
        if (mailSender != null) {
            log.info("Sending {} OTP email to: {} via SMTP", purpose, recipientEmail);
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(recipientEmail);
                message.setSubject(subject);
                message.setText(text);
                mailSender.send(message);
                log.info("Successfully sent {} OTP email via SMTP to {}", purpose, recipientEmail);
                return;
            } catch (Exception e) {
                log.error("Failed to send {} OTP via SMTP: {}", purpose, e.getMessage());
                if (!effectiveMockFallback) {
                    throw new RuntimeException("SMTP email delivery failed and mock fallback is disabled in production environment", e);
                }
            }
        } else {
            log.warn("JavaMailSender is not configured.");
            if (!effectiveMockFallback) {
                throw new RuntimeException("No email sender (Resend or SMTP) configured and mock fallback is disabled in production environment");
            }
        }

        // 3. Fallback only in development/testing
        if (effectiveMockFallback) {
            log.info("=================================================");
            log.info("MOCK {} EMAIL SENT TO: {}", purpose, recipientEmail);
            log.info("VERIFICATION CODE (OTP): {}", otp);
            log.info("=================================================");
        }
    }

    private boolean isProductionEnvironment() {
        return System.getenv("RENDER") != null 
            || System.getenv("PORT") != null 
            || "prod".equalsIgnoreCase(System.getProperty("spring.profiles.active"))
            || "production".equalsIgnoreCase(System.getProperty("spring.profiles.active"));
    }

    private static class ResendEmailRequest {
        public final String from;
        public final List<String> to;
        public final String subject;
        public final String html;

        public ResendEmailRequest(String from, String to, String subject, String text) {
            this.from = from;
            this.to = Collections.singletonList(to);
            this.subject = subject;
            this.html = text.replace("\n", "<br>");
        }
    }
}
