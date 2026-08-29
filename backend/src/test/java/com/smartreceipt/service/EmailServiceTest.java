package com.smartreceipt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.springframework.http.HttpMethod;

class EmailServiceTest {

    private EmailService emailService;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.brevo.com/v3");
        this.mockServer = MockRestServiceServer.bindTo(builder).build();
        this.emailService = new EmailService(builder.build());
        
        ReflectionTestUtils.setField(emailService, "brevoApiKey", "test-api-key");
        ReflectionTestUtils.setField(emailService, "mailFrom", "nvsinha114@gmail.com");
        ReflectionTestUtils.setField(emailService, "mockFallbackEnabled", false);
    }

    @Test
    void sendOtpEmail_Success() {
        mockServer.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("accept", "application/json"))
                .andExpect(header("api-key", "test-api-key"))
                .andExpect(header("content-type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().json("{\"sender\":{\"name\":\"Smart Receipt\",\"email\":\"nvsinha114@gmail.com\"},\"to\":[{\"email\":\"recipient@example.com\"}],\"subject\":\"Smart Receipt - Email Verification Code\",\"htmlContent\":\"Welcome to Smart Receipt!<br><br>Your 6-digit verification code is: 123456<br><br>This code will expire in 5 minutes. Do not share this code with anyone.\"}"))
                .andRespond(withSuccess("{\"messageId\":\"abc-123\"}", MediaType.APPLICATION_JSON));

        assertDoesNotThrow(() -> emailService.sendOtpEmail("recipient@example.com", "123456"));
        mockServer.verify();
    }

    @Test
    void sendOtpEmail_Unauthorized_401() {
        mockServer.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                emailService.sendOtpEmail("recipient@example.com", "123456"));

        assertEquals("Brevo email delivery failed: 401 Unauthorized (Invalid API Key)", exception.getMessage());
    }

    @Test
    void sendOtpEmail_Forbidden_403() {
        mockServer.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                emailService.sendOtpEmail("recipient@example.com", "123456"));

        assertEquals("Brevo email delivery failed: 403 Forbidden (Sender not verified or account suspended)", exception.getMessage());
    }

    @Test
    void sendOtpEmail_BadRequest_400() {
        mockServer.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                emailService.sendOtpEmail("recipient@example.com", "123456"));

        assertEquals("Brevo email delivery failed: 400 Bad Request (Invalid parameters)", exception.getMessage());
    }

    @Test
    void sendOtpEmail_RateLimit_429() {
        mockServer.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                emailService.sendOtpEmail("recipient@example.com", "123456"));

        assertEquals("Brevo email delivery failed: 429 Too Many Requests (Rate limit exceeded)", exception.getMessage());
    }

    @Test
    void sendOtpEmail_ServerError_500() {
        mockServer.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                emailService.sendOtpEmail("recipient@example.com", "123456"));

        assertEquals("Brevo email delivery failed: 500 Server Error (Brevo service unavailable)", exception.getMessage());
    }

    @Test
    void sendOtpEmail_MockFallbackDisabled() {
        ReflectionTestUtils.setField(emailService, "brevoApiKey", "");
        ReflectionTestUtils.setField(emailService, "mockFallbackEnabled", false);

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                emailService.sendOtpEmail("recipient@example.com", "123456"));

        assertEquals("Brevo email delivery failed: API key is not configured and mock fallback is disabled", exception.getMessage());
    }

    @Test
    void sendOtpEmail_MockFallbackEnabled() {
        ReflectionTestUtils.setField(emailService, "brevoApiKey", "");
        ReflectionTestUtils.setField(emailService, "mockFallbackEnabled", true);

        // Should complete successfully without throwing exception as fallback is enabled and not in production
        assertDoesNotThrow(() -> emailService.sendOtpEmail("recipient@example.com", "123456"));
    }
}
