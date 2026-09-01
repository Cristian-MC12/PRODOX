// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.controller;

import com.prodox.dto.ai.AISprintReportDto;
import com.prodox.ratelimit.RateLimitService;
import com.prodox.security.JwtUtil;
import com.prodox.service.AIReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AIReportController.class)
@DisplayName("AIReportController — pruebas de integración")
class AIReportControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AIReportService reportService;

    @MockBean
    RateLimitService rateLimitService;

    // FASE 16: ver AIInsightsControllerTest — mismo motivo (JwtUtil ausente del
    // slice @WebMvcTest, requerido por el bean jwtAuthFilter de SecurityConfig).
    @MockBean
    JwtUtil jwtUtil;

    private UUID sprintId;
    private String userId;
    private AISprintReportDto reportDto;

    @BeforeEach
    void setUp() {
        sprintId = UUID.randomUUID();
        userId = "test-user-123";

        reportDto = new AISprintReportDto(
                sprintId,
                5,
                "Implementar reportes",
                LocalDate.now().minusWeeks(2),
                LocalDate.now().minusWeeks(1),
                "Sprint 5 completó exitosamente los objetivos",
                Map.of("Calidad", new BigDecimal("8.5"), "Productividad", new BigDecimal("7.2")),
                List.of("Calidad superior", "Productividad estable"),
                List.of("Ninguno detectado"),
                List.of(),
                "Continuar con prácticas actuales",
                Instant.now()
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST /api/ai/reports/sprint/{sprintId}/generate
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST generate: sin autenticación retorna 401")
    void generateReport_sinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(post("/api/ai/reports/sprint/{sprintId}/generate", sprintId)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(rateLimitService);
        verifyNoInteractions(reportService);
    }

    @Test
    @WithMockUser(username = "test-user-123")
    @DisplayName("POST generate: con autenticación y dentro de límite genera reporte")
    void generateReport_conAutenticacionYDentroLimite_generaReporte() throws Exception {
        when(rateLimitService.allowRequest(userId)).thenReturn(true);
        when(reportService.generateReport(eq(sprintId), eq(userId))).thenReturn(reportDto);

        mockMvc.perform(post("/api/ai/reports/sprint/{sprintId}/generate", sprintId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sprintId").value(sprintId.toString()))
                .andExpect(jsonPath("$.sprintNumero").value(5))
                .andExpect(jsonPath("$.sprintGoal").value("Implementar reportes"))
                .andExpect(jsonPath("$.resumenEjecutivo").value("Sprint 5 completó exitosamente los objetivos"))
                .andExpect(jsonPath("$.metricas.Calidad").value(8.5))
                .andExpect(jsonPath("$.highlights[0]").value("Calidad superior"))
                .andExpect(jsonPath("$.concerns[0]").value("Ninguno detectado"));

        verify(rateLimitService).allowRequest(userId);
        verify(reportService).generateReport(sprintId, userId);
    }

    @Test
    @WithMockUser(username = "test-user-123")
    @DisplayName("POST generate: excede rate limit retorna error")
    void generateReport_excedeRateLimit_retornaError() throws Exception {
        when(rateLimitService.allowRequest(userId)).thenReturn(false);

        mockMvc.perform(post("/api/ai/reports/sprint/{sprintId}/generate", sprintId)
                        .with(csrf()))
                .andExpect(status().is5xxServerError());

        verify(rateLimitService).allowRequest(userId);
        verifyNoInteractions(reportService);
    }

    @Test
    @WithMockUser(username = "test-user-123")
    @DisplayName("POST generate: usuario no autorizado retorna error")
    void generateReport_usuarioNoAutorizado_retornaError() throws Exception {
        when(rateLimitService.allowRequest(userId)).thenReturn(true);
        when(reportService.generateReport(eq(sprintId), eq(userId)))
                .thenThrow(new SecurityException("No tienes acceso a este proyecto"));

        mockMvc.perform(post("/api/ai/reports/sprint/{sprintId}/generate", sprintId)
                        .with(csrf()))
                // FASE 16: GlobalExceptionHandler.handleSecurityException() mapea
                // SecurityException a 403 (no a un 5xx) — la aserción original nunca
                // se había ejecutado de verdad porque el ApplicationContext fallaba
                // antes de llegar a correr este test.
                .andExpect(status().isForbidden());

        verify(reportService).generateReport(sprintId, userId);
    }

    @Test
    @WithMockUser(username = "test-user-123")
    @DisplayName("POST generate: sprint inexistente retorna error")
    void generateReport_sprintInexistente_retornaError() throws Exception {
        when(rateLimitService.allowRequest(userId)).thenReturn(true);
        when(reportService.generateReport(eq(sprintId), eq(userId)))
                .thenThrow(new IllegalArgumentException("Sprint no encontrado"));

        mockMvc.perform(post("/api/ai/reports/sprint/{sprintId}/generate", sprintId)
                        .with(csrf()))
                // FASE 16: GlobalExceptionHandler.handleBadRequest() mapea
                // IllegalArgumentException a 400 (no a un 5xx) — misma causa que arriba.
                .andExpect(status().isBadRequest());

        verify(reportService).generateReport(sprintId, userId);
    }
}
