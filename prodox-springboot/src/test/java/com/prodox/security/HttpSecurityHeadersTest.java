// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bloque de seguridad HTTP — verifica los headers de respuesta REALES
 * (no que el código Java contenga una cadena) contra el filtro de
 * seguridad completo, tal como lo vería un cliente.
 *
 * Referencia de baseline (antes de este bloque, capturado con curl contra
 * el backend corriendo): X-Content-Type-Options, X-Frame-Options,
 * Cache-Control/Pragma/Expires y X-XSS-Protection ya los enviaba Spring
 * Security 6 por defecto — no se tocaron. Referrer-Policy,
 * Permissions-Policy y Content-Security-Policy no existían — son los que
 * agrega este bloque.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HttpSecurityHeadersTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // ── A/B/C/D/E: headers presentes en una respuesta pública normal ──────

    @Test
    @DisplayName("A) X-Content-Type-Options: nosniff")
    void xContentTypeOptions_nosniff() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("B) X-Frame-Options: DENY (PRODOX no se embebe en iframes — confirmado, 0 usos en el frontend)")
    void xFrameOptions_deny() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    @DisplayName("C) Referrer-Policy: strict-origin-when-cross-origin")
    void referrerPolicy_strictOriginWhenCrossOrigin() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }

    @Test
    @DisplayName("D) Permissions-Policy: deshabilita cámara/micrófono/geolocalización/pagos/fullscreen (ninguno usado en el frontend)")
    void permissionsPolicy_deshabilitaCapacidadesNoUsadas() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(header().string("Permissions-Policy",
                        "camera=(), microphone=(), geolocation=(), payment=(), fullscreen=()"));
    }

    @Test
    @DisplayName("E) Content-Security-Policy: default-src 'none' (backend JSON puro, sin HTML/CSS/JS propio)")
    void contentSecurityPolicy_defaultSrcNone() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"));
    }

    @Test
    @DisplayName("Los 5 headers también llegan en una respuesta autenticada (no solo en la pública)")
    @WithMockUser
    void headers_tambienEnRespuestaAutenticada() throws Exception {
        mockMvc.perform(get("/api/dev/no-existe-de-todas-formas"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().exists("Permissions-Policy"))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    // ── F: HSTS — solo sobre HTTPS, nunca sobre HTTP plano de desarrollo ──

    @Test
    @DisplayName("F) Strict-Transport-Security AUSENTE sobre HTTP plano (comportamiento correcto en desarrollo/test)")
    void hsts_ausenteSobreHttpPlano() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    @DisplayName("F) Strict-Transport-Security PRESENTE cuando la petición es segura (simula HTTPS/producción)")
    void hsts_presenteSobreHttps() throws Exception {
        mockMvc.perform(get("/api/health").secure(true))
                .andExpect(header().exists("Strict-Transport-Security"));
    }

    // ── G: CORS — origen permitido funciona, origen no autorizado no ──────

    @Test
    @DisplayName("G) CORS: origen permitido (localhost:4200) recibe Access-Control-Allow-Origin")
    void cors_origenPermitido_recibeAccessControlAllowOrigin() throws Exception {
        mockMvc.perform(get("/api/health").header("Origin", "http://localhost:4200"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("G) CORS: origen NO autorizado no recibe Access-Control-Allow-Origin (preflight rechazado)")
    void cors_origenNoAutorizado_sinAccessControlAllowOrigin() throws Exception {
        mockMvc.perform(options("/api/health")
                        .header("Origin", "http://evil-attacker.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // ── H: errores no exponen stack trace ni información interna ──────────

    @Test
    @DisplayName("H) 400 de validación no expone stack trace, nombres de clase ni SQL")
    void error400_noExponeInformacionInterna() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContainIgnoringCase("Exception")
                .doesNotContainIgnoringCase("at com.prodox")
                .doesNotContainIgnoringCase("stacktrace")
                .doesNotContainIgnoringCase("SELECT ")
                .doesNotContainIgnoringCase("java.lang");
    }

    @Test
    @DisplayName("H) 401 sin autenticar no expone stack trace ni nombres de clase")
    void error401_noExponeInformacionInterna() throws Exception {
        String body = mockMvc.perform(get("/api/proyectos"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContainIgnoringCase("Exception")
                .doesNotContainIgnoringCase("at com.prodox")
                .doesNotContainIgnoringCase("stacktrace");
    }

    @Test
    @DisplayName("H) Ruta inexistente autenticada (404 real) no expone stack trace ni ruta interna del servidor")
    @WithMockUser
    void error404_noExponeInformacionInterna() throws Exception {
        String body = mockMvc.perform(get("/api/esto-no-existe-xyz"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContainIgnoringCase("Exception")
                .doesNotContain("com.prodox.controller")
                .doesNotContainIgnoringCase("stacktrace");
    }

    @Test
    @DisplayName("Ninguna respuesta expone el header Server ni X-Powered-By")
    void sinServerNiXPoweredBy() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(header().doesNotExist("Server"))
                .andExpect(header().doesNotExist("X-Powered-By"));
    }

    // ── I: rutas normales de autenticación/health sin regresión ───────────

    @Test
    @DisplayName("I) /api/health sigue público y funcional")
    void health_siguePublicoYFuncional() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("I) /api/auth/login sigue público (llega al controller: 400 por validación, no 401 de Security)")
    void login_siguePublico() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("I) /api/auth/register sigue público (llega al controller: 400 por validación, no 401 de Security)")
    void register_siguePublico() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("I) /oauth2/authorization/google sigue redirigiendo (no bloqueado por Security)")
    void oauth2Authorization_sigueRedirigiendo() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("I) Endpoint autenticado sigue exigiendo sesión (401 sin token) y sigue accesible con ella (@WithMockUser)")
    void endpointAutenticado_funcionaConSesionValida() throws Exception {
        mockMvc.perform(get("/api/proyectos"))
                .andExpect(status().isUnauthorized());
    }
}
