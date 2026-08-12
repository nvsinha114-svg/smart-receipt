package com.smartreceipt.service;

import com.smartreceipt.dto.ReceiptItemDto;
import com.smartreceipt.dto.ReceiptRequest;
import com.smartreceipt.dto.ReceiptResponse;
import com.smartreceipt.entity.Receipt;
import com.smartreceipt.entity.ReceiptItem;
import com.smartreceipt.entity.Role;
import com.smartreceipt.entity.User;
import com.smartreceipt.exception.UnauthorizedAccessException;
import com.smartreceipt.repository.ReceiptRepository;
import com.smartreceipt.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    @Mock
    private ReceiptRepository receiptRepository;

    @InjectMocks
    private ReceiptService receiptService;

    private User normalUser;
    private User adminUser;
    private UserPrincipal normalPrincipal;
    private UserPrincipal adminPrincipal;
    private Receipt receiptUser1;

    @BeforeEach
    void setUp() {
        normalUser = User.builder().id("user-1").email("user1@example.com").role(Role.USER).build();
        adminUser = User.builder().id("admin-1").email("admin@example.com").role(Role.ADMIN).build();

        normalPrincipal = new UserPrincipal(normalUser);
        adminPrincipal = new UserPrincipal(adminUser);

        receiptUser1 = Receipt.builder()
                .id("receipt-1")
                .merchantName("Walmart")
                .receiptDate(LocalDate.now())
                .totalAmount(new BigDecimal("45.99"))
                .userId("user-1")
                .createdAt(LocalDateTime.now())
                .items(List.of(ReceiptItem.builder().name("Milk").quantity(2).price(new BigDecimal("4.50")).build()))
                .build();
    }

    @Test
    @DisplayName("Should create receipt for user successfully")
    void createReceipt_Success() {
        ReceiptRequest request = ReceiptRequest.builder()
                .merchantName("Walmart")
                .receiptDate(LocalDate.now())
                .totalAmount(new BigDecimal("45.99"))
                .items(List.of(ReceiptItemDto.builder().name("Milk").quantity(2).price(new BigDecimal("4.50")).build()))
                .build();

        when(receiptRepository.save(any(Receipt.class))).thenReturn(receiptUser1);

        ReceiptResponse response = receiptService.createReceipt(request, normalPrincipal);

        assertNotNull(response);
        assertEquals("Walmart", response.getMerchantName());
        assertEquals("user-1", response.getUserId());
        verify(receiptRepository).save(any(Receipt.class));
    }

    @Test
    @DisplayName("Normal user should get only their own receipts")
    void getAllReceipts_UserRole() {
        when(receiptRepository.findByUserId("user-1")).thenReturn(List.of(receiptUser1));

        List<ReceiptResponse> receipts = receiptService.getAllReceipts(normalPrincipal);

        assertEquals(1, receipts.size());
        assertEquals("user-1", receipts.get(0).getUserId());
        verify(receiptRepository).findByUserId("user-1");
    }

    @Test
    @DisplayName("Admin user should get all receipts")
    void getAllReceipts_AdminRole() {
        when(receiptRepository.findAll()).thenReturn(List.of(receiptUser1));

        List<ReceiptResponse> receipts = receiptService.getAllReceipts(adminPrincipal);

        assertEquals(1, receipts.size());
        verify(receiptRepository).findAll();
    }

    @Test
    @DisplayName("Should throw UnauthorizedAccessException when user accesses another user receipt")
    void getReceiptById_Unauthorized_ThrowsException() {
        User otherUser = User.builder().id("user-2").email("user2@example.com").role(Role.USER).build();
        UserPrincipal otherPrincipal = new UserPrincipal(otherUser);

        when(receiptRepository.findById("receipt-1")).thenReturn(Optional.of(receiptUser1));

        assertThrows(UnauthorizedAccessException.class,
                () -> receiptService.getReceiptById("receipt-1", otherPrincipal));
    }

    @Test
    @DisplayName("Admin should be able to access any user receipt")
    void getReceiptById_AdminAccess_Success() {
        when(receiptRepository.findById("receipt-1")).thenReturn(Optional.of(receiptUser1));

        ReceiptResponse response = receiptService.getReceiptById("receipt-1", adminPrincipal);

        assertNotNull(response);
        assertEquals("receipt-1", response.getId());
    }

    @Test
    @DisplayName("Should delete receipt successfully")
    void deleteReceipt_Success() {
        when(receiptRepository.findById("receipt-1")).thenReturn(Optional.of(receiptUser1));

        receiptService.deleteReceipt("receipt-1", normalPrincipal);

        verify(receiptRepository).delete(receiptUser1);
    }
}
