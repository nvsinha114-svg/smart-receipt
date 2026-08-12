package com.smartreceipt.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartreceipt.dto.AuthRequest;
import com.smartreceipt.dto.AuthResponse;
import com.smartreceipt.dto.RegisterRequest;
import com.smartreceipt.entity.Role;
import com.smartreceipt.exception.DuplicateResourceException;
import com.smartreceipt.exception.GlobalExceptionHandler;
import com.smartreceipt.security.JwtAuthenticationFilter;
import com.smartreceipt.security.JwtService;
import com.smartreceipt.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private RegisterRequest registerRequest;
    private AuthRequest authRequest;
    private AuthResponse authResponse;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterRequest.builder()
                .name("Alex Smith")
                .email("alex@example.com")
                .password("password123")
                .role(Role.USER)
                .build();

        authRequest = AuthRequest.builder()
                .email("alex@example.com")
                .password("password123")
                .build();

        authResponse = AuthResponse.builder()
                .token("valid_jwt_token")
                .type("Bearer")
                .id("user-100")
                .name("Alex Smith")
                .email("alex@example.com")
                .role(Role.USER)
                .build();
    }

    @Test
    @DisplayName("POST /api/auth/register - Success (201 Created)")
    void register_Success() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("valid_jwt_token"))
                .andExpect(jsonPath("$.email").value("alex@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("POST /api/auth/register - Conflict when email already exists (409 Conflict)")
    void register_DuplicateEmail_Returns409() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateResourceException("User already exists with email: alex@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("User already exists with email: alex@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Success (200 OK)")
    void login_Success() throws Exception {
        when(authService.login(any(AuthRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("valid_jwt_token"))
                .andExpect(jsonPath("$.email").value("alex@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Invalid Credentials (401 Unauthorized)")
    void login_InvalidCredentials_Returns401() throws Exception {
        when(authService.login(any(AuthRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }
}
