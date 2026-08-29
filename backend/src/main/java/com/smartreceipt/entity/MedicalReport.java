package com.smartreceipt.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "medical_reports")
public class MedicalReport {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Builder.Default
    private String documentType = "MEDICAL_REPORT";

    private String fileName;
    private String patientName;
    private LocalDate reportDate;
    private String laboratoryName;

    @Builder.Default
    private List<MedicalParameter> parameters = new ArrayList<>();

    @Builder.Default
    private List<String> categories = new ArrayList<>();

    private MedicalSummary summary;

    @Builder.Default
    private List<String> overallNotes = new ArrayList<>();

    @Builder.Default
    private String disclaimer = "This analysis is for informational purposes only and is not a medical diagnosis. Please consult a qualified healthcare professional for interpretation of your results.";

    @CreatedDate
    private LocalDateTime createdAt;
}
