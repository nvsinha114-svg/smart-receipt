package com.smartreceipt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptResponse {

    private String id;
    private String merchantName;
    private LocalDate receiptDate;
    private BigDecimal totalAmount;
    private List<ReceiptItemDto> items;
    private String userId;
    private LocalDateTime createdAt;
}
