package com.smartreceipt.service;

import com.smartreceipt.dto.ReceiptItemDto;
import com.smartreceipt.dto.ReceiptRequest;
import com.smartreceipt.dto.ReceiptResponse;
import com.smartreceipt.entity.Receipt;
import com.smartreceipt.entity.ReceiptItem;
import com.smartreceipt.entity.Role;
import com.smartreceipt.exception.ResourceNotFoundException;
import com.smartreceipt.exception.UnauthorizedAccessException;
import com.smartreceipt.repository.ReceiptRepository;
import com.smartreceipt.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

        Receipt receipt = Receipt.builder()
                .merchantName(request.getMerchantName())
                .receiptDate(request.getReceiptDate())
                .totalAmount(request.getTotalAmount())
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

        receipt.setMerchantName(request.getMerchantName());
        receipt.setReceiptDate(request.getReceiptDate());
        receipt.setTotalAmount(request.getTotalAmount());
        receipt.setItems(mapItemsToEntity(request.getItems()));

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
            throw new UnauthorizedAccessException("You are not authorized to access or modify this receipt");
        }

        return receipt;
    }

    public ReceiptResponse saveReceiptEntity(Receipt receipt) {
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
                                .build())
                        .collect(Collectors.toList()) : new ArrayList<>();

        return ReceiptResponse.builder()
                .id(receipt.getId())
                .merchantName(receipt.getMerchantName())
                .receiptDate(receipt.getReceiptDate())
                .totalAmount(receipt.getTotalAmount())
                .items(itemDtos)
                .userId(receipt.getUserId())
                .createdAt(receipt.getCreatedAt())
                .build();
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
                        .build())
                .collect(Collectors.toList());
    }
}
