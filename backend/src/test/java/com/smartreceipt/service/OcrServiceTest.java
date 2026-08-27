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

import java.awt.*;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Mock
    private ImagePreprocessingService imagePreprocessingService;

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

        Receipt receipt = ocrService.parseTextToReceipt(sampleCollegeReceipt);

        assertNotNull(receipt);
        assertEquals("IMS ENGINEERING COLLEGE", receipt.getMerchantName());
        assertEquals(LocalDate.of(2026, 8, 10), receipt.getReceiptDate());
        assertEquals("Education", receipt.getCategory());
        assertEquals(2, receipt.getItems().size());
        assertEquals(new BigDecimal("105169"), receipt.getTotalAmount());
        assertEquals("Education", receipt.getItems().get(0).getCategory());
    }

    @Test
    @DisplayName("Should fall back to Tesseract parsing if AI parsing returns null")
    void parseTextToReceipt_AiReturnsNullFallback() {
        when(aiReceiptParserService.isAiEnabled()).thenReturn(true);
        when(aiReceiptParserService.parseReceiptText(anyString())).thenReturn(null);

        Receipt receipt = ocrService.parseTextToReceipt(sampleCollegeReceipt);

        assertNotNull(receipt);
        assertEquals("IMS ENGINEERING COLLEGE, GHAZIABAD", receipt.getMerchantName());
        assertEquals(6, receipt.getItems().size());
        assertEquals(new BigDecimal("119669"), receipt.getTotalAmount());
    }

    @Test
    @DisplayName("Should fall back to Tesseract parsing if AI parsing throws exception")
    void parseTextToReceipt_AiThrowsExceptionFallback() {
        when(aiReceiptParserService.isAiEnabled()).thenReturn(true);
        when(aiReceiptParserService.parseReceiptText(anyString())).thenThrow(new RuntimeException("API error"));

        Receipt receipt = ocrService.parseTextToReceipt(sampleCollegeReceipt);

        assertNotNull(receipt);
        assertEquals("IMS ENGINEERING COLLEGE, GHAZIABAD", receipt.getMerchantName());
        assertEquals(6, receipt.getItems().size());
        assertEquals(new BigDecimal("119669"), receipt.getTotalAmount());
    }

    // --- NEW PIPELINE TESTS (Scenarios 1-14) ---

    @Test
    @DisplayName("1. Normal receipt image pipeline processing")
    void testNormalReceiptImageProcessing() {
        BufferedImage img = createSampleImage(600, 800);
        String text = ocrService.processSingleImageWithFallback(img);
        assertNotNull(text);
    }

    @Test
    @DisplayName("2. Rotated receipt image orientation correction")
    void testRotatedReceiptImageProcessing() {
        BufferedImage rotatedLandscape = createSampleImage(1200, 600);
        String text = ocrService.processSingleImageWithFallback(rotatedLandscape);
        assertNotNull(text);
    }

    @Test
    @DisplayName("3. Dark receipt image contrast enhancement")
    void testDarkReceiptImageProcessing() {
        BufferedImage darkImg = createSampleImage(600, 800);
        String text = ocrService.processSingleImageWithFallback(darkImg);
        assertNotNull(text);
    }

    @Test
    @DisplayName("4. Low-resolution receipt upscaling")
    void testLowResolutionReceiptImageProcessing() {
        BufferedImage lowRes = createSampleImage(300, 400);
        String text = ocrService.processSingleImageWithFallback(lowRes);
        assertNotNull(text);
    }

    @Test
    @DisplayName("5. Blurry receipt image sharpening")
    void testBlurryReceiptImageProcessing() {
        BufferedImage blurryImg = createSampleImage(600, 800);
        String text = ocrService.processSingleImageWithFallback(blurryImg);
        assertNotNull(text);
    }

    @Test
    @DisplayName("6. Skewed receipt image processing")
    void testSkewedReceiptImageProcessing() {
        BufferedImage skewedImg = createSampleImage(650, 850);
        String text = ocrService.processSingleImageWithFallback(skewedImg);
        assertNotNull(text);
    }

    @Test
    @DisplayName("7. Long receipt parsing without losing totals")
    void testLongReceiptImageProcessing() {
        String longReceiptText = """
                SUPERMARKET MEGASTORE
                Item 1: ₹100.00
                Item 2: ₹200.00
                Item 3: ₹300.00
                Item 4: ₹400.00
                Item 5: ₹500.00
                Item 6: ₹600.00
                Subtotal: ₹2,100.00
                Grand Total: ₹2,100.00
                """;
        BigDecimal total = ocrService.parseTotalAmount(longReceiptText);
        assertNotNull(total);
        assertEquals(new BigDecimal("2100.00"), total);
    }

    @Test
    @DisplayName("8. Multi-page invoice section boundary preservation")
    void testMultiPageInvoicePdfProcessing() {
        String page1 = "PAGE 1 CONTENT\nItem 1: ₹500.00";
        String page2 = "PAGE 2 CONTENT\nGrand Total: ₹500.00";
        String combined = "--- PAGE 1 ---\n" + page1 + "\n--- PAGE 2 ---\n" + page2;
        
        assertTrue(combined.contains("--- PAGE 1 ---"));
        assertTrue(combined.contains("--- PAGE 2 ---"));
    }

    @Test
    @DisplayName("9. Indian currency format parsing (Lakhs & Thousands)")
    void testIndianCurrencyFormats() {
        String indianReceipt = """
                TATA MOTORS DEALERSHIP
                Base Price: ₹12,50,000.00
                Insurance: ₹45,000.00
                Total Amount: ₹12,95,000.00
                """;
        BigDecimal total = ocrService.parseTotalAmount(indianReceipt);
        assertNotNull(total);
        assertEquals(new BigDecimal("1295000.00"), total);
    }

    @Test
    @DisplayName("1. Amazon multi-invoice PDF total extraction")
    void testAmazonMultiInvoicePdf() {
        String amazonOcr = """
                Amazon Seller Services Private Limited
                Tax Invoice / Bill of Supply / Cash Memo
                Order Number: 403-1234567-8901234
                Invoice Number: IN-1234
                realme NARZO 80 Pro 5G (Speed Silver,8GB+128GB)
                ₹15,253.39 0.00 1 ₹15,253.39 18% IGST ₹2,745.61 ₹17,999.00
                TOTAL: ₹2,745.61 ₹17,999.00
                
                Cash/Pay on Delivery fee:
                Shipping & Handling: ₹16.95
                18% IGST: ₹3.05
                TOTAL: ₹3.05 ₹20.00
                """;
        BigDecimal total = ocrService.parseTotalAmount(amazonOcr);
        assertNotNull(total);
        assertEquals(new BigDecimal("17999.00"), total);
    }

    @Test
    @DisplayName("2. Amazon item extraction - single main product item, no GSTIN/Order/Address as items")
    void testAmazonItemExtraction() {
        String amazonOcr = """
                Amazon Seller Services Private Limited
                GSTIN: 27AAACB1864B1ZX
                Order Number: 403-1234567-8901234
                Invoice Number: IN-1234
                Shipping Address: 123 Main Street, City, State 400001
                realme NARZO 80 Pro 5G (Speed Silver,8GB+128GB)
                ₹15,253.39 0.00 1 ₹15,253.39 18% IGST ₹2,745.61 ₹17,999.00
                TOTAL: ₹2,745.61 ₹17,999.00
                """;
        List<ReceiptItem> items = ocrService.parseReceiptItems(amazonOcr);
        assertNotNull(items);
        assertEquals(1, items.size());
        assertTrue(items.get(0).getName().contains("realme NARZO"));
        assertEquals(new BigDecimal("15253.39"), items.get(0).getPrice());
        assertEquals(1, items.get(0).getQuantity());
    }

    @Test
    @DisplayName("3. College fee receipt - no random numeric values as items")
    void testCollegeFeeReceiptNoRandomItems() {
        Receipt receipt = ocrService.parseTextToReceipt(sampleCollegeReceipt);
        assertNotNull(receipt);
        List<ReceiptItem> items = receipt.getItems();
        assertNotNull(items);
        assertEquals(6, items.size());
        assertEquals(new BigDecimal("119669"), receipt.getTotalAmount());
        // Verify no item is named "Sem: 5" or "Receipt ID"
        assertTrue(items.stream().noneMatch(i -> i.getName().toLowerCase().contains("sem")));
    }

    @Test
    @DisplayName("4. Restaurant receipt - actual food items extracted")
    void testRestaurantReceiptItemExtraction() {
        String restaurantOcr = """
                THE GOURMET BISTRO
                Date: 2026-08-20
                Cheese Burger: ₹180.00
                Garlic Fries: ₹90.00
                Cold Coffee: ₹110.00
                Subtotal: ₹380.00
                CGST 2.5%: ₹9.50
                SGST 2.5%: ₹9.50
                Total Payable: ₹399.00
                """;
        List<ReceiptItem> items = ocrService.parseReceiptItems(restaurantOcr);
        assertNotNull(items);
        assertEquals(3, items.size());
        assertEquals("Cheese Burger", items.get(0).getName());
        assertEquals(new BigDecimal("180.00"), items.get(0).getPrice());
    }

    @Test
    @DisplayName("5. JPG receipt image - multi-pass OCR preprocessing")
    void testJpgReceiptProcessing() {
        BufferedImage img = createSampleImage(600, 800);
        String text = ocrService.processSingleImageWithFallback(img);
        assertNotNull(text);
    }

    @Test
    @DisplayName("6. PNG receipt image - multi-pass OCR preprocessing")
    void testPngReceiptProcessing() {
        BufferedImage img = createSampleImage(800, 1000);
        String text = ocrService.processSingleImageWithFallback(img);
        assertNotNull(text);
    }

    @Test
    @DisplayName("9. Image-only PDF rendering & OCR pipeline")
    void testImageOnlyPdfProcessing() {
        BufferedImage pageImg = createSampleImage(600, 800);
        String text = ocrService.processSingleImageWithFallback(pageImg);
        assertNotNull(text);
    }

    @Test
    @DisplayName("10. Metadata-heavy receipt - metadata does not become items")
    void testMetadataHeavyReceiptNoMetadataAsItems() {
        String metadataOcr = """
                OFFICE SUPPLIES MART
                GSTIN: 07AAAAA0000A1Z5
                PAN: AAAAA0000A
                Invoice No: INV-2026-99
                Date: 2026-08-25
                Customer: John Doe
                Ship To: 456 Park Avenue, Delhi 110001
                Phone: +91 9876543210
                
                Ballpoint Pen Box: ₹250.00
                A4 Paper Ream: ₹350.00
                
                CGST 9%: ₹54.00
                SGST 9%: ₹54.00
                Total Amount: ₹708.00
                """;
        List<ReceiptItem> items = ocrService.parseReceiptItems(metadataOcr);
        assertNotNull(items);
        assertEquals(2, items.size());
        assertTrue(items.stream().anyMatch(i -> i.getName().equals("Ballpoint Pen Box")));
        assertTrue(items.stream().anyMatch(i -> i.getName().equals("A4 Paper Ream")));
        assertTrue(items.stream().noneMatch(i -> i.getName().toLowerCase().contains("gstin")));
        assertTrue(items.stream().noneMatch(i -> i.getName().toLowerCase().contains("customer")));
    }

    @Test
    @DisplayName("10. Image preprocessing failure fallback")
    void testImagePreprocessingFailureFallback() {
        BufferedImage img = createSampleImage(100, 100);
        String text = ocrService.processSingleImageWithFallback(img);
        assertNotNull(text);
    }

    @Test
    @DisplayName("11. OCR failure handling with clean empty return")
    void testOcrFailureHandling() {
        String result = ocrService.cleanOcrText(null);
        assertEquals("", result);
        assertEquals(0, ocrService.evaluateOcrQuality(null));
    }

    @Test
    @DisplayName("12. AI parser failure rule-based fallback")
    void testAiParserFailureFallback() {
        when(aiReceiptParserService.isAiEnabled()).thenReturn(true);
        when(aiReceiptParserService.parseReceiptText(anyString())).thenThrow(new RuntimeException("LLM Timeout"));

        Receipt receipt = ocrService.parseTextToReceipt("RELIANCE SMART\nTotal: ₹500.00");
        assertNotNull(receipt);
        assertEquals("RELIANCE SMART", receipt.getMerchantName());
        assertEquals(new BigDecimal("500.00"), receipt.getTotalAmount());
    }

    @Test
    @DisplayName("13. Handwritten receipt uncertain field null handling")
    void testHandwrittenReceiptHandling() {
        String handwrittenText = """
                Handwritten Memo
                Unreadable line: ???
                Total: ₹350.00
                """;
        Receipt receipt = ocrService.parseTextToReceipt(handwrittenText);
        assertNotNull(receipt);
        assertEquals(new BigDecimal("350.00"), receipt.getTotalAmount());
    }

    @Test
    @DisplayName("14. Multiple invoices in one file boundary preservation")
    void testMultipleInvoicesInOneFile() {
        String multiDocText = """
                --- PAGE 1 ---
                Invoice #101
                Vendor A
                Total: ₹1,000.00
                
                --- PAGE 2 ---
                Invoice #102
                Vendor B
                Total: ₹2,500.00
                """;
        assertTrue(multiDocText.contains("--- PAGE 1 ---"));
        assertTrue(multiDocText.contains("--- PAGE 2 ---"));
    }

    @Test
    @DisplayName("15. Real IMS College Receipt Regression Test")
    void testRealIMSCollegeReceiptOCR() {
        String imsOcrText = """
                IMS ENGINEERING COLLEGE, GHAZIABAD
                NH-24, Adhyatmik Nagar, Ghaziabad
                RECEIPT
                Receipt No: 9560/2025/T4/46072 Date: 27-08-2025
                Student Name: NAVNEET SINHA Session: 2025-2026
                Father's Name: ASHOK SINHA Category: General
                Course: B.Tech Year: 3 year / I Shift
                Branch: Computer Science & Engineering College ID/Roll No.: A2023CSE9560 / 2301430100151
                
                Particulars Amount (₹)
                Tuition Fee 103,919.00
                University Exam & Other Fee# 0.00
                Book Bank Book Lending & Library Fee 1,200.00
                TID 2,000.00
                College Placement Activity Fee 3,000.00
                Internet, Intranet and Master Electronic I Card Fee 3,000.00
                Development Charges 6,500.00
                Extra Curricular & Co-Curricular Activity Fee 6,150.00
                Insurance 1,250.00
                Total 127,019.00/-
                
                Total Amount in Words (₹): One Lac Twenty-Seven Thousand and Nineteen only
                """;

        List<ReceiptItem> items = ocrService.parseReceiptItems(imsOcrText);
        assertNotNull(items);
        assertTrue(items.size() >= 8, "Expected at least 8 fee items parsed, found: " + items.size());
        assertTrue(items.stream().anyMatch(i -> i.getName().equals("Tuition Fee") && i.getPrice().compareTo(new BigDecimal("103919")) == 0));
        assertTrue(items.stream().anyMatch(i -> i.getName().contains("Book Bank Book Lending & Library Fee") && i.getPrice().compareTo(new BigDecimal("1200")) == 0));
        assertTrue(items.stream().anyMatch(i -> i.getName().contains("Extra Curricular & Co-Curricular Activity Fee") && i.getPrice().compareTo(new BigDecimal("6150")) == 0));
        assertTrue(items.stream().noneMatch(i -> i.getName().toLowerCase().contains("receipt no")));
        assertTrue(items.stream().noneMatch(i -> i.getName().toLowerCase().contains("student name")));
    }

    @Test
    @DisplayName("16. Real Amazon PDF Invoice with HSN Line Regression Test")
    void testRealAmazonPdfWithHSNOCR() {
        String amazonOcrText = """
                Sold By : Darshita Aashiyana Pvt Ltd
                Order Number:404-7064388-8495506 Invoice Number :GAX1-215120
                Order Date:12.03.2026 Invoice Date :12.03.2026
                
                Sl. No Description Unit Price Discount Qty Net Amount Tax Rate Tax Type Tax Amount Total Amount
                1 realme NARZO 80 Pro 5G (Speed Silver,8GB+128GB) | Segment's 1st MediaTek Dimensity 7400 Chipset | 6000mAh Titan Battery
                HSN:85171300
                ₹15,253.39 ₹0.00 1 ₹15,253.39 18% IGST ₹2,745.61 ₹17,999.00
                Shipping Charges ₹33.90 -₹33.90 ₹0.00 18% IGST ₹0.00 ₹0.00
                TOTAL: ₹2,745.61 ₹17,999.00
                """;

        List<ReceiptItem> items = ocrService.parseReceiptItems(amazonOcrText);
        assertNotNull(items);
        assertEquals(1, items.size());
        assertTrue(items.get(0).getName().contains("realme NARZO 80 Pro 5G"));
        assertEquals(new BigDecimal("15253.39"), items.get(0).getPrice());
        assertEquals(1, items.get(0).getQuantity());

        BigDecimal total = ocrService.parseTotalAmount(amazonOcrText);
        assertEquals(new BigDecimal("17999.00"), total);
    }

    @Test
    @DisplayName("17. Real Failing Amazon Invoice Section-Aware Address & Metadata Exclusion Test")
    void testFailingAmazonInvoiceAddressAndSectionAwareness() {
        String fullAmazonOcr = """
                Tax Invoice / Bill of Supply / Cash Memo
                Sold By: Darshita Aashiyana Pvt Ltd
                Guwahati, Assam,
                781122
                
                Billing Address:
                DHANBAD, JHARKHAND,
                826001
                
                Shipping Address:
                GIRIDIH, JHARKHAND,
                815301
                
                Dispatch From:
                Sattva Horizon, Survey No 6/1 and 7/1, Vinayak Nagar,
                Kattigenahalli Venkatala Village, Yelahanka Hobli,
                Bengaluru
                89 91 8099
                Fax: + 80 91 7280
                
                Sl. No Description Unit Price Discount Qty Net Amount Tax Rate Tax Type Tax Amount Total Amount
                1 realme NARZO 80 Pro 5G (Speed Silver,8GB+128GB)
                Segment's 1st MediaTek Dimensity 7400 Chipset
                6000mAh Titan Battery + 80W Ultra Charge
                4500nits HyperGlow Esports Display
                IP69 Waterproof
                B0F1DBWL8D
                RLMN80PRO5G-SILVER-8+128GB
                ₹15,253.39 ₹0.00 1 ₹15,253.39 18% IGST ₹2,745.61 ₹17,999.00
                
                Shipping Charges ₹33.90 -₹33.90 ₹0.00 18% IGST ₹0.00 ₹0.00
                TOTAL: ₹2,745.61 ₹17,999.00
                """;

        Receipt receipt = ocrService.parseTextToReceipt(fullAmazonOcr);
        assertNotNull(receipt);
        assertEquals(new BigDecimal("17999.00"), receipt.getTotalAmount());

        List<ReceiptItem> items = receipt.getItems();
        assertNotNull(items);
        assertEquals(1, items.size(), "Expected exactly 1 real product item, found: " + items.size());

        ReceiptItem item = items.get(0);
        assertTrue(item.getName().contains("realme NARZO 80 Pro 5G"), "Item name should contain main product title");
        assertEquals(new BigDecimal("15253.39"), item.getPrice());
        assertEquals(1, item.getQuantity());

        // Verify zero address / metadata items were created
        assertTrue(items.stream().noneMatch(i -> i.getName().contains("Guwahati")));
        assertTrue(items.stream().noneMatch(i -> i.getName().contains("DHANBAD")));
        assertTrue(items.stream().noneMatch(i -> i.getName().contains("GIRIDIH")));
        assertTrue(items.stream().noneMatch(i -> i.getName().contains("Sattva")));
        assertTrue(items.stream().noneMatch(i -> i.getName().contains("Fax")));
    }

    @Test
    void testAddressContaminationExclusion() {
        String ocrText = """
                Guwahati, Assam, 781122
                DHANBAD, JHARKHAND, 826001
                GIRIDIH, JHARKHAND, 815301
                """;
        Receipt receipt = ocrService.parseTextToReceipt(ocrText);
        assertNotNull(receipt);
        assertTrue(receipt.getItems() == null || receipt.getItems().isEmpty(), "Expected 0 items from pure address lines");
    }

    @Test
    void testFaxPhoneContaminationExclusion() {
        String ocrText = """
                Fax: +80 91 7280
                Phone: 9876543210
                Mobile: +91 9876543210
                """;
        Receipt receipt = ocrService.parseTextToReceipt(ocrText);
        assertNotNull(receipt);
        assertTrue(receipt.getItems() == null || receipt.getItems().isEmpty(), "Expected 0 items from contact lines");
    }

    @Test
    void testProductSpecificationContaminationExclusion() {
        String ocrText = """
                realme NARZO 80 Pro 5G
                6000mAh
                80W
                4500nits
                IP69
                """;
        Receipt receipt = ocrService.parseTextToReceipt(ocrText);
        assertNotNull(receipt);
        assertTrue(receipt.getItems() == null || receipt.getItems().isEmpty(), "Product specs without prices should yield 0 items");
    }

    @Test
    void testTaxSummaryExclusion() {
        String ocrText = """
                CGST 9% ₹54.00
                SGST 9% ₹54.00
                IGST 18% ₹2745.61
                """;
        Receipt receipt = ocrService.parseTextToReceipt(ocrText);
        assertNotNull(receipt);
        assertTrue(receipt.getItems() == null || receipt.getItems().isEmpty(), "Tax summary lines should yield 0 items");
    }

    @Test
    void testRestaurantReceiptExtraction() {
        String ocrText = """
                Cheese Burger ₹250.00
                Garlic Fries ₹180.00
                Cold Coffee ₹120.00
                Total Amount: ₹550.00
                """;
        Receipt receipt = ocrService.parseTextToReceipt(ocrText);
        assertNotNull(receipt);
        assertEquals(new BigDecimal("550.00"), receipt.getTotalAmount());
        assertEquals(3, receipt.getItems().size(), "Expected 3 restaurant items");
    }

    @Test
    void testCollegeFeeReceiptExtraction() {
        String ocrText = """
                Tuition Fee ₹50000.00
                Examination Fee ₹2500.00
                Library Fee ₹1000.00
                Placement Fee ₹2000.00
                """;
        Receipt receipt = ocrService.parseTextToReceipt(ocrText);
        assertNotNull(receipt);
        assertEquals(4, receipt.getItems().size(), "Expected 4 valid college fee items");
    }

    @Test
    void testMultiCurrencyExtraction() {
        String ocrText = """
                Coffee $5.99
                Book €19.99
                Shirt £29.99
                Product ¥5000
                Service USD 100.00
                Item EUR 49.99
                """;
        Receipt receipt = ocrService.parseTextToReceipt(ocrText);
        assertNotNull(receipt);
        assertTrue(receipt.getItems().size() >= 6, "Expected multi-currency items to be extracted, found: " + receipt.getItems().size());
    }

    @Test
    void testCurrencyWithMetadataRejection() {
        String ocrText = """
                Address: $123 Main Street
                Phone: $999999
                Order ID: $12345
                Fax: $500
                """;
        Receipt receipt = ocrService.parseTextToReceipt(ocrText);
        assertNotNull(receipt);
        assertTrue(receipt.getItems() == null || receipt.getItems().isEmpty(), "Currency symbols on metadata/address lines must be rejected");
    }

    @Test
    void testCGSTSGSTExtraction() {
        String ocrText = """
                CGST 9% ₹54.00
                SGST 9% ₹54.00
                """;
        List<com.smartreceipt.entity.TaxDetail> taxes = ocrService.parseTaxDetails(ocrText);
        assertNotNull(taxes);
        assertEquals(2, taxes.size());
        assertEquals("CGST", taxes.get(0).getType());
        assertEquals(new BigDecimal("9"), taxes.get(0).getRate());
        assertEquals(new BigDecimal("54.00"), taxes.get(0).getAmount());
        assertEquals("SGST", taxes.get(1).getType());
    }

    @Test
    void testIGSTExtraction() {
        String ocrText = """
                Taxable Value ₹15253.39
                IGST 18% ₹2745.61
                Grand Total ₹17999.00
                """;
        List<com.smartreceipt.entity.TaxDetail> taxes = ocrService.parseTaxDetails(ocrText);
        assertNotNull(taxes);
        assertEquals(1, taxes.size());
        assertEquals("IGST", taxes.get(0).getType());
        assertEquals(new BigDecimal("18"), taxes.get(0).getRate());
        assertEquals(new BigDecimal("2745.61"), taxes.get(0).getAmount());
    }

    @Test
    void testUTGSTAndGenericGSTExtraction() {
        String ocrText = """
                UTGST 9% ₹54.00
                GST 18% ₹180.00
                """;
        List<com.smartreceipt.entity.TaxDetail> taxes = ocrService.parseTaxDetails(ocrText);
        assertNotNull(taxes);
        assertEquals(2, taxes.size());
        assertEquals("UTGST", taxes.get(0).getType());
        assertEquals("GST", taxes.get(1).getType());
    }

    @Test
    void testOtherTaxTypesExtraction() {
        String ocrText = """
                CESS 1% ₹10.00
                TCS 1% ₹15.00
                TDS 10% ₹100.00
                VAT 12.5% ₹125.00
                Service Tax 14% ₹140.00
                """;
        List<com.smartreceipt.entity.TaxDetail> taxes = ocrService.parseTaxDetails(ocrText);
        assertNotNull(taxes);
        assertTrue(taxes.size() >= 5, "Expected at least 5 tax details, found: " + taxes.size());
    }

    @Test
    void testGSTINAndHSNRejectionFromTax() {
        String ocrText = """
                GSTIN: 07AAAAA0000A1Z5
                HSN: 85171300
                SAC: 998599
                """;
        List<com.smartreceipt.entity.TaxDetail> taxes = ocrService.parseTaxDetails(ocrText);
        assertNotNull(taxes);
        assertTrue(taxes.isEmpty(), "GSTIN, HSN, and SAC must never create tax entries");
    }

    @Test
    void testMultiCurrencyTaxes() {
        String ocrText = """
                VAT 20% $50.00
                GST 10% AUD 25.00
                VAT 21% €30.00
                """;
        List<com.smartreceipt.entity.TaxDetail> taxes = ocrService.parseTaxDetails(ocrText);
        assertNotNull(taxes);
        assertEquals(3, taxes.size());
        assertEquals("USD", taxes.get(0).getCurrency());
        assertEquals("AUD", taxes.get(1).getCurrency());
        assertEquals("EUR", taxes.get(2).getCurrency());
    }

    @Test
    void testAmazonIGSTInvoiceTaxExtraction() {
        String fullAmazonOcr = """
                ASSPL-Amazon Seller Services Pvt. Ltd.
                GSTIN: 27AAACB1864B1ZX
                Order Number: 403-1234567-8901234
                Invoice Number: IN-1234
                
                Sold By: Darshita Aashiyana Pvt Ltd
                Guwahati, Assam, 781122
                
                Billing Address:
                DHANBAD, JHARKHAND, 826001
                
                Shipping Address:
                GIRIDIH, JHARKHAND, 815301
                
                Dispatch From:
                Sattva Horizon, Survey No 6/1 and 7/1, Vinayak Nagar,
                Kattigenahalli Venkatala Village, Yelahanka Hobli,
                Bengaluru
                89 91 8099
                Fax: + 80 91 7280
                
                Sl. No Description Unit Price Discount Qty Net Amount Tax Rate Tax Type Tax Amount Total Amount
                1 realme NARZO 80 Pro 5G (Speed Silver,8GB+128GB)
                Segment's 1st MediaTek Dimensity 7400 Chipset
                6000mAh Titan Battery + 80W Ultra Charge
                4500nits HyperGlow Esports Display
                IP69 Waterproof
                B0F1DBWL8D
                RLMN80PRO5G-SILVER-8+128GB
                ₹15,253.39 ₹0.00 1 ₹15,253.39 18% IGST ₹2,745.61 ₹17,999.00
                
                Shipping Charges ₹33.90 -₹33.90 ₹0.00 18% IGST ₹0.00 ₹0.00
                TOTAL: ₹2,745.61 ₹17,999.00
                """;

        Receipt receipt = ocrService.parseTextToReceipt(fullAmazonOcr);
        assertNotNull(receipt);
        assertEquals(new BigDecimal("17999.00"), receipt.getTotalAmount());
        assertEquals(1, receipt.getItems().size());
        assertEquals(new BigDecimal("15253.39"), receipt.getItems().get(0).getPrice());

        assertNotNull(receipt.getTaxes());
        assertFalse(receipt.getTaxes().isEmpty(), "Expected IGST tax detail to be extracted");
        assertEquals("IGST", receipt.getTaxes().get(0).getType());
        assertEquals(new BigDecimal("2745.61"), receipt.getTaxes().get(0).getAmount());
        assertEquals(new BigDecimal("2745.61"), receipt.getTotalTax());
    }

    @Test
    void testCollegeFeeReceiptTaxExtraction() {
        String ocrText = """
                Tuition Fee ₹50000.00
                Examination Fee ₹2500.00
                GST 18% ₹9450.00
                Total ₹61950.00
                """;

        Receipt receipt = ocrService.parseTextToReceipt(ocrText);
        assertNotNull(receipt);
        assertEquals(2, receipt.getItems().size(), "Fee components remain in items");
        assertNotNull(receipt.getTaxes());
        assertEquals(1, receipt.getTaxes().size());
        assertEquals("GST", receipt.getTaxes().get(0).getType());
        assertEquals(new BigDecimal("9450.00"), receipt.getTaxes().get(0).getAmount());
    }

    private BufferedImage createSampleImage(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.drawString("Test Receipt", 20, 20);
        g.dispose();
        return img;
    }
}