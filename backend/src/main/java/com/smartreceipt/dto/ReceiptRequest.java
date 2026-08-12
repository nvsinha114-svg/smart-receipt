package com.smartreceipt.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptRequest {

    @NotBlank(message = "Merchant name is required")
    private String merchantName;

    @NotNull(message = "Receipt date is required")
    private LocalDate receiptDate;

    @NotNull(message = "Total amount is required")
    @PositiveOrZero(message = "Total amount cannot be negative")
    private BigDecimal totalAmount;

    @Valid
    private List<ReceiptItemDto> items;
}
