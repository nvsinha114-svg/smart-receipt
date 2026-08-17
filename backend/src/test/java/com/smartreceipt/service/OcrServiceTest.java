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
}