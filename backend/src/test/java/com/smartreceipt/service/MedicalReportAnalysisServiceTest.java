package com.smartreceipt.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MedicalReportAnalysisServiceTest {

    @Test
    @DisplayName("Should evaluate status against numeric bounds correctly")
    void evaluateStatus_NumericBounds() {
        // Upper bounds
        assertEquals("NORMAL", MedicalReportAnalysisService.evaluateStatus(new BigDecimal("95"), "< 100", "UNDETERMINED"));
        assertEquals("HIGH", MedicalReportAnalysisService.evaluateStatus(new BigDecimal("105"), "< 100", "UNDETERMINED"));
        assertEquals("NORMAL", MedicalReportAnalysisService.evaluateStatus(new BigDecimal("5.6"), "<= 5.6", "UNDETERMINED"));
        assertEquals("HIGH", MedicalReportAnalysisService.evaluateStatus(new BigDecimal("5.7"), "<= 5.6", "UNDETERMINED"));

        // Lower bounds
        assertEquals("NORMAL", MedicalReportAnalysisService.evaluateStatus(new BigDecimal("45"), "> 40", "UNDETERMINED"));
        assertEquals("LOW", MedicalReportAnalysisService.evaluateStatus(new BigDecimal("35"), "> 40", "UNDETERMINED"));
        assertEquals("NORMAL", MedicalReportAnalysisService.evaluateStatus(new BigDecimal("60"), ">= 60", "UNDETERMINED"));
        assertEquals("LOW", MedicalReportAnalysisService.evaluateStatus(new BigDecimal("59"), ">= 60", "UNDETERMINED"));
    }

    @Test
    @DisplayName("Should evaluate status against hyphenated and textual ranges correctly")
    void evaluateStatus_NumericRanges() {
        // Hyphen range
        assertEquals("LOW", MedicalReportAnalysisService.evaluateStatus(new BigDecimal("11.5"), "13 - 17", "UNDETERMINED"));
        assertEquals("NORMAL", MedicalReportAnalysisService.evaluateStatus(new BigDecimal("14.2"), "13-17", "UNDETERMINED"));
        assertEquals("HIGH", MedicalReportAnalysisService.evaluateStatus(new BigDecimal("17.5"), "13-17", "UNDETERMINED"));

        // En-dash (\\u2013) range
        assertEquals("LOW", MedicalReportAnalysisService.evaluateStatus(new BigDecimal("11.5"), "13–17", "UNDETERMINED"));
        assertEquals("NORMAL", MedicalReportAnalysisService.evaluateStatus(new BigDecimal("15.0"), "13–17", "UNDETERMINED"));

        // Em-dash (\\u2014) range
        assertEquals("NORMAL", MedicalReportAnalysisService.evaluateStatus(new BigDecimal("15.0"), "13—17", "UNDETERMINED"));

        // "to" range
        assertEquals("LOW", MedicalReportAnalysisService.evaluateStatus(new BigDecimal("3.0"), "3.5 to 5.2", "UNDETERMINED"));
        assertEquals("NORMAL", MedicalReportAnalysisService.evaluateStatus(new BigDecimal("4.5"), "3.5 to 5.2", "UNDETERMINED"));
        assertEquals("HIGH", MedicalReportAnalysisService.evaluateStatus(new BigDecimal("5.8"), "3.5 to 5.2", "UNDETERMINED"));
    }

    @Test
    @DisplayName("Should return REFERENCE_NOT_AVAILABLE if range is missing")
    void evaluateStatus_MissingRange() {
        assertEquals("REFERENCE_NOT_AVAILABLE", MedicalReportAnalysisService.evaluateStatus(new BigDecimal("10"), null, "NORMAL"));
        assertEquals("REFERENCE_NOT_AVAILABLE", MedicalReportAnalysisService.evaluateStatus(new BigDecimal("10"), "", "NORMAL"));
        assertEquals("REFERENCE_NOT_AVAILABLE", MedicalReportAnalysisService.evaluateStatus(new BigDecimal("10"), "null", "NORMAL"));
    }

    @Test
    @DisplayName("Should fallback to current status for qualitative values")
    void evaluateStatus_QualitativeFallback() {
        assertEquals("POSITIVE", MedicalReportAnalysisService.evaluateStatus(null, "Negative", "POSITIVE"));
        assertEquals("NEGATIVE", MedicalReportAnalysisService.evaluateStatus(null, "Negative", "NEGATIVE"));
    }
}
