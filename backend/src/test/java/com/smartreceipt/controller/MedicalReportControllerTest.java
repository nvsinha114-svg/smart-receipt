package com.smartreceipt.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartreceipt.entity.MedicalReport;
import com.smartreceipt.entity.Role;
import com.smartreceipt.entity.User;
import com.smartreceipt.repository.MedicalReportRepository;
import com.smartreceipt.security.JwtAuthenticationFilter;
import com.smartreceipt.security.JwtService;
import com.smartreceipt.security.UserPrincipal;
import com.smartreceipt.service.DocumentClassificationService;
import com.smartreceipt.service.MedicalReportAnalysisService;
import com.smartreceipt.service.OcrService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MedicalReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class MedicalReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MedicalReportRepository medicalReportRepository;

    @MockitoBean
    private MedicalReportAnalysisService medicalReportAnalysisService;

    @MockitoBean
    private DocumentClassificationService classificationService;

    @MockitoBean
    private OcrService ocrService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private User testUser;
    private UserPrincipal userPrincipal;
    private MedicalReport medicalReport;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id("user-200")
                .name("Bob Vance")
                .email("bob@example.com")
                .role(Role.USER)
                .build();
        userPrincipal = new UserPrincipal(testUser);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities())
        );

        medicalReport = MedicalReport.builder()
                .id("report-700")
                .userId("user-200")
                .laboratoryName("Quest Diagnostics")
                .reportDate(LocalDate.of(2026, 8, 15))
                .fileName("lab_report.pdf")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("GET /api/medical-reports - List all medical reports (200 OK)")
    void getAllMedicalReports_Success() throws Exception {
        when(medicalReportRepository.findByUserId("user-200")).thenReturn(List.of(medicalReport));

        mockMvc.perform(get("/api/medical-reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("report-700"))
                .andExpect(jsonPath("$[0].laboratoryName").value("Quest Diagnostics"));
    }

    @Test
    @DisplayName("GET /api/medical-reports/{id} - Get medical report by ID (200 OK)")
    void getMedicalReportById_Success() throws Exception {
        when(medicalReportRepository.findByIdAndUserId("report-700", "user-200"))
                .thenReturn(Optional.of(medicalReport));

        mockMvc.perform(get("/api/medical-reports/report-700"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.laboratoryName").value("Quest Diagnostics"));
    }

    @Test
    @DisplayName("DELETE /api/medical-reports/{id} - Delete medical report (204 No Content)")
    void deleteMedicalReport_Success() throws Exception {
        when(medicalReportRepository.findByIdAndUserId("report-700", "user-200"))
                .thenReturn(Optional.of(medicalReport));

        mockMvc.perform(delete("/api/medical-reports/report-700"))
                .andExpect(status().isNoContent());

        verify(medicalReportRepository).delete(eq(medicalReport));
    }
}
