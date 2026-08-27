package com.smartreceipt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptAIConfidence {
    private Double invoiceNumber;
    private Double invoiceDate;
    private Double sellerName;
    private Double totalAmount;
}
