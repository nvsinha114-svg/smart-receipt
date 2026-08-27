package com.smartreceipt.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "otp_verifications")
public class OtpVerification {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String otpHash; // BCrypt hashed OTP

    private String tempName;
    private String tempPassword; // Hashed password
    private Role tempRole;

    private LocalDateTime expiresAt;
    private LocalDateTime lastSentAt;

    @Builder.Default
    private String purpose = "REGISTRATION"; // "REGISTRATION" or "LOGIN"

    @Builder.Default
    private int attempts = 0;

    @Indexed(name = "expire_at_index", expireAfterSeconds = 0)
    private LocalDateTime createdAt;
}
