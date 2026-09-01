// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.controller;

import com.prodox.dto.ai.AIInsightDto;
import com.prodox.dto.ai.GenerateInsightsResultDto;
import com.prodox.security.JwtUtil;
import com.prodox.service.AIInsightsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AIInsightsController.class)
@DisplayName("AIInsightsController — pruebas de integración")
class AIInsightsControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AIInsightsService insightsService;

    // FASE 16: @WebMvcTest solo carga el slice web — SecurityConfig (sí incluida
    // por defecto) intenta construir el bean jwtAuthFilter, cuyo constructor
    // requiere JwtUtil (un @Component fuera del slice web). Sin este mock, el
    // ApplicationContext falla al arrancar para los 13 tests de esta clase.
    // Ningún test aquí envía un header Authorization: Bearer, así que JwtAuthFilter
    // nunca invoca a jwtUtil — no se necesita ningún stubbing sobre este mock.
    @MockBean
    JwtUtil jwtUtil;

    private UUID proyectoId;
    private UUID insightId;
    private String userId;
    private AIInsightDto insightDto;

    @BeforeEach
    void setUp() {
        proyectoId = UUID.randomUUID();
        insightId = UUID.randomUUID();
        userId = "test-user-123";

        insightDto = new AIInsightDto(
                insightId,
                proyectoId,
                null,
                "TREND",
                "MEDIUM",
                "Tendencia positiva en Calidad",
                "La calidad ha mejorado consistentemente en los últimos 3 sprints",
                List.of(),
                "Continuar con las prácticas actuales",
                "HIGH",
                false,
                Instant.now()
        );
    }

    // ── GET /api/ai/insights/{proyectoId} ─────────────────────────────────

    @Test
    @DisplayName("GET insights: usuario autenticado retorna 200 con lista de insights")
    @WithMockUser(username = "test-user-123")
    void getInsights_usuarioAutenticado_retorna200() throws Exception {
        when(insightsService.getProjectInsights(proyectoId, userId))
                .thenReturn(List.of(insightDto));

        mockMvc.perform(get("/api/ai/insights/{proyectoId}", proyectoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(insightId.toString()))
                .andExpect(jsonPath("$[0].type").value("TREND"))
                .andExpect(jsonPath("$[0].severity").value("MEDIUM"))
                .andExpect(jsonPath("$[0].title").value("Tendencia positiva en Calidad"));

        verify(insightsService).getProjectInsights(proyectoId, userId);
    }

    @Test
    @DisplayName("GET insights: proyecto sin insights retorna 200 con lista vacía")
    @WithMockUser(username = "test-user-123")
    void getInsights_proyectoSinInsights_retorna200Vacio() throws Exception {
        when(insightsService.getProjectInsights(proyectoId, userId))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/ai/insights/{proyectoId}", proyectoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(insightsService).getProjectInsights(proyectoId, userId);
    }

    @Test
    @DisplayName("GET insights: usuario sin autenticar retorna 401")
    void getInsights_sinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(get("/api/ai/insights/{proyectoId}", proyectoId))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(insightsService);
    }

    @Test
    @DisplayName("GET insights: usuario no autorizado retorna 403")
    @WithMockUser(username = "test-user-123")
    void getInsights_usuarioNoAutorizado_retorna403() throws Exception {
        when(insightsService.getProjectInsights(proyectoId, userId))
                .thenThrow(new SecurityException("No tienes acceso a este proyecto"));

        mockMvc.perform(get("/api/ai/insights/{proyectoId}", proyectoId))
                .andExpect(status().isForbidden());

        verify(insightsService).getProjectInsights(proyectoId, userId);
    }

    @Test
    @DisplayName("GET insights: proyecto inexistente retorna 400")
    @WithMockUser(username = "test-user-123")
    void getInsights_proyectoInexistente_retorna400() throws Exception {
        when(insightsService.getProjectInsights(proyectoId, userId))
                .thenThrow(new IllegalArgumentException("Proyecto no encontrado"));

        mockMvc.perform(get("/api/ai/insights/{proyectoId}", proyectoId))
                .andExpect(status().isBadRequest());

        verify(insightsService).getProjectInsights(proyectoId, userId);
    }

    // ── POST /api/ai/insights/generate/{proyectoId} ───────────────────────

    @Test
    @DisplayName("POST generate: usuario autenticado genera insights y retorna 200")
    @WithMockUser(username = "test-user-123")
    void generateInsights_usuarioAutenticado_retorna200() throws Exception {
        GenerateInsightsResultDto resultado = new GenerateInsightsResultDto(
                List.of(insightDto), "COMPLETE", 1, 1, 0, List.of());
        when(insightsService.generateInsights(proyectoId, userId))
                .thenReturn(resultado);

        mockMvc.perform(post("/api/ai/insights/generate/{proyectoId}", proyectoId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETE"))
                .andExpect(jsonPath("$.insights").isArray())
                .andExpect(jsonPath("$.insights[0].id").value(insightId.toString()))
                .andExpect(jsonPath("$.insights[0].type").value("TREND"));

        verify(insightsService).generateInsights(proyectoId, userId);
    }

    @Test
    @DisplayName("POST generate: proyecto con datos insuficientes retorna 200 con lista vacía (SIN_DATOS)")
    @WithMockUser(username = "test-user-123")
    void generateInsights_datosInsuficientes_retorna200Vacio() throws Exception {
        GenerateInsightsResultDto resultado = new GenerateInsightsResultDto(
                List.of(), "SIN_DATOS", 0, 0, 0, List.of());
        when(insightsService.generateInsights(proyectoId, userId))
                .thenReturn(resultado);

        mockMvc.perform(post("/api/ai/insights/generate/{proyectoId}", proyectoId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIN_DATOS"))
                .andExpect(jsonPath("$.insights").isArray())
                .andExpect(jsonPath("$.insights").isEmpty());

        verify(insightsService).generateInsights(proyectoId, userId);
    }

    @Test
    @DisplayName("POST generate: usuario sin autenticar retorna 401")
    void generateInsights_sinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(post("/api/ai/insights/generate/{proyectoId}", proyectoId)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(insightsService);
    }

    @Test
    @DisplayName("POST generate: usuario no autorizado retorna 403")
    @WithMockUser(username = "test-user-123")
    void generateInsights_usuarioNoAutorizado_retorna403() throws Exception {
        when(insightsService.generateInsights(proyectoId, userId))
                .thenThrow(new SecurityException("No tienes acceso a este proyecto"));

        mockMvc.perform(post("/api/ai/insights/generate/{proyectoId}", proyectoId)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(insightsService).generateInsights(proyectoId, userId);
    }

    // ── POST /api/ai/insights/{insightId}/dismiss ─────────────────────────

    @Test
    @DisplayName("POST dismiss: usuario autenticado descarta insight y retorna 204")
    @WithMockUser(username = "test-user-123")
    void dismissInsight_usuarioAutenticado_retorna204() throws Exception {
        doNothing().when(insightsService).dismissInsight(insightId, userId);

        mockMvc.perform(post("/api/ai/insights/{insightId}/dismiss", insightId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(insightsService).dismissInsight(insightId, userId);
    }

    @Test
    @DisplayName("POST dismiss: usuario sin autenticar retorna 401")
    void dismissInsight_sinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(post("/api/ai/insights/{insightId}/dismiss", insightId)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(insightsService);
    }

    @Test
    @DisplayName("POST dismiss: usuario no autorizado retorna 403")
    @WithMockUser(username = "test-user-123")
    void dismissInsight_usuarioNoAutorizado_retorna403() throws Exception {
        doThrow(new SecurityException("No tienes acceso a este proyecto"))
                .when(insightsService).dismissInsight(insightId, userId);

        mockMvc.perform(post("/api/ai/insights/{insightId}/dismiss", insightId)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(insightsService).dismissInsight(insightId, userId);
    }

    @Test
    @DisplayName("POST dismiss: insight inexistente retorna 400")
    @WithMockUser(username = "test-user-123")
    void dismissInsight_insightInexistente_retorna400() throws Exception {
        doThrow(new IllegalArgumentException("Insight no encontrado"))
                .when(insightsService).dismissInsight(insightId, userId);

        mockMvc.perform(post("/api/ai/insights/{insightId}/dismiss", insightId)
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(insightsService).dismissInsight(insightId, userId);
    }
}
