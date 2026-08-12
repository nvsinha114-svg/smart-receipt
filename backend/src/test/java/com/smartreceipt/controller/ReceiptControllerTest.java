package com.smartreceipt.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartreceipt.dto.ReceiptItemDto;
import com.smartreceipt.dto.ReceiptRequest;
import com.smartreceipt.dto.ReceiptResponse;
import com.smartreceipt.entity.Receipt;
import com.smartreceipt.entity.Role;
import com.smartreceipt.entity.User;
import com.smartreceipt.security.JwtAuthenticationFilter;
import com.smartreceipt.security.JwtService;
import com.smartreceipt.security.UserPrincipal;
import com.smartreceipt.service.OcrService;
import com.smartreceipt.service.PdfService;
import com.smartreceipt.service.ReceiptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReceiptController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReceiptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReceiptService receiptService;

    @MockitoBean
    private OcrService ocrService;

    @MockitoBean
    private PdfService pdfService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private User testUser;
    private UserPrincipal userPrincipal;
    private ReceiptResponse receiptResponse;
    private ReceiptRequest receiptRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id("user-100")
                .name("Alex Smith")
                .email("alex@example.com")
                .role(Role.USER)
                .build();
        userPrincipal = new UserPrincipal(testUser);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities())
        );

        receiptRequest = ReceiptRequest.builder()
                .merchantName("Best Buy")
                .receiptDate(LocalDate.of(2026, 8, 12))
                .totalAmount(new BigDecimal("299.99"))
                .items(List.of(ReceiptItemDto.builder().name("Headphones").quantity(1).price(new BigDecimal("299.99")).build()))
                .build();

        receiptResponse = ReceiptResponse.builder()
                .id("receipt-500")
                .merchantName("Best Buy")
                .receiptDate(LocalDate.of(2026, 8, 12))
                .totalAmount(new BigDecimal("299.99"))
                .items(List.of(ReceiptItemDto.builder().name("Headphones").quantity(1).price(new BigDecimal("299.99")).build()))
                .userId("user-100")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("POST /api/receipts - Create receipt (201 Created)")
    void createReceipt_Success() throws Exception {
        when(receiptService.createReceipt(any(ReceiptRequest.class), any())).thenReturn(receiptResponse);

        mockMvc.perform(post("/api/receipts")
                        .principal(() -> userPrincipal.getUsername())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(receiptRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("receipt-500"))
                .andExpect(jsonPath("$.merchantName").value("Best Buy"));
    }

    @Test
    @DisplayName("GET /api/receipts - Get all receipts (200 OK)")
    void getAllReceipts_Success() throws Exception {
        when(receiptService.getAllReceipts(any())).thenReturn(List.of(receiptResponse));

        mockMvc.perform(get("/api/receipts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("receipt-500"));
    }

    @Test
    @DisplayName("GET /api/receipts/{id} - Get receipt by ID (200 OK)")
    void getReceiptById_Success() throws Exception {
        when(receiptService.getReceiptById(eq("receipt-500"), any())).thenReturn(receiptResponse);

        mockMvc.perform(get("/api/receipts/receipt-500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantName").value("Best Buy"));
    }

    @Test
    @DisplayName("PUT /api/receipts/{id} - Update receipt (200 OK)")
    void updateReceipt_Success() throws Exception {
        when(receiptService.updateReceipt(eq("receipt-500"), any(ReceiptRequest.class), any()))
                .thenReturn(receiptResponse);

        mockMvc.perform(put("/api/receipts/receipt-500")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(receiptRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("receipt-500"));
    }

    @Test
    @DisplayName("DELETE /api/receipts/{id} - Delete receipt (204 No Content)")
    void deleteReceipt_Success() throws Exception {
        mockMvc.perform(delete("/api/receipts/receipt-500"))
                .andExpect(status().isNoContent());

        verify(receiptService).deleteReceipt(eq("receipt-500"), any());
    }

    @Test
    @DisplayName("GET /api/receipts/{id}/pdf - Download PDF report (200 OK)")
    void downloadReceiptPdf_Success() throws Exception {
        Receipt receiptEntity = Receipt.builder()
                .id("receipt-500")
                .merchantName("Best Buy")
                .receiptDate(LocalDate.of(2026, 8, 12))
                .totalAmount(new BigDecimal("299.99"))
                .userId("user-100")
                .build();

        byte[] fakePdfBytes = "%PDF-1.4 Fake PDF Content".getBytes();

        when(receiptService.findReceiptEntityById(eq("receipt-500"), any())).thenReturn(receiptEntity);
        when(pdfService.generateReceiptPdf(receiptEntity)).thenReturn(fakePdfBytes);

        mockMvc.perform(get("/api/receipts/receipt-500/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "form-data; name=\"attachment\"; filename=\"receipt_receipt-500.pdf\""))
                .andExpect(content().bytes(fakePdfBytes));
    }
}
