package com.smartreceipt.service;

import com.smartreceipt.dto.ReceiptAIItem;
import com.smartreceipt.dto.ReceiptAIResponse;
import com.smartreceipt.entity.Receipt;
import com.smartreceipt.entity.ReceiptItem;
import com.smartreceipt.repository.ReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OcrServiceTest {

    @Mock
    private ReceiptRepository receiptRepository;

    @Mock
    private ReceiptService receiptService;

    @Mock
    private AIReceiptParserService aiReceiptParserService;

    @InjectMocks
    private OcrService ocrService;

    private String sampleCollegeReceipt;

    @BeforeEach
    void setUp() {
        sampleCollegeReceipt = """
                IMS ENGINEERING COLLEGE, GHAZIABAD
                Receipt ID: 6a83229bca71d5002391ea02
                Date: 2026-08-10
                
                Tuition Fee: ₹103,919
                TID: ₹2,000
                College Placement Activity Fee: ₹3,000
                Internet, Intranet and Master Electronic I Card Fee: ₹3,000
                Development Charges: ₹6,500
                Insurance: ₹1,250
                
                Sem: 5
                """;
        // Default lenient stub to bypass AI parser in existing OCR tests
        lenient().when(aiReceiptParserService.isAiEnabled()).thenReturn(false);
    }

    @Test
    @DisplayName("Should extract items and calculate exact total ₹119,669 from college receipt")
    void parseCollegeReceipt_Success() {
        Receipt receipt = ocrService.parseTextToReceipt(sampleCollegeReceipt);
        assertNotNull(receipt);
        assertEquals("IMS ENGINEERING COLLEGE, GHAZIABAD", receipt.getMerchantName());
        
        List<ReceiptItem> items = receipt.getItems();
        assertNotNull(items);
        assertEquals(6, items.size());
        
        // Sum: 103919 + 2000 + 3000 + 3000 + 6500 + 1250 = 119669
        assertNotNull(receipt.getTotalAmount());
        assertEquals(new BigDecimal("119669"), receipt.getTotalAmount());
    }

    @Test
    @DisplayName("Should parse explicit total amount for Indian Rupee formats")
    void parseTotalAmount_IndianRupeeFormats() {
        String text = """
                RELIANCE SMART
                Subtotal ₹700.00
                CGST ₹75.00
                SGST ₹75.00
                Total: ₹850.00
                """;
        BigDecimal total = ocrService.parseTotalAmount(text);
        assertNotNull(total);
        assertEquals(new BigDecimal("850.00"), total);
    }

    @Test
    @DisplayName("Should parse receipt successfully using AI parser when enabled")
    void parseTextToReceipt_AiEnabledSuccess() {
        // Arrange
        when(aiReceiptParserService.isAiEnabled()).thenReturn(true);
        ReceiptAIResponse mockResponse = ReceiptAIResponse.builder()
                .merchantName("IMS ENGINEERING COLLEGE")
                .receiptDate("2026-08-10")
                .currency("INR")
                .category("Education")
                .items(List.of(
                        ReceiptAIItem.builder().name("Tuition Fee").quantity(1).unitPrice(new BigDecimal("103919")).category("Education").build(),
                        ReceiptAIItem.builder().name("Insurance").quantity(1).unitPrice(new BigDecimal("1250")).category("Other").build()
                ))
                .build();
        when(aiReceiptParserService.parseReceiptText(anyString())).thenReturn(mockResponse);

        // Act
        Receipt receipt = ocrService.parseTextToReceipt(sampleCollegeReceipt);

        // Assert
        assertNotNull(receipt);
        assertEquals("IMS ENGINEERING COLLEGE", receipt.getMerchantName());
        assertEquals(LocalDate.of(2026, 8, 10), receipt.getReceiptDate());
        assertEquals("Education", receipt.getCategory());
        assertEquals(2, receipt.getItems().size());
        assertEquals(new BigDecimal("105169"), receipt.getTotalAmount()); // 103919 + 1250 = 105169
        assertEquals("Education", receipt.getItems().get(0).getCategory());
    }

    @Test
    @DisplayName("Should fall back to Tesseract parsing if AI parsing returns null")
    void parseTextToReceipt_AiReturnsNullFallback() {
        // Arrange
        when(aiReceiptParserService.isAiEnabled()).thenReturn(true);
        when(aiReceiptParserService.parseReceiptText(anyString())).thenReturn(null);

        // Act
        Receipt receipt = ocrService.parseTextToReceipt(sampleCollegeReceipt);

        // Assert
        assertNotNull(receipt);
        assertEquals("IMS ENGINEERING COLLEGE, GHAZIABAD", receipt.getMerchantName());
        assertEquals(6, receipt.getItems().size());
        assertEquals(new BigDecimal("119669"), receipt.getTotalAmount());
    }

    @Test
    @DisplayName("Should fall back to Tesseract parsing if AI parsing throws exception")
    void parseTextToReceipt_AiThrowsExceptionFallback() {
        // Arrange
        when(aiReceiptParserService.isAiEnabled()).thenReturn(true);
        when(aiReceiptParserService.parseReceiptText(anyString())).thenThrow(new RuntimeException("API error"));

        // Act
        Receipt receipt = ocrService.parseTextToReceipt(sampleCollegeReceipt);

        // Assert
        assertNotNull(receipt);
        assertEquals("IMS ENGINEERING COLLEGE, GHAZIABAD", receipt.getMerchantName());
        assertEquals(6, receipt.getItems().size());
        assertEquals(new BigDecimal("119669"), receipt.getTotalAmount());
    }
}