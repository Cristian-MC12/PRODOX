// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodox.entity.AppUser;
import com.prodox.ratelimit.AuthRateLimitService;
import com.prodox.repository.AppUserRepository;
import com.prodox.repository.PasswordResetTokenRepository;
import com.prodox.service.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bloque de seguridad Validación/Config (Sub-bloque 3B, C4) — verificación
 * HTTP real (MockMvc contra AuthController real, AuthService/PasswordResetService
 * reales, sin mocks del límite) del límite de 72 BYTES UTF-8 en la contraseña,
 * aplicado únicamente al CREAR una contraseña (register, reset-password),
 * nunca al verificar una ya existente (login).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerPasswordLimitTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthRateLimitService authRateLimitService;
    @Autowired private AppUserRepository userRepo;
    @Autowired private PasswordResetTokenRepository tokenRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    /** Evita envíos SMTP reales — mismo patrón que AuthControllerRateLimitTest. */
    @MockBean private EmailService emailService;

    private final List<UUID> usuariosCreados = new ArrayList<>();

    @BeforeEach
    void limpiarEstado() {
        authRateLimitService.resetAll();
        usuariosCreados.clear();
    }

    @AfterEach
    void limpiarUsuarios() {
        for (UUID id : usuariosCreados) {
            try { userRepo.deleteById(id); } catch (Exception ignored) { }
        }
        usuariosCreados.clear();
    }

    private String cuerpoRegister(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "email", email, "password", password, "role", "scrum_master", "nombre", "Test"));
    }

    // ── register ────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: contraseña de exactamente 72 bytes ASCII es permitida (200)")
    void register_password72BytesAscii_permitido() throws Exception {
        String email = "c4-register-ok-" + UUID.randomUUID() + "@test.com";
        String password72Bytes = "a".repeat(72);

        String respuesta = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRegister(email, password72Bytes)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        userRepo.findByEmail(email).ifPresent(u -> usuariosCreados.add(u.getId()));
        assertThat(respuesta).doesNotContain(password72Bytes);
    }

    @Test
    @DisplayName("register: contraseña de 73 bytes ASCII es rechazada con 400, sin exponer la contraseña ni stack trace")
    void register_password73BytesAscii_rechazado400() throws Exception {
        String email = "c4-register-reject-" + UUID.randomUUID() + "@test.com";
        String password73Bytes = "a".repeat(73);

        String respuesta = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRegister(email, password73Bytes)))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(respuesta).doesNotContain(password73Bytes);
        assertThat(respuesta).doesNotContain("com.prodox");
        assertThat(respuesta).doesNotContain("java.lang");
        assertThat(userRepo.findByEmail(email)).isEmpty();
    }

    @Test
    @DisplayName("register: contraseña multibyte con <=72 caracteres pero >72 bytes es rechazada con 400")
    void register_passwordMultibyteMasDe72Bytes_rechazado400() throws Exception {
        String email = "c4-register-multibyte-reject-" + UUID.randomUUID() + "@test.com";
        String password40CaracteresNn = "ñ".repeat(40); // 40 chars, 80 bytes UTF-8
        assertThat(password40CaracteresNn.getBytes(StandardCharsets.UTF_8).length).isEqualTo(80);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRegister(email, password40CaracteresNn)))
                .andExpect(status().isBadRequest());

        assertThat(userRepo.findByEmail(email)).isEmpty();
    }

    @Test
    @DisplayName("register: contraseña multibyte de exactamente 72 bytes es permitida (200)")
    void register_passwordMultibyteExactamente72Bytes_permitido() throws Exception {
        String email = "c4-register-multibyte-ok-" + UUID.randomUUID() + "@test.com";
        String password36CaracteresNn = "ñ".repeat(36); // 36 chars, 72 bytes UTF-8 exactos
        assertThat(password36CaracteresNn.getBytes(StandardCharsets.UTF_8).length).isEqualTo(72);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRegister(email, password36CaracteresNn)))
                .andExpect(status().isOk());

        userRepo.findByEmail(email).ifPresent(u -> usuariosCreados.add(u.getId()));
    }

    @Test
    @DisplayName("login: contraseña larga (>72 bytes) NO introduce ninguna restricción nueva — sigue devolviendo credenciales inválidas, no 400 por longitud")
    void login_passwordLarga_noIntroduceRestriccionDeLongitud() throws Exception {
        // Usuario real con password normal — el login con una password de
        // prueba larga debe fallar por "credenciales inválidas" (401/400 de
        // negocio, mismo comportamiento que hoy), nunca por un rechazo de
        // longitud nuevo — confirma que AuthRequest/login no fueron tocados.
        String email = "c4-login-largo-" + UUID.randomUUID() + "@test.com";
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("passwordReal1"));
        u.setRole("scrum_master");
        u = userRepo.save(u);
        usuariosCreados.add(u.getId());

        String passwordLarga = "x".repeat(300); // muy por encima de 72 bytes
        String body = objectMapper.writeValueAsString(Map.of("email", email, "password", passwordLarga));

        String respuesta = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest()) // credenciales inválidas — comportamiento normal, no un rechazo por longitud
                .andReturn().getResponse().getContentAsString();

        assertThat(respuesta).contains("Credenciales inválidas");
        assertThat(respuesta).doesNotContain("72 bytes");
    }

    // ── reset-password ─────────────────────────────────────────────────

    private com.prodox.entity.PasswordResetToken crearTokenValido(UUID userId) {
        com.prodox.entity.PasswordResetToken t = new com.prodox.entity.PasswordResetToken();
        t.setToken("token-c4-" + UUID.randomUUID());
        t.setUserId(userId);
        t.setExpiresAt(java.time.Instant.now().plusSeconds(1800));
        t.setUsado(false);
        return tokenRepo.save(t);
    }

    @Test
    @DisplayName("reset-password: nueva contraseña de exactamente 72 bytes es permitida (200)")
    void resetPassword_nuevaPassword72Bytes_permitido() throws Exception {
        AppUser u = new AppUser();
        u.setEmail("c4-reset-ok-" + UUID.randomUUID() + "@test.com");
        u.setPasswordHash(passwordEncoder.encode("original1"));
        u.setRole("scrum_master");
        u = userRepo.save(u);
        usuariosCreados.add(u.getId());

        var token = crearTokenValido(u.getId());
        String nuevaPassword72Bytes = "b".repeat(72);
        String body = objectMapper.writeValueAsString(Map.of("token", token.getToken(), "newPassword", nuevaPassword72Bytes));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("reset-password: nueva contraseña de más de 72 bytes es rechazada con 400, sin exponerla")
    void resetPassword_nuevaPasswordMasDe72Bytes_rechazado400() throws Exception {
        AppUser u = new AppUser();
        u.setEmail("c4-reset-reject-" + UUID.randomUUID() + "@test.com");
        u.setPasswordHash(passwordEncoder.encode("original1"));
        u.setRole("scrum_master");
        u = userRepo.save(u);
        usuariosCreados.add(u.getId());

        var token = crearTokenValido(u.getId());
        String nuevaPassword73Bytes = "b".repeat(73);
        String body = objectMapper.writeValueAsString(Map.of("token", token.getToken(), "newPassword", nuevaPassword73Bytes));

        String respuesta = mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(respuesta).doesNotContain(nuevaPassword73Bytes);
        assertThat(respuesta).doesNotContain("com.prodox");
    }
}
