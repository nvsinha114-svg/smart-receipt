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
    private String merchantName;
    private String receiptDate;
    private String currency;
    private String category;
    private List<ReceiptAIItem> items;
}
