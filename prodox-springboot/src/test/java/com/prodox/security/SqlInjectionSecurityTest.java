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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bloque 12B (P0-4) — SQL injection: pocos tests representativos, no un
 * catálogo artificial. Objetivo: demostrar que payloads SQLi comunes se
 * tratan como DATOS (parámetros bind / valores de columna), nunca como SQL
 * ejecutable — consistente con que todo el acceso a datos en el backend usa
 * métodos derivados de Spring Data JPA o consultas nativas parametrizadas
 * (":param" + .setParameter(), nunca concatenación de String — verificado
 * en la auditoría de 12A).
 *
 * El caso de "parámetro tipado como UUID" (metricaId en
 * /api/parametrizacion/ultima-aprobada) se agregó en ParametrizacionControllerTest
 * — no se duplica aquí.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SqlInjectionSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AppUserRepository userRepo;
    @Autowired private AuthRateLimitService authRateLimitService;

    /** Evita envíos SMTP reales — mismo patrón que AuthControllerPasswordLimitTest. */
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

    // ── Parámetro textual: consulta derivada de Spring Data (findByEmail) ──

    @Test
    @DisplayName("SQLi: findByEmail con payload clásico ('OR '1'='1') no retorna ningún usuario ni rompe la consulta")
    void appUserRepository_findByEmail_conPayloadSQLiClasico_noRetornaUsuarioNiRompeLaConsulta() {
        // Si el valor se interpretara como SQL en vez de como dato, una
        // tautología como esta podría devolver TODOS los usuarios (bypass de
        // autenticación) en vez de ninguno.
        Optional<AppUser> resultado = userRepo.findByEmail("x' OR '1'='1");
        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("SQLi: findByEmail con payload destructivo (DROP TABLE) no ejecuta nada — la tabla sigue existiendo y consultable")
    void appUserRepository_findByEmail_conPayloadDestructivo_noEjecutaNadaYLaTablaSigueViva() {
        Optional<AppUser> resultado = userRepo.findByEmail("x'; DROP TABLE app_users; --");
        assertThat(resultado).isEmpty();

        // Prueba directa de que la tabla sigue intacta: una consulta normal
        // después del payload sigue funcionando sin excepción.
        assertThat(userRepo.count()).isGreaterThanOrEqualTo(0);
    }

    // ── Body: campo de texto libre sin restricción de formato (nombre) ─────

    @Test
    @DisplayName("SQLi: payload en el campo 'nombre' del registro se almacena como texto literal, nunca se ejecuta")
    void register_nombreConPayloadSQLi_seAlmacenaComoTextoLiteralSinEjecutarse() throws Exception {
        String email = "sqli-nombre-" + UUID.randomUUID() + "@test.com";
        String payloadSQLi = "Robert'); DROP TABLE app_users; --";

        String cuerpo = objectMapper.writeValueAsString(Map.of(
                "email", email, "password", "PasswordSegura123",
                "role", "scrum_master", "nombre", payloadSQLi));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk());

        AppUser creado = userRepo.findByEmail(email).orElseThrow();
        usuariosCreados.add(creado.getId());

        // El payload se guardó tal cual, como dato inerte — la tabla sigue
        // existiendo (si se hubiera ejecutado el DROP, esta misma consulta
        // ya habría fallado con una excepción de tabla inexistente).
        assertThat(creado.getNombre()).isEqualTo(payloadSQLi);
        assertThat(userRepo.count()).isGreaterThanOrEqualTo(1);
    }
}
