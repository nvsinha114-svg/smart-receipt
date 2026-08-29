package com.smartreceipt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalParameterAIResponse {
    private String testName;
    private String value;
    private BigDecimal numericValue;
    private String unit;
    private String referenceRange;
    private String status; // LOW, NORMAL, HIGH, ABNORMAL, POSITIVE, NEGATIVE, DETECTED, NOT_DETECTED, REFERENCE_NOT_AVAILABLE, UNDETERMINED
    private String category;
    private String labFlag;
    private Double confidence;
}
