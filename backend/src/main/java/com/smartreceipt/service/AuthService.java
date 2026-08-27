package com.smartreceipt.service;

import com.smartreceipt.dto.AuthRequest;
import com.smartreceipt.dto.AuthResponse;
import com.smartreceipt.dto.MessageResponse;
import com.smartreceipt.dto.RegisterRequest;
import com.smartreceipt.dto.ResendOtpRequest;
import com.smartreceipt.dto.VerifyOtpRequest;
import com.smartreceipt.entity.User;
import com.smartreceipt.exception.UnauthorizedAccessException;
import com.smartreceipt.repository.UserRepository;
import com.smartreceipt.security.JwtService;
import com.smartreceipt.security.UserPrincipal;
import com.smartreceipt.util.EmailValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public MessageResponse register(RegisterRequest request) {
        return userService.initiateRegistration(request);
    }

    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        User user = userService.verifyOtpAndCreateUser(request);
        UserPrincipal userPrincipal = new UserPrincipal(user);
        String token = jwtService.generateToken(userPrincipal);

        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    public MessageResponse resendOtp(ResendOtpRequest request) {
        return userService.resendOtp(request);
    }

    public AuthResponse login(AuthRequest request) {
        String rawEmail = request.getEmail();
        if (!EmailValidator.isValid(rawEmail)) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String normalizedEmail = rawEmail.toLowerCase().trim();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (!user.isEmailVerified()) {
            throw new UnauthorizedAccessException("Email address is not verified. Please verify your email with OTP before signing in.");
        }

        UserPrincipal userPrincipal = new UserPrincipal(user);
        String token = jwtService.generateToken(userPrincipal);

        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }
}
