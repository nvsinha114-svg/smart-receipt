package com.smartreceipt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalReportAIResponse {
    private String patientName;
    private String reportDate; // YYYY-MM-DD
    private String laboratoryName;
    private List<String> categories;
    private List<MedicalParameterAIResponse> parameters;
    private List<String> overallNotes;
}
