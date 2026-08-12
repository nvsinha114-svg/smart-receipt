package com.smartreceipt.service;

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

@ExtendWith(MockitoExtension.class)
class OcrServiceTest {

    @Mock
    private ReceiptRepository receiptRepository;

    @Mock
    private ReceiptService receiptService;

    @InjectMocks
    private OcrService ocrService;

    private String sampleText;

    @BeforeEach
    void setUp() {
        sampleText = """
                WALMART SUPERCENTER
                Store #1234
                Date: 2026-08-10
                
                Milk 2 4.50
                Bread 1 2.99
                Coffee 8.99
                
                SUBTOTAL: $16.48
                TAX: $1.32
                TOTAL: $17.80
                Thank you for shopping!
                """;
    }

    @Test
    @DisplayName("Should parse merchant name from receipt raw text")
    void parseMerchantName_Success() {
        String merchant = ocrService.parseMerchantName(sampleText);
        assertEquals("WALMART SUPERCENTER", merchant);
    }

    @Test
    @DisplayName("Should parse receipt date successfully")
    void parseReceiptDate_Success() {
        LocalDate date = ocrService.parseReceiptDate(sampleText);
        assertNotNull(date);
        assertEquals(LocalDate.of(2026, 8, 10), date);
    }

    @Test
    @DisplayName("Should parse total amount successfully")
    void parseTotalAmount_Success() {
        BigDecimal total = ocrService.parseTotalAmount(sampleText);
        assertNotNull(total);
        assertEquals(new BigDecimal("17.80"), total);
    }

    @Test
    @DisplayName("Should parse items from raw receipt text")
    void parseReceiptItems_Success() {
        List<ReceiptItem> items = ocrService.parseReceiptItems(sampleText);
        assertNotNull(items);
        assertEquals(3, items.size());
        assertEquals("Milk", items.get(0).getName());
        assertEquals(2, items.get(0).getQuantity());
        assertEquals(new BigDecimal("4.50"), items.get(0).getPrice());
    }

    @Test
    @DisplayName("Should return null for missing unconfident fields")
    void parseTextToReceipt_EmptyText() {
        Receipt receipt = ocrService.parseTextToReceipt("");
        assertNotNull(receipt);
        assertNull(receipt.getMerchantName());
        assertNull(receipt.getReceiptDate());
        assertNull(receipt.getTotalAmount());
    }
}
