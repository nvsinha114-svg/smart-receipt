package com.smartreceipt.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired(required = false)
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public String generate6DigitOtp() {
        int otp = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(otp);
    }

    public void sendOtpEmail(String recipientEmail, String otp) {
        log.info("Sending OTP verification email to: {}", recipientEmail);

        if (mailSender != null) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(recipientEmail);
                message.setSubject("Smart Receipt - Email Verification Code");
                message.setText("Welcome to Smart Receipt!\n\nYour 6-digit verification code is: " + otp + "\n\nThis code will expire in 5 minutes. Do not share this code with anyone.");
                mailSender.send(message);
                log.info("Successfully sent OTP email via SMTP to {}", recipientEmail);
                return;
            } catch (Exception e) {
                log.warn("Failed to send OTP via SMTP (MailSender error: {}). Falling back to secure log delivery.", e.getMessage());
            }
        } else {
            log.info("JavaMailSender is not configured. Delivery handled via logger output.");
        }

        // Fallback for development/testing environments without external SMTP
        log.info("=================================================");
        log.info("MOCK EMAIL SENT TO: {}", recipientEmail);
        log.info("VERIFICATION CODE (OTP): {}", otp);
        log.info("=================================================");
    }
}
