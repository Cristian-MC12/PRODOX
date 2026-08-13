// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.ai.AIInsightDto;
import com.mpdia.dto.ai.AIRetrospectiveDto;
import com.mpdia.dto.analytics.*;
import com.mpdia.entity.Sprint;
import com.mpdia.repository.ProjectMemberRepository;
import com.mpdia.repository.SprintRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AIRetrospectiveService — pruebas unitarias")
class AIRetrospectiveServiceTest {

    @Mock SprintRepository sprintRepository;
    @Mock ProjectMemberRepository projectMemberRepository;
    @Mock AgileAnalyticsService analyticsService;
    @Mock AIInsightsService insightsService;
    @Mock GeminiService geminiService;

    @InjectMocks AIRetrospectiveService service;

    private UUID proyectoId;
    private UUID sprintId;
    private UUID previousSprintId;
    private String userId;
    private Sprint sprint;
    private Sprint previousSprint;

    @BeforeEach
    void setUp() {
        proyectoId = UUID.randomUUID();
        sprintId = UUID.randomUUID();
        previousSprintId = UUID.randomUUID();
        userId = UUID.randomUUID().toString();

        sprint = new Sprint();
        sprint.setId(sprintId);
        sprint.setProyectoId(proyectoId);
        sprint.setNumero(5);
        sprint.setSprintGoal("Implementar retrospectivas");
        sprint.setEstado("finalizado");
        sprint.setFechaInicio(LocalDate.now().minusWeeks(2));
        sprint.setFechaFin(LocalDate.now().minusWeeks(1));

        previousSprint = new Sprint();
        previousSprint.setId(previousSprintId);
        previousSprint.setProyectoId(proyectoId);
        previousSprint.setNumero(4);
        previousSprint.setEstado("finalizado");
        previousSprint.setFechaInicio(LocalDate.now().minusWeeks(4));
        previousSprint.setFechaFin(LocalDate.now().minusWeeks(3));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // VALIDACIÓN DE ACCESO
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("generateRetrospective: sprint inexistente lanza IllegalArgumentException")
    void generateRetrospective_sprintInexistente_lanzaException() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateRetrospective(sprintId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sprint no encontrado");

        verify(sprintRepository).findById(sprintId);
        verifyNoInteractions(projectMemberRepository);
    }

    @Test
    @DisplayName("generateRetrospective: usuario no autorizado lanza SecurityException")
    void generateRetrospective_usuarioNoAutorizado_lanzaSecurityException() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(false);

        assertThatThrownBy(() -> service.generateRetrospective(sprintId, userId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("No tienes acceso a este proyecto");

        verify(projectMemberRepository).existsByProyectoIdAndUserId(proyectoId, userId);
        verifyNoInteractions(analyticsService);
        verifyNoInteractions(geminiService);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PRIMER SPRINT SIN ANTERIOR
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("generateRetrospective: primer sprint sin anterior genera retrospectiva válida")
    void generateRetrospective_primerSprintSinAnterior_generaRetrospectiva() {
        // Arrange
        sprint.setNumero(1);
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        when(sprintRepository.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint));

        SprintMetricsSummaryDto summary = new SprintMetricsSummaryDto(
                sprintId, 1, "Goal", "finalizado",
                LocalDate.now().minusWeeks(2), LocalDate.now().minusWeeks(1),
                14, Map.of("Calidad", BigDecimal.TEN), 5, true
        );

        when(analyticsService.getSprintMetricsSummary(sprintId)).thenReturn(summary);
        when(insightsService.getProjectInsights(proyectoId, userId)).thenReturn(List.of());
        when(analyticsService.identifyRisks(proyectoId)).thenReturn(List.of());

        String geminiResponse = """
                WHAT WENT WELL:
                - Primer sprint completado exitosamente
                
                WHAT COULD IMPROVE:
                - Establecer proceso de retrospectiva regular
                
                RISKS:
                
                RECOMMENDATIONS:
                - Definir baseline de métricas
                
                QUESTIONS FOR TEAM:
                - ¿Qué expectativas tenemos para el próximo sprint?
                """;
        when(geminiService.generate(anyString())).thenReturn(geminiResponse);

        // Act
        AIRetrospectiveDto result = service.generateRetrospective(sprintId, userId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.sprintId()).isEqualTo(sprintId);
        assertThat(result.sprintNumero()).isEqualTo(1);
        assertThat(result.whatWentWell()).isNotEmpty();
        assertThat(result.whatCouldImprove()).isNotEmpty();
        assertThat(result.questionsForTeam()).isNotEmpty();
        assertThat(result.generatedAt()).isNotNull();

        verify(geminiService).generate(contains("primer sprint"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SPRINT CON ANTERIOR - COMPARACIÓN
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("generateRetrospective: sprint con anterior incluye comparación")
    void generateRetrospective_sprintConAnterior_incluyeComparacion() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        when(sprintRepository.findByProyectoIdOrderByNumeroDesc(proyectoId))
                .thenReturn(List.of(sprint, previousSprint));

        SprintMetricsSummaryDto summary = new SprintMetricsSummaryDto(
                sprintId, 5, "Goal", "finalizado",
                LocalDate.now().minusWeeks(2), LocalDate.now().minusWeeks(1),
                14, Map.of("Calidad", new BigDecimal("9.0")), 10, true
        );

        SprintComparisonDto comparison = new SprintComparisonDto(
                sprintId, 5, previousSprintId, 4,
                Map.of("Calidad", new BigDecimal("9.0")),
                Map.of("Calidad", new BigDecimal("8.0")),
                Map.of("Calidad", new BigDecimal("1.0")),
                Map.of("Calidad", new BigDecimal("12.5")),
                Map.of("Calidad", "UP"),
                true
        );

        when(analyticsService.getSprintMetricsSummary(sprintId)).thenReturn(summary);
        when(analyticsService.compareSprints(sprintId, previousSprintId)).thenReturn(comparison);
        when(insightsService.getProjectInsights(proyectoId, userId)).thenReturn(List.of());
        when(analyticsService.identifyRisks(proyectoId)).thenReturn(List.of());

        String geminiResponse = """
                WHAT WENT WELL:
                - Calidad mejoró 12.5% respecto al sprint anterior
                
                WHAT COULD IMPROVE:
                - Mantener la consistencia en la calidad
                
                RISKS:
                
                RECOMMENDATIONS:
                - Documentar las prácticas que mejoraron la calidad
                
                QUESTIONS FOR TEAM:
                - ¿Qué cambios en el proceso contribuyeron a la mejora?
                """;
        when(geminiService.generate(anyString())).thenReturn(geminiResponse);

        AIRetrospectiveDto result = service.generateRetrospective(sprintId, userId);

        assertThat(result).isNotNull();
        assertThat(result.whatWentWell()).anyMatch(item -> item.contains("12.5%"));
        verify(analyticsService).compareSprints(sprintId, previousSprintId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INSIGHTS Y RIESGOS
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("generateRetrospective: incluye insights y riesgos identificados")
    void generateRetrospective_incluyeInsightsYRiesgos() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        when(sprintRepository.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint));

        SprintMetricsSummaryDto summary = new SprintMetricsSummaryDto(
                sprintId, 5, "Goal", "finalizado",
                LocalDate.now().minusWeeks(2), LocalDate.now().minusWeeks(1),
                14, Map.of("Productividad", BigDecimal.TEN), 10, true
        );

        AIInsightDto insight = new AIInsightDto(
                UUID.randomUUID(), proyectoId, sprintId,
                "ANOMALY", "HIGH", "Anomalía en productividad",
                "Caída repentina detectada", List.of(),
                "Investigar causa raíz", "HIGH",
                false, java.time.Instant.now()
        );

        RiskDto risk = new RiskDto(
                proyectoId, "DECLINING_METRIC", "MEDIUM",
                "Productividad en descenso",
                "Disminución de 15% en 3 sprints",
                "Productividad",
                java.time.Instant.now()
        );

        when(analyticsService.getSprintMetricsSummary(sprintId)).thenReturn(summary);
        when(insightsService.getProjectInsights(proyectoId, userId)).thenReturn(List.of(insight));
        when(analyticsService.identifyRisks(proyectoId)).thenReturn(List.of(risk));

        String geminiResponse = """
                WHAT WENT WELL:
                - Sprint completado dentro del tiempo
                
                WHAT COULD IMPROVE:
                - Investigar anomalía en productividad detectada
                
                RISKS:
                - Tendencia descendente en productividad
                
                RECOMMENDATIONS:
                - Realizar análisis de causa raíz
                
                QUESTIONS FOR TEAM:
                - ¿Qué obstáculos afectaron la productividad?
                """;
        when(geminiService.generate(anyString())).thenReturn(geminiResponse);

        AIRetrospectiveDto result = service.generateRetrospective(sprintId, userId);

        assertThat(result.risks()).isNotEmpty();
        assertThat(result.whatCouldImprove()).anyMatch(item -> item.contains("productividad"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DATOS INSUFICIENTES
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("generateRetrospective: datos insuficientes genera retrospectiva con defaults")
    void generateRetrospective_datosInsuficientes_generaRetrospectiva() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        when(sprintRepository.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint));

        SprintMetricsSummaryDto summaryVacio = new SprintMetricsSummaryDto(
                sprintId, 5, "Goal", "finalizado",
                LocalDate.now().minusWeeks(2), LocalDate.now().minusWeeks(1),
                14, Map.of(), 0, false
        );

        when(analyticsService.getSprintMetricsSummary(sprintId)).thenReturn(summaryVacio);
        when(insightsService.getProjectInsights(proyectoId, userId)).thenReturn(List.of());
        when(analyticsService.identifyRisks(proyectoId)).thenReturn(List.of());

        String geminiResponse = """
                WHAT WENT WELL:
                
                WHAT COULD IMPROVE:
                
                RISKS:
                
                RECOMMENDATIONS:
                
                QUESTIONS FOR TEAM:
                """;
        when(geminiService.generate(anyString())).thenReturn(geminiResponse);

        AIRetrospectiveDto result = service.generateRetrospective(sprintId, userId);

        assertThat(result).isNotNull();
        assertThat(result.whatWentWell().get(0)).contains("Datos insuficientes");
        assertThat(result.whatCouldImprove().get(0)).contains("registrar más métricas");
        assertThat(result.questionsForTeam()).isNotEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MANEJO DE ERRORES
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("generateRetrospective: error en Gemini genera retrospectiva con fallback")
    void generateRetrospective_errorGemini_generaFallback() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        when(sprintRepository.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint));

        SprintMetricsSummaryDto summary = new SprintMetricsSummaryDto(
                sprintId, 5, "Goal", "finalizado",
                LocalDate.now().minusWeeks(2), LocalDate.now().minusWeeks(1),
                14, Map.of("Calidad", BigDecimal.TEN), 10, true
        );

        when(analyticsService.getSprintMetricsSummary(sprintId)).thenReturn(summary);
        when(insightsService.getProjectInsights(proyectoId, userId)).thenReturn(List.of());
        when(analyticsService.identifyRisks(proyectoId)).thenReturn(List.of());
        when(geminiService.generate(anyString())).thenReturn("RESPUESTA INVALIDA");

        AIRetrospectiveDto result = service.generateRetrospective(sprintId, userId);

        assertThat(result).isNotNull();
        // El parseo defensivo retorna defaults cuando no encuentra las secciones
        assertThat(result.whatWentWell()).isNotEmpty();
        assertThat(result.recommendations()).isNotEmpty();
    }

    @Test
    @DisplayName("generateRetrospective: error en comparación no bloquea generación")
    void generateRetrospective_errorComparacion_noBloquea() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        when(sprintRepository.findByProyectoIdOrderByNumeroDesc(proyectoId))
                .thenReturn(List.of(sprint, previousSprint));

        SprintMetricsSummaryDto summary = new SprintMetricsSummaryDto(
                sprintId, 5, "Goal", "finalizado",
                LocalDate.now().minusWeeks(2), LocalDate.now().minusWeeks(1),
                14, Map.of("Calidad", BigDecimal.TEN), 10, true
        );

        when(analyticsService.getSprintMetricsSummary(sprintId)).thenReturn(summary);
        when(analyticsService.compareSprints(sprintId, previousSprintId))
                .thenThrow(new RuntimeException("Comparison error"));
        when(insightsService.getProjectInsights(proyectoId, userId)).thenReturn(List.of());
        when(analyticsService.identifyRisks(proyectoId)).thenReturn(List.of());

        String geminiResponse = """
                WHAT WENT WELL:
                - Sprint completado
                WHAT COULD IMPROVE:
                - Mejorar tracking
                RISKS:
                RECOMMENDATIONS:
                - Continuar
                QUESTIONS FOR TEAM:
                - ¿Cómo mejorar?
                """;
        when(geminiService.generate(anyString())).thenReturn(geminiResponse);

        AIRetrospectiveDto result = service.generateRetrospective(sprintId, userId);

        assertThat(result).isNotNull();
        verify(analyticsService).compareSprints(sprintId, previousSprintId);
    }
}
