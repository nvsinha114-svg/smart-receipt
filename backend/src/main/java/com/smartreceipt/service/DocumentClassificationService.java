package com.smartreceipt.service;

import com.smartreceipt.entity.DocumentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class DocumentClassificationService {

    private final ChatClient chatClient;
    private final boolean isAiEnabled;

    @Autowired
    public DocumentClassificationService(org.springframework.beans.factory.ObjectProvider<ChatModel> chatModelProvider,
                                         @Value("${spring.ai.vertex.ai.gemini.api-key:}") String apiKey) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel != null && apiKey != null && !apiKey.trim().isEmpty() 
                && !apiKey.equals("dummy-key-to-bypass-startup-check") 
                && !apiKey.contains("GEMINI_API_KEY")) {
            this.chatClient = ChatClient.create(chatModel);
            this.isAiEnabled = true;
            log.info("DocumentClassificationService initialized with AI classifier.");
        } else {
            this.chatClient = null;
            this.isAiEnabled = false;
            log.warn("Gemini API key not configured for document classification. Bypassing AI classifier.");
        }
    }

    public DocumentType classifyDocument(String text) {
        if (text == null || text.trim().isEmpty()) {
            return DocumentType.UNKNOWN;
        }

        // 1. Deterministic Safeguards/Keyword Scoring
        int receiptScore = scoreReceiptKeywords(text);
        int medicalScore = scoreMedicalKeywords(text);

        log.info("Classification scoring: receiptScore={}, medicalScore={}", receiptScore, medicalScore);

        // Strong deterministic classification if one score is highly dominant
        if (receiptScore > 8 && medicalScore == 0) {
            log.info("Classified as RECEIPT deterministically based on keyword scoring.");
            return DocumentType.RECEIPT;
        }
        if (medicalScore > 8 && receiptScore == 0) {
            log.info("Classified as MEDICAL_REPORT deterministically based on keyword scoring.");
            return DocumentType.MEDICAL_REPORT;
        }

        // 2. AI-based Classification Fallback
        if (isAiEnabled && chatClient != null) {
            try {
                String prompt = """
                        You are a highly accurate document classifier for the Smart Receipt application.
                        Classify the following document content into exactly one of three categories:
                        - RECEIPT (for financial transactions, bills, invoices, purchases)
                        - MEDICAL_REPORT (for clinical lab reports, blood tests, diagnostic tests, medical pathology parameters)
                        - UNKNOWN (if the content does not clearly belong to either category or is unreadable)

                        CRITICAL RULES:
                        - Respond with ONLY the exact name of the category (RECEIPT, MEDICAL_REPORT, or UNKNOWN).
                        - Do NOT include any explanations, markdown code blocks, punctuation, or other text.
                        - If there is not enough evidence to confidently classify, respond with UNKNOWN.

                        Document Content:
                        """ + text;

                log.info("Invoking Gemini for document classification...");
                String response = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();

                if (response != null) {
                    String cleanResponse = response.trim().toUpperCase();
                    log.info("AI classification response: {}", cleanResponse);
                    if (cleanResponse.contains("RECEIPT")) {
                        return DocumentType.RECEIPT;
                    } else if (cleanResponse.contains("MEDICAL_REPORT") || cleanResponse.contains("MEDICAL")) {
                        return DocumentType.MEDICAL_REPORT;
                    } else if (cleanResponse.contains("UNKNOWN")) {
                        return DocumentType.UNKNOWN;
                    }
                }
            } catch (Exception e) {
                log.error("AI classification failed. Falling back to scoring.", e);
            }
        }

        // 3. Fallback to scoring comparison
        if (receiptScore > medicalScore && receiptScore > 2) {
            return DocumentType.RECEIPT;
        } else if (medicalScore > receiptScore && medicalScore > 2) {
            return DocumentType.MEDICAL_REPORT;
        }

        return DocumentType.UNKNOWN;
    }

    private int scoreReceiptKeywords(String text) {
        String lowerText = text.toLowerCase();
        int score = 0;
        
        String[] strongKeywords = {
            "total", "subtotal", "tax", "gst", "invoice", "receipt", "merchant", 
            "qty", "quantity", "payment method", "gstin", "bill no", "amount", 
            "payment", "cashier", "items"
        };
        for (String kw : strongKeywords) {
            if (lowerText.contains(kw)) {
                score += 2;
            }
        }
        
        // Count monetary numbers (like ₹500.00, $10)
        Pattern monetaryPattern = Pattern.compile("(?:₹|rs\\.?|\\$)\\s*\\d+");
        Matcher matcher = monetaryPattern.matcher(text);
        while (matcher.find()) {
            score += 1;
        }

        return score;
    }

    private int scoreMedicalKeywords(String text) {
        String lowerText = text.toLowerCase();
        int score = 0;
        
        String[] strongKeywords = {
            "patient", "laboratory", "test name", "reference range", "result", 
            "hemoglobin", "wbc", "rbc", "platelets", "glucose", "tsh", "creatinine", 
            "bilirubin", "cholesterol", "mg/dl", "g/dl", "u/l", "pg/ml", "mmol/l", 
            "clinical", "pathology", "specimen", "reference interval", "observed value"
        };
        for (String kw : strongKeywords) {
            if (lowerText.contains(kw)) {
                score += 2;
            }
        }

        return score;
    }
}
