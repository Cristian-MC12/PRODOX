// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.ai.AIInsightDto;
import com.mpdia.dto.ai.AISprintReportDto;
import com.mpdia.dto.analytics.SprintMetricsSummaryDto;
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
@DisplayName("AIReportService — pruebas unitarias")
class AIReportServiceTest {

    @Mock SprintRepository sprintRepository;
    @Mock ProjectMemberRepository projectMemberRepository;
    @Mock AgileAnalyticsService analyticsService;
    @Mock AIInsightsService insightsService;
    @Mock GeminiService geminiService;

    @InjectMocks AIReportService service;

    private UUID proyectoId;
    private UUID sprintId;
    private String userId;
    private Sprint sprint;

    @BeforeEach
    void setUp() {
        proyectoId = UUID.randomUUID();
        sprintId = UUID.randomUUID();
        userId = UUID.randomUUID().toString();

        sprint = new Sprint();
        sprint.setId(sprintId);
        sprint.setProyectoId(proyectoId);
        sprint.setNumero(5);
        sprint.setSprintGoal("Implementar módulo de reportes");
        sprint.setEstado("finalizado");
        sprint.setFechaInicio(LocalDate.now().minusWeeks(2));
        sprint.setFechaFin(LocalDate.now().minusWeeks(1));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // VALIDACIÓN DE ACCESO
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("generateReport: sprint inexistente lanza IllegalArgumentException")
    void generateReport_sprintInexistente_lanzaException() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateReport(sprintId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sprint no encontrado");

        verify(sprintRepository).findById(sprintId);
        verifyNoInteractions(projectMemberRepository);
    }

    @Test
    @DisplayName("generateReport: usuario no autorizado lanza SecurityException")
    void generateReport_usuarioNoAutorizado_lanzaSecurityException() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(false);

        assertThatThrownBy(() -> service.generateReport(sprintId, userId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("No tienes acceso a este proyecto");

        verify(projectMemberRepository).existsByProyectoIdAndUserId(proyectoId, userId);
        verifyNoInteractions(analyticsService);
        verifyNoInteractions(geminiService);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GENERACIÓN EXITOSA
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("generateReport: sprint válido con datos genera reporte correctamente")
    void generateReport_sprintValidoConDatos_generaReporteCorrectamente() {
        // Arrange
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);

        Map<String, BigDecimal> metricas = Map.of(
                "Calidad", new BigDecimal("8.5"),
                "Productividad", new BigDecimal("7.2")
        );
        
        SprintMetricsSummaryDto summary = new SprintMetricsSummaryDto(
                sprintId, 5, "Goal", "finalizado",
                LocalDate.now().minusWeeks(2), LocalDate.now().minusWeeks(1),
                14, metricas, 10, true
        );

        when(analyticsService.getSprintMetricsSummary(sprintId)).thenReturn(summary);
        when(insightsService.getProjectInsights(proyectoId, userId)).thenReturn(List.of());

        String geminiResponse = """
                RESUMEN: El sprint 5 completó exitosamente sus objetivos con métricas sólidas.
                HIGHLIGHTS:
                - Calidad superior al promedio (8.5)
                - Productividad estable
                CONCERNS:
                - Ninguno detectado
                RECOMENDACIONES: Continuar con las prácticas actuales del equipo.
                """;
        when(geminiService.generate(anyString())).thenReturn(geminiResponse);

        // Act
        AISprintReportDto result = service.generateReport(sprintId, userId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.sprintId()).isEqualTo(sprintId);
        assertThat(result.sprintNumero()).isEqualTo(5);
        assertThat(result.sprintGoal()).isEqualTo("Implementar módulo de reportes");
        assertThat(result.metricas()).hasSize(2);
        assertThat(result.metricas().get("Calidad")).isEqualByComparingTo("8.5");
        assertThat(result.resumenEjecutivo()).contains("sprint 5");
        assertThat(result.highlights()).hasSize(2);
        assertThat(result.concerns()).hasSize(1);
        assertThat(result.recomendaciones()).contains("prácticas actuales");
        assertThat(result.generatedAt()).isNotNull();

        verify(geminiService).generate(anyString());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DATOS INSUFICIENTES
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("generateReport: sprint sin métricas genera reporte con datos insuficientes")
    void generateReport_sprintSinMetricas_generaReporteConDatosInsuficientes() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);

        SprintMetricsSummaryDto summaryVacio = new SprintMetricsSummaryDto(
                sprintId, 5, "Goal", "finalizado",
                LocalDate.now().minusWeeks(2), LocalDate.now().minusWeeks(1),
                14, Map.of(), 0, false
        );

        when(analyticsService.getSprintMetricsSummary(sprintId)).thenReturn(summaryVacio);
        when(insightsService.getProjectInsights(proyectoId, userId)).thenReturn(List.of());

        String geminiResponse = """
                RESUMEN: Datos insuficientes para análisis completo.
                HIGHLIGHTS:
                CONCERNS:
                RECOMENDACIONES: Registrar métricas para futuros sprints.
                """;
        when(geminiService.generate(anyString())).thenReturn(geminiResponse);

        AISprintReportDto result = service.generateReport(sprintId, userId);

        assertThat(result).isNotNull();
        assertThat(result.metricas()).isEmpty();
        assertThat(result.resumenEjecutivo()).contains("Datos insuficientes");
        assertThat(result.highlights()).isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INSIGHTS EXISTENTES
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("generateReport: incluye insights existentes en el reporte")
    void generateReport_incluyeInsightsExistentes() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);

        SprintMetricsSummaryDto summary = new SprintMetricsSummaryDto(
                sprintId, 5, "Goal", "finalizado",
                LocalDate.now().minusWeeks(2), LocalDate.now().minusWeeks(1),
                14, Map.of("Calidad", BigDecimal.TEN), 5, true
        );

        AIInsightDto insight = new AIInsightDto(
                UUID.randomUUID(), proyectoId, sprintId,
                "TREND", "MEDIUM", "Calidad mejorando",
                "La calidad ha mejorado 15%", List.of(),
                "Continuar enfoque en calidad", "HIGH",
                false, java.time.Instant.now()
        );

        when(analyticsService.getSprintMetricsSummary(sprintId)).thenReturn(summary);
        when(insightsService.getProjectInsights(proyectoId, userId)).thenReturn(List.of(insight));

        String geminiResponse = """
                RESUMEN: Sprint con insights relevantes detectados.
                HIGHLIGHTS:
                - Calidad en tendencia positiva
                CONCERNS:
                RECOMENDACIONES: Mantener el enfoque.
                """;
        when(geminiService.generate(anyString())).thenReturn(geminiResponse);

        AISprintReportDto result = service.generateReport(sprintId, userId);

        assertThat(result.insights()).hasSize(1);
        assertThat(result.insights().get(0).title()).isEqualTo("Calidad mejorando");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MANEJO DE ERRORES
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("generateReport: error en Gemini genera reporte con mensaje de error")
    void generateReport_errorGemini_generaReporteConError() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);

        SprintMetricsSummaryDto summary = new SprintMetricsSummaryDto(
                sprintId, 5, "Goal", "finalizado",
                LocalDate.now().minusWeeks(2), LocalDate.now().minusWeeks(1),
                14, Map.of("Calidad", BigDecimal.TEN), 5, true
        );

        when(analyticsService.getSprintMetricsSummary(sprintId)).thenReturn(summary);
        when(insightsService.getProjectInsights(proyectoId, userId)).thenReturn(List.of());
        when(geminiService.generate(anyString())).thenReturn("RESPUESTA INVALIDA SIN FORMATO");

        AISprintReportDto result = service.generateReport(sprintId, userId);

        assertThat(result).isNotNull();
        // El parseo defensivo retorna defaults cuando no encuentra las secciones
        assertThat(result.resumenEjecutivo()).isNotNull();
        assertThat(result.recomendaciones()).isNotNull();
    }

    @Test
    @DisplayName("generateReport: error obteniendo métricas no bloquea generación")
    void generateReport_errorMetricas_noBloquea() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        when(analyticsService.getSprintMetricsSummary(sprintId)).thenThrow(new RuntimeException("DB error"));
        when(insightsService.getProjectInsights(proyectoId, userId)).thenReturn(List.of());

        String geminiResponse = """
                RESUMEN: No hay métricas disponibles para este sprint.
                HIGHLIGHTS:
                CONCERNS:
                RECOMENDACIONES: Verificar la configuración de métricas.
                """;
        when(geminiService.generate(anyString())).thenReturn(geminiResponse);

        AISprintReportDto result = service.generateReport(sprintId, userId);

        assertThat(result).isNotNull();
        assertThat(result.metricas()).isEmpty();
    }
}
