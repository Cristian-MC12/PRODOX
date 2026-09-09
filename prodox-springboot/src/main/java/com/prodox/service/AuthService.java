// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.prodox.dto.AuthRequest;
import com.prodox.dto.AuthResponse;
import com.prodox.entity.AppUser;
import com.prodox.repository.AppUserRepository;
import com.prodox.security.JwtUtil;
import com.prodox.util.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
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

        // Bloque de seguridad Validación/Config (C4): solo al CREAR una
        // contraseña nueva — login (más abajo) nunca aplica este límite, para
        // no arriesgar romper la autenticación de una cuenta ya existente.
        PasswordPolicy.validarLongitudMaxima(request.password());

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
                .orElseThrow(() -> {
                    // Bloque de seguridad Logging/Exposición (H4, Bloque 8A):
                    // no existe userId (el usuario no fue encontrado) y no se
                    // registra el email — solo el evento, sin PII.
                    log.warn("Intento de login fallido: usuario no encontrado");
                    return new IllegalArgumentException("Credenciales inválidas.");
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // Bloque de seguridad Logging/Exposición (H4, Bloque 8A): nunca
            // se registra la contraseña; se usa userId (pseudónimo, ya
            // disponible en este punto) en lugar del email.
            log.warn("Intento de login fallido: contraseña incorrecta para userId={}", user.getId());
            throw new IllegalArgumentException("Credenciales inválidas.");
        }

        return buildResponse(user);
    }

    private AuthResponse buildResponse(AppUser user) {
        String token = jwtUtil.generateToken(user.getId().toString(), user.getEmail(), user.getRole(), user.getNombre());
        return new AuthResponse(token, user.getId().toString(), user.getEmail(), user.getRole(), user.getNombre());
    }
}
