// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.AuthRequest;
import com.mpdia.dto.AuthResponse;
import com.mpdia.entity.AppUser;
import com.mpdia.repository.AppUserRepository;
import com.mpdia.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder   passwordEncoder;
    private final JwtUtil           jwtUtil;

    @Transactional
    public AuthResponse register(AuthRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("El correo ya está registrado.");
        }

        String role = (request.role() != null &&
                       List.of("scrum_master", "scrum_member").contains(request.role()))
                      ? request.role()
                      : "scrum_member";

        AppUser user = new AppUser();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role);
        AppUser saved = userRepository.save(user);

        return buildResponse(saved);
    }

    public AuthResponse login(AuthRequest request) {
        AppUser user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Credenciales inválidas."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Credenciales inválidas.");
        }

        return buildResponse(user);
    }

    private AuthResponse buildResponse(AppUser user) {
        String token = jwtUtil.generateToken(user.getId().toString(), user.getEmail(), user.getRole());
        return new AuthResponse(token, user.getId().toString(), user.getEmail(), user.getRole());
    }
}
