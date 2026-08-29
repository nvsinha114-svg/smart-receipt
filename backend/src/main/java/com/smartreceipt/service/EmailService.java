package com.smartreceipt.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final RestClient restClient;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${brevo.api-key:}")
    private String brevoApiKey;

    @Value("${brevo.from:}")
    private String mailFrom;

    @Value("${app.email.mock-fallback-enabled:false}")
    private boolean mockFallbackEnabled;

    public EmailService() {
        this.restClient = RestClient.builder().baseUrl("https://api.brevo.com/v3").build();
    }

    // Visible for testing
    EmailService(RestClient restClient) {
        this.restClient = restClient;
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

        if (brevoApiKey != null && !brevoApiKey.trim().isEmpty()) {
            log.info("Sending {} OTP email to recipient via Brevo API", purpose);
            try {
                BrevoEmailRequest request = new BrevoEmailRequest(mailFrom, recipientEmail, subject, text);
                ResponseEntity<String> response = restClient.post()
                        .uri("/smtp/email")
                        .header("accept", "application/json")
                        .header("api-key", brevoApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .toEntity(String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("OTP email submitted successfully through Brevo");
                    return;
                } else {
                    int statusCode = response.getStatusCode().value();
                    String statusText = response.getStatusCode().toString();
                    throw new RuntimeException("Brevo API returned status code " + statusCode + " - " + statusText);
                }
            } catch (org.springframework.web.client.RestClientResponseException e) {
                int statusCode = e.getStatusCode().value();
                String statusText = e.getStatusText();
                String errorMsg;
                if (statusCode == 401) {
                    errorMsg = "Brevo email delivery failed: 401 Unauthorized (Invalid API Key)";
                } else if (statusCode == 403) {
                    errorMsg = "Brevo email delivery failed: 403 Forbidden (Sender not verified or account suspended)";
                } else if (statusCode == 400) {
                    errorMsg = "Brevo email delivery failed: 400 Bad Request (Invalid parameters)";
                } else if (statusCode == 429) {
                    errorMsg = "Brevo email delivery failed: 429 Too Many Requests (Rate limit exceeded)";
                } else if (statusCode >= 500) {
                    errorMsg = "Brevo email delivery failed: " + statusCode + " Server Error (Brevo service unavailable)";
                } else {
                    errorMsg = "Brevo email delivery failed: " + statusCode + " " + statusText;
                }
                log.error("Brevo API call failed: {}", errorMsg);
                if (!effectiveMockFallback) {
                    throw new RuntimeException(errorMsg);
                }
            } catch (Exception e) {
                log.error("Error sending {} email via Brevo: {}", purpose, e.getMessage());
                if (!effectiveMockFallback) {
                    throw new RuntimeException("Brevo email delivery failed: " + e.getMessage(), e);
                }
            }
        } else {
            log.warn("Brevo API Key is not configured.");
            if (!effectiveMockFallback) {
                throw new RuntimeException("Brevo email delivery failed: API key is not configured and mock fallback is disabled");
            }
        }

        if (effectiveMockFallback) {
            log.info("=================================================");
            log.info("MOCK {} EMAIL SENT TO RECIPIENT", purpose);
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

    private static class BrevoEmailRequest {
        public final Sender sender;
        public final List<Recipient> to;
        public final String subject;
        public final String htmlContent;

        public BrevoEmailRequest(String from, String toEmail, String subject, String text) {
            this.sender = new Sender("Smart Receipt", from);
            this.to = Collections.singletonList(new Recipient(toEmail));
            this.subject = subject;
            this.htmlContent = text.replace("\n", "<br>");
        }
    }

    private static class Sender {
        public final String name;
        public final String email;

        public Sender(String name, String email) {
            this.name = name;
            this.email = email;
        }
    }

    private static class Recipient {
        public final String email;

        public Recipient(String email) {
            this.email = email;
        }
    }
}
