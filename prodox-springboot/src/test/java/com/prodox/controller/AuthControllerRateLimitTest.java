// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodox.entity.AppUser;
import com.prodox.ratelimit.AuthRateLimitService;
import com.prodox.repository.AppUserRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Rate limiting de autenticación — verificación HTTP real (MockMvc contra
 * AuthController real, AuthRateLimitService real, sin mocks del propio
 * limitador). Límites bajados vía @TestPropertySource para que los tests
 * sean rápidos y deterministas sin depender de las ventanas de producción
 * (5 min/1 hora).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "prodox.auth.rate-limit.login.max-per-email=3",
        "prodox.auth.rate-limit.login.max-per-ip=10",
        "prodox.auth.rate-limit.login.window-seconds=60",
        "prodox.auth.rate-limit.register.max-per-ip=3",
        "prodox.auth.rate-limit.register.window-seconds=60",
        "prodox.auth.rate-limit.forgot-password.max-per-ip=3",
        "prodox.auth.rate-limit.forgot-password.max-per-email=3",
        "prodox.auth.rate-limit.forgot-password.window-seconds=60",
        "prodox.auth.rate-limit.reset-password.max-per-ip=3",
        "prodox.auth.rate-limit.reset-password.window-seconds=60",
})
class AuthControllerRateLimitTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthRateLimitService authRateLimitService;
    @Autowired private AppUserRepository userRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    /**
     * Evita envíos SMTP reales durante los tests (PasswordResetService.
     * solicitarRecuperacion() los dispara de forma síncrona para cualquier
     * usuario que exista de verdad — ver EmailService, sin @Async). No se
     * modifica PasswordResetService ni EmailService: solo se reemplaza el
     * bean en el contexto de ESTE test por un mock que no hace nada.
     */
    @MockBean private EmailService emailService;

    private final java.util.List<UUID> usuariosCreados = new java.util.ArrayList<>();

    @BeforeEach
    void limpiarEstadoDelLimitador() {
        // El bean es un singleton reutilizado entre tests — se limpia acá
        // para que cada test empiece con el contador en cero, sin depender
        // de que MockMvc use IPs distintas por request (por defecto usa
        // siempre 127.0.0.1).
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

    private String cuerpoLogin(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    }

    // ── login ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Login: devuelve 429 al superar el límite configurado")
    void login_devuelve429AlSuperarElLimite() throws Exception {
        String email = "ratelimit-login-" + UUID.randomUUID() + "@test.com";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpoLogin(email, "cualquierPassword1")))
                    .andExpect(status().isBadRequest()); // credenciales inválidas — pero DENTRO del límite
        }
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoLogin(email, "cualquierPassword1")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Login: debajo del límite, el flujo normal (login exitoso) sigue funcionando sin cambios")
    void login_debajoDelLimite_flujoExitosoSigueFuncionando() throws Exception {
        AppUser u = new AppUser();
        u.setEmail("ratelimit-login-ok-" + UUID.randomUUID() + "@test.com");
        u.setPasswordHash(passwordEncoder.encode("passwordReal1"));
        u.setRole("scrum_master");
        u = userRepo.save(u);
        usuariosCreados.add(u.getId());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoLogin(u.getEmail(), "passwordReal1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.email").value(u.getEmail()));
    }

    // ── register ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Register: devuelve 429 al superar el límite configurado")
    void register_devuelve429AlSuperarElLimite() throws Exception {
        for (int i = 0; i < 3; i++) {
            String email = "ratelimit-reg-" + UUID.randomUUID() + "@test.com";
            String body = objectMapper.writeValueAsString(Map.of(
                    "email", email, "password", "passwordValido1"));
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
            userRepo.findByEmail(email).ifPresent(u -> usuariosCreados.add(u.getId()));
        }
        String bodyExtra = objectMapper.writeValueAsString(Map.of(
                "email", "ratelimit-reg-" + UUID.randomUUID() + "@test.com",
                "password", "passwordValido1"));
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyExtra))
                .andExpect(status().isTooManyRequests());
    }

    // ── forgot-password ────────────────────────────────────────────────

    @Test
    @DisplayName("Forgot-password: devuelve 429 al superar el límite configurado")
    void forgotPassword_devuelve429AlSuperarElLimite() throws Exception {
        String email = "ratelimit-forgot-" + UUID.randomUUID() + "@test.com";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("email", email))))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Forgot-password: NO revela existencia de cuenta — misma respuesta 200 y mismo comportamiento de límite para email real e inexistente")
    void forgotPassword_noRevelaExistenciaDeCuenta() throws Exception {
        AppUser real = new AppUser();
        real.setEmail("ratelimit-forgot-real-" + UUID.randomUUID() + "@test.com");
        real.setPasswordHash(passwordEncoder.encode("passwordReal1"));
        real.setRole("scrum_member");
        real = userRepo.save(real);
        usuariosCreados.add(real.getId());

        String respuestaReal = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", real.getEmail()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String respuestaInexistente = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "no-existe-" + UUID.randomUUID() + "@test.com"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(respuestaReal).isEqualTo(respuestaInexistente);

        // El límite también se agota igual para un email inventado (3 intentos ya consumidos: 2 arriba + éste).
        for (int i = 0; i < 1; i++) {
            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("email", "otro-inexistente-" + UUID.randomUUID() + "@test.com"))))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "otro-inexistente-final-" + UUID.randomUUID() + "@test.com"))))
                .andExpect(status().isTooManyRequests()); // por límite de IP, ya que los emails de este bloque son todos distintos
    }

    // ── reset-password ──────────────────────────────────────────────────

    @Test
    @DisplayName("Reset-password: devuelve 429 al superar el límite configurado")
    void resetPassword_devuelve429AlSuperarElLimite() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "token", "token-invalido-" + i, "newPassword", "nuevaPassword1"))))
                    .andExpect(status().isBadRequest()); // token inválido — pero DENTRO del límite
        }
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", "token-invalido-extra", "newPassword", "nuevaPassword1"))))
                .andExpect(status().isTooManyRequests());
    }

    // ── no afecta rutas no relacionadas ─────────────────────────────────

    @Test
    @DisplayName("OAuth2 (/oauth2/authorization/google) sigue funcionando sin cambios — no pasa por AuthRateLimitService")
    void oauth2_sigueFuncionandoSinCambios() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("/api/health no se ve afectado — endpoint no relacionado con autenticación")
    void health_noSeVeAfectado() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }
}
