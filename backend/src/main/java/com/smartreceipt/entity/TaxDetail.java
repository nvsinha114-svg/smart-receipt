package com.smartreceipt.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxDetail {
    private String type;
    private BigDecimal rate;
    private BigDecimal amount;
    private String currency;
}
