package com.smartreceipt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptAIResponse {
    private String documentType;

    private String invoiceNumber;
    private String receiptNumber;
    private String orderNumber;

    private String invoiceDate;
    private String receiptDate;
    private String dueDate;

    private String currency;

    private ReceiptAISeller seller;
    private ReceiptAIBuyer buyer;

    private String paymentMethod;

    private List<ReceiptAIItem> items;
    private ReceiptAIFinancials financials;
    private ReceiptAIConfidence confidence;

    private String category;
    private String merchantName;

    public String getMerchantName() {
        if (seller != null && seller.getName() != null && !seller.getName().trim().isEmpty()) {
            return seller.getName().trim();
        }
        return merchantName;
    }

    public String getReceiptDate() {
        if (invoiceDate != null && !invoiceDate.trim().isEmpty()) {
            return invoiceDate.trim();
        }
        return receiptDate;
    }
}
