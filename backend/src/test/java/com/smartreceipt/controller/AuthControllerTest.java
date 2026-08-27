package com.smartreceipt.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartreceipt.dto.AuthRequest;
import com.smartreceipt.dto.AuthResponse;
import com.smartreceipt.dto.LoginOtpResponse;
import com.smartreceipt.dto.MessageResponse;
import com.smartreceipt.dto.RegisterRequest;
import com.smartreceipt.dto.ResendOtpRequest;
import com.smartreceipt.dto.VerifyOtpRequest;
import com.smartreceipt.entity.Role;
import com.smartreceipt.exception.DuplicateResourceException;
import com.smartreceipt.exception.UnauthorizedAccessException;
import com.smartreceipt.security.JwtAuthenticationFilter;
import com.smartreceipt.security.JwtService;
import com.smartreceipt.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private RegisterRequest registerRequest;
    private AuthRequest authRequest;
    private AuthResponse authResponse;
    private MessageResponse messageResponse;
    private LoginOtpResponse loginOtpResponse;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterRequest.builder()
                .name("Alex Smith")
                .email("alex@example.com")
                .password("password123")
                .role(Role.USER)
                .build();

        authRequest = AuthRequest.builder()
                .email("alex@example.com")
                .password("password123")
                .build();

        authResponse = AuthResponse.builder()
                .token("valid_jwt_token")
                .type("Bearer")
                .id("user-100")
                .name("Alex Smith")
                .email("alex@example.com")
                .role(Role.USER)
                .build();

        messageResponse = MessageResponse.builder()
                .message("Verification code sent successfully to alex@example.com")
                .email("alex@example.com")
                .build();

        loginOtpResponse = LoginOtpResponse.builder()
                .requiresOtp(true)
                .email("alex@example.com")
                .message("Login verification code sent to alex@example.com.")
                .build();
    }

    // ─── Registration ────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/register - Success (200 OK - OTP Sent)")
    void register_Success() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(messageResponse);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Verification code sent successfully to alex@example.com"))
                .andExpect(jsonPath("$.email").value("alex@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/register - Reject Invalid Email Format (400 Bad Request)")
    void register_InvalidEmail_Returns400() throws Exception {
        RegisterRequest invalidReq = RegisterRequest.builder()
                .name("Alex Smith")
                .email("abc@gmail") // invalid Gmail format
                .password("password123")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register - Duplicate Email (409 Conflict)")
    void register_DuplicateEmail_Returns409() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateResourceException("User already exists with email: alex@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isConflict());
    }

    // ─── Registration OTP Verification ───────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/verify-otp - Success (201 Created)")
    void verifyOtp_Success() throws Exception {
        VerifyOtpRequest verifyReq = VerifyOtpRequest.builder()
                .email("alex@example.com")
                .otp("123456")
                .build();

        when(authService.verifyOtp(any(VerifyOtpRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("valid_jwt_token"))
                .andExpect(jsonPath("$.email").value("alex@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/verify-otp - Invalid OTP (400 Bad Request)")
    void verifyOtp_InvalidOtp_Returns400() throws Exception {
        VerifyOtpRequest verifyReq = VerifyOtpRequest.builder()
                .email("alex@example.com")
                .otp("000000")
                .build();

        when(authService.verifyOtp(any(VerifyOtpRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid OTP code. Please check and try again."));

        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid OTP code. Please check and try again."));
    }

    // ─── Resend Registration OTP ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/resend-otp - Success (200 OK)")
    void resendOtp_Success() throws Exception {
        ResendOtpRequest resendReq = ResendOtpRequest.builder()
                .email("alex@example.com")
                .build();

        when(authService.resendOtp(any(ResendOtpRequest.class))).thenReturn(messageResponse);

        mockMvc.perform(post("/api/auth/resend-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resendReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alex@example.com"));
    }

    // ─── Login (Step 1 — Password Only, Returns requiresOtp) ─────────────────

    @Test
    @DisplayName("POST /api/auth/login - Success (200 OK — Login OTP Sent, requiresOtp=true)")
    void login_Success_ReturnsRequiresOtp() throws Exception {
        when(authService.login(any(AuthRequest.class))).thenReturn(loginOtpResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresOtp").value(true))
                .andExpect(jsonPath("$.email").value("alex@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Invalid Credentials (401 Unauthorized)")
    void login_InvalidCredentials_Returns401() throws Exception {
        when(authService.login(any(AuthRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Email Not Verified (403 Forbidden)")
    void login_EmailNotVerified_Returns403() throws Exception {
        when(authService.login(any(AuthRequest.class)))
                .thenThrow(new UnauthorizedAccessException(
                        "Email address is not verified. Please verify your email with OTP before signing in."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Email address is not verified. Please verify your email with OTP before signing in."));
    }

    // ─── Login OTP Verification (Step 2 — Issues JWT) ─────────────────────────

    @Test
    @DisplayName("POST /api/auth/verify-login-otp - Success (200 OK — JWT Issued)")
    void verifyLoginOtp_Success() throws Exception {
        VerifyOtpRequest verifyReq = VerifyOtpRequest.builder()
                .email("alex@example.com")
                .otp("654321")
                .build();

        when(authService.verifyLoginOtp(any(VerifyOtpRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/verify-login-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("valid_jwt_token"))
                .andExpect(jsonPath("$.email").value("alex@example.com"))
                .andExpect(jsonPath("$.type").value("Bearer"));
    }

    @Test
    @DisplayName("POST /api/auth/verify-login-otp - Invalid OTP (400 Bad Request)")
    void verifyLoginOtp_InvalidOtp_Returns400() throws Exception {
        VerifyOtpRequest verifyReq = VerifyOtpRequest.builder()
                .email("alex@example.com")
                .otp("000000")
                .build();

        when(authService.verifyLoginOtp(any(VerifyOtpRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid OTP code. 4 attempt(s) remaining."));

        mockMvc.perform(post("/api/auth/verify-login-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid OTP code. 4 attempt(s) remaining."));
    }

    @Test
    @DisplayName("POST /api/auth/verify-login-otp - Expired OTP (400 Bad Request)")
    void verifyLoginOtp_ExpiredOtp_Returns400() throws Exception {
        VerifyOtpRequest verifyReq = VerifyOtpRequest.builder()
                .email("alex@example.com")
                .otp("123456")
                .build();

        when(authService.verifyLoginOtp(any(VerifyOtpRequest.class)))
                .thenThrow(new IllegalArgumentException("Login OTP code has expired. Please sign in again."));

        mockMvc.perform(post("/api/auth/verify-login-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Login OTP code has expired. Please sign in again."));
    }

    @Test
    @DisplayName("POST /api/auth/verify-login-otp - No Pending OTP (400 Bad Request)")
    void verifyLoginOtp_NoPendingOtp_Returns400() throws Exception {
        VerifyOtpRequest verifyReq = VerifyOtpRequest.builder()
                .email("alex@example.com")
                .otp("123456")
                .build();

        when(authService.verifyLoginOtp(any(VerifyOtpRequest.class)))
                .thenThrow(new IllegalArgumentException(
                        "No pending login OTP found for email: alex@example.com. Please sign in again."));

        mockMvc.perform(post("/api/auth/verify-login-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "No pending login OTP found for email: alex@example.com. Please sign in again."));
    }

    // ─── Resend Login OTP ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/resend-login-otp - Success (200 OK)")
    void resendLoginOtp_Success() throws Exception {
        ResendOtpRequest resendReq = ResendOtpRequest.builder()
                .email("alex@example.com")
                .build();

        MessageResponse loginResendResponse = MessageResponse.builder()
                .message("A new login OTP code has been sent to alex@example.com.")
                .email("alex@example.com")
                .build();

        when(authService.resendLoginOtp(any(ResendOtpRequest.class))).thenReturn(loginResendResponse);

        mockMvc.perform(post("/api/auth/resend-login-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resendReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alex@example.com"))
                .andExpect(jsonPath("$.message").value("A new login OTP code has been sent to alex@example.com."));
    }

    @Test
    @DisplayName("POST /api/auth/resend-login-otp - Rate Limited (400 Bad Request)")
    void resendLoginOtp_RateLimited_Returns400() throws Exception {
        ResendOtpRequest resendReq = ResendOtpRequest.builder()
                .email("alex@example.com")
                .build();

        when(authService.resendLoginOtp(any(ResendOtpRequest.class)))
                .thenThrow(new IllegalArgumentException("Please wait 60 seconds before requesting a new OTP."));

        mockMvc.perform(post("/api/auth/resend-login-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resendReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please wait 60 seconds before requesting a new OTP."));
    }
}
