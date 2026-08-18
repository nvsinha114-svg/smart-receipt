package com.smartreceipt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptItemDto {

    @NotBlank(message = "Item name is required")
    private String name;

    @NotNull(message = "Item quantity is required")
    @Positive(message = "Item quantity must be greater than zero")
    private Integer quantity;

    @NotNull(message = "Item price is required")
    @PositiveOrZero(message = "Item price cannot be negative")
    private BigDecimal price;

    private String category;
}
