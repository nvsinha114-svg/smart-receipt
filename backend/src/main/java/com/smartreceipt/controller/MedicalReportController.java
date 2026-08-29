package com.smartreceipt.controller;

import com.smartreceipt.dto.MedicalReportResponse;
import com.smartreceipt.entity.DocumentType;
import com.smartreceipt.entity.MedicalReport;
import com.smartreceipt.exception.OcrException;
import com.smartreceipt.repository.MedicalReportRepository;
import com.smartreceipt.security.UserPrincipal;
import com.smartreceipt.service.DocumentClassificationService;
import com.smartreceipt.service.MedicalReportAnalysisService;
import com.smartreceipt.service.OcrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/medical-reports")
@RequiredArgsConstructor
@Tag(name = "Medical Report Management", description = "Medical report upload, parsing, and CRUD endpoints")
public class MedicalReportController {

    private final MedicalReportRepository medicalReportRepository;
    private final MedicalReportAnalysisService medicalReportAnalysisService;
    private final DocumentClassificationService classificationService;
    private final OcrService ocrService;

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and analyze medical report", description = "Accepts image or PDF, extracts text via OCR, classifies it as MEDICAL_REPORT, parses clinical parameters using AI, and saves to MongoDB.")
    @ApiResponse(responseCode = "201", description = "Medical report analyzed and stored successfully")
    @ApiResponse(responseCode = "400", description = "Invalid document, classification mismatch, or OCR error")
    public ResponseEntity<MedicalReportResponse> analyzeMedicalReport(
            @Parameter(description = "Medical report image or PDF file") @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal currentUser) throws Exception {

        log.info("Medical report upload request received");
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);

        File tempFile = null;
        try {
            tempFile = File.createTempFile("medical_upload_", "." + extension);
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("OCR processing started");
            String rawText = ocrService.extractRawText(tempFile, extension);
            log.info("OCR processing completed");

            if (rawText == null || rawText.trim().isEmpty()) {
                throw new OcrException("The document could not be read clearly. Please upload a clearer image or PDF.");
            }

            // Document Type Classification
            DocumentType docType = classificationService.classifyDocument(rawText);
            if (docType == DocumentType.RECEIPT) {
                throw new IllegalArgumentException("This document is classified as a financial receipt. Please upload it to the Receipt section.");
            }
            if (docType == DocumentType.UNKNOWN) {
                throw new IllegalArgumentException("This document could not be identified as a receipt or medical report.");
            }

            MedicalReport report = medicalReportAnalysisService.analyzeReport(rawText, originalFilename);
            report.setUserId(currentUser.getId());
            report.setCreatedAt(LocalDateTime.now());

            log.info("Medical report persistence started");
            MedicalReport savedReport = medicalReportRepository.save(report);
            log.info("Medical report persistence completed");

            log.info("Medical report upload response returned");
            return new ResponseEntity<>(MedicalReportResponse.builder()
                    .success(true)
                    .documentType("MEDICAL_REPORT")
                    .analysis(savedReport)
                    .build(), HttpStatus.CREATED);

        } catch (Exception e) {
            log.error("Medical report processing failed", e);
            throw e;
        } finally {
            if (tempFile != null && tempFile.exists()) {
                try {
                    Files.delete(tempFile.toPath());
                } catch (Exception e) {
                    log.warn("Could not delete temporary file: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }

    @GetMapping
    @Operation(summary = "Get all medical reports", description = "Returns a list of all analyzed medical reports for the authenticated user.")
    public ResponseEntity<List<MedicalReport>> getAllMedicalReports(@AuthenticationPrincipal UserPrincipal currentUser) {
        List<MedicalReport> reports = medicalReportRepository.findByUserId(currentUser.getId());
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get medical report by ID", description = "Returns details of a single medical report after verifying ownership.")
    public ResponseEntity<MedicalReport> getMedicalReportById(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        MedicalReport report = medicalReportRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new com.smartreceipt.exception.ResourceNotFoundException("Medical report not found."));
        return ResponseEntity.ok(report);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete medical report", description = "Deletes a medical report by ID after verifying ownership.")
    public ResponseEntity<Void> deleteMedicalReport(
            @PathVariable String id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        MedicalReport report = medicalReportRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new com.smartreceipt.exception.ResourceNotFoundException("Medical report not found."));
        medicalReportRepository.delete(report);
        return ResponseEntity.noContent().build();
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "png";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}
