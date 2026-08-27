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
public class ReceiptAIFinancials {
    private BigDecimal subtotal;
    private BigDecimal totalDiscount;
    private BigDecimal shippingCharges;
    private BigDecimal deliveryCharges;
    private BigDecimal serviceCharges;
    private BigDecimal handlingCharges;
    private BigDecimal platformFees;
    private BigDecimal otherCharges;
    private BigDecimal taxableAmount;
    private BigDecimal totalTax;
    private java.util.List<ReceiptAITax> taxes;
    private BigDecimal totalAmount;
    private BigDecimal amountPaid;
    private BigDecimal balanceDue;
}
