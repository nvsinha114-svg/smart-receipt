package com.smartreceipt.service;

import com.smartreceipt.entity.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentClassificationServiceTest {

    @Mock
    private ObjectProvider<ChatModel> chatModelProvider;

    private DocumentClassificationService classificationService;

    @BeforeEach
    void setUp() {
        // Set up service with AI disabled (Gemini API key empty) to test deterministic fallbacks
        when(chatModelProvider.getIfAvailable()).thenReturn(null);
        classificationService = new DocumentClassificationService(chatModelProvider, "");
    }

    @Test
    @DisplayName("Should classify document containing receipt keywords as RECEIPT")
    void classifyDocument_ReceiptKeywords() {
        String receiptText = """
                TAX INVOICE
                Merchant: Star Market
                GSTIN: 27AAAAA1111A1Z1
                1x Cheese Burger - 250.00
                Subtotal: 250.00
                GST 18%: 45.00
                Total Amount: 295.00
                Payment: Cash
                """;

        DocumentType type = classificationService.classifyDocument(receiptText);
        assertEquals(DocumentType.RECEIPT, type);
    }

    @Test
    @DisplayName("Should classify document containing medical keywords as MEDICAL_REPORT")
    void classifyDocument_MedicalKeywords() {
        String medicalText = """
                CLINICAL PATHOLOGY LABORATORY REPORT
                Patient Name: Jane Doe
                Specimen: Blood Serum
                Hemoglobin: 12.5 g/dL (Reference Range: 12.0 - 15.0 g/dL)
                WBC Count: 7200 /uL (Reference Range: 4000 - 11000)
                Glucose: 95 mg/dL (Reference Range: < 100)
                ALT (SGPT): 22 U/L (Reference Range: 7 - 56)
                Result: Normal
                """;

        DocumentType type = classificationService.classifyDocument(medicalText);
        assertEquals(DocumentType.MEDICAL_REPORT, type);
    }

    @Test
    @DisplayName("Should classify empty or unrelated document as UNKNOWN")
    void classifyDocument_UnknownText() {
        String randomText = "Welcome to the city library. Please keep silence and return books on time.";
        DocumentType type = classificationService.classifyDocument(randomText);
        assertEquals(DocumentType.UNKNOWN, type);

        assertEquals(DocumentType.UNKNOWN, classificationService.classifyDocument(""));
        assertEquals(DocumentType.UNKNOWN, classificationService.classifyDocument(null));
    }
}
