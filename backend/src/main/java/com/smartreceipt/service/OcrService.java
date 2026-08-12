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
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    private final ReceiptRepository receiptRepository;
    private final ReceiptService receiptService;

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

        String merchantName = parseMerchantName(text);
        LocalDate receiptDate = parseReceiptDate(text);
        BigDecimal totalAmount = parseTotalAmount(text);
        List<ReceiptItem> items = parseReceiptItems(text);

        return Receipt.builder()
                .merchantName(merchantName)
                .receiptDate(receiptDate)
                .totalAmount(totalAmount)
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
        // Match standard date formats: YYYY-MM-DD, MM/DD/YYYY, DD-MM-YYYY, Month DD, YYYY
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

    public BigDecimal parseTotalAmount(String text) {
        // Look for lines containing TOTAL, GRAND TOTAL, AMOUNT DUE, NET followed by a number
        Pattern totalPattern = Pattern.compile("(?i)(?:total|grand\\s+total|amount\\s+due|net\\s+amount|subtotal)[^0-9\\$]*\\$?\\s*([0-9]+\\.[0-9]{2})");
        Matcher matcher = totalPattern.matcher(text);
        BigDecimal lastFound = null;

        while (matcher.find()) {
            try {
                lastFound = new BigDecimal(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }

        if (lastFound != null) {
            return lastFound;
        }

        // Fallback: search for prices at line ends
        Pattern pricePattern = Pattern.compile("\\$\\s*([0-9]+\\.[0-9]{2})");
        Matcher priceMatcher = pricePattern.matcher(text);
        BigDecimal maxPrice = null;
        while (priceMatcher.find()) {
            try {
                BigDecimal val = new BigDecimal(priceMatcher.group(1));
                if (maxPrice == null || val.compareTo(maxPrice) > 0) {
                    maxPrice = val;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return maxPrice;
    }

    public List<ReceiptItem> parseReceiptItems(String text) {
        List<ReceiptItem> items = new ArrayList<>();
        String[] lines = text.split("\r?\n");

        // Pattern 1: Item Name [Qty] Price (e.g. Milk 2 4.50 or Milk 4.50)
        Pattern itemPattern = Pattern.compile("^([A-Za-z0-9\\s\\-\\.,]+?)\\s+(?:(\\d+)\\s+)?\\$?\\s*([0-9]+\\.[0-9]{2})$");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase().contains("total") || trimmed.toLowerCase().contains("subtotal")
                    || trimmed.toLowerCase().contains("tax") || trimmed.toLowerCase().contains("change")) {
                continue;
            }

            Matcher matcher = itemPattern.matcher(trimmed);
            if (matcher.find()) {
                String itemName = matcher.group(1).trim();
                String qtyStr = matcher.group(2);
                String priceStr = matcher.group(3);

                if (!itemName.isEmpty()) {
                    int qty = qtyStr != null ? Integer.parseInt(qtyStr) : 1;
                    BigDecimal price = new BigDecimal(priceStr);
                    items.add(ReceiptItem.builder()
                            .name(itemName)
                            .quantity(qty)
                            .price(price)
                            .build());
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
                // Look for traineddata in classpath
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
