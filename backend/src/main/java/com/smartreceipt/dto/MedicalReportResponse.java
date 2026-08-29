package com.smartreceipt.dto;

import com.smartreceipt.entity.MedicalReport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalReportResponse {
    private boolean success;
    private String documentType;
    private MedicalReport analysis;
}
