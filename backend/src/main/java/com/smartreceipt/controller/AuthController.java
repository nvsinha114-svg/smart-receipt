package com.smartreceipt.controller;

import com.smartreceipt.dto.AuthRequest;
import com.smartreceipt.dto.AuthResponse;
import com.smartreceipt.dto.LoginOtpResponse;
import com.smartreceipt.dto.MessageResponse;
import com.smartreceipt.dto.RegisterRequest;
import com.smartreceipt.dto.ResendOtpRequest;
import com.smartreceipt.dto.VerifyOtpRequest;
import com.smartreceipt.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration, OTP verification, and login endpoints")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Initiate user registration", description = "Validates email format, generates 6-digit OTP, and sends verification code to email.")
    @ApiResponse(responseCode = "200", description = "OTP successfully sent")
    @ApiResponse(responseCode = "400", description = "Invalid email format or data")
    @ApiResponse(responseCode = "409", description = "Email already registered")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        MessageResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify registration OTP code", description = "Verifies 6-digit OTP code and creates user account upon successful verification.")
    @ApiResponse(responseCode = "201", description = "Account created and email verified successfully")
    @ApiResponse(responseCode = "400", description = "Invalid or expired OTP code")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        AuthResponse response = authService.verifyOtp(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/resend-otp")
    @Operation(summary = "Resend registration OTP code", description = "Resends a new 6-digit OTP code for registration with 60-second cooldown rate limiting.")
    @ApiResponse(responseCode = "200", description = "New OTP successfully sent")
    @ApiResponse(responseCode = "400", description = "Rate limit cooldown active or invalid request")
    public ResponseEntity<MessageResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        MessageResponse response = authService.resendOtp(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Step 1 — User login (password verification)", description = "Authenticates user credentials, enforces email verification check, sends a login OTP, and returns requiresOtp=true. No JWT is issued yet.")
    @ApiResponse(responseCode = "200", description = "Credentials valid — login OTP sent")
    @ApiResponse(responseCode = "401", description = "Invalid email or password")
    @ApiResponse(responseCode = "403", description = "Email not verified")
    public ResponseEntity<LoginOtpResponse> login(@Valid @RequestBody AuthRequest request) {
        LoginOtpResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-login-otp")
    @Operation(summary = "Step 2 — Verify login OTP and issue JWT", description = "Verifies the login OTP and returns a fully authenticated JWT token to be used for all protected API calls.")
    @ApiResponse(responseCode = "200", description = "Login OTP verified — JWT issued")
    @ApiResponse(responseCode = "400", description = "Invalid or expired login OTP")
    public ResponseEntity<AuthResponse> verifyLoginOtp(@Valid @RequestBody VerifyOtpRequest request) {
        AuthResponse response = authService.verifyLoginOtp(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-login-otp")
    @Operation(summary = "Resend login OTP code", description = "Resends a new login OTP code with 60-second cooldown rate limiting.")
    @ApiResponse(responseCode = "200", description = "New login OTP successfully sent")
    @ApiResponse(responseCode = "400", description = "Rate limit cooldown active or no pending login OTP")
    public ResponseEntity<MessageResponse> resendLoginOtp(@Valid @RequestBody ResendOtpRequest request) {
        MessageResponse response = authService.resendLoginOtp(request);
        return ResponseEntity.ok(response);
    }
}
