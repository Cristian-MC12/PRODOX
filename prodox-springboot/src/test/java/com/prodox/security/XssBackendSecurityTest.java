// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.security;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bloque 12B (P1-8) — XSS a nivel backend, solamente 1-2 pruebas
 * representativas (no se inventa un XSS backend inexistente): PRODOX es un
 * backend JSON puro (Content-Type application/json en todas las respuestas,
 * CSP default-src 'none' — ver HttpSecurityHeadersTest) que nunca renderiza
 * HTML propio. La mitigación real de XSS es Angular (DomSanitizer,
 * escaping automático en interpolación de templates), fuera del alcance de
 * este backend.
 *
 * Lo único verificable y relevante desde el backend es que un payload
 * HTML/JS se trata como texto JSON inerte: se almacena y se devuelve tal
 * cual, dentro de un Content-Type que ningún navegador interpretaría como
 * HTML ejecutable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class XssBackendSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AppUserRepository userRepo;
    @Autowired private AuthRateLimitService authRateLimitService;

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

    @Test
    @DisplayName("XSS: payload <script> en 'nombre' se devuelve como texto JSON inerte, con Content-Type application/json (nunca text/html)")
    void register_nombreConPayloadXSS_respondeComoJsonInerteNuncaComoHtml() throws Exception {
        String email = "xss-nombre-" + UUID.randomUUID() + "@test.com";
        String payloadXSS = "<script>alert('xss')</script>";

        String cuerpo = objectMapper.writeValueAsString(Map.of(
                "email", email, "password", "PasswordSegura123",
                "role", "scrum_master", "nombre", payloadXSS));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        AppUser creado = userRepo.findByEmail(email).orElseThrow();
        usuariosCreados.add(creado.getId());

        // El backend nunca "limpia" ni ejecuta el payload — lo persiste tal
        // cual, como cualquier otro string. No hay backend HTML que pueda
        // interpretarlo: el riesgo de XSS real vive en el frontend Angular,
        // fuera de este backend.
        assertThat(creado.getNombre()).isEqualTo(payloadXSS);
    }
}
