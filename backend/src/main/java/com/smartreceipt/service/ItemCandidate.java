package com.smartreceipt.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemCandidate {
    private String name;
    private String description;
    @Builder.Default
    private Integer quantity = 1;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
    @Builder.Default
    private double confidence = 0.8;
    private Source source;

    public enum Source {
        AI,
        TABLE,
        STRUCTURED_LINE,
        FALLBACK
    }
}
