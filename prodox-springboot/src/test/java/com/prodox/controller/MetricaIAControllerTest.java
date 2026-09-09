// Autor: Cristian Santiago Martinez Cordoba — PRODOX
// Bloque 10B-2: primer test suite para MetricaIAController (no existía
// ninguno). Cubre exclusivamente el rate limiting agregado a /propuesta
// (único endpoint de este controller que llama a Gemini) — mismo patrón
// que AICopilotControllerTest.
package com.prodox.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodox.dto.MetricaIAPropuestaDto;
import com.prodox.dto.MetricaIAPropuestaRequest;
import com.prodox.ratelimit.RateLimitService;
import com.prodox.service.MetricaIAService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MetricaIAControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MetricaIAService metricaIAService;

    @Autowired
    private RateLimitService rateLimitService;

    @BeforeEach
    void resetRateLimit() {
        rateLimitService.resetAll();
    }

    private MetricaIAPropuestaRequest requestValido() {
        return new MetricaIAPropuestaRequest("Quiero medir el estado de ánimo del equipo");
    }

    private MetricaIAPropuestaDto propuestaMock() {
        return new MetricaIAPropuestaDto(
                "Estado de ánimo del equipo", "Descripción", "Objetivo", "Qué mide",
                "Variables", "PROMEDIO", "Σ x / n", "puntos", "Fuente sugerida");
    }

    @Test
    @WithMockUser(username = "user-mia")
    void generarPropuesta_dentroDelLimite_retorna200() throws Exception {
        when(metricaIAService.generarPropuesta(anyString())).thenReturn(propuestaMock());

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/metricas-ia/propuesta")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestValido())))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @WithMockUser(username = "user-mia")
    @DisplayName("generarPropuesta: request número 11 en la ventana recibe 429 y NO llama a Gemini")
    void generarPropuesta_excedeLimite_retorna429SinLlamarAGemini() throws Exception {
        when(metricaIAService.generarPropuesta(anyString())).thenReturn(propuestaMock());

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/metricas-ia/propuesta")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestValido())))
                    .andExpect(status().isOk());
        }

        org.mockito.Mockito.clearInvocations(metricaIAService);

        mockMvc.perform(post("/api/metricas-ia/propuesta")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Exception"))));

        verifyNoInteractions(metricaIAService);
    }

    @Test
    @DisplayName("generarPropuesta: el límite de un usuario no afecta a otro usuario distinto")
    void generarPropuesta_usuariosDistintos_noComparteContador() throws Exception {
        when(metricaIAService.generarPropuesta(anyString())).thenReturn(propuestaMock());

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/metricas-ia/propuesta")
                            .with(csrf()).with(user("mia-usuario-a"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestValido())))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/metricas-ia/propuesta")
                        .with(csrf()).with(user("mia-usuario-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/api/metricas-ia/propuesta")
                        .with(csrf()).with(user("mia-usuario-b"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isOk());
    }

    @Test
    void generarPropuesta_sinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(post("/api/metricas-ia/propuesta")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isUnauthorized());
    }
}
