package com.smartreceipt.service;

import com.smartreceipt.entity.Receipt;
import com.smartreceipt.entity.ReceiptItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfServiceTest {

    private final PdfService pdfService = new PdfService();

    @Test
    @DisplayName("Should generate valid non-empty PDF byte array")
    void generateReceiptPdf_Success() {
        Receipt receipt = Receipt.builder()
                .id("receipt-999")
                .merchantName("Target")
                .receiptDate(LocalDate.of(2026, 8, 12))
                .totalAmount(new BigDecimal("120.50"))
                .userId("user-1")
                .createdAt(LocalDateTime.now())
                .items(List.of(
                        ReceiptItem.builder().name("Shirt").quantity(2).price(new BigDecimal("35.00")).build(),
                        ReceiptItem.builder().name("Jeans").quantity(1).price(new BigDecimal("50.50")).build()
                ))
                .build();

        byte[] pdfBytes = pdfService.generateReceiptPdf(receipt);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
        String header = new String(pdfBytes, 0, 5);
        assertEquals("%PDF-", header);
    }
}
