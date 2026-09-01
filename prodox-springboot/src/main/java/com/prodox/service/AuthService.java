// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.prodox.dto.AuthRequest;
import com.prodox.dto.AuthResponse;
import com.prodox.entity.AppUser;
import com.prodox.repository.AppUserRepository;
import com.prodox.security.JwtUtil;
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
        user.setNombre(request.nombre());
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
        String token = jwtUtil.generateToken(user.getId().toString(), user.getEmail(), user.getRole(), user.getNombre());
        return new AuthResponse(token, user.getId().toString(), user.getEmail(), user.getRole(), user.getNombre());
    }
}
