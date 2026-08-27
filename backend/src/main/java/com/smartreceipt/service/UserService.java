package com.smartreceipt.service;

import com.smartreceipt.dto.MessageResponse;
import com.smartreceipt.dto.RegisterRequest;
import com.smartreceipt.dto.ResendOtpRequest;
import com.smartreceipt.dto.VerifyOtpRequest;
import com.smartreceipt.entity.OtpVerification;
import com.smartreceipt.entity.Role;
import com.smartreceipt.entity.User;
import com.smartreceipt.exception.DuplicateResourceException;
import com.smartreceipt.exception.ResourceNotFoundException;
import com.smartreceipt.repository.OtpVerificationRepository;
import com.smartreceipt.repository.UserRepository;
import com.smartreceipt.util.EmailValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OtpVerificationRepository otpVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public MessageResponse initiateRegistration(RegisterRequest request) {
        String rawEmail = request.getEmail();
        if (!EmailValidator.isValid(rawEmail)) {
            throw new IllegalArgumentException("Invalid email address format. Please enter a valid email.");
        }

        String normalizedEmail = rawEmail.toLowerCase().trim();

        // 1. Check if account already exists and is verified
        Optional<User> existingUserOpt = userRepository.findByEmail(normalizedEmail);
        if (existingUserOpt.isPresent() && existingUserOpt.get().isEmailVerified()) {
            throw new DuplicateResourceException("User already exists with email: " + request.getEmail());
        }

        // 2. Check rate limiting (60s cooldown)
        Optional<OtpVerification> existingOtpOpt = otpVerificationRepository.findByEmail(normalizedEmail);
        if (existingOtpOpt.isPresent()) {
            OtpVerification existingOtp = existingOtpOpt.get();
            if (existingOtp.getLastSentAt() != null && existingOtp.getLastSentAt().plusSeconds(60).isAfter(LocalDateTime.now())) {
                throw new IllegalArgumentException("Please wait 60 seconds before requesting a new OTP.");
            }
        }

        // 3. Generate 6-digit OTP
        String otp = emailService.generate6DigitOtp();
        String otpHash = passwordEncoder.encode(otp);

        Role role = request.getRole() != null ? request.getRole() : Role.USER;

        OtpVerification otpVerification = existingOtpOpt.orElse(new OtpVerification());
        otpVerification.setEmail(normalizedEmail);
        otpVerification.setOtpHash(otpHash);
        otpVerification.setTempName(request.getName().trim());
        otpVerification.setTempPassword(passwordEncoder.encode(request.getPassword()));
        otpVerification.setTempRole(role);
        otpVerification.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otpVerification.setLastSentAt(LocalDateTime.now());
        otpVerification.setCreatedAt(LocalDateTime.now());
        otpVerification.setAttempts(0);
        otpVerification.setPurpose("REGISTRATION");

        otpVerificationRepository.save(otpVerification);

        // 4. Send email
        emailService.sendOtpEmail(normalizedEmail, otp);

        return MessageResponse.builder()
                .message("Verification code sent successfully to " + normalizedEmail + ". Please verify your email to complete registration.")
                .email(normalizedEmail)
                .build();
    }

    public User verifyOtpAndCreateUser(VerifyOtpRequest request) {
        String rawEmail = request.getEmail();
        if (!EmailValidator.isValid(rawEmail)) {
            throw new IllegalArgumentException("Invalid email address format.");
        }

        String normalizedEmail = rawEmail.toLowerCase().trim();

        OtpVerification otpRecord = otpVerificationRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("No pending registration found for email: " + request.getEmail() + ". Please register first."));

        // Check expiration
        if (LocalDateTime.now().isAfter(otpRecord.getExpiresAt())) {
            throw new IllegalArgumentException("OTP code has expired. Please request a new OTP code.");
        }

        // Check attempt limit (max 3)
        if (otpRecord.getAttempts() >= 3) {
            throw new IllegalArgumentException("Maximum verification attempts exceeded. Please request a new OTP code.");
        }

        // Verify OTP
        if (!passwordEncoder.matches(request.getOtp().trim(), otpRecord.getOtpHash())) {
            otpRecord.setAttempts(otpRecord.getAttempts() + 1);
            otpVerificationRepository.save(otpRecord);
            throw new IllegalArgumentException("Invalid OTP code. Please check and try again.");
        }

        // Create or update verified user
        User user = userRepository.findByEmail(normalizedEmail)
                .orElse(User.builder().email(normalizedEmail).build());

        user.setName(otpRecord.getTempName());
        user.setPassword(otpRecord.getTempPassword());
        user.setRole(otpRecord.getTempRole() != null ? otpRecord.getTempRole() : Role.USER);
        user.setEmailVerified(true);

        User savedUser = userRepository.save(user);

        // Delete temporary OTP record
        otpVerificationRepository.deleteByEmail(normalizedEmail);

        log.info("User successfully created and email verified for: {}", normalizedEmail);
        return savedUser;
    }

    public MessageResponse resendOtp(ResendOtpRequest request) {
        String rawEmail = request.getEmail();
        if (!EmailValidator.isValid(rawEmail)) {
            throw new IllegalArgumentException("Invalid email address format.");
        }

        String normalizedEmail = rawEmail.toLowerCase().trim();

        OtpVerification otpRecord = otpVerificationRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("No pending registration found for email: " + request.getEmail()));

        // Cooldown check (60 seconds)
        if (otpRecord.getLastSentAt() != null && otpRecord.getLastSentAt().plusSeconds(60).isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Please wait 60 seconds before requesting a new OTP.");
        }

        String newOtp = emailService.generate6DigitOtp();
        otpRecord.setOtpHash(passwordEncoder.encode(newOtp));
        otpRecord.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otpRecord.setLastSentAt(LocalDateTime.now());
        otpRecord.setAttempts(0);

        otpVerificationRepository.save(otpRecord);

        emailService.sendOtpEmail(normalizedEmail, newOtp);

        return MessageResponse.builder()
                .message("A new OTP code has been sent to " + normalizedEmail + ".")
                .email(normalizedEmail)
                .build();
    }

    public MessageResponse initiateLoginOtp(String email) {
        String normalizedEmail = email.toLowerCase().trim();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        if (!user.isEmailVerified()) {
            throw new com.smartreceipt.exception.UnauthorizedAccessException(
                    "Email address is not verified. Please verify your email with OTP before signing in.");
        }

        // Delete any existing LOGIN OTP for this email to avoid stale records
        OtpVerification existingOtp = otpVerificationRepository.findByEmail(normalizedEmail).orElse(null);
        if (existingOtp != null) {
            if ("LOGIN".equals(existingOtp.getPurpose())) {
                // Rate limit check
                if (existingOtp.getLastSentAt() != null
                        && existingOtp.getLastSentAt().plusSeconds(60).isAfter(LocalDateTime.now())) {
                    throw new IllegalArgumentException("Please wait 60 seconds before requesting a new OTP.");
                }
                // Reuse existing record
                String newOtp = emailService.generate6DigitOtp();
                existingOtp.setOtpHash(passwordEncoder.encode(newOtp));
                existingOtp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
                existingOtp.setLastSentAt(LocalDateTime.now());
                existingOtp.setCreatedAt(LocalDateTime.now());
                existingOtp.setAttempts(0);
                otpVerificationRepository.save(existingOtp);
                emailService.sendLoginOtpEmail(normalizedEmail, newOtp);
                return MessageResponse.builder()
                        .message("Login verification code sent to " + normalizedEmail + ".")
                        .email(normalizedEmail)
                        .build();
            } else {
                // There's a REGISTRATION OTP — don't overwrite; the user can't log in without verifying first
                throw new com.smartreceipt.exception.UnauthorizedAccessException(
                        "Email address is not verified. Please verify your email with OTP before signing in.");
            }
        }

        String otp = emailService.generate6DigitOtp();
        String otpHash = passwordEncoder.encode(otp);

        OtpVerification loginOtp = OtpVerification.builder()
                .email(normalizedEmail)
                .otpHash(otpHash)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .lastSentAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .attempts(0)
                .purpose("LOGIN")
                .build();

        otpVerificationRepository.save(loginOtp);
        emailService.sendLoginOtpEmail(normalizedEmail, otp);

        return MessageResponse.builder()
                .message("Login verification code sent to " + normalizedEmail + ".")
                .email(normalizedEmail)
                .build();
    }

    public User verifyLoginOtp(VerifyOtpRequest request) {
        String rawEmail = request.getEmail();
        if (!EmailValidator.isValid(rawEmail)) {
            throw new IllegalArgumentException("Invalid email address format.");
        }

        String normalizedEmail = rawEmail.toLowerCase().trim();

        OtpVerification otpRecord = otpVerificationRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No pending login OTP found for email: " + request.getEmail() + ". Please sign in again."));

        if (!"LOGIN".equals(otpRecord.getPurpose())) {
            throw new IllegalArgumentException(
                    "No pending login OTP found. Please sign in again.");
        }

        // Check expiration
        if (LocalDateTime.now().isAfter(otpRecord.getExpiresAt())) {
            otpVerificationRepository.deleteByEmail(normalizedEmail);
            throw new IllegalArgumentException("Login OTP code has expired. Please sign in again.");
        }

        // Check attempt limit (max 5 for login)
        if (otpRecord.getAttempts() >= 5) {
            otpVerificationRepository.deleteByEmail(normalizedEmail);
            throw new IllegalArgumentException("Maximum verification attempts exceeded. Please sign in again.");
        }

        // Verify OTP
        if (!passwordEncoder.matches(request.getOtp().trim(), otpRecord.getOtpHash())) {
            otpRecord.setAttempts(otpRecord.getAttempts() + 1);
            otpVerificationRepository.save(otpRecord);
            int remaining = 5 - otpRecord.getAttempts();
            throw new IllegalArgumentException("Invalid OTP code. " + remaining + " attempt(s) remaining.");
        }

        // OTP is valid — delete it (prevent reuse)
        otpVerificationRepository.deleteByEmail(normalizedEmail);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + normalizedEmail));

        log.info("Login OTP verified successfully for: {}", normalizedEmail);
        return user;
    }

    public MessageResponse resendLoginOtp(String email) {
        String normalizedEmail = email.toLowerCase().trim();

        OtpVerification otpRecord = otpVerificationRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No pending login OTP found for email: " + email + ". Please sign in again."));

        if (!"LOGIN".equals(otpRecord.getPurpose())) {
            throw new IllegalArgumentException("No pending login OTP found. Please sign in again.");
        }

        // Cooldown check (60 seconds)
        if (otpRecord.getLastSentAt() != null
                && otpRecord.getLastSentAt().plusSeconds(60).isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Please wait 60 seconds before requesting a new OTP.");
        }

        String newOtp = emailService.generate6DigitOtp();
        otpRecord.setOtpHash(passwordEncoder.encode(newOtp));
        otpRecord.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otpRecord.setLastSentAt(LocalDateTime.now());
        otpRecord.setAttempts(0);
        otpVerificationRepository.save(otpRecord);

        emailService.sendLoginOtpEmail(normalizedEmail, newOtp);

        return MessageResponse.builder()
                .message("A new login OTP code has been sent to " + normalizedEmail + ".")
                .email(normalizedEmail)
                .build();
    }

    public User registerUser(RegisterRequest request) {
        // Backwards-compatible registration helper creating verified user directly if required by tests
        String normalizedEmail = request.getEmail().toLowerCase().trim();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("User already exists with email: " + request.getEmail());
        }

        Role role = request.getRole() != null ? request.getRole() : Role.USER;

        User user = User.builder()
                .name(request.getName().trim())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .emailVerified(true)
                .build();

        return userRepository.save(user);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    public User findById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }
}
