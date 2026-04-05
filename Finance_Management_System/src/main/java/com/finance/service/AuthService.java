package com.finance.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.finance.dto.AuthRequest;
import com.finance.dto.AuthResponse;
import com.finance.dto.RegisterRequest;
import com.finance.entity.User;
import com.finance.enums.Role;
import com.finance.exception.BadRequestException;
import com.finance.repo.UserRepository;
import com.finance.security.JwtUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {
	
	private final AuthenticationManager authManager;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    
    public AuthResponse login(AuthRequest request) {
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(), request.getPassword()
                )
        );

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String token = jwtUtil.generateToken(user.getUsername());

        return new AuthResponse(token, user.getUsername(), user.getRole().name());
    }
    
    
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username already taken: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered: " + request.getEmail());
        }

        // Public registration only allows VIEWER or ANALYST
        // ADMIN can only be created by another Admin via /api/users
        Role role = Role.ROLE_VIEWER; // default
        if (request.getRole() != null && !request.getRole().isBlank()) {
            try {
                Role requested = Role.valueOf(request.getRole().toUpperCase());
                if (requested == Role.ROLE_ADMIN) {
                    throw new BadRequestException(
                        "Admin accounts cannot be self-registered. Contact an existing admin.");
                }
                role = requested;
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(
                    "Invalid role. Valid values: ROLE_VIEWER, ROLE_ANALYST");
            }
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(role)
                .active(true)
                .build();

        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getRole().name());
    }

}
