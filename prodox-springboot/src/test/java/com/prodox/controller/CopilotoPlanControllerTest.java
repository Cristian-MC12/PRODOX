// Autor: Cristian Santiago Martinez Cordoba — PRODOX
// Bloque 10B-2: primer test suite para CopilotoPlanController (no existía
// ninguno). Cubre exclusivamente el rate limiting agregado a
// /generar-metricas (único endpoint de este controller, y el mismo cuyo
// hallazgo G-11 de BOLA/IDOR fue cerrado como NO APLICA en el Bloque
// 10B-1: Factor es un catálogo global, no un recurso de Proyecto — este
// bloque solo agrega protección contra abuso/costo, no autorización).
package com.prodox.controller;

import com.prodox.dto.MetricaSugeridaDto;
import com.prodox.ratelimit.RateLimitService;
import com.prodox.service.CopilotoPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CopilotoPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CopilotoPlanService copilotoPlanService;

    @Autowired
    private RateLimitService rateLimitService;

    private UUID factorId;

    @BeforeEach
    void setUp() {
        factorId = UUID.randomUUID();
        rateLimitService.resetAll();
    }

    private List<MetricaSugeridaDto> metricasMock() {
        return List.of(new MetricaSugeridaDto(
                "Velocidad del equipo", "Descripción", "puntos", 20.0,
                "Fuente sugerida", "Justificación"));
    }

    @Test
    @WithMockUser(username = "user-cp")
    void generarMetricas_dentroDelLimite_retorna200() throws Exception {
        when(copilotoPlanService.generarMetricas(any(UUID.class))).thenReturn(metricasMock());

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/copiloto-plan/generar-metricas")
                            .with(csrf())
                            .param("factorId", factorId.toString()))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @WithMockUser(username = "user-cp")
    @DisplayName("generarMetricas: request número 11 en la ventana recibe 429 y NO llama a Gemini")
    void generarMetricas_excedeLimite_retorna429SinLlamarAGemini() throws Exception {
        when(copilotoPlanService.generarMetricas(any(UUID.class))).thenReturn(metricasMock());

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/copiloto-plan/generar-metricas")
                            .with(csrf())
                            .param("factorId", factorId.toString()))
                    .andExpect(status().isOk());
        }

        org.mockito.Mockito.clearInvocations(copilotoPlanService);

        mockMvc.perform(post("/api/copiloto-plan/generar-metricas")
                        .with(csrf())
                        .param("factorId", factorId.toString()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Exception"))));

        verifyNoInteractions(copilotoPlanService);
    }

    @Test
    @DisplayName("generarMetricas: el límite de un usuario no afecta a otro usuario distinto")
    void generarMetricas_usuariosDistintos_noComparteContador() throws Exception {
        when(copilotoPlanService.generarMetricas(any(UUID.class))).thenReturn(metricasMock());

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/copiloto-plan/generar-metricas")
                            .with(csrf()).with(user("cp-usuario-a"))
                            .param("factorId", factorId.toString()))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/copiloto-plan/generar-metricas")
                        .with(csrf()).with(user("cp-usuario-a"))
                        .param("factorId", factorId.toString()))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/api/copiloto-plan/generar-metricas")
                        .with(csrf()).with(user("cp-usuario-b"))
                        .param("factorId", factorId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void generarMetricas_sinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(post("/api/copiloto-plan/generar-metricas")
                        .with(csrf())
                        .param("factorId", factorId.toString()))
                .andExpect(status().isUnauthorized());
    }
}
