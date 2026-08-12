package com.smartreceipt.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "receipts")
public class Receipt {

    @Id
    private String id;

    private String merchantName;

    private LocalDate receiptDate;

    private BigDecimal totalAmount;

    @Builder.Default
    private List<ReceiptItem> items = new ArrayList<>();

    @Indexed
    private String userId;

    @CreatedDate
    private LocalDateTime createdAt;
}
