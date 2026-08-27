package com.smartreceipt.service;

import com.smartreceipt.dto.AuthRequest;
import com.smartreceipt.dto.AuthResponse;
import com.smartreceipt.dto.LoginOtpResponse;
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

    /**
     * Step 1 of login: validate credentials, then send a login OTP.
     * Returns a LoginOtpResponse (no JWT) to force OTP verification before access.
     */
    public LoginOtpResponse login(AuthRequest request) {
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
            throw new UnauthorizedAccessException(
                    "Email address is not verified. Please verify your email with OTP before signing in.");
        }

        // Credentials are valid — generate and send login OTP (no JWT yet)
        MessageResponse otpResult = userService.initiateLoginOtp(normalizedEmail);
        log.info("Login credentials validated for {}. Login OTP sent.", normalizedEmail);

        return LoginOtpResponse.builder()
                .requiresOtp(true)
                .email(normalizedEmail)
                .message(otpResult.getMessage())
                .build();
    }

    /**
     * Step 2 of login: verify the login OTP, then issue the JWT.
     */
    public AuthResponse verifyLoginOtp(VerifyOtpRequest request) {
        User user = userService.verifyLoginOtp(request);
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

    /**
     * Resend login OTP (step 2 of login flow).
     */
    public MessageResponse resendLoginOtp(ResendOtpRequest request) {
        return userService.resendLoginOtp(request.getEmail());
    }
}
