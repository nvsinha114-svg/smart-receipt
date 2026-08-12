package com.smartreceipt.service;

import com.smartreceipt.dto.AuthRequest;
import com.smartreceipt.dto.AuthResponse;
import com.smartreceipt.dto.RegisterRequest;
import com.smartreceipt.entity.User;
import com.smartreceipt.security.JwtService;
import com.smartreceipt.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        User user = userService.registerUser(request);
        UserPrincipal userPrincipal = new UserPrincipal(user);
        String token = jwtService.generateToken(userPrincipal);

        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    public AuthResponse login(AuthRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail().toLowerCase().trim(),
                        request.getPassword()
                )
        );

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String token = jwtService.generateToken(userPrincipal);

        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .id(userPrincipal.getId())
                .name(userPrincipal.getName())
                .email(userPrincipal.getUsername())
                .role(userPrincipal.getUser().getRole())
                .build();
    }
}
