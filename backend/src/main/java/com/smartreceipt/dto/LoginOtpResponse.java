package com.smartreceipt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginOtpResponse {
    @Builder.Default
    private boolean requiresOtp = true;
    private String email;
    private String message;
}
