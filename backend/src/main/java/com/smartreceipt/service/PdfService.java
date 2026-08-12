package com.smartreceipt.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.smartreceipt.entity.Receipt;
import com.smartreceipt.entity.ReceiptItem;
import com.smartreceipt.exception.OcrException;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PdfService {

    public byte[] generateReceiptPdf(Receipt receipt) {
        Document document = new Document();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Fonts
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, new Color(41, 128, 185));
            Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.DARK_GRAY);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);

            // Title Header
            Paragraph title = new Paragraph("Smart Receipt - Receipt Summary", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(15);
            document.add(title);

            // Metadata Box
            Paragraph metadata = new Paragraph();
            metadata.setFont(normalFont);
            metadata.add(new Phrase("Receipt ID: ", boldFont));
            metadata.add(new Phrase((receipt.getId() != null ? receipt.getId() : "N/A") + "\n", normalFont));

            metadata.add(new Phrase("Merchant Name: ", boldFont));
            metadata.add(new Phrase((receipt.getMerchantName() != null ? receipt.getMerchantName() : "N/A") + "\n", normalFont));

            metadata.add(new Phrase("Receipt Date: ", boldFont));
            metadata.add(new Phrase((receipt.getReceiptDate() != null ? receipt.getReceiptDate().toString() : "N/A") + "\n", normalFont));

            metadata.add(new Phrase("Generated Timestamp: ", boldFont));
            metadata.add(new Phrase(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n\n", normalFont));

            document.add(metadata);

            // Table of items
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{4, 1, 2, 2});
            table.setSpacingBefore(10);
            table.setSpacingAfter(15);

            // Table Headers
            addTableHeader(table, "Item Name", boldFont);
            addTableHeader(table, "Qty", boldFont);
            addTableHeader(table, "Unit Price ($)", boldFont);
            addTableHeader(table, "Subtotal ($)", boldFont);

            List<ReceiptItem> items = receipt.getItems();
            if (items != null && !items.isEmpty()) {
                for (ReceiptItem item : items) {
                    BigDecimal qty = BigDecimal.valueOf(item.getQuantity() != null ? item.getQuantity() : 1);
                    BigDecimal price = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
                    BigDecimal subtotal = price.multiply(qty);

                    table.addCell(new PdfPCell(new Phrase(item.getName() != null ? item.getName() : "Item", normalFont)));
                    table.addCell(new PdfPCell(new Phrase(String.valueOf(qty), normalFont)));
                    table.addCell(new PdfPCell(new Phrase(String.format("%.2f", price), normalFont)));
                    table.addCell(new PdfPCell(new Phrase(String.format("%.2f", subtotal), normalFont)));
                }
            } else {
                PdfPCell emptyCell = new PdfPCell(new Phrase("No items recorded", normalFont));
                emptyCell.setColspan(4);
                emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(emptyCell);
            }

            document.add(table);

            // Total Amount Summary
            Paragraph totalPara = new Paragraph();
            totalPara.setAlignment(Element.ALIGN_RIGHT);
            BigDecimal total = receipt.getTotalAmount() != null ? receipt.getTotalAmount() : BigDecimal.ZERO;
            totalPara.add(new Phrase("Total Amount: ", subTitleFont));
            totalPara.add(new Phrase("$" + String.format("%.2f", total), titleFont));
            document.add(totalPara);

            document.close();
        } catch (DocumentException e) {
            throw new OcrException("Error while generating PDF report: " + e.getMessage(), e);
        }

        return out.toByteArray();
    }

    private void addTableHeader(PdfPTable table, String headerTitle, Font font) {
        PdfPCell header = new PdfPCell();
        header.setBackgroundColor(new Color(236, 240, 241));
        header.setPhrase(new Phrase(headerTitle, font));
        header.setPadding(5);
        table.addCell(header);
    }
}
