package com.smartreceipt.service;

import com.smartreceipt.dto.AuthRequest;
import com.smartreceipt.dto.AuthResponse;
import com.smartreceipt.dto.RegisterRequest;
import com.smartreceipt.entity.Role;
import com.smartreceipt.entity.User;
import com.smartreceipt.security.JwtService;
import com.smartreceipt.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private User mockUser;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id("user-123")
                .name("Jane Doe")
                .email("jane@example.com")
                .password("password123")
                .role(Role.USER)
                .build();
        userPrincipal = new UserPrincipal(mockUser);
    }

    @Test
    @DisplayName("Should register user and return JWT token response")
    void register_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Jane Doe")
                .email("jane@example.com")
                .password("password123")
                .role(Role.USER)
                .build();

        when(userService.registerUser(request)).thenReturn(mockUser);
        when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn("mock_jwt_token");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("mock_jwt_token", response.getToken());
        assertEquals("jane@example.com", response.getEmail());
        assertEquals("user-123", response.getId());
    }

    @Test
    @DisplayName("Should authenticate user and return JWT token on login")
    void login_Success() {
        AuthRequest request = AuthRequest.builder()
                .email("jane@example.com")
                .password("password123")
                .build();

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authToken);
        when(jwtService.generateToken(userPrincipal)).thenReturn("mock_jwt_token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock_jwt_token", response.getToken());
        assertEquals("jane@example.com", response.getEmail());

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtService).generateToken(userPrincipal);
    }
}
