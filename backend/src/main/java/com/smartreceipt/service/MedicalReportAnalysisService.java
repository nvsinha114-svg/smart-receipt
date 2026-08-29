package com.smartreceipt.service;

import com.smartreceipt.dto.MedicalParameterAIResponse;
import com.smartreceipt.dto.MedicalReportAIResponse;
import com.smartreceipt.entity.MedicalParameter;
import com.smartreceipt.entity.MedicalReport;
import com.smartreceipt.entity.MedicalSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class MedicalReportAnalysisService {

    private final ChatClient chatClient;
    private final boolean isAiEnabled;

    private static final String MEDICAL_SYSTEM_PROMPT = """
            You are an expert clinical laboratory data extractor and structured document understanding engine.
            Your role is to analyze medical laboratory reports, blood tests, and diagnostic summaries to extract clinical parameters.

            CORE REQUIREMENTS:
            1. Extract every single measurable or reportable test/parameter present in the document. Do not omit any parameter.
            2. For each parameter, extract:
               - testName (e.g. "Hemoglobin", "WBC Count", "ALT (SGPT)", "TSH")
               - value (the raw result as a string, e.g. "10.8", "Positive", "Non-reactive")
               - numericValue (the decimal representation of the value if numeric, e.g. 10.8, 7200, 68.0, or null if qualitative/non-numeric)
               - unit (the measurement unit, e.g. "g/dL", "U/L", "mg/dL", "/uL", or null if none)
               - referenceRange (the exact reference range/interval printed on the report, e.g. "13-17 g/dL", "< 100", "Negative")
               - status (LOW, NORMAL, HIGH, ABNORMAL, POSITIVE, NEGATIVE, DETECTED, NOT_DETECTED, REFERENCE_NOT_AVAILABLE, or UNDETERMINED)
               - category (grouping category, e.g. "Hematology", "Liver Function", "Kidney Function", "Thyroid", "Diabetes / Glucose", "Lipid Profile", etc.)
               - labFlag (any lab-provided flag like "H", "L", "*", or null if none)
               - confidence (decimal score between 0.0 and 1.0 representing extraction confidence)

            CRITICAL ACCURACY RULES:
            - Use the laboratory's own reference range as the primary comparison source for status.
            - If reference range information is not available, set status = "REFERENCE_NOT_AVAILABLE" and referenceRange = null.
            - Do NOT invent/hallucinate reference ranges or assume normal values if they are missing.
            - Do NOT make medical diagnoses or treat/prescribe.
            - Do NOT recommend treatment plans, drugs, or stopping/changing medications.
            - Return JSON matching the requested structure.
            """;

    @Autowired
    public MedicalReportAnalysisService(org.springframework.beans.factory.ObjectProvider<ChatModel> chatModelProvider,
                                         @Value("${spring.ai.openai.api-key:}") String apiKey) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel != null && apiKey != null && !apiKey.trim().isEmpty() 
                && !apiKey.equals("dummy-key-to-bypass-startup-check") 
                && !apiKey.contains("GEMINI_API_KEY")) {
            this.chatClient = ChatClient.create(chatModel);
            this.isAiEnabled = true;
            log.info("MedicalReportAnalysisService initialized with Gemini API ChatModel.");
        } else {
            this.chatClient = null;
            this.isAiEnabled = false;
            log.warn("Gemini API key not configured for medical report analysis. Bypassing AI analyzer.");
        }
    }

    public MedicalReport analyzeReport(String ocrText, String fileName) {
        log.info("AI parsing started");
        
        MedicalReportAIResponse aiResponse = null;
        if (isAiEnabled && chatClient != null) {
            try {
                aiResponse = chatClient.prompt()
                        .system(MEDICAL_SYSTEM_PROMPT)
                        .user(ocrText)
                        .call()
                        .entity(MedicalReportAIResponse.class);
            } catch (Exception e) {
                String errMsg = e.getMessage() != null ? e.getMessage() : "";
                log.error("AI medical parsing failed: {}", errMsg);
                
                if (errMsg.contains("401") || errMsg.contains("403") || errMsg.contains("Unauthorized") || errMsg.contains("Forbidden")) {
                    throw new RuntimeException("Gemini authentication/configuration error. Please verify API key setup.");
                } else if (errMsg.contains("429") || errMsg.contains("RESOURCE_EXHAUSTED") || errMsg.contains("Rate limit")) {
                    throw new RuntimeException("Gemini service rate limit exceeded. Please try again in a few moments.");
                } else if (errMsg.contains("500") || errMsg.contains("503") || errMsg.contains("UNAVAILABLE")) {
                    throw new RuntimeException("Medical analysis service is temporarily unavailable. Please try again.");
                } else if (errMsg.toLowerCase().contains("timeout")) {
                    throw new RuntimeException("Gemini request timed out. Please try again.");
                }
                
                throw new RuntimeException("Medical analysis service is temporarily unavailable. Please try again.");
            }
        } else {
            throw new RuntimeException("Medical analysis failed: Gemini API key is not enabled/configured.");
        }

        log.info("AI parsing completed");
        
        if (aiResponse == null) {
            throw new RuntimeException("Medical analysis failed: empty response from AI parser.");
        }

        // Post-processing and deterministic status validation
        List<MedicalParameter> parameters = new ArrayList<>();
        int normalCount = 0;
        int lowCount = 0;
        int highCount = 0;
        int abnormalCount = 0;
        int refUnavailableCount = 0;

        if (aiResponse.getParameters() != null) {
            for (MedicalParameterAIResponse aiParam : aiResponse.getParameters()) {
                String testName = aiParam.getTestName();
                if (testName == null || testName.trim().isEmpty()) {
                    continue;
                }

                String refRange = aiParam.getReferenceRange();
                String initialStatus = aiParam.getStatus();
                if (initialStatus == null) {
                    initialStatus = "UNDETERMINED";
                }

                String finalStatus = initialStatus;
                
                // Enforce: if no reference range, status MUST be REFERENCE_NOT_AVAILABLE
                if (refRange == null || refRange.trim().isEmpty() || "null".equalsIgnoreCase(refRange.trim())) {
                    finalStatus = "REFERENCE_NOT_AVAILABLE";
                    refRange = null;
                } else if (aiParam.getNumericValue() != null) {
                    // Validate status mathematically
                    finalStatus = evaluateStatus(aiParam.getNumericValue(), refRange, initialStatus);
                }

                // Update summary counters
                switch (finalStatus) {
                    case "NORMAL":
                        normalCount++;
                        break;
                    case "LOW":
                        lowCount++;
                        break;
                    case "HIGH":
                        highCount++;
                        break;
                    case "ABNORMAL":
                    case "CRITICAL":
                        abnormalCount++;
                        break;
                    case "REFERENCE_NOT_AVAILABLE":
                        refUnavailableCount++;
                        break;
                    default:
                        if ("POSITIVE".equals(finalStatus) || "DETECTED".equals(finalStatus)) {
                            abnormalCount++;
                        } else if ("NEGATIVE".equals(finalStatus) || "NOT_DETECTED".equals(finalStatus)) {
                            normalCount++;
                        } else {
                            refUnavailableCount++;
                        }
                        break;
                }

                parameters.add(MedicalParameter.builder()
                        .testName(testName.trim())
                        .value(aiParam.getValue())
                        .numericValue(aiParam.getNumericValue())
                        .unit(aiParam.getUnit())
                        .referenceRange(refRange)
                        .status(finalStatus)
                        .category(aiParam.getCategory() != null ? aiParam.getCategory().trim() : "Other")
                        .labFlag(aiParam.getLabFlag())
                        .confidence(aiParam.getConfidence())
                        .build());
            }
        }

        MedicalSummary summary = MedicalSummary.builder()
                .totalParameters(parameters.size())
                .normalCount(normalCount)
                .lowCount(lowCount)
                .highCount(highCount)
                .abnormalCount(abnormalCount)
                .referenceUnavailableCount(refUnavailableCount)
                .build();

        LocalDate reportDate = null;
        if (aiResponse.getReportDate() != null) {
            try {
                reportDate = LocalDate.parse(aiResponse.getReportDate().trim());
            } catch (Exception e) {
                // If parsing fails, leave as null
            }
        }

        return MedicalReport.builder()
                .fileName(fileName)
                .patientName(aiResponse.getPatientName())
                .reportDate(reportDate)
                .laboratoryName(aiResponse.getLaboratoryName())
                .parameters(parameters)
                .categories(aiResponse.getCategories() != null ? aiResponse.getCategories() : new ArrayList<>())
                .summary(summary)
                .overallNotes(aiResponse.getOverallNotes() != null ? aiResponse.getOverallNotes() : new ArrayList<>())
                .build();
    }

    public static String evaluateStatus(BigDecimal value, String refRange, String currentStatus) {
        if (refRange == null || refRange.trim().isEmpty() || "null".equalsIgnoreCase(refRange.trim())) {
            return "REFERENCE_NOT_AVAILABLE";
        }
        if (value == null) {
            return currentStatus;
        }
        try {
            String cleanRef = refRange.replaceAll("\\s+", "").trim();
            
            // 1. Bounds
            if (cleanRef.startsWith("<=")) {
                BigDecimal limit = new BigDecimal(cleanRef.substring(2));
                return value.compareTo(limit) <= 0 ? "NORMAL" : "HIGH";
            }
            if (cleanRef.startsWith("<")) {
                BigDecimal limit = new BigDecimal(cleanRef.substring(1));
                return value.compareTo(limit) < 0 ? "NORMAL" : "HIGH";
            }
            if (cleanRef.startsWith(">=")) {
                BigDecimal limit = new BigDecimal(cleanRef.substring(2));
                return value.compareTo(limit) >= 0 ? "NORMAL" : "LOW";
            }
            if (cleanRef.startsWith(">")) {
                BigDecimal limit = new BigDecimal(cleanRef.substring(1));
                return value.compareTo(limit) > 0 ? "NORMAL" : "LOW";
            }

            // 2. Ranges
            String[] separators = {"-", "–", "—", "to"}; // Hyphen, en-dash, em-dash, and "to"
            for (String sep : separators) {
                if (cleanRef.contains(sep)) {
                    String[] parts = cleanRef.split(sep);
                    if (parts.length == 2) {
                        BigDecimal min = new BigDecimal(parts[0].trim());
                        BigDecimal max = new BigDecimal(parts[1].trim());
                        if (value.compareTo(min) < 0) {
                            return "LOW";
                        } else if (value.compareTo(max) > 0) {
                            return "HIGH";
                        } else {
                            return "NORMAL";
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore and fallback
        }
        return currentStatus;
    }

    public boolean isAiEnabled() {
        return this.isAiEnabled;
    }
}
