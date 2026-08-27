package com.smartreceipt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptAISeller {
    private String name;
    private String address;
    private String phone;
    private String email;
    private String gstin;
    private String pan;
}
