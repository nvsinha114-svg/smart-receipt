package com.smartreceipt.service;

import com.smartreceipt.dto.RegisterRequest;
import com.smartreceipt.entity.Role;
import com.smartreceipt.entity.User;
import com.smartreceipt.exception.DuplicateResourceException;
import com.smartreceipt.exception.ResourceNotFoundException;
import com.smartreceipt.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private RegisterRequest registerRequest;
    private User mockUser;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterRequest.builder()
                .name("John Doe")
                .email("john@example.com")
                .password("secret123")
                .role(Role.USER)
                .build();

        mockUser = User.builder()
                .id("user-123")
                .name("John Doe")
                .email("john@example.com")
                .password("encoded_secret123")
                .role(Role.USER)
                .build();
    }

    @Test
    @DisplayName("Should register new user successfully")
    void registerUser_Success() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded_secret123");
        when(userRepository.save(any(User.class))).thenReturn(mockUser);

        User created = userService.registerUser(registerRequest);

        assertNotNull(created);
        assertEquals("user-123", created.getId());
        assertEquals("john@example.com", created.getEmail());
        assertEquals(Role.USER, created.getRole());

        verify(userRepository).existsByEmail("john@example.com");
        verify(passwordEncoder).encode("secret123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when email exists")
    void registerUser_DuplicateEmail_ThrowsException() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> userService.registerUser(registerRequest));

        verify(userRepository).existsByEmail("john@example.com");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should find user by email")
    void findByEmail_Success() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(mockUser));

        User found = userService.findByEmail("john@example.com");

        assertNotNull(found);
        assertEquals("john@example.com", found.getEmail());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user email not found")
    void findByEmail_NotFound_ThrowsException() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.findByEmail("unknown@example.com"));
    }
}
