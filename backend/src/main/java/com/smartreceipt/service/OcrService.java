package com.smartreceipt.service;

import com.smartreceipt.dto.ReceiptResponse;
import com.smartreceipt.entity.Receipt;
import com.smartreceipt.entity.ReceiptItem;
import com.smartreceipt.exception.OcrException;
import com.smartreceipt.repository.ReceiptRepository;
import com.smartreceipt.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.smartreceipt.dto.ReceiptAIResponse;
import com.smartreceipt.dto.ReceiptAIItem;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    private final ReceiptRepository receiptRepository;
    private final ReceiptService receiptService;
    private final AIReceiptParserService aiReceiptParserService;

    @Value("${tesseract.datapath:./tessdata}")
    private String tesseractDataPath;

    @Value("${tesseract.language:eng}")
    private String tesseractLanguage;

    public ReceiptResponse processReceiptUpload(MultipartFile file, UserPrincipal currentUser) {
        validateFile(file);

        File tempFile = null;
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            tempFile = File.createTempFile("receipt_upload_", "." + extension);
            file.transferTo(tempFile);

            String extractedText = extractRawText(tempFile, extension);
            log.info("Extracted raw text from file {}: \n{}", originalFilename, extractedText);

            Receipt receipt = parseTextToReceipt(extractedText);
            receipt.setUserId(currentUser.getId());
            receipt.setCreatedAt(LocalDateTime.now());

            log.info("Final extracted total amount for file {}: {}", originalFilename, receipt.getTotalAmount());

            Receipt saved = receiptRepository.save(receipt);
            return receiptService.mapToResponse(saved);

        } catch (IOException e) {
            log.error("Failed to process file upload", e);
            throw new OcrException("Failed to read uploaded file: " + e.getMessage(), e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                try {
                    Files.delete(tempFile.toPath());
                } catch (IOException ignored) {
                }
            }
        }
    }

    public String extractRawText(File file, String extension) {
        if ("pdf".equalsIgnoreCase(extension)) {
            return extractTextFromPdf(file);
        } else {
            return extractTextFromImage(file);
        }
    }

    private String extractTextFromPdf(File pdfFile) {
        StringBuilder textBuilder = new StringBuilder();
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String directText = stripper.getText(document);

            if (directText != null && !directText.trim().isEmpty()) {
                return directText;
            }

            // Fallback to rendering PDF pages as images for Tesseract OCR
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); ++page) {
                BufferedImage bim = pdfRenderer.renderImageWithDPI(page, 300);
                String pageText = runTesseractOnImage(bim);
                textBuilder.append(pageText).append("\n");
            }

        } catch (IOException e) {
            log.error("PDF processing error", e);
            throw new OcrException("Failed to parse PDF document: " + e.getMessage(), e);
        }
        return textBuilder.toString();
    }

    private String extractTextFromImage(File imageFile) {
        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                throw new OcrException("Invalid or unsupported image file");
            }
            return runTesseractOnImage(image);
        } catch (IOException e) {
            throw new OcrException("Failed to read image file: " + e.getMessage(), e);
        }
    }

    private String runTesseractOnImage(BufferedImage image) {
        ITesseract tesseract = new Tesseract();
        ensureTessDataAvailable();
        tesseract.setDatapath(tesseractDataPath);
        tesseract.setLanguage(tesseractLanguage);

        try {
            return tesseract.doOCR(image);
        } catch (UnsatisfiedLinkError e) {
            log.warn("Tesseract native library not available in runtime environment: {}", e.getMessage());
            return "";
        } catch (TesseractException e) {
            log.warn("Tesseract OCR execution warning: {}", e.getMessage());
            return "";
        } catch (Exception e) {
            log.warn("Tesseract processing warning: {}", e.getMessage());
            return "";
        }
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
                    String merchantName = aiResponse.getMerchantName() != null ? aiResponse.getMerchantName().trim() : null;
                    
                    LocalDate receiptDate = null;
                    if (aiResponse.getReceiptDate() != null) {
                        receiptDate = tryParseDate(aiResponse.getReceiptDate());
                    }
                    if (receiptDate == null) {
                        receiptDate = parseReceiptDate(text); // Fallback to regex date parsing
                    }

                    List<ReceiptItem> items = new ArrayList<>();
                    if (aiResponse.getItems() != null) {
                        for (ReceiptAIItem aiItem : aiResponse.getItems()) {
                            if (aiItem.getName() != null && !aiItem.getName().trim().isEmpty()) {
                                int qty = aiItem.getQuantity() != null ? aiItem.getQuantity() : 1;
                                BigDecimal price = aiItem.getUnitPrice() != null ? aiItem.getUnitPrice() : BigDecimal.ZERO;
                                items.add(ReceiptItem.builder()
                                        .name(aiItem.getName().trim())
                                        .quantity(qty)
                                        .price(price)
                                        .category(aiItem.getCategory() != null ? aiItem.getCategory().trim() : null)
                                        .build());
                            }
                        }
                    }

                    // Deterministic total calculation: total = sum(quantity * price)
                    BigDecimal finalTotal = BigDecimal.ZERO;
                    if (!items.isEmpty()) {
                        finalTotal = items.stream()
                                .map(item -> {
                                    BigDecimal qty = BigDecimal.valueOf(item.getQuantity() != null ? item.getQuantity() : 1);
                                    BigDecimal price = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
                                    return price.multiply(qty);
                                })
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                    }

                    return Receipt.builder()
                            .merchantName(merchantName)
                            .receiptDate(receiptDate)
                            .totalAmount(finalTotal)
                            .category(aiResponse.getCategory() != null ? aiResponse.getCategory().trim() : null)
                            .items(items)
                            .build();
                }
            } catch (Exception e) {
                log.error("Error during AI parsing integration. Falling back to local OCR parsing.", e);
            }
        }

        // Fallback: Local OCR Tesseract parsing logic
        log.info("Using local OCR Tesseract parsing fallback.");
        String merchantName = parseMerchantName(text);
        LocalDate receiptDate = parseReceiptDate(text);
        BigDecimal extractedTotal = parseTotalAmount(text);
        List<ReceiptItem> items = parseReceiptItems(text);

        // Compute sum of item subtotals: sum(quantity * price)
        BigDecimal itemsSum = null;
        if (items != null && !items.isEmpty()) {
            itemsSum = items.stream()
                    .map(item -> {
                        BigDecimal qty = BigDecimal.valueOf(item.getQuantity() != null ? item.getQuantity() : 1);
                        BigDecimal price = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
                        return price.multiply(qty);
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // Determine final totalAmount:
        // If items exist and itemsSum > 0:
        //   - If extractedTotal is null or itemsSum > extractedTotal or extractedTotal <= 0:
        //     use itemsSum!
        BigDecimal finalTotal = extractedTotal;
        if (itemsSum != null && itemsSum.compareTo(BigDecimal.ZERO) > 0) {
            if (extractedTotal == null || itemsSum.compareTo(extractedTotal) > 0 || extractedTotal.compareTo(BigDecimal.ZERO) <= 0) {
                finalTotal = itemsSum;
                log.info("Dynamically calculated totalAmount from item subtotals: {}", finalTotal);
            }
        }

        return Receipt.builder()
                .merchantName(merchantName)
                .receiptDate(receiptDate)
                .totalAmount(finalTotal)
                .items(items)
                .build();
    }

    public String parseMerchantName(String text) {
        String[] lines = text.split("\r?\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() > 2 && !trimmed.matches("(?i).*receipt|tax|invoice|date|total|welcome|thank.*")) {
                return trimmed;
            }
        }
        return null;
    }

    public LocalDate parseReceiptDate(String text) {
        List<Pattern> datePatterns = List.of(
                Pattern.compile("(?i)\\b(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2})\\b"),
                Pattern.compile("(?i)\\b(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4})\\b"),
                Pattern.compile("(?i)\\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2},?\\s+\\d{4}\\b")
        );

        for (Pattern pattern : datePatterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String dateStr = matcher.group(1) != null ? matcher.group(1) : matcher.group(0);
                LocalDate parsed = tryParseDate(dateStr);
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private LocalDate tryParseDate(String dateStr) {
        String cleanDate = dateStr.replace(",", "").trim();
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("MM-dd-yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("MMMM d yyyy", Locale.ENGLISH)
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(cleanDate, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    /**
     * Tiered priority model for candidate total extraction.
     */
    private static class TotalCandidate {
        int tier; // 1 = Grand Total, 2 = Total Amount / Net Amount / Amount Payable, 3 = Total / Payable / Balance Due
        int lineIndex;
        BigDecimal amount;
        String lineContent;

        TotalCandidate(int tier, int lineIndex, BigDecimal amount, String lineContent) {
            this.tier = tier;
            this.lineIndex = lineIndex;
            this.amount = amount;
            this.lineContent = lineContent;
        }
    }

    public BigDecimal parseTotalAmount(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        String[] lines = text.split("\r?\n");
        log.debug("--- OCR PARSING DEBUG: Analyzing {} raw lines ---", lines.length);

        // Keywords for Priority Tiers
        Pattern tier1Pattern = Pattern.compile("(?i)\\b(grand\\s*total)\\b");
        Pattern tier2Pattern = Pattern.compile("(?i)\\b(total\\s*amount|net\\s*amount|net\\s*total|amount\\s*payable|payable\\s*amount|final\\s*amount)\\b");
        Pattern tier3Pattern = Pattern.compile("(?i)\\b(total|payable|balance\\s*due|amount\\s*due)\\b");

        // Keywords that disqualify a line from being a main total line
        Pattern excludePattern = Pattern.compile("(?i)\\b(subtotal|sub\\s*total|sub-total|tax|cgst|sgst|igst|vat|discount|round\\s*off|cash|change|tendered|items|qty|quantity|invoice|gstin|phone|tel|zip|pin|account|card)\\b");

        List<TotalCandidate> candidates = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String rawLine = lines[i].trim();
            if (rawLine.isEmpty()) {
                continue;
            }

            boolean hasExclusion = excludePattern.matcher(rawLine).find();
            boolean isTier1 = tier1Pattern.matcher(rawLine).find();
            boolean isTier2 = tier2Pattern.matcher(rawLine).find();
            boolean isTier3 = tier3Pattern.matcher(rawLine).find();

            if (hasExclusion && !isTier1 && !isTier2) {
                // Skip subtotal/tax lines even if "total" matches inside word
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

            if (tier > 0) {
                BigDecimal extracted = extractMonetaryValueFromLine(rawLine);
                if (extracted == null && i + 1 < lines.length) {
                    // Check next line if total label is standalone on its own line
                    extracted = extractMonetaryValueFromLine(lines[i + 1].trim());
                }

                if (extracted != null && extracted.compareTo(BigDecimal.ZERO) > 0) {
                    candidates.add(new TotalCandidate(tier, i, extracted, rawLine));
                    log.debug("Candidate detected [Tier {}] line {}: '{}' -> Amount: {}", tier, i, rawLine, extracted);
                }
            }
        }

        if (!candidates.isEmpty()) {
            // Sort by tier ascending (1 first), then lineIndex descending (prefer later lines near bottom)
            candidates.sort((c1, c2) -> {
                if (c1.tier != c2.tier) {
                    return Integer.compare(c1.tier, c2.tier);
                }
                return Integer.compare(c2.lineIndex, c1.lineIndex);
            });

            TotalCandidate selected = candidates.get(0);
            log.info("OCR Total Extraction SUCCESS: Selected Tier {} candidate: '{}' -> {}", selected.tier, selected.lineContent, selected.amount);
            return selected.amount;
        }

        // Fallback Heuristic when explicit total labels are missing
        log.debug("No explicit total label found. Running fallback monetary heuristics...");
        BigDecimal fallbackAmount = extractFallbackTotal(lines, excludePattern);
        if (fallbackAmount != null) {
            log.info("OCR Total Extraction FALLBACK SUCCESS: Found monetary total -> {}", fallbackAmount);
            return fallbackAmount;
        }

        log.warn("OCR Total Extraction UNRESOLVED: Could not confidently detect total amount. Returning null.");
        return null;
    }

    private BigDecimal extractFallbackTotal(String[] lines, Pattern excludePattern) {
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.isEmpty() || excludePattern.matcher(line).find()) {
                continue;
            }

            if (line.matches("(?i).*(date|inv|invoice|gst|tel|ph|phone|st#|store|table|bill).*")) {
                continue;
            }

            BigDecimal amount = extractMonetaryValueFromLine(line);
            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                if (isPlausibleTotalAmount(amount, line)) {
                    return amount;
                }
            }
        }
        return null;
    }

    private boolean isPlausibleTotalAmount(BigDecimal amount, String line) {
        double val = amount.doubleValue();
        if ((val >= 2020 && val <= 2035) && (line.contains("-") || line.contains("/"))) {
            return false;
        }
        if (val > 10000000) {
            return false;
        }
        return true;
    }

    private BigDecimal extractMonetaryValueFromLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        // Matches currency symbol optionally followed by digits/OCR-chars
        Pattern pattern = Pattern.compile("(?i)(?:₹|rs\\.?|inr|\\$)?\\s*([0-9OISlB,\\.\\s]+)");
        Matcher matcher = pattern.matcher(line);

        BigDecimal bestCandidate = null;

        while (matcher.find()) {
            String token = matcher.group(1).trim();
            BigDecimal parsed = parseAndNormalizeMonetaryToken(token);
            if (parsed != null && parsed.compareTo(BigDecimal.ZERO) > 0) {
                if (bestCandidate == null || parsed.compareTo(bestCandidate) > 0) {
                    bestCandidate = parsed;
                }
            }
        }
        return bestCandidate;
    }

    private BigDecimal parseAndNormalizeMonetaryToken(String token) {
        if (token == null) return null;

        String clean = token.replaceAll("^[^0-9OISlB]+|[^0-9OISlB]+$", "");
        if (clean.isEmpty()) return null;

        // Normalize common OCR character typos inside numeric candidates
        String normalizedDigits = clean
                .replace('O', '0')
                .replace('o', '0')
                .replace('I', '1')
                .replace('l', '1')
                .replace('L', '1')
                .replace('S', '5')
                .replace('s', '5')
                .replace('B', '8');

        normalizedDigits = normalizedDigits.replaceAll("\\s+", "");

        if (normalizedDigits.matches("^[0-9]+,[0-9]{1,2}$")) {
            normalizedDigits = normalizedDigits.replace(',', '.');
        } else {
            normalizedDigits = normalizedDigits.replace(",", "");
        }

        if (!normalizedDigits.matches("^[0-9]+(\\.[0-9]{1,4})?$")) {
            return null;
        }

        try {
            BigDecimal val = new BigDecimal(normalizedDigits);
            if (normalizedDigits.length() > 1 && normalizedDigits.startsWith("0") && !normalizedDigits.startsWith("0.")) {
                return null;
            }
            return val;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public List<ReceiptItem> parseReceiptItems(String text) {
        List<ReceiptItem> items = new ArrayList<>();
        String[] lines = text.split("\r?\n");

        Pattern itemPattern = Pattern.compile("(?i)^([^0-9\\$₹\\n\\r]+?)\\s*:?\\s+(?:(\\d+)\\s+)?(?:₹|rs\\.?|inr|\\$)?\\s*([0-9OISlB,\\.\\s]+)$");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String lower = trimmed.toLowerCase();
            if (lower.contains("subtotal") || lower.contains("sub total")
                    || lower.contains("tax") || lower.contains("cgst") || lower.contains("sgst")
                    || lower.contains("igst") || lower.contains("change") || lower.contains("grand total")
                    || lower.contains("amount payable") || lower.contains("net amount")
                    || lower.startsWith("sem") || lower.startsWith("semester") || lower.startsWith("receipt id")
                    || lower.startsWith("receipt no") || lower.startsWith("invoice") || lower.startsWith("date")
                    || lower.startsWith("page") || lower.startsWith("table") || lower.startsWith("store")) {
                continue;
            }

            Matcher matcher = itemPattern.matcher(trimmed);
            if (matcher.find()) {
                String rawName = matcher.group(1).trim();
                String qtyStr = matcher.group(2);
                String priceStr = matcher.group(3);

                String itemName = rawName.replaceAll("[:\\-=\\.]+$", "").trim();

                if (!itemName.isEmpty() && priceStr != null) {
                    if (itemName.matches("(?i)^(sem|semester|receipt|date|page|invoice|bill|table|store|ph|phone|tel).*")) {
                        continue;
                    }

                    BigDecimal price = parseAndNormalizeMonetaryToken(priceStr);
                    if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                        int qty = (qtyStr != null && !qtyStr.isEmpty()) ? Integer.parseInt(qtyStr) : 1;
                        items.add(ReceiptItem.builder()
                                .name(itemName)
                                .quantity(qty)
                                .price(price)
                                .build());
                    }
                }
            }
        }
        return items;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new OcrException("Uploaded file is empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new OcrException("File name is invalid");
        }

        String extension = getFileExtension(originalFilename).toLowerCase();
        List<String> allowedExtensions = List.of("jpg", "jpeg", "png", "pdf");
        if (!allowedExtensions.contains(extension)) {
            throw new OcrException("Invalid file type: ." + extension + ". Allowed types: JPG, JPEG, PNG, PDF");
        }
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filename.substring(dotIndex + 1);
    }

    private void ensureTessDataAvailable() {
        try {
            File tessDataDir = new File(tesseractDataPath);
            if (!tessDataDir.exists()) {
                tessDataDir.mkdirs();
            }
            File engFile = new File(tessDataDir, tesseractLanguage + ".traineddata");
            if (!engFile.exists()) {
                try (InputStream is = getClass().getResourceAsStream("/tessdata/" + tesseractLanguage + ".traineddata")) {
                    if (is != null) {
                        Files.copy(is, engFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        log.info("Extracted {} to {}", engFile.getName(), engFile.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not ensure tessdata availability: {}", e.getMessage());
        }
    }
}

