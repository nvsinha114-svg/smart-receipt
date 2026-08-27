package com.smartreceipt.service;

import com.smartreceipt.dto.AuthRequest;
import com.smartreceipt.dto.AuthResponse;
import com.smartreceipt.dto.LoginOtpResponse;
import com.smartreceipt.dto.MessageResponse;
import com.smartreceipt.dto.RegisterRequest;
import com.smartreceipt.dto.VerifyOtpRequest;
import com.smartreceipt.entity.Role;
import com.smartreceipt.entity.User;
import com.smartreceipt.exception.UnauthorizedAccessException;
import com.smartreceipt.repository.UserRepository;
import com.smartreceipt.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private User mockVerifiedUser;
    private User mockUnverifiedUser;

    @BeforeEach
    void setUp() {
        mockVerifiedUser = User.builder()
                .id("user-123")
                .name("Jane Doe")
                .email("jane@example.com")
                .password("encoded_password")
                .role(Role.USER)
                .emailVerified(true)
                .build();

        mockUnverifiedUser = User.builder()
                .id("user-456")
                .name("John Unverified")
                .email("john@example.com")
                .password("encoded_password")
                .role(Role.USER)
                .emailVerified(false)
                .build();
    }

    @Test
    @DisplayName("Should initiate registration and return message response")
    void register_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Jane Doe")
                .email("jane@example.com")
                .password("password123")
                .role(Role.USER)
                .build();

        MessageResponse mockResponse = MessageResponse.builder()
                .message("Verification code sent")
                .email("jane@example.com")
                .build();

        when(userService.initiateRegistration(request)).thenReturn(mockResponse);

        MessageResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("jane@example.com", response.getEmail());
    }

    @Test
    @DisplayName("Should verify registration OTP and return JWT token response")
    void verifyOtp_Success() {
        VerifyOtpRequest request = VerifyOtpRequest.builder()
                .email("jane@example.com")
                .otp("123456")
                .build();

        when(userService.verifyOtpAndCreateUser(request)).thenReturn(mockVerifiedUser);
        when(jwtService.generateToken(any())).thenReturn("mock_jwt_token");

        AuthResponse response = authService.verifyOtp(request);

        assertNotNull(response);
        assertEquals("mock_jwt_token", response.getToken());
        assertEquals("jane@example.com", response.getEmail());
    }

    @Test
    @DisplayName("Login Step 1 — Should validate credentials, send login OTP, return requiresOtp=true (no JWT)")
    void login_Success_ReturnsLoginOtpResponse() {
        AuthRequest request = AuthRequest.builder()
                .email("jane@example.com")
                .password("password123")
                .build();

        MessageResponse otpSentMsg = MessageResponse.builder()
                .message("Login verification code sent to jane@example.com.")
                .email("jane@example.com")
                .build();

        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(mockVerifiedUser));
        when(passwordEncoder.matches("password123", "encoded_password")).thenReturn(true);
        when(userService.initiateLoginOtp(anyString())).thenReturn(otpSentMsg);

        LoginOtpResponse response = authService.login(request);

        assertNotNull(response);
        assertTrue(response.isRequiresOtp());
        assertEquals("jane@example.com", response.getEmail());
    }

    @Test
    @DisplayName("Login Step 2 — Should verify login OTP and return JWT token")
    void verifyLoginOtp_Success() {
        VerifyOtpRequest request = VerifyOtpRequest.builder()
                .email("jane@example.com")
                .otp("654321")
                .build();

        when(userService.verifyLoginOtp(request)).thenReturn(mockVerifiedUser);
        when(jwtService.generateToken(any())).thenReturn("mock_jwt_token");

        AuthResponse response = authService.verifyLoginOtp(request);

        assertNotNull(response);
        assertEquals("mock_jwt_token", response.getToken());
        assertEquals("jane@example.com", response.getEmail());
    }

    @Test
    @DisplayName("Should reject login if email is not verified")
    void login_UnverifiedEmail_ThrowsException() {
        AuthRequest request = AuthRequest.builder()
                .email("john@example.com")
                .password("password123")
                .build();

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mockUnverifiedUser));
        when(passwordEncoder.matches("password123", "encoded_password")).thenReturn(true);

        assertThrows(UnauthorizedAccessException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("Should return generic authentication error for non-existent user")
    void login_NonExistentUser_ThrowsBadCredentialsException() {
        AuthRequest request = AuthRequest.builder()
                .email("nobody@example.com")
                .password("password123")
                .build();

        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }
}
