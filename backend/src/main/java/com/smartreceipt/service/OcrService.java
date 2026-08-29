package com.smartreceipt.service;

import com.smartreceipt.entity.DocumentType;
import com.smartreceipt.dto.ReceiptAIItem;
import com.smartreceipt.dto.ReceiptAIResponse;
import com.smartreceipt.dto.ReceiptAITax;
import com.smartreceipt.dto.ReceiptResponse;
import com.smartreceipt.entity.Receipt;
import com.smartreceipt.entity.ReceiptItem;
import com.smartreceipt.entity.TaxDetail;
import com.smartreceipt.exception.OcrException;
import com.smartreceipt.repository.ReceiptRepository;
import com.smartreceipt.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OcrService {

    private final ReceiptRepository receiptRepository;
    private final ReceiptService receiptService;
    private final AIReceiptParserService aiReceiptParserService;
    private final ImagePreprocessingService imagePreprocessingService;
    private final DocumentClassificationService classificationService;

    @Value("${tesseract.datapath:./tessdata}")
    private String tesseractDataPath;

    @Value("${tesseract.language:eng}")
    private String tesseractLanguage;

    @Autowired
    public OcrService(ReceiptRepository receiptRepository,
                      ReceiptService receiptService,
                      AIReceiptParserService aiReceiptParserService,
                      ImagePreprocessingService imagePreprocessingService,
                      DocumentClassificationService classificationService) {
        this.receiptRepository = receiptRepository;
        this.receiptService = receiptService;
        this.aiReceiptParserService = aiReceiptParserService;
        this.imagePreprocessingService = imagePreprocessingService != null ? imagePreprocessingService : new ImagePreprocessingService();
        this.classificationService = classificationService;
    }

    public ReceiptResponse processReceiptUpload(MultipartFile file, UserPrincipal currentUser) {
        validateFile(file);
        log.info("Image validation completed");

        File tempFile = null;
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            tempFile = File.createTempFile("receipt_upload_", "." + extension);

            try (InputStream is = file.getInputStream()) {
                Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Processing receipt upload: filename={}, size={} bytes, mimeType={}",
                    originalFilename, file.getSize(), file.getContentType());

            log.info("OCR processing started");
            String rawText = extractRawText(tempFile, extension);
            log.info("OCR processing completed");

            if (rawText == null || rawText.trim().isEmpty()) {
                throw new OcrException("Failed to extract any text from the uploaded receipt.");
            }

            // Document Type Classification
            DocumentType docType = classificationService.classifyDocument(rawText);
            if (docType == DocumentType.MEDICAL_REPORT) {
                throw new OcrException("This document is classified as a medical report. Please upload it to the Medical Report section.");
            }
            if (docType == DocumentType.UNKNOWN) {
                throw new OcrException("This document could not be identified as a receipt or medical report.");
            }

            Receipt receipt = parseTextToReceipt(rawText);
            receipt.setUserId(currentUser.getId());
            receipt.setCreatedAt(LocalDateTime.now());

            log.info("Receipt persistence started");
            Receipt savedReceipt = receiptRepository.save(receipt);
            log.info("Receipt persistence completed");
            
            // ========== SMART RECEIPT DEBUG LOGGING ==========
            log.info("\n========== SMART RECEIPT DEBUG ==========\n" +
                    "[1] FILE\nname: {}\ntype: {}\nsize: {} bytes\n\n" +
                    "[2] RAW OCR TEXT\n{}\n\n" +
                    "[3] CLEANED OCR TEXT\n{}\n\n" +
                    "[4] AI ENABLED\n{}\n\n" +
                    "[8] FINAL RECEIPT\nmerchant: {}\ndate: {}\nitems count: {}\ntotal: {}\n" +
                    "==========================================",
                    originalFilename, extension, file.getSize(),
                    rawText, cleanOcrText(rawText),
                    aiReceiptParserService != null && aiReceiptParserService.isAiEnabled(),
                    savedReceipt.getMerchantName(), savedReceipt.getReceiptDate(),
                    savedReceipt.getItems() != null ? savedReceipt.getItems().size() : 0,
                    savedReceipt.getTotalAmount());

            return receiptService.mapToResponse(savedReceipt);

        } catch (IOException e) {
            log.error("Failed to store or process uploaded file", e);
            throw new OcrException("Error processing receipt file upload: " + e.getMessage(), e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                try {
                    Files.delete(tempFile.toPath());
                } catch (IOException e) {
                    log.warn("Could not delete temporary file: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }

    public String extractRawText(File file, String extension) {
        log.info("Starting text extraction for file: {}, type: {}, size: {} bytes",
                file.getName(), extension, file.length());
        if ("pdf".equalsIgnoreCase(extension)) {
            return extractTextFromPdf(file);
        } else {
            return extractTextFromImage(file);
        }
    }

    public String extractTextFromPdf(File pdfFile) {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String pdfText = stripper.getText(document);

            int textQualityScore = evaluateOcrQuality(pdfText);
            log.info("PDF direct text extraction complete: pages={}, extractedChars={}, qualityScore={}",
                    document.getNumberOfPages(), pdfText.length(), textQualityScore);

            if (textQualityScore > 10) {
                return cleanOcrText(pdfText);
            }

            log.warn("PDF direct text quality low (score: {}). Falling back to multi-pass image rendering OCR.", textQualityScore);
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            StringBuilder combinedOcrText = new StringBuilder();

            for (int page = 0; page < document.getNumberOfPages(); ++page) {
                log.info("Rendering PDF page {}/{} to high-resolution image for multi-pass OCR...", page + 1, document.getNumberOfPages());
                BufferedImage pageImage = pdfRenderer.renderImageWithDPI(page, 300);
                String pageText = processSingleImageWithFallback(pageImage);
                combinedOcrText.append("--- PAGE ").append(page + 1).append(" ---\n");
                combinedOcrText.append(pageText).append("\n");
            }

            return cleanOcrText(combinedOcrText.toString());

        } catch (IOException e) {
            log.error("Failed to extract text from PDF file", e);
            throw new OcrException("Failed to read PDF document: " + e.getMessage(), e);
        }
    }

    public String extractTextFromImage(File imageFile) {
        try {
            BufferedImage originalImage = ImageIO.read(imageFile);
            if (originalImage == null) {
                throw new OcrException("Unsupported or corrupt image format.");
            }

            log.info("Loaded image file: dimensions={}x{}, colorModel={}",
                    originalImage.getWidth(), originalImage.getHeight(), originalImage.getColorModel().getClass().getSimpleName());

            return processSingleImageWithFallback(originalImage);
        } catch (IOException e) {
            log.error("Failed to read image file for OCR", e);
            throw new OcrException("Error reading image file: " + e.getMessage(), e);
        }
    }

    public String processSingleImageWithFallback(BufferedImage original) {
        List<ImagePreprocessingService.ImageVariant> variants = imagePreprocessingService.generatePreprocessingVariants(original);

        String bestOcrText = "";
        int bestScore = -1;
        String bestVariantName = "None";

        for (ImagePreprocessingService.ImageVariant variant : variants) {
            try {
                String variantText = runTesseractOnImage(variant.getImage());
                String cleanedVariantText = cleanOcrText(variantText);
                int score = evaluateOcrQuality(cleanedVariantText);

                log.info("OCR Variant [{}] -> dimensions={}x{}, chars={}, qualityScore={}",
                        variant.getName(), variant.getImage().getWidth(), variant.getImage().getHeight(),
                        cleanedVariantText.length(), score);

                if (score > bestScore) {
                    bestScore = score;
                    bestOcrText = cleanedVariantText;
                    bestVariantName = variant.getName();
                }
            } catch (Exception e) {
                log.warn("Error running OCR on variant [{}]: {}", variant.getName(), e.getMessage());
            }
        }

        log.info("Selected OCR variant: [{}] (score: {})", bestVariantName, bestScore);

        if (bestOcrText.isEmpty()) {
            log.warn("All preprocessed OCR variants returned empty text. Executing original image fallback OCR.");
            try {
                bestOcrText = cleanOcrText(runTesseractOnImage(original));
            } catch (Exception e) {
                log.error("Original image fallback OCR failed: {}", e.getMessage());
            }
        }

        return bestOcrText;
    }

    public String cleanOcrText(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return "";
        }

        // 1. Remove non-printable control characters (preserve line breaks \n \r \t)
        String cleaned = rawText.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");

        // 2. Contextually replace '?' with '₹' ONLY when followed by digits or monetary format (e.g. "?250.00" -> "₹250.00")
        cleaned = cleaned.replaceAll("(^|\\s)\\?(\\s*\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d{2})?)", "$1₹$2");

        // 3. Normalize currency spaces (e.g. "Rs . 500" -> "Rs. 500", "₹  100" -> "₹ 100")
        cleaned = cleaned.replaceAll("(?i)(₹|rs\\.?|inr|\\$)\\s+\\.", "$1.");
        cleaned = cleaned.replaceAll("(?i)(₹|rs\\.?|inr|\\$)\\s{2,}", "$1 ");

        // 4. Remove excessive blank lines (> 2 -> 1)
        cleaned = cleaned.replaceAll("(\\r?\\n){3,}", "\n\n");

        return cleaned.trim();
    }

    public int evaluateOcrQuality(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }

        int score = 0;
        String[] lines = text.split("\r?\n");
        score += Math.min(lines.length * 2, 40);

        // 1. Key financial/receipt keywords
        Pattern keywordPattern = Pattern.compile("(?i)\\b(total|grand\\s*total|subtotal|amount|payable|tax|cgst|sgst|igst|vat|discount|net|receipt|invoice|date|qty|quantity|price|item|bill|store|merchant|thank|description|unit\\s*price)\\b");
        Matcher kwMatcher = keywordPattern.matcher(text);
        while (kwMatcher.find()) {
            score += 12;
        }

        // 2. Explicit total presence
        if (Pattern.compile("(?i)\\b(grand\\s*total|total\\s*amount|amount\\s*payable|net\\s*payable|total)\\b").matcher(text).find()) {
            score += 30;
        }

        // 3. Date pattern presence
        if (Pattern.compile("(?i)\\b(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}|\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}|[a-z]{3}\\s+\\d{1,2},\\s*\\d{4})\\b").matcher(text).find()) {
            score += 20;
        }

        // 4. Currency symbols & monetary numbers
        Pattern monetaryPattern = Pattern.compile("(?:₹|rs\\.?|inr|\\$)?\\s*\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d{2})?\\b", Pattern.CASE_INSENSITIVE);
        Matcher monMatcher = monetaryPattern.matcher(text);
        while (monMatcher.find()) {
            score += 15;
        }

        // 5. Ratio of meaningful words vs garbage
        String[] words = text.split("\\s+");
        int validWordCount = 0;
        int garbageCount = 0;
        for (String word : words) {
            if (word.matches("^[a-zA-Z0-9.,₹$#-]{2,}$")) {
                validWordCount++;
            } else if (word.matches(".*[^a-zA-Z0-9.,₹$#-]{2,}.*")) {
                garbageCount++;
            }
        }
        score += validWordCount * 2;
        score -= garbageCount * 3;

        return Math.max(0, score);
    }

    public String runTesseractOnImage(BufferedImage image) {
        if (image == null) {
            return "";
        }
        ensureTessDataAvailable();

        String bestText = "";
        int bestScore = -1;
        String bestPsmStr = "PSM 3";

        // Multi-PSM evaluation: PSM 3 (auto layout), PSM 6 (uniform block), PSM 11 (sparse text)
        int[] psms = {3, 6, 11};
        for (int psm : psms) {
            try {
                ITesseract tesseract = new Tesseract();
                tesseract.setDatapath(tesseractDataPath);
                tesseract.setLanguage(tesseractLanguage);
                tesseract.setPageSegMode(psm);

                String rawText = tesseract.doOCR(image);
                String cleaned = cleanOcrText(rawText);
                int score = evaluateOcrQuality(cleaned);

                if (score > bestScore) {
                    bestScore = score;
                    bestText = rawText;
                    bestPsmStr = "PSM " + psm;
                }

                if (psm == 3 && score > 45) {
                    return rawText;
                }
            } catch (Throwable e) {
                log.warn("Tesseract PSM {} OCR warning: {}", psm, e.getMessage());
            }
        }

        if (!bestPsmStr.equals("PSM 3")) {
            log.info("Selected Tesseract mode {} with quality score {}", bestPsmStr, bestScore);
        }
        return bestText;
    }

    public Receipt parseTextToReceipt(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Receipt.builder()
                    .items(new ArrayList<>())
                    .build();
        }

        // Try AI parsing first if enabled
        if (aiReceiptParserService != null && aiReceiptParserService.isAiEnabled()) {
            try {
                ReceiptAIResponse aiResponse = aiReceiptParserService.parseReceiptText(text);

                if (aiResponse != null) {
                    log.info("Successfully parsed receipt using AI parser. Mapping fields...");
                    log.info("[5] AI RAW RESPONSE:\n{}", aiResponse);

                    String merchantName = aiResponse.getMerchantName() != null
                            ? aiResponse.getMerchantName().trim()
                            : null;

                    LocalDate receiptDate = null;
                    if (aiResponse.getReceiptDate() != null) {
                        receiptDate = tryParseDate(aiResponse.getReceiptDate());
                    }
                    if (receiptDate == null) {
                        receiptDate = parseReceiptDate(text);
                    }

                    // Map AI items to ReceiptItem with deterministic validation
                    List<ReceiptItem> items = new ArrayList<>();
                    if (aiResponse.getItems() != null) {
                        for (ReceiptAIItem aiItem : aiResponse.getItems()) {
                            if (aiItem.getName() != null && !aiItem.getName().trim().isEmpty()) {
                                String name = aiItem.getName().trim();
                                int qty = aiItem.getQuantity() != null ? aiItem.getQuantity() : 1;

                                BigDecimal unitPrice = aiItem.getUnitPrice();
                                BigDecimal lineTotal = aiItem.getItemTotal();
                                BigDecimal price = unitPrice;
                                if ((price == null || price.compareTo(BigDecimal.ZERO) <= 0) && lineTotal != null && lineTotal.compareTo(BigDecimal.ZERO) > 0) {
                                    price = lineTotal.divide(BigDecimal.valueOf(qty), 2, RoundingMode.HALF_UP);
                                }
                                if (price == null) {
                                    price = BigDecimal.ZERO;
                                }

                                ItemCandidate candidate = ItemCandidate.builder()
                                        .name(name)
                                        .quantity(qty)
                                        .unitPrice(price)
                                        .lineTotal(lineTotal)
                                        .source(ItemCandidate.Source.AI)
                                        .confidence(0.95)
                                        .build();

                                if (validateItemCandidate(candidate)) {
                                    items.add(ReceiptItem.builder()
                                            .name(name)
                                            .quantity(qty)
                                            .price(price)
                                            .category(aiItem.getCategory() != null ? aiItem.getCategory().trim() : null)
                                            .build());
                                }
                            }
                        }
                    }

                    /*
                     * Validation & Total Selection:
                     * 1. Explicit AI financial total is primary.
                     * 2. Deterministic OCR total acts as a validation layer around AI.
                     * 3. Calculated items sum is strictly a last resort fallback when no explicit total exists.
                     */
                    BigDecimal finalTotal = null;

                    if (aiResponse.getFinancials() != null && aiResponse.getFinancials().getTotalAmount() != null
                            && aiResponse.getFinancials().getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
                        finalTotal = aiResponse.getFinancials().getTotalAmount();
                        log.info("Extracted primary total from AI financials: {}", finalTotal);
                    }

                    // Deterministic OCR total validation layer
                    BigDecimal ocrTotal = parseTotalAmount(text);
                    if (ocrTotal != null && ocrTotal.compareTo(BigDecimal.ZERO) > 0) {
                        if (finalTotal == null || finalTotal.compareTo(BigDecimal.ZERO) <= 0) {
                            finalTotal = ocrTotal;
                            log.info("AI financial total was missing/zero. Using deterministic OCR total: {}", finalTotal);
                        } else if (finalTotal.compareTo(new BigDecimal("100000")) > 0
                                && ocrTotal.compareTo(new BigDecimal("100000")) < 0) {
                            log.warn("AI financial total ({}) is unrealistically large compared to explicit OCR total ({}). Preferring explicit OCR total.", finalTotal, ocrTotal);
                            finalTotal = ocrTotal;
                        }
                    }

                    // Fallback: Items subtotal sum ONLY if both AI total and OCR total are missing/zero
                    if (finalTotal == null || finalTotal.compareTo(BigDecimal.ZERO) <= 0) {
                        if (!items.isEmpty()) {
                            finalTotal = items.stream()
                                    .map(item -> {
                                        BigDecimal qty = BigDecimal.valueOf(item.getQuantity() != null ? item.getQuantity() : 1);
                                        BigDecimal price = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
                                        return price.multiply(qty);
                                    })
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                            log.info("No explicit AI or OCR total found. Calculated fallback total from items: {}", finalTotal);
                        }
                    }

                    List<TaxDetail> taxes = new ArrayList<>();
                    BigDecimal subtotal = null;
                    BigDecimal totalTax = null;
                    BigDecimal discount = null;
                    BigDecimal shippingAmount = null;

                    if (aiResponse.getFinancials() != null) {
                        subtotal = aiResponse.getFinancials().getSubtotal() != null ? aiResponse.getFinancials().getSubtotal() : aiResponse.getFinancials().getTaxableAmount();
                        discount = aiResponse.getFinancials().getTotalDiscount();
                        shippingAmount = aiResponse.getFinancials().getShippingCharges() != null ? aiResponse.getFinancials().getShippingCharges() : aiResponse.getFinancials().getDeliveryCharges();
                        totalTax = aiResponse.getFinancials().getTotalTax();

                        if (aiResponse.getFinancials().getTaxes() != null) {
                            for (ReceiptAITax aiTax : aiResponse.getFinancials().getTaxes()) {
                                if (validateTaxCandidate(aiTax.getType(), aiTax.getRate(), aiTax.getAmount(), null)) {
                                    taxes.add(TaxDetail.builder()
                                            .type(aiTax.getType())
                                            .rate(aiTax.getRate())
                                            .amount(aiTax.getAmount())
                                            .currency(aiTax.getCurrency() != null ? aiTax.getCurrency() : "INR")
                                            .build());
                                }
                            }
                        }
                    }

                    if (taxes.isEmpty()) {
                        taxes = parseTaxDetails(text);
                    }
                    if (subtotal == null) {
                        subtotal = parseSubtotal(text);
                    }
                    if (discount == null) {
                        discount = parseDiscount(text);
                    }
                    if (shippingAmount == null) {
                        shippingAmount = parseShippingAmount(text);
                    }
                    if (totalTax == null && !taxes.isEmpty()) {
                        totalTax = taxes.stream().map(TaxDetail::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                    }

                    log.info("Final AI validated receipt total: {}", finalTotal);

                    return Receipt.builder()
                            .merchantName(merchantName)
                            .receiptDate(receiptDate)
                            .totalAmount(finalTotal)
                            .subtotal(subtotal)
                            .totalTax(totalTax)
                            .discount(discount)
                            .shippingAmount(shippingAmount)
                            .taxes(taxes)
                            .category(aiResponse.getCategory() != null ? aiResponse.getCategory().trim() : null)
                            .items(items)
                            .build();
                }

            } catch (Exception e) {
                log.error("Error during AI parsing integration. Falling back to local OCR parsing.", e);
            }
        }

        // ---------------------------------------------------------
        // Fallback: Local OCR Tesseract parsing
        // ---------------------------------------------------------
        log.info("Using local OCR Tesseract parsing fallback.");

        String merchantName = parseMerchantName(text);
        LocalDate receiptDate = parseReceiptDate(text);
        BigDecimal extractedTotal = parseTotalAmount(text);
        List<ReceiptItem> items = parseReceiptItems(text);
        List<TaxDetail> taxes = parseTaxDetails(text);
        BigDecimal subtotal = parseSubtotal(text);
        BigDecimal discount = parseDiscount(text);
        BigDecimal shippingAmount = parseShippingAmount(text);
        BigDecimal totalTax = taxes.isEmpty() ? null : taxes.stream().map(TaxDetail::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Fallback total logic: if no explicit total label candidate was found, compute sum of item subtotals
        if ((extractedTotal == null || extractedTotal.compareTo(BigDecimal.ZERO) <= 0) && !items.isEmpty()) {
            BigDecimal computedSubtotal = items.stream()
                    .map(item -> {
                        BigDecimal qty = BigDecimal.valueOf(item.getQuantity() != null ? item.getQuantity() : 1);
                        BigDecimal price = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
                        return price.multiply(qty);
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (computedSubtotal.compareTo(BigDecimal.ZERO) > 0) {
                extractedTotal = computedSubtotal;
                log.info("No valid explicit OCR total found. Using items sum fallback: {}", extractedTotal);
            }
        }

        log.info("Final extracted OCR total amount: {}", extractedTotal);

        return Receipt.builder()
                .merchantName(merchantName)
                .receiptDate(receiptDate)
                .totalAmount(extractedTotal)
                .subtotal(subtotal)
                .totalTax(totalTax)
                .discount(discount)
                .shippingAmount(shippingAmount)
                .taxes(taxes)
                .items(items)
                .build();
    }

    public String parseMerchantName(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        String[] lines = text.split("\r?\n");
        Pattern metadataExclusionPattern = Pattern.compile("(?i)\\b(tax\\s*invoice|invoice|receipt|bill|cash\\s*memo|order|order\\s*no|date|gstin|pan|phone|tel|mobile|fax|email|address|ship\\s*to|bill\\s*to|sold\\s*by|page|copy|original)\\b");

        for (int i = 0; i < Math.min(lines.length, 6); i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.length() < 3) {
                continue;
            }
            if (line.matches("(?i)^--- PAGE \\d+ ---$")) {
                continue;
            }
            if (line.matches("^[0-9\\s.,/#:-]+$") || line.matches(".*\\b(07[A-Z]{5}\\d{4}[A-Z][A-Z0-9]{3}|\\+?\\d{10,})\\b.*")) {
                continue;
            }
            if (metadataExclusionPattern.matcher(line).find()) {
                continue;
            }
            String cleanName = line.replaceAll("^[^a-zA-Z0-9]+|[^a-zA-Z0-9]+$", "").trim();
            if (cleanName.length() >= 3 && cleanName.matches(".*[a-zA-Z]{2,}.*")) {
                return cleanName;
            }
        }
        return null;
    }

    public LocalDate parseReceiptDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        String[] lines = text.split("\r?\n");
        Pattern datePattern = Pattern.compile("(?i)\\b(\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}|[a-zA-Z]{3,9}\\s+\\d{1,2},\\s*\\d{4}|\\d{1,2}\\s+[a-zA-Z]{3,9}\\s+\\d{4})\\b");

        for (String line : lines) {
            Matcher matcher = datePattern.matcher(line);
            if (matcher.find()) {
                String dateStr = matcher.group(1);
                LocalDate parsedDate = tryParseDate(dateStr);
                if (parsedDate != null) {
                    return parsedDate;
                }
            }
        }
        return null;
    }

    private LocalDate tryParseDate(String dateStr) {
        String cleanDate = dateStr.trim().replaceAll("\\.", "/").replaceAll("-", "/");
        String[] formats = {
                "yyyy/MM/dd", "dd/MM/yyyy", "MM/dd/yyyy", "dd/MM/yy", "MM/dd/yy",
                "MMM dd, yyyy", "dd MMM yyyy", "MMMM dd, yyyy", "dd MMMM yyyy"
        };

        for (String format : formats) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format, Locale.ENGLISH);
                return LocalDate.parse(cleanDate, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private static class TotalCandidate {
        int tier;
        int lineIndex;
        BigDecimal amount;
        String lineContent;
        int score;
        boolean isServiceFee;

        TotalCandidate(int tier, int lineIndex, BigDecimal amount, String lineContent, int score, boolean isServiceFee) {
            this.tier = tier;
            this.lineIndex = lineIndex;
            this.amount = amount;
            this.lineContent = lineContent;
            this.score = score;
            this.isServiceFee = isServiceFee;
        }
    }

    public BigDecimal parseTotalAmount(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        String[] lines = text.split("\\r?\\n");
        log.debug("--- OCR PARSING DEBUG: Analyzing {} raw lines ---", lines.length);

        Pattern tier1Pattern = Pattern.compile("(?i)\\bgrand\\s*total(\\s*amount)?\\b");
        Pattern tier2Pattern = Pattern.compile("(?i)\\b(total\\s*amount|amount\\s*payable|payable\\s*amount|final\\s*amount|net\\s*total|invoice\\s*total|net\\s*payable|total)\\b");
        Pattern tier3Pattern = Pattern.compile("(?i)\\b(balance\\s*due|amount\\s*due)\\b");

        Pattern headerPattern = Pattern.compile("(?i)\\b(sl\\.?\\s*no|description|unit\\s*price|qty|quantity|net\\s*amount|tax\\s*rate|tax\\s*type|tax\\s*amount|hsn|sac)\\b");
        Pattern excludePattern = Pattern.compile("(?i)\\b(subtotal|sub\\s*total|sub-total|cgst|sgst|igst|vat|discount|round\\s*off|cash|change|tendered|items|qty|quantity|invoice\\s*no|gstin|phone|tel|zip|pin|account|card|sem|semester|receipt\\s*id|receipt\\s*no|page|table|roll|sl|sr)\\b");
        Pattern serviceFeePattern = Pattern.compile("(?i)\\b(cash/pay\\s*on\\s*delivery|cod\\s*fee|delivery\\s*charge|shipping\\s*charge|service\\s*charge|convenience\\s*fee|handling\\s*fee|platform\\s*fee)\\b");

        List<TotalCandidate> candidates = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String rawLine = lines[i].trim();
            if (rawLine.isEmpty() || headerPattern.matcher(rawLine).find()) {
                continue;
            }

            boolean isTier1 = tier1Pattern.matcher(rawLine).find();
            boolean isTier2 = tier2Pattern.matcher(rawLine).find();
            boolean isTier3 = tier3Pattern.matcher(rawLine).find();

            boolean hasExclusion = excludePattern.matcher(rawLine).find();
            if (hasExclusion && !isTier1 && !isTier2) {
                continue;
            }

            int tier = 0;
            if (isTier1) {
                tier = 1;
            } else if (isTier2) {
                tier = 2;
            } else if (isTier3) {
                tier = 3;
            }

            if (tier == 0) {
                continue;
            }

            BigDecimal extracted = extractLastMonetaryValueFromLine(rawLine);
            if (extracted == null && i + 1 < lines.length) {
                String nextLine = lines[i + 1].trim();
                if (!nextLine.isEmpty() && !headerPattern.matcher(nextLine).find()) {
                    extracted = extractLastMonetaryValueFromLine(nextLine);
                }
            }

            if (extracted != null && extracted.compareTo(BigDecimal.ZERO) > 0) {
                boolean isServiceFee = false;

                // Check preceding lines up to 25 lines back for explicit fee indicators (ignoring address lines)
                int startScan = Math.max(0, i - 25);
                for (int s = startScan; s <= i; s++) {
                    String scanLine = lines[s].trim();
                    if (scanLine.toLowerCase().contains("address") || scanLine.toLowerCase().contains("ship to") || scanLine.toLowerCase().contains("bill to")) {
                        continue;
                    }
                    if (serviceFeePattern.matcher(scanLine).find()) {
                        isServiceFee = true;
                        break;
                    }
                }

                int score = 0;
                if (extracted.compareTo(new BigDecimal("1000")) > 0) {
                    score += 50; // Main purchase total boost
                }

                candidates.add(new TotalCandidate(tier, i, extracted, rawLine, score, isServiceFee));
                log.debug("Candidate detected [Tier {}] line {}: '{}' -> Amount: {}, score: {}, serviceFee: {}",
                        tier, i, rawLine, extracted, score, isServiceFee);
            }
        }

        if (!candidates.isEmpty()) {
            candidates.sort((c1, c2) -> {
                if (c1.tier != c2.tier) {
                    return Integer.compare(c1.tier, c2.tier);
                }
                // Non-service fee candidates win over COD/service fee candidates
                if (c1.isServiceFee != c2.isServiceFee) {
                    return Boolean.compare(c1.isServiceFee, c2.isServiceFee);
                }
                if (c1.score != c2.score) {
                    return Integer.compare(c2.score, c1.score);
                }
                // Same tier & type: prefer larger amount (main purchase invoice over small fees)
                int amtComp = c2.amount.compareTo(c1.amount);
                if (amtComp != 0) {
                    return amtComp;
                }
                return Integer.compare(c1.lineIndex, c2.lineIndex);
            });

            TotalCandidate selected = candidates.get(0);
            log.info("OCR Total Extraction SUCCESS: Selected Tier {} candidate: '{}' -> {}",
                    selected.tier, selected.lineContent, selected.amount);
            return selected.amount;
        }

        log.info("No explicit total label candidate detected. Returning null to allow item subtotal calculation fallback.");
        return null;
    }

    private BigDecimal extractLastMonetaryValueFromLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        Pattern pattern = Pattern.compile("(?:₹|rs\\.?|inr|\\$)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(line);

        BigDecimal lastValue = null;
        while (matcher.find()) {
            String token = matcher.group(1);
            BigDecimal value = parseAndNormalizeMonetaryToken(token);
            if (value != null && value.compareTo(BigDecimal.ZERO) > 0) {
                lastValue = value;
            }
        }
        return lastValue;
    }

    public BigDecimal parseAndNormalizeMonetaryToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }

        String cleaned = token.replaceAll("[^0-9.,]", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }

        try {
            if (cleaned.contains(",") && cleaned.contains(".")) {
                cleaned = cleaned.replace(",", "");
            } else if (cleaned.contains(",") && !cleaned.contains(".")) {
                int lastCommaIndex = cleaned.lastIndexOf(",");
                if (cleaned.length() - lastCommaIndex - 1 == 2) {
                    cleaned = cleaned.substring(0, lastCommaIndex) + "." + cleaned.substring(lastCommaIndex + 1);
                } else {
                    cleaned = cleaned.replace(",", "");
                }
            }

            if (cleaned.startsWith("0") && !cleaned.startsWith("0.") && cleaned.length() > 1) {
                cleaned = cleaned.replaceAll("^0+", "");
                if (cleaned.isEmpty()) {
                    return null;
                }
            }

            BigDecimal val = new BigDecimal(cleaned);
            if (!cleaned.contains(".") && val.compareTo(new BigDecimal("1000000")) > 0) {
                return null;
            }
            return val;
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================================
    // Line Classification & Table-Aware Item Extraction Engine
    // =========================================================================

    private static final Pattern STORE_HEADER_PATTERN = Pattern.compile("(?i).*\\b(private limited|pvt\\.?\\s*ltd|ltd\\.?|inc\\.?|llp|supermarket|hypermarket|bistro)\\b.*");
    private static final Pattern GSTIN_PATTERN = Pattern.compile("\\b\\d{2}[A-Z]{5}\\d{4}[A-Z][A-Z0-9]{3}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?i)^(?:\\+?\\d{1,4}[-\\s.]?)?\\(?\\d{3}\\)?[-\\s.]?\\d{3}[-\\s.]?\\d{4}$");
    private static final Pattern URL_EMAIL_PATTERN = Pattern.compile("(?i)(https?://|www\\.|@|[a-z0-9.-]+\\.[a-z]{2,4}/)");
    private static final Pattern HSN_SAC_CODE_PATTERN = Pattern.compile("(?i)^\\s*(?:hsn|sac)\\s*:?\\s*\\d+\\s*$");

    private static final Pattern METADATA_IDENTIFIER_PATTERN = Pattern.compile("(?i)^\\s*("
            + "order\\s*(?:no|number|id)|invoice\\s*(?:no|number|details|id)|receipt\\s*(?:no|number|id)|bill\\s*(?:no|number)|"
            + "ref(?:erence)?\\s*(?:no|number|id)|transaction\\s*(?:id|no|ref)|customer\\s*(?:name|id)|student\\s*(?:name|id)|"
            + "hsn|sac|pan|gstin|asin|serial\\s*(?:no|number)|sl\\.?\\s*no|sem|semester|session|payment\\s*mode|paid\\s*by|upi|bank|ifsc|account"
            + ").*");

    private static final Pattern ADDRESS_PATTERN = Pattern.compile("(?i)^\\s*("
            + "ship\\s*to|bill\\s*to|deliver\\s*to|shipping\\s*address|billing\\s*address|seller\\s*address|buyer\\s*address|"
            + "address|pin\\s*code|pincode|zip|state|ut\\s*code|sold\\s*by|ship\\s*from|fulfillment\\s*center|dispatch\\s*from"
            + ")\\b.*");

    private static final Pattern ADDRESS_INDICATORS_PATTERN = Pattern.compile("(?i)\\b("
            + "road|street|nagar|village|hobli|district|dist|state|pincode|zip|india|survey\\s*no|plot\\s*no|house\\s*no|building|floor|landmark|sector|marg|cross|main|post|po|taluk|thana|near|opposite|opp|behind|industrial\\s*area|fax|phone|tel|mobile"
            + ")\\b");

    private static final Pattern ADDRESS_GEOGRAPHIC_NAMES_PATTERN = Pattern.compile("(?i)\\b("
            + "guwahati|assam|dhanbad|jharkhand|giridih|delhi|mumbai|maharashtra|bengaluru|bangalore|karnataka|kattigenahalli|venkatala|yelahanka|sattva|horizon|chennai|tamil\\s*nadu|kolkata|west\\s*bengal|hyderabad|telangana|pune|ahmedabad|gujarat|noida|gurgaon|gurugram|haryana|ghaziabad|uttar\\s*pradesh"
            + ")\\b");

    private static final Pattern ITEM_TABLE_HEADER_PATTERN = Pattern.compile("(?i).*\\b("
            + "description|unit\\s*price|discount|qty|quantity|net\\s*amount|tax\\s*rate|tax\\s*type|tax\\s*amount|particulars|amount\\s*\\(₹\\)|sl\\.?\\s*no|item\\s*description"
            + ")\\b.*");

    private static final Pattern NON_ITEM_SECTION_HEADER_PATTERN = Pattern.compile("(?i)^\\s*("
            + "billing\\s*address|shipping\\s*address|delivery\\s*address|sold\\s*by|ship\\s*from|fulfillment\\s*center|dispatch\\s*from|customer|buyer|student|gstin|pan|cin|contact|phone|mobile|fax|email|tax\\s*summary|tax\\s*details|payment|payment\\s*details|payment\\s*mode|bank\\s*details|ifsc|account|amount\\s*in\\s*words|total\\s*in\\s*words|authorized\\s*signatory|signature|declaration|total:|grand\\s*total|subtotal|net\\s*payable|amount\\s*payable|cash/pay\\s*on\\s*delivery|shipping\\s*&\\s*handling|cod\\s*fee|shipping\\s*charges"
            + ")\\b.*");

    private static final Pattern TAX_PATTERN = Pattern.compile("(?i)^\\s*("
            + "cgst|sgst|igst|vat|cess|tax|tax\\s*summary|tax\\s*details|taxable\\s*amount|tax\\s*rate|\\d+%\\s*(?:cgst|sgst|igst|vat|tax)?"
            + ").*");

    private static final Pattern DISCOUNT_SHIPPING_PAYMENT_PATTERN = Pattern.compile("(?i)^\\s*("
            + "discount|coupon|promo|shipping|delivery|convenience|handling|cod|cash\\s*on\\s*delivery|service\\s*charge|service\\s*fee|platform\\s*fee"
            + ").*");

    private static final Pattern TOTAL_SUBTOTAL_PATTERN = Pattern.compile("(?i)^\\s*("
            + "subtotal|sub\\s*total|net\\s*amount\\s*before\\s*tax|grand\\s*total|total\\s*amount|amount\\s*payable|net\\s*payable|total|balance\\s*due|amount\\s*due|final\\s*amount"
            + ").*");

    private static final Pattern FOOTER_NOISE_PATTERN = Pattern.compile("(?i)^\\s*("
            + "thank\\s*you|thankyou|welcome|qr\\s*code|signature|page\\s*\\d+|www\\.|http"
            + ").*");

    public boolean isAddressLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }
        String trimmed = line.trim();
        if (ADDRESS_PATTERN.matcher(trimmed).matches()) {
            return true;
        }
        if (trimmed.matches("(?i)^\\s*(ship\\s*to|bill\\s*to|deliver\\s*to|shipping|billing|dispatch\\s*from|sold\\s*by|fulfillment\\s*center|pincode|pin|zip|address|fax:?|phone:?).*")) {
            return true;
        }

        boolean hasAddressKeyword = ADDRESS_INDICATORS_PATTERN.matcher(trimmed).find();
        boolean hasGeoName = ADDRESS_GEOGRAPHIC_NAMES_PATTERN.matcher(trimmed).find();
        boolean hasPinCode = trimmed.matches(".*\\b\\d{6}\\b.*");

        if (hasPinCode) {
            return true;
        }
        if (hasGeoName && (hasAddressKeyword || trimmed.contains(",") || trimmed.length() < 35)) {
            return true;
        }
        return false;
    }

    public LineContext classifyLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return LineContext.NOISE;
        }
        String trimmed = line.trim();

        if (STORE_HEADER_PATTERN.matcher(trimmed).matches()) {
            return LineContext.HEADER;
        }
        if (GSTIN_PATTERN.matcher(trimmed).find() || METADATA_IDENTIFIER_PATTERN.matcher(trimmed).matches() || URL_EMAIL_PATTERN.matcher(trimmed).find()) {
            return LineContext.IDENTIFIER;
        }
        if (isAddressLine(trimmed)) {
            return LineContext.ADDRESS;
        }
        if (TOTAL_SUBTOTAL_PATTERN.matcher(trimmed).matches()) {
            return LineContext.TOTAL;
        }
        if (DISCOUNT_SHIPPING_PAYMENT_PATTERN.matcher(trimmed).matches()) {
            return LineContext.SHIPPING;
        }
        // ONLY classify as pure TAX if it's NOT a full product table row
        if (TAX_PATTERN.matcher(trimmed).matches() && !trimmed.matches(".*\\d+\\s+.*₹?\\d+.*")) {
            return LineContext.TAX;
        }
        if (FOOTER_NOISE_PATTERN.matcher(trimmed).matches()) {
            return LineContext.FOOTER;
        }
        if (PHONE_PATTERN.matcher(trimmed).matches() || trimmed.matches("^[0-9\\s.,/#:-]+$")) {
            return LineContext.NOISE;
        }

        return LineContext.ITEM;
    }

    private static final Pattern MULTI_CURRENCY_PATTERN = Pattern.compile("(?i)("
            + "[₹$€£¥₩₽₺₫฿₱₦₴₪₸₾]|"
            + "\\b(?:inr|usd|eur|gbp|jpy|cny|cad|aud|sgd|aed|sar|chf|hkd|nzd|krw|thb|myr|php|zar|brl|mxn|rs\\.?|rupees?|dollars?|euros?|pounds?|yen|dirhams?)\\b"
            + ")");

    public boolean hasFinancialContext(String line, String name, BigDecimal price, ItemCandidate.Source source) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        // Candidates extracted from AI, product table rows, or structured name: price lines have explicit monetary context
        if (source == ItemCandidate.Source.AI || source == ItemCandidate.Source.TABLE || source == ItemCandidate.Source.STRUCTURED_LINE) {
            return true;
        }

        // Explicit currency symbol, ISO code, or currency word present in line or attached to price
        if (line != null && MULTI_CURRENCY_PATTERN.matcher(line).find()) {
            return true;
        }

        // Line contains explicit financial label
        if (line != null && Pattern.compile("(?i)\\b(price|fee|amount|rate|charge|cost|subtotal|total|val|value)\\b").matcher(line).find()) {
            return true;
        }

        return false;
    }

    public boolean validateItemCandidate(ItemCandidate candidate) {
        if (candidate == null || candidate.getName() == null) {
            log.info("ITEM REJECTED: candidate or candidate name is null, reason=NULL_CANDIDATE");
            return false;
        }

        // Clean HSN / SAC / ASIN tags from name before validating candidate name
        String name = candidate.getName()
                .replaceAll("(?i)\\b(hsn|sac)\\s*:?\\s*\\d+\\b", "")
                .replaceAll("(?i)\\b(asin|sku)\\s*:?\\s*[a-z0-9\\-]+\\b", "")
                .replaceAll("\\s*\\|\\s*\\|\\s*", " | ")
                .replaceAll("^\\s*\\|\\s*|\\s*\\|\\s*$", "")
                .trim();

        if (name.isEmpty() || name.length() < 2) {
            log.info("ITEM REJECTED: name='{}', reason=NAME_TOO_SHORT", candidate.getName());
            return false;
        }

        if (isAddressLine(name)) {
            log.info("ITEM REJECTED: name='{}', reason=ADDRESS_SECTION", name);
            return false;
        }

        // Update candidate name with cleaned name
        candidate.setName(name);

        LineContext ctx = classifyLine(name);
        if (ctx != LineContext.ITEM && ctx != LineContext.ITEM_CONTINUATION) {
            log.info("ITEM REJECTED: name='{}', classification='{}', reason=CLASSIFIED_NON_ITEM_METADATA", name, ctx);
            return false;
        }

        // Strict metadata & address indicators filter on item name
        Pattern metadataKeywordsPattern = Pattern.compile("(?i)\\b("
                + "gstin|pan|order\\s*no|invoice\\s*no|receipt\\s*id|transaction\\s*id|cgst|sgst|igst|vat|subtotal|grand\\s*total|amount\\s*payable|ship\\s*to|bill\\s*to|delivery\\s*address|shipping\\s*address|billing\\s*address|customer\\s*name|student\\s*name|sem|semester|page\\s*\\d+|welcome|thank\\s*you|fax:?|phone:?|tel:?|survey\\s*no|plot\\s*no|village|hobli|nagar|pincode|zip|dist|state"
                + ")\\b");
        if (metadataKeywordsPattern.matcher(name).find()) {
            log.info("ITEM REJECTED: name='{}', reason=METADATA_KEYWORD", name);
            return false;
        }

        if (ADDRESS_GEOGRAPHIC_NAMES_PATTERN.matcher(name).find() && (ADDRESS_INDICATORS_PATTERN.matcher(name).find() || name.contains(","))) {
            log.info("ITEM REJECTED: name='{}', reason=GEOGRAPHIC_LOCATION_NAME", name);
            return false;
        }

        BigDecimal price = candidate.getUnitPrice() != null ? candidate.getUnitPrice() : candidate.getLineTotal();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("ITEM REJECTED: name='{}', price={}, reason=ZERO_OR_NEGATIVE_PRICE", name, price);
            return false;
        }
        if (price.compareTo(new BigDecimal("1000000")) >= 0) {
            log.info("ITEM REJECTED: name='{}', price={}, reason=PRICE_EXCEEDS_THRESHOLD", name, price);
            return false;
        }

        if (!hasFinancialContext(name, name, price, candidate.getSource())) {
            log.info("ITEM REJECTED: name='{}', price={}, reason=NO_FINANCIAL_CONTEXT", name, price);
            return false;
        }

        log.info("ITEM CANDIDATE ACCEPTED: name='{}', qty={}, unitPrice={}, lineTotal={}, source={}, confidence={}, decision=ACCEPT",
                name, candidate.getQuantity(), candidate.getUnitPrice(), candidate.getLineTotal(), candidate.getSource(), candidate.getConfidence());

        return true;
    }

    public List<ReceiptItem> parseReceiptItems(String text) {
        List<ReceiptItem> items = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return items;
        }

        String[] lines = text.split("\r?\n");

        log.info("\n========== SECTION DETECTION & ITEM TABLE PARSING ==========");

        // Detect if document contains a formal item table header
        boolean hasDocumentTableHeader = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (ITEM_TABLE_HEADER_PATTERN.matcher(trimmed).matches() && !NON_ITEM_SECTION_HEADER_PATTERN.matcher(trimmed).matches()) {
                hasDocumentTableHeader = true;
                log.info("Document contains formal item table header: '{}'", trimmed);
                break;
            }
        }

        Pattern singleLineItemPattern = Pattern.compile("(?i)^([a-zA-Z][a-zA-Z0-9\\s.,&/()#\\-']+?)\\s*:?\\s+(?:(\\d+)\\s+)?(?:[₹$€£¥₩₽₺₫฿₱₦₴₪₸₾]|inr|usd|eur|gbp|jpy|cny|cad|aud|sgd|aed|sar|chf|hkd|nzd|krw|thb|myr|php|zar|brl|mxn|rs\\.?|rupees?|dollars?|euros?|pounds?|yen|dirhams?|\\?)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:inr|usd|eur|gbp|jpy|cny|cad|aud|sgd|aed|sar|chf|hkd|nzd|krw|thb|myr|php|zar|brl|mxn)?\\s*(?:/-)?$");
        Pattern tableRowPriceQtyPattern = Pattern.compile("(?:₹|rs\\.?|inr|\\$)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s+(?:-?₹?\\s*0\\.00\\s+)?(\\d+)\\s+(?:₹|rs\\.?|inr|\\$)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)");

        StringBuilder accumulatedDescription = new StringBuilder();
        List<ItemCandidate> candidates = new ArrayList<>();

        boolean inItemTableSection = !hasDocumentTableHeader; // Default to true if no formal table header exists

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            // 1. Check if line is an item table header
            if (ITEM_TABLE_HEADER_PATTERN.matcher(line).matches() && !NON_ITEM_SECTION_HEADER_PATTERN.matcher(line).matches()) {
                inItemTableSection = true;
                accumulatedDescription.setLength(0);
                log.info("===== ITEM TABLE START ===== Detected table header: '{}'", line);
                continue;
            }

            // 2. Check if line is a non-item section header or address line
            if (NON_ITEM_SECTION_HEADER_PATTERN.matcher(line).matches() || isAddressLine(line)) {
                if (hasDocumentTableHeader && (NON_ITEM_SECTION_HEADER_PATTERN.matcher(line).matches() || line.toLowerCase().contains("address"))) {
                    log.info("===== ITEM TABLE END ===== Exiting item table section due to section header: '{}'", line);
                    inItemTableSection = false;
                }
                log.info("ITEM REJECTED: line='{}', reason=NON_ITEM_OR_ADDRESS_SECTION", line);
                accumulatedDescription.setLength(0);
                continue;
            }

            // HSN / SAC code or ASIN/SKU line: attach to current accumulated title if inside table section
            if (HSN_SAC_CODE_PATTERN.matcher(line).matches() || line.matches("(?i)^\\s*(?:asin|sku|b0[a-z0-9]{8}|rlm[a-z0-9\\-+]+)\\s*$")) {
                if (inItemTableSection && accumulatedDescription.length() > 0) {
                    accumulatedDescription.append(" | ").append(line);
                    log.info("Appending ASIN/SKU/HSN code line to item description: '{}'", line);
                } else {
                    log.info("Ignoring ASIN/SKU/HSN code line: '{}'", line);
                }
                continue;
            }

            if (hasDocumentTableHeader && !inItemTableSection) {
                log.info("ITEM REJECTED: line='{}', reason=OUTSIDE_ITEM_TABLE_SECTION", line);
                continue;
            }

            LineContext ctx = classifyLine(line);
            if (ctx != LineContext.ITEM && ctx != LineContext.ITEM_CONTINUATION) {
                log.info("ITEM REJECTED: line='{}', context='{}', classification='{}', reason=CLASSIFIED_NON_ITEM_METADATA", line, line, ctx);
                accumulatedDescription.setLength(0);
                continue;
            }

            // A. Single-line item pattern
            Matcher singleMatcher = singleLineItemPattern.matcher(line);
            if (singleMatcher.matches()) {
                String rawName = singleMatcher.group(1).trim();
                String qtyStr = singleMatcher.group(2);
                String priceStr = singleMatcher.group(3);

                String itemName = rawName.replaceAll("[:\\-=\\.]+$", "").trim();
                BigDecimal price = parseAndNormalizeMonetaryToken(priceStr);
                int qty = (qtyStr != null && !qtyStr.isEmpty()) ? Integer.parseInt(qtyStr) : 1;

                ItemCandidate candidate = ItemCandidate.builder()
                        .name(itemName)
                        .quantity(qty)
                        .unitPrice(price)
                        .source(ItemCandidate.Source.STRUCTURED_LINE)
                        .confidence(0.85)
                        .build();

                if (validateItemCandidate(candidate)) {
                    candidates.add(candidate);
                    accumulatedDescription.setLength(0);
                    continue;
                }
            }

            // B. Table row price/quantity line (Amazon / GST multi-value row)
            Matcher tableMatcher = tableRowPriceQtyPattern.matcher(line);
            if (tableMatcher.find()) {
                String unitPriceStr = tableMatcher.group(1);
                String qtyStr = tableMatcher.group(2);

                BigDecimal unitPrice = parseAndNormalizeMonetaryToken(unitPriceStr);
                int qty = (qtyStr != null && !qtyStr.isEmpty()) ? Integer.parseInt(qtyStr) : 1;

                String itemName = accumulatedDescription.toString().trim();
                if (itemName.isEmpty()) {
                    int priceIdx = line.indexOf(unitPriceStr);
                    if (priceIdx > 0) {
                        itemName = line.substring(0, priceIdx).replaceAll("[|\\-:=]+$", "").trim();
                    }
                }

                ItemCandidate candidate = ItemCandidate.builder()
                        .name(itemName)
                        .quantity(qty)
                        .unitPrice(unitPrice)
                        .source(ItemCandidate.Source.TABLE)
                        .confidence(0.90)
                        .build();

                if (validateItemCandidate(candidate)) {
                    candidates.add(candidate);
                    accumulatedDescription.setLength(0);
                    continue;
                }
            }

            // C. Accumulate product title text across lines inside table section
            if (line.matches(".*[a-zA-Z]{2,}.*")) {
                if (accumulatedDescription.length() > 0) {
                    accumulatedDescription.append(" | ");
                }
                accumulatedDescription.append(line);
                log.info("Accumulating product description line inside table: '{}'", line);
            }
        }

        for (ItemCandidate c : candidates) {
            items.add(ReceiptItem.builder()
                    .name(c.getName())
                    .quantity(c.getQuantity())
                    .price(c.getUnitPrice())
                    .build());
        }

        log.info("Finished item parsing. Total accepted items: {}", items.size());
        return items;
    }

    public List<TaxDetail> parseTaxDetails(String text) {
        List<TaxDetail> taxes = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return taxes;
        }

        String[] lines = text.split("\\r?\\n");
        Set<String> seenTypes = new HashSet<>();

        Pattern taxLinePattern = Pattern.compile("(?i)^\\s*(CGST|SGST|IGST|UTGST|GST|CESS|GST\\s*CESS|COMPENSATION\\s*CESS|TCS|TDS|VAT|SERVICE\\s*TAX|SALES\\s*TAX|EXCISE\\s*DUTY|CUSTOMS\\s*DUTY|IMPORT\\s*DUTY|LOCAL\\s*TAX|MUNICIPAL\\s*TAX)\\b(?:\\s*@?\\s*(\\d+(?:\\.\\d+)?)\\s*%)?[\\s:]*(?:[₹$€£¥₩₽₺₫฿₱₦₴₪₸₾]|inr|usd|eur|gbp|aud|cad|sgd|jpy|cny|rs\\.?)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)");
        Pattern inlineTaxPattern = Pattern.compile("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*%\\s*(IGST|CGST|SGST|UTGST|GST|VAT|CESS|TCS|TDS)\\s*(?:[₹$€£¥₩₽₺₫฿₱₦₴₪₸₾]|inr|usd|eur|gbp|aud|cad|sgd|jpy|cny|rs\\.?)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || isAddressLine(trimmed) || GSTIN_PATTERN.matcher(trimmed).find() || METADATA_IDENTIFIER_PATTERN.matcher(trimmed).matches()) {
                continue;
            }

            Matcher inlineMatcher = inlineTaxPattern.matcher(trimmed);
            if (inlineMatcher.find()) {
                String rateStr = inlineMatcher.group(1);
                String rawType = inlineMatcher.group(2).toUpperCase();
                String amountStr = inlineMatcher.group(3);

                BigDecimal rate = parseAndNormalizeMonetaryToken(rateStr);
                BigDecimal amount = parseAndNormalizeMonetaryToken(amountStr);
                String currency = extractCurrencyFromLine(trimmed);

                if (validateTaxCandidate(rawType, rate, amount, trimmed)) {
                    String taxKey = rawType + "_" + amount;
                    if (!seenTypes.contains(taxKey)) {
                        seenTypes.add(taxKey);
                        taxes.add(TaxDetail.builder()
                                .type(rawType)
                                .rate(rate)
                                .amount(amount)
                                .currency(currency)
                                .build());
                    }
                }
                continue;
            }

            Matcher matcher = taxLinePattern.matcher(trimmed);
            if (matcher.find()) {
                String rawType = matcher.group(1).toUpperCase().replaceAll("\\s+", " ");
                String rateStr = matcher.group(2);
                String amountStr = matcher.group(3);

                if (rawType.contains("CESS")) {
                    rawType = "CESS";
                } else if (rawType.equals("SERVICE TAX")) {
                    rawType = "Service Tax";
                } else if (rawType.equals("SALES TAX")) {
                    rawType = "Sales Tax";
                } else if (rawType.equals("EXCISE DUTY")) {
                    rawType = "Excise Duty";
                } else if (rawType.equals("CUSTOMS DUTY") || rawType.equals("IMPORT DUTY")) {
                    rawType = "Customs Duty";
                } else if (rawType.equals("LOCAL TAX") || rawType.equals("MUNICIPAL TAX")) {
                    rawType = "Local Tax";
                }

                BigDecimal rate = rateStr != null ? parseAndNormalizeMonetaryToken(rateStr) : null;
                BigDecimal amount = parseAndNormalizeMonetaryToken(amountStr);
                String currency = extractCurrencyFromLine(trimmed);

                if (validateTaxCandidate(rawType, rate, amount, trimmed)) {
                    String taxKey = rawType + "_" + amount;
                    if (!seenTypes.contains(taxKey)) {
                        seenTypes.add(taxKey);
                        taxes.add(TaxDetail.builder()
                                .type(rawType)
                                .rate(rate)
                                .amount(amount)
                                .currency(currency)
                                .build());
                    }
                }
            }
        }

        return taxes;
    }

    public String extractCurrencyFromLine(String line) {
        if (line == null) return "INR";
        if (line.contains("$") || line.toUpperCase().contains("USD")) return "USD";
        if (line.contains("€") || line.toUpperCase().contains("EUR")) return "EUR";
        if (line.contains("£") || line.toUpperCase().contains("GBP")) return "GBP";
        if (line.contains("¥") || line.toUpperCase().contains("JPY") || line.toUpperCase().contains("CNY")) return "JPY";
        if (line.toUpperCase().contains("AUD")) return "AUD";
        if (line.toUpperCase().contains("CAD")) return "CAD";
        if (line.toUpperCase().contains("SGD")) return "SGD";
        return "INR";
    }

    public boolean validateTaxCandidate(String type, BigDecimal rate, BigDecimal amount, String line) {
        if (type == null || type.trim().isEmpty() || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (line != null) {
            String trimmed = line.trim();
            if (isAddressLine(trimmed) || GSTIN_PATTERN.matcher(trimmed).find() || HSN_SAC_CODE_PATTERN.matcher(trimmed).matches()) {
                return false;
            }
        }
        return true;
    }

    public BigDecimal parseSubtotal(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        Pattern pattern = Pattern.compile("(?i)^\\s*(?:subtotal|sub\\s*total|taxable\\s*(?:amount|value)|net\\s*amount\\s*before\\s*tax)\\s*:?\\s*(?:[₹$€£¥₩₽₺₫฿₱₦₴₪₸₾]|inr|usd|eur|gbp|rs\\.?)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)");
        for (String line : text.split("\\r?\\n")) {
            Matcher m = pattern.matcher(line.trim());
            if (m.find()) {
                return parseAndNormalizeMonetaryToken(m.group(1));
            }
        }
        return null;
    }

    public BigDecimal parseDiscount(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        Pattern pattern = Pattern.compile("(?i)^\\s*(?:discount|less|coupon|promo)\\s*:?\\s*-?\\s*(?:[₹$€£¥₩₽₺₫฿₱₦₴₪₸₾]|inr|usd|eur|gbp|rs\\.?)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)");
        for (String line : text.split("\\r?\\n")) {
            Matcher m = pattern.matcher(line.trim());
            if (m.find()) {
                return parseAndNormalizeMonetaryToken(m.group(1));
            }
        }
        return null;
    }

    public BigDecimal parseShippingAmount(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        Pattern pattern = Pattern.compile("(?i)^\\s*(?:shipping|delivery|convenience|handling|service\\s*charge|service\\s*fee|platform\\s*fee)(?:\\s*(?:charges?|fee))?\\s*:?\\s*(?:[₹$€£¥₩₽₺₫฿₱₦₴₪₸₾]|inr|usd|eur|gbp|rs\\.?)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)");
        for (String line : text.split("\\r?\\n")) {
            Matcher m = pattern.matcher(line.trim());
            if (m.find()) {
                return parseAndNormalizeMonetaryToken(m.group(1));
            }
        }
        return null;
    }

    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new OcrException("Cannot process an empty or null file.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.contains(".")) {
            throw new OcrException("Invalid file format. File must have an extension.");
        }
        String extension = getFileExtension(filename).toLowerCase();
        if (!extension.matches("jpg|jpeg|png|pdf")) {
            throw new OcrException("Unsupported file type: ." + extension + ". Only JPG, PNG, and PDF are supported.");
        }
    }

    public String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    private void ensureTessDataAvailable() {
        try {
            File tessDataDir = new File(tesseractDataPath);
            if (!tessDataDir.exists()) {
                tessDataDir.mkdirs();
            }

            File trainedDataFile = new File(tessDataDir, tesseractLanguage + ".traineddata");
            if (!trainedDataFile.exists()) {
                log.info("Tesseract traineddata file missing at {}. Attempting to copy default language file from classpath...", trainedDataFile.getAbsolutePath());
                try (InputStream is = getClass().getResourceAsStream("/tessdata/" + tesseractLanguage + ".traineddata")) {
                    if (is != null) {
                        Files.copy(is, trainedDataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        log.info("Successfully copied traineddata to {}", trainedDataFile.getAbsolutePath());
                    } else {
                        log.warn("Could not find /tessdata/{}.traineddata on classpath. Ensure tessdata is configured in environment.", tesseractLanguage);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not ensure tessdata availability: {}", e.getMessage());
        }
    }
}
