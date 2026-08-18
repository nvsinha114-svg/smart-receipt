package com.smartreceipt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptAIItem {
    private String name;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String category;
}
