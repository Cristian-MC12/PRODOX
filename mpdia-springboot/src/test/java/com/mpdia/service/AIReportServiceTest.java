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
    // MANEJO DE ERRORES (FASE 23 — corrección del HTTP 500 opaco de FASE 22)
    // ═══════════════════════════════════════════════════════════════════════

    private SprintMetricsSummaryDto summaryConDatos() {
        return new SprintMetricsSummaryDto(
                sprintId, 5, "Goal", "finalizado",
                LocalDate.now().minusWeeks(2), LocalDate.now().minusWeeks(1),
                14, Map.of("Calidad", BigDecimal.TEN), 5, true
        );
    }

    @Test
    @DisplayName("generateReport: Gemini lanza excepción (ej. cuota agotada) -> ReporteIANoDisponibleException, NO HTTP 500 opaco")
    void generateReport_geminiLanzaExcepcion_lanzaReporteIANoDisponible() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        when(analyticsService.getSprintMetricsSummary(sprintId)).thenReturn(summaryConDatos());
        when(insightsService.getProjectInsights(proyectoId, userId)).thenReturn(List.of());
        when(geminiService.generate(anyString()))
                .thenThrow(new RuntimeException("Gemini error 429 TOO_MANY_REQUESTS: quota exceeded"));

        assertThatThrownBy(() -> service.generateReport(sprintId, userId))
                .isInstanceOf(ReporteIANoDisponibleException.class)
                .hasMessageNotContaining("500")
                .hasMessageContaining("Intenta nuevamente");
    }

    @Test
    @DisplayName("generateReport: respuesta vacía/inválida de Gemini (sin ninguna sección reconocible) -> ReporteIANoDisponibleException, no fabrica un reporte falso")
    void generateReport_respuestaGeminiSinSeccionesReconocibles_lanzaReporteIANoDisponible() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        when(analyticsService.getSprintMetricsSummary(sprintId)).thenReturn(summaryConDatos());
        when(insightsService.getProjectInsights(proyectoId, userId)).thenReturn(List.of());
        when(geminiService.generate(anyString())).thenReturn("RESPUESTA INVALIDA SIN FORMATO");

        assertThatThrownBy(() -> service.generateReport(sprintId, userId))
                .isInstanceOf(ReporteIANoDisponibleException.class)
                .hasMessageContaining("no se pudo interpretar");
    }

    @Test
    @DisplayName("generateReport: sección RESUMEN presente diciendo 'datos insuficientes' sigue siendo un 200 legítimo (no se confunde con fallo de IA)")
    void generateReport_resumenPresenteConDatosInsuficientes_siguSiendoExitoso() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        when(analyticsService.getSprintMetricsSummary(sprintId)).thenReturn(summaryConDatos());
        when(insightsService.getProjectInsights(proyectoId, userId)).thenReturn(List.of());
        when(geminiService.generate(anyString())).thenReturn("""
                RESUMEN: Datos insuficientes para un análisis completo, pero el sprint se ejecutó.
                """);

        AISprintReportDto result = service.generateReport(sprintId, userId);

        assertThat(result).isNotNull();
        assertThat(result.resumenEjecutivo()).contains("Datos insuficientes");
    }

    @Test
    @DisplayName("generateReport: reintento tras fallo de Gemini funciona correctamente (sin datos corruptos persistidos)")
    void generateReport_reintentoTrasFallo_funcionaCorrectamente() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        when(analyticsService.getSprintMetricsSummary(sprintId)).thenReturn(summaryConDatos());
        when(insightsService.getProjectInsights(proyectoId, userId)).thenReturn(List.of());

        String geminiResponseValido = """
                RESUMEN: El sprint 5 completó exitosamente sus objetivos.
                HIGHLIGHTS:
                - Calidad superior al promedio
                CONCERNS:
                RECOMENDACIONES: Continuar con las prácticas actuales.
                """;

        when(geminiService.generate(anyString()))
                .thenThrow(new RuntimeException("Gemini error 429 TOO_MANY_REQUESTS"))
                .thenReturn(geminiResponseValido);

        // Primer intento: falla de forma controlada
        assertThatThrownBy(() -> service.generateReport(sprintId, userId))
                .isInstanceOf(ReporteIANoDisponibleException.class);

        // Segundo intento (reintento del usuario): funciona con normalidad,
        // el servicio no arrastra estado del intento fallido (es stateless).
        AISprintReportDto result = service.generateReport(sprintId, userId);
        assertThat(result).isNotNull();
        assertThat(result.resumenEjecutivo()).contains("sprint 5");

        verify(geminiService, times(2)).generate(anyString());
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
