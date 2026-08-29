package com.smartreceipt.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalSummary {
    private int totalParameters;
    private int normalCount;
    private int lowCount;
    private int highCount;
    private int abnormalCount;
    private int referenceUnavailableCount;
}
