// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.entity.AppUser;
import com.mpdia.entity.PasswordResetToken;
import com.mpdia.repository.AppUserRepository;
import com.mpdia.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * Recuperación de contraseña vía token propio (no reutiliza el JWT de
 * sesión): un token aleatorio de un solo uso, con expiración, guardado en
 * password_reset_tokens (ver V34).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final AppUserRepository userRepo;
    private final PasswordResetTokenRepository tokenRepo;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${mpdia.password-reset.expiration-minutes:30}")
    private long expirationMinutes;

    @Value("${mpdia.app.url}")
    private String frontendUrl;

    /**
     * Nunca revela si el email existe: si no hay usuario con ese correo,
     * simplemente no hace nada — el llamador (AuthController) siempre
     * responde el mismo mensaje genérico sin importar el resultado.
     */
    @Transactional
    public void solicitarRecuperacion(String email) {
        userRepo.findByEmail(email).ifPresent(user -> {
            PasswordResetToken t = new PasswordResetToken();
            t.setUserId(user.getId());
            t.setToken(generarToken());
            t.setExpiresAt(Instant.now().plus(expirationMinutes, ChronoUnit.MINUTES));
            tokenRepo.save(t);

            String link = UriComponentsBuilder.fromUriString(frontendUrl + "/reset-password")
                    .queryParam("token", t.getToken())
                    .build()
                    .toUriString();

            log.info("Solicitud de recuperación de contraseña para email: {}", email);
            emailService.enviar(user.getEmail(), "Recuperación de contraseña — MPDIA",
                    "Hola,\n\n" +
                    "Solicitaste recuperar tu contraseña en el sistema MPDIA.\n\n" +
                    "Ingresá al siguiente enlace para establecer una nueva contraseña:\n\n" +
                    "  " + link + "\n\n" +
                    "Este enlace vence en " + expirationMinutes + " minutos y solo puede usarse una vez.\n\n" +
                    "Si no solicitaste esto, podés ignorar este correo.\n\n" +
                    "Saludos,\nSistema MPDIA"
            );
        });
    }

    @Transactional
    public void restablecerContrasena(String token, String nuevaPassword) {
        PasswordResetToken t = tokenRepo.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token de recuperación inválido."));

        if (Boolean.TRUE.equals(t.getUsado())) {
            throw new IllegalArgumentException("Este enlace de recuperación ya fue utilizado.");
        }
        if (t.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("El enlace de recuperación expiró.");
        }

        AppUser user = userRepo.findById(t.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        // Mismo mecanismo de hash (bcrypt) que usa el registro/login normal.
        // Solo se toca passwordHash: rol y demás datos del usuario no se modifican.
        user.setPasswordHash(passwordEncoder.encode(nuevaPassword));
        userRepo.save(user);

        t.setUsado(true);
        tokenRepo.save(t);

        log.info("Contraseña restablecida correctamente para userId: {}", user.getId());
    }

    private String generarToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
