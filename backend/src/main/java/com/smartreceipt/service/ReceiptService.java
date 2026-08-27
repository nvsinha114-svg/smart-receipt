package com.smartreceipt.service;

import com.smartreceipt.dto.ReceiptItemDto;
import com.smartreceipt.dto.ReceiptRequest;
import com.smartreceipt.dto.ReceiptResponse;
import com.smartreceipt.dto.TaxDetailDto;
import com.smartreceipt.entity.Receipt;
import com.smartreceipt.entity.ReceiptItem;
import com.smartreceipt.entity.Role;
import com.smartreceipt.entity.TaxDetail;
import com.smartreceipt.exception.ResourceNotFoundException;
import com.smartreceipt.exception.UnauthorizedAccessException;
import com.smartreceipt.repository.ReceiptRepository;
import com.smartreceipt.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ReceiptRepository receiptRepository;

    public ReceiptResponse createReceipt(ReceiptRequest request, UserPrincipal currentUser) {
        List<ReceiptItem> items = mapItemsToEntity(request.getItems());
        List<TaxDetail> taxes = mapTaxesToEntity(request.getTaxes());
        Receipt temp = Receipt.builder().items(items).totalAmount(request.getTotalAmount()).build();
        BigDecimal effectiveTotal = calculateEffectiveTotal(temp);

        Receipt receipt = Receipt.builder()
                .merchantName(request.getMerchantName())
                .receiptDate(request.getReceiptDate())
                .totalAmount(effectiveTotal)
                .subtotal(request.getSubtotal())
                .totalTax(request.getTotalTax())
                .discount(request.getDiscount())
                .shippingAmount(request.getShippingAmount())
                .taxes(taxes)
                .category(request.getCategory())
                .items(items)
                .userId(currentUser.getId())
                .createdAt(LocalDateTime.now())
                .build();

        Receipt saved = receiptRepository.save(receipt);
        return mapToResponse(saved);
    }

    public List<ReceiptResponse> getAllReceipts(UserPrincipal currentUser) {
        List<Receipt> receipts;
        if (currentUser.getUser().getRole() == Role.ADMIN) {
            receipts = receiptRepository.findAll();
        } else {
            receipts = receiptRepository.findByUserId(currentUser.getId());
        }
        return receipts.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public ReceiptResponse getReceiptById(String id, UserPrincipal currentUser) {
        Receipt receipt = findReceiptEntityById(id, currentUser);
        return mapToResponse(receipt);
    }

    public ReceiptResponse updateReceipt(String id, ReceiptRequest request, UserPrincipal currentUser) {
        Receipt receipt = findReceiptEntityById(id, currentUser);

        List<ReceiptItem> items = mapItemsToEntity(request.getItems());
        List<TaxDetail> taxes = mapTaxesToEntity(request.getTaxes());
        receipt.setMerchantName(request.getMerchantName());
        receipt.setReceiptDate(request.getReceiptDate());
        receipt.setCategory(request.getCategory());
        receipt.setSubtotal(request.getSubtotal());
        receipt.setTotalTax(request.getTotalTax());
        receipt.setDiscount(request.getDiscount());
        receipt.setShippingAmount(request.getShippingAmount());
        receipt.setTaxes(taxes);
        receipt.setItems(items);
        
        Receipt temp = Receipt.builder().items(items).totalAmount(request.getTotalAmount()).build();
        receipt.setTotalAmount(calculateEffectiveTotal(temp));

        Receipt updated = receiptRepository.save(receipt);
        return mapToResponse(updated);
    }

    public void deleteReceipt(String id, UserPrincipal currentUser) {
        Receipt receipt = findReceiptEntityById(id, currentUser);
        receiptRepository.delete(receipt);
    }

    public Receipt findReceiptEntityById(String id, UserPrincipal currentUser) {
        Receipt receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found with id: " + id));

        boolean isAdmin = currentUser.getUser().getRole() == Role.ADMIN;
        boolean isOwner = receipt.getUserId() != null && receipt.getUserId().equals(currentUser.getId());

        if (!isAdmin && !isOwner) {
            throw new UnauthorizedAccessException("You do not have permission to access this receipt");
        }

        return receipt;
    }

    public ReceiptResponse saveReceiptEntity(Receipt receipt) {
        if (receipt.getItems() != null && !receipt.getItems().isEmpty()) {
            receipt.setTotalAmount(calculateEffectiveTotal(receipt));
        }
        Receipt saved = receiptRepository.save(receipt);
        return mapToResponse(saved);
    }

    public ReceiptResponse mapToResponse(Receipt receipt) {
        List<ReceiptItemDto> itemDtos = receipt.getItems() != null ?
                receipt.getItems().stream()
                        .map(item -> ReceiptItemDto.builder()
                                .name(item.getName())
                                .quantity(item.getQuantity())
                                .price(item.getPrice())
                                .category(item.getCategory())
                                .build())
                        .collect(Collectors.toList()) : new ArrayList<>();

        List<TaxDetailDto> taxDtos = receipt.getTaxes() != null ?
                receipt.getTaxes().stream()
                        .map(tax -> TaxDetailDto.builder()
                                .type(tax.getType())
                                .rate(tax.getRate())
                                .amount(tax.getAmount())
                                .currency(tax.getCurrency())
                                .build())
                        .collect(Collectors.toList()) : new ArrayList<>();

        BigDecimal effectiveTotal = calculateEffectiveTotal(receipt);

        return ReceiptResponse.builder()
                .id(receipt.getId())
                .merchantName(receipt.getMerchantName())
                .receiptDate(receipt.getReceiptDate())
                .totalAmount(effectiveTotal)
                .subtotal(receipt.getSubtotal())
                .totalTax(receipt.getTotalTax())
                .discount(receipt.getDiscount())
                .shippingAmount(receipt.getShippingAmount())
                .taxes(taxDtos)
                .category(receipt.getCategory())
                .items(itemDtos)
                .userId(receipt.getUserId())
                .createdAt(receipt.getCreatedAt())
                .build();
    }

    public BigDecimal calculateEffectiveTotal(Receipt receipt) {
        if (receipt.getTotalAmount() != null && receipt.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            return receipt.getTotalAmount();
        }

        if (receipt.getItems() != null && !receipt.getItems().isEmpty()) {
            BigDecimal itemsSum = receipt.getItems().stream()
                    .map(item -> {
                        BigDecimal qty = BigDecimal.valueOf(item.getQuantity() != null ? item.getQuantity() : 1);
                        BigDecimal price = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
                        return price.multiply(qty);
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (itemsSum.compareTo(BigDecimal.ZERO) > 0) {
                return itemsSum;
            }
        }
        return receipt.getTotalAmount();
    }

    private List<ReceiptItem> mapItemsToEntity(List<ReceiptItemDto> dtos) {
        if (dtos == null) {
            return new ArrayList<>();
        }
        return dtos.stream()
                .map(dto -> ReceiptItem.builder()
                        .name(dto.getName())
                        .quantity(dto.getQuantity())
                        .price(dto.getPrice())
                        .category(dto.getCategory())
                        .build())
                .collect(Collectors.toList());
    }

    private List<TaxDetail> mapTaxesToEntity(List<TaxDetailDto> dtos) {
        if (dtos == null) {
            return new ArrayList<>();
        }
        return dtos.stream()
                .map(dto -> TaxDetail.builder()
                        .type(dto.getType())
                        .rate(dto.getRate())
                        .amount(dto.getAmount())
                        .currency(dto.getCurrency())
                        .build())
                .collect(Collectors.toList());
    }
}
