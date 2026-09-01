// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.controller;

import com.prodox.dto.ai.AIRetrospectiveDto;
import com.prodox.ratelimit.RateLimitService;
import com.prodox.security.JwtUtil;
import com.prodox.service.AIRetrospectiveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AIRetrospectiveController.class)
@DisplayName("AIRetrospectiveController — pruebas de integración")
class AIRetrospectiveControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AIRetrospectiveService retrospectiveService;

    @MockBean
    RateLimitService rateLimitService;

    // FASE 16: ver AIInsightsControllerTest — mismo motivo (JwtUtil ausente del
    // slice @WebMvcTest, requerido por el bean jwtAuthFilter de SecurityConfig).
    @MockBean
    JwtUtil jwtUtil;

    private UUID sprintId;
    private String userId;
    private AIRetrospectiveDto retrospectiveDto;

    @BeforeEach
    void setUp() {
        sprintId = UUID.randomUUID();
        userId = "test-user-123";

        retrospectiveDto = new AIRetrospectiveDto(
                sprintId,
                5,
                "Implementar retrospectivas",
                LocalDate.now().minusWeeks(2),
                LocalDate.now().minusWeeks(1),
                List.of("Calidad mejoró 12%", "Equipo colaborativo"),
                List.of("Mejorar documentación", "Reducir reuniones"),
                List.of("Tendencia descendente en productividad"),
                List.of("Establecer daily standup más eficiente"),
                List.of("¿Qué obstáculos encontramos?", "¿Cómo mejorar la comunicación?"),
                Instant.now()
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POST /api/ai/retrospectives/sprint/{sprintId}/generate
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST generate: sin autenticación retorna 401")
    void generateRetrospective_sinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(post("/api/ai/retrospectives/sprint/{sprintId}/generate", sprintId)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(rateLimitService);
        verifyNoInteractions(retrospectiveService);
    }

    @Test
    @WithMockUser(username = "test-user-123")
    @DisplayName("POST generate: con autenticación y dentro de límite genera retrospectiva")
    void generateRetrospective_conAutenticacionYDentroLimite_generaRetrospectiva() throws Exception {
        when(rateLimitService.allowRequest(userId)).thenReturn(true);
        when(retrospectiveService.generateRetrospective(eq(sprintId), eq(userId))).thenReturn(retrospectiveDto);

        mockMvc.perform(post("/api/ai/retrospectives/sprint/{sprintId}/generate", sprintId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sprintId").value(sprintId.toString()))
                .andExpect(jsonPath("$.sprintNumero").value(5))
                .andExpect(jsonPath("$.sprintGoal").value("Implementar retrospectivas"))
                .andExpect(jsonPath("$.whatWentWell[0]").value("Calidad mejoró 12%"))
                .andExpect(jsonPath("$.whatWentWell[1]").value("Equipo colaborativo"))
                .andExpect(jsonPath("$.whatCouldImprove[0]").value("Mejorar documentación"))
                .andExpect(jsonPath("$.risks[0]").value("Tendencia descendente en productividad"))
                .andExpect(jsonPath("$.recommendations[0]").value("Establecer daily standup más eficiente"))
                .andExpect(jsonPath("$.questionsForTeam[0]").value("¿Qué obstáculos encontramos?"));

        verify(rateLimitService).allowRequest(userId);
        verify(retrospectiveService).generateRetrospective(sprintId, userId);
    }

    @Test
    @WithMockUser(username = "test-user-123")
    @DisplayName("POST generate: excede rate limit retorna error")
    void generateRetrospective_excedeRateLimit_retornaError() throws Exception {
        when(rateLimitService.allowRequest(userId)).thenReturn(false);

        mockMvc.perform(post("/api/ai/retrospectives/sprint/{sprintId}/generate", sprintId)
                        .with(csrf()))
                .andExpect(status().is5xxServerError());

        verify(rateLimitService).allowRequest(userId);
        verifyNoInteractions(retrospectiveService);
    }

    @Test
    @WithMockUser(username = "test-user-123")
    @DisplayName("POST generate: usuario no autorizado retorna error")
    void generateRetrospective_usuarioNoAutorizado_retornaError() throws Exception {
        when(rateLimitService.allowRequest(userId)).thenReturn(true);
        when(retrospectiveService.generateRetrospective(eq(sprintId), eq(userId)))
                .thenThrow(new SecurityException("No tienes acceso a este proyecto"));

        mockMvc.perform(post("/api/ai/retrospectives/sprint/{sprintId}/generate", sprintId)
                        .with(csrf()))
                // FASE 16: GlobalExceptionHandler.handleSecurityException() mapea
                // SecurityException a 403 (no a un 5xx) — la aserción original nunca
                // se había ejecutado de verdad porque el ApplicationContext fallaba
                // antes de llegar a correr este test.
                .andExpect(status().isForbidden());

        verify(retrospectiveService).generateRetrospective(sprintId, userId);
    }

    @Test
    @WithMockUser(username = "test-user-123")
    @DisplayName("POST generate: sprint inexistente retorna error")
    void generateRetrospective_sprintInexistente_retornaError() throws Exception {
        when(rateLimitService.allowRequest(userId)).thenReturn(true);
        when(retrospectiveService.generateRetrospective(eq(sprintId), eq(userId)))
                .thenThrow(new IllegalArgumentException("Sprint no encontrado"));

        mockMvc.perform(post("/api/ai/retrospectives/sprint/{sprintId}/generate", sprintId)
                        .with(csrf()))
                // FASE 16: GlobalExceptionHandler.handleBadRequest() mapea
                // IllegalArgumentException a 400 (no a un 5xx) — misma causa que arriba.
                .andExpect(status().isBadRequest());

        verify(retrospectiveService).generateRetrospective(sprintId, userId);
    }

    @Test
    @WithMockUser(username = "test-user-123")
    @DisplayName("POST generate: primer sprint sin anterior genera retrospectiva válida")
    void generateRetrospective_primerSprint_generaRetrospectiva() throws Exception {
        AIRetrospectiveDto primerSprintRetro = new AIRetrospectiveDto(
                sprintId, 1, "Primer sprint",
                LocalDate.now().minusWeeks(2), LocalDate.now().minusWeeks(1),
                List.of("Primer sprint completado"),
                List.of("Establecer proceso de retrospectiva"),
                List.of(),
                List.of("Definir baseline de métricas"),
                List.of("¿Qué expectativas tenemos?"),
                Instant.now()
        );

        when(rateLimitService.allowRequest(userId)).thenReturn(true);
        when(retrospectiveService.generateRetrospective(eq(sprintId), eq(userId)))
                .thenReturn(primerSprintRetro);

        mockMvc.perform(post("/api/ai/retrospectives/sprint/{sprintId}/generate", sprintId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sprintNumero").value(1))
                .andExpect(jsonPath("$.whatWentWell[0]").value("Primer sprint completado"));

        verify(retrospectiveService).generateRetrospective(sprintId, userId);
    }
}
