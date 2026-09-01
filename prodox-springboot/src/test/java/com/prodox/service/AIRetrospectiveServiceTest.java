// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.prodox.dto.ai.AIInsightDto;
import com.prodox.dto.ai.AIRetrospectiveDto;
import com.prodox.dto.analytics.*;
import com.prodox.entity.ProjectMember;
import com.prodox.entity.Sprint;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.repository.SprintRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
        when(projectMemberRepository.findByProyectoIdAndUserId(proyectoId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateRetrospective(sprintId, userId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("No tienes acceso a este proyecto");

        verify(projectMemberRepository).findByProyectoIdAndUserId(proyectoId, userId);
        verifyNoInteractions(analyticsService);
        verifyNoInteractions(geminiService);
    }

    @Test
    @DisplayName("generateRetrospective: miembro normal (no Scrum Master) lanza SecurityException")
    void generateRetrospective_miembroNormalNoScrumMaster_lanzaSecurityException() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.findByProyectoIdAndUserId(proyectoId, userId)).thenReturn(Optional.of(miembro()));

        assertThatThrownBy(() -> service.generateRetrospective(sprintId, userId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Solo el Scrum Master");

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
        when(projectMemberRepository.findByProyectoIdAndUserId(proyectoId, userId)).thenReturn(Optional.of(scrumMaster()));
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
        when(projectMemberRepository.findByProyectoIdAndUserId(proyectoId, userId)).thenReturn(Optional.of(scrumMaster()));
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
        // FASE 12.8: el orden correcto es (anterior, actual).
        when(analyticsService.compareSprints(previousSprintId, sprintId)).thenReturn(comparison);
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
        verify(analyticsService).compareSprints(previousSprintId, sprintId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INSIGHTS Y RIESGOS
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("generateRetrospective: incluye insights y riesgos identificados")
    void generateRetrospective_incluyeInsightsYRiesgos() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.findByProyectoIdAndUserId(proyectoId, userId)).thenReturn(Optional.of(scrumMaster()));
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
        when(projectMemberRepository.findByProyectoIdAndUserId(proyectoId, userId)).thenReturn(Optional.of(scrumMaster()));
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
    // MANEJO DE ERRORES (FASE 24 — corrección del HTTP 500 opaco confirmado
    // en vivo: mismo defecto que Reportes tenía antes de FASE 23)
    // ═══════════════════════════════════════════════════════════════════════

    private SprintMetricsSummaryDto summaryConDatos() {
        return new SprintMetricsSummaryDto(
                sprintId, 5, "Goal", "finalizado",
                LocalDate.now().minusWeeks(2), LocalDate.now().minusWeeks(1),
                14, Map.of("Calidad", BigDecimal.TEN), 10, true
        );
    }

    private void mockDatosBasicos() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.findByProyectoIdAndUserId(proyectoId, userId)).thenReturn(Optional.of(scrumMaster()));
        when(sprintRepository.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint));
        when(analyticsService.getSprintMetricsSummary(sprintId)).thenReturn(summaryConDatos());
        when(insightsService.getProjectInsights(proyectoId, userId)).thenReturn(List.of());
        when(analyticsService.identifyRisks(proyectoId)).thenReturn(List.of());
    }

    @Test
    @DisplayName("generateRetrospective: Gemini 429 (cuota agotada) -> RetrospectivaIANoDisponibleException, NO HTTP 500 opaco")
    void generateRetrospective_gemini429_lanzaRetrospectivaIANoDisponible() {
        mockDatosBasicos();
        when(geminiService.generate(anyString()))
                .thenThrow(new RuntimeException("Gemini error 429 TOO_MANY_REQUESTS: quota exceeded"));

        assertThatThrownBy(() -> service.generateRetrospective(sprintId, userId))
                .isInstanceOf(RetrospectivaIANoDisponibleException.class)
                .hasMessageNotContaining("500")
                .hasMessageContaining("Intenta nuevamente")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("generateRetrospective: Gemini 503 (servicio no disponible) -> RetrospectivaIANoDisponibleException")
    void generateRetrospective_gemini503_lanzaRetrospectivaIANoDisponible() {
        mockDatosBasicos();
        when(geminiService.generate(anyString()))
                .thenThrow(new RuntimeException("Gemini error 503 SERVICE_UNAVAILABLE: overloaded"));

        assertThatThrownBy(() -> service.generateRetrospective(sprintId, userId))
                .isInstanceOf(RetrospectivaIANoDisponibleException.class);
    }

    @Test
    @DisplayName("generateRetrospective: timeout/error inesperado de Gemini -> RetrospectivaIANoDisponibleException")
    void generateRetrospective_timeoutInesperado_lanzaRetrospectivaIANoDisponible() {
        mockDatosBasicos();
        when(geminiService.generate(anyString()))
                .thenThrow(new RuntimeException("Error al llamar Gemini: Read timed out"));

        assertThatThrownBy(() -> service.generateRetrospective(sprintId, userId))
                .isInstanceOf(RetrospectivaIANoDisponibleException.class);
    }

    @Test
    @DisplayName("generateRetrospective: respuesta sin NINGÚN marcador reconocible -> RetrospectivaIANoDisponibleException, no fabrica una retrospectiva falsa")
    void generateRetrospective_respuestaSinMarcadoresReconocibles_lanzaRetrospectivaIANoDisponible() {
        mockDatosBasicos();
        when(geminiService.generate(anyString())).thenReturn("RESPUESTA INVALIDA SIN FORMATO");

        assertThatThrownBy(() -> service.generateRetrospective(sprintId, userId))
                .isInstanceOf(RetrospectivaIANoDisponibleException.class)
                .hasMessageContaining("no se pudo interpretar");
    }

    @Test
    @DisplayName("generateRetrospective: fallo de Gemini no persiste ni retorna ninguna retrospectiva (falsa o real)")
    void generateRetrospective_fallo_noPersisteNiRetornaNada() {
        mockDatosBasicos();
        when(geminiService.generate(anyString()))
                .thenThrow(new RuntimeException("Gemini error 429 TOO_MANY_REQUESTS"));

        assertThatThrownBy(() -> service.generateRetrospective(sprintId, userId))
                .isInstanceOf(RetrospectivaIANoDisponibleException.class);
        // AIRetrospectiveService nunca persiste (no hay repositorio de retrospectivas):
        // el único requisito verificable es que ninguna excepción distinta a la
        // dedicada escape y que no exista ningún otro efecto secundario además
        // de la llamada a Gemini ya verificada arriba.
    }

    @Test
    @DisplayName("generateRetrospective: reintento tras fallo de Gemini funciona correctamente")
    void generateRetrospective_reintentoTrasFallo_funcionaCorrectamente() {
        mockDatosBasicos();

        String geminiResponseValido = """
                WHAT WENT WELL:
                - Sprint completado dentro del tiempo
                WHAT COULD IMPROVE:
                - Mejorar tracking
                RISKS:
                RECOMMENDATIONS:
                - Continuar con las prácticas actuales
                QUESTIONS FOR TEAM:
                - ¿Qué aprendimos este sprint?
                """;

        when(geminiService.generate(anyString()))
                .thenThrow(new RuntimeException("Gemini error 429 TOO_MANY_REQUESTS"))
                .thenReturn(geminiResponseValido);

        // Primer intento: falla de forma controlada
        assertThatThrownBy(() -> service.generateRetrospective(sprintId, userId))
                .isInstanceOf(RetrospectivaIANoDisponibleException.class);

        // Segundo intento (reintento del usuario): funciona con normalidad,
        // el servicio no arrastra estado del intento fallido (es stateless).
        AIRetrospectiveDto result = service.generateRetrospective(sprintId, userId);
        assertThat(result).isNotNull();
        assertThat(result.whatWentWell()).anyMatch(item -> item.contains("Sprint completado"));

        verify(geminiService, times(2)).generate(anyString());
    }

    @Test
    @DisplayName("generateRetrospective: sección con marcador presente pero vacío (datos insuficientes real) sigue siendo exitosa, no se confunde con fallo de IA")
    void generateRetrospective_marcadoresPresentesPeroVacios_sigueSiendoExitosa() {
        mockDatosBasicos();
        when(geminiService.generate(anyString())).thenReturn("""
                WHAT WENT WELL:
                WHAT COULD IMPROVE:
                RISKS:
                RECOMMENDATIONS:
                QUESTIONS FOR TEAM:
                """);

        AIRetrospectiveDto result = service.generateRetrospective(sprintId, userId);

        assertThat(result).isNotNull();
        assertThat(result.whatWentWell().get(0)).contains("Datos insuficientes");
    }

    @Test
    @DisplayName("generateRetrospective: error en comparación no bloquea generación")
    void generateRetrospective_errorComparacion_noBloquea() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.findByProyectoIdAndUserId(proyectoId, userId)).thenReturn(Optional.of(scrumMaster()));
        when(sprintRepository.findByProyectoIdOrderByNumeroDesc(proyectoId))
                .thenReturn(List.of(sprint, previousSprint));

        SprintMetricsSummaryDto summary = new SprintMetricsSummaryDto(
                sprintId, 5, "Goal", "finalizado",
                LocalDate.now().minusWeeks(2), LocalDate.now().minusWeeks(1),
                14, Map.of("Calidad", BigDecimal.TEN), 10, true
        );

        when(analyticsService.getSprintMetricsSummary(sprintId)).thenReturn(summary);
        // FASE 12.8: el orden correcto es (anterior, actual).
        when(analyticsService.compareSprints(previousSprintId, sprintId))
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
        verify(analyticsService).compareSprints(previousSprintId, sprintId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FASE 12.8 — Corrección de dirección temporal en compareSprints()
    // Antes: compareSprints(sprintId, previousSprintId) → actual→anterior (invertido).
    // Ahora: compareSprints(previousSprintId, sprintId) → anterior→actual (correcto).
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("FASE 12.8: anterior=4, actual=3 → variación -25.00% DOWN, orden anterior→actual")
    void generateRetrospective_anterior4Actual3_variacionMenos25PorcientoDown() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.findByProyectoIdAndUserId(proyectoId, userId)).thenReturn(Optional.of(scrumMaster()));
        when(sprintRepository.findByProyectoIdOrderByNumeroDesc(proyectoId))
                .thenReturn(List.of(sprint, previousSprint));

        SprintMetricsSummaryDto summary = new SprintMetricsSummaryDto(
                sprintId, 5, "Goal", "finalizado",
                LocalDate.now().minusWeeks(2), LocalDate.now().minusWeeks(1),
                14, Map.of("Significado", new BigDecimal("3.00")), 10, true
        );

        // Refleja exactamente lo que AgileAnalyticsService.compareSprints(anterior, actual)
        // calcularía para anterior=4.00, actual=3.00: (3-4)/4*100 = -25.00%.
        SprintComparisonDto comparison = new SprintComparisonDto(
                previousSprintId, 4, sprintId, 5,
                Map.of("Significado", new BigDecimal("4.00")),
                Map.of("Significado", new BigDecimal("3.00")),
                Map.of("Significado", new BigDecimal("-1.00")),
                Map.of("Significado", new BigDecimal("-25.00")),
                Map.of("Significado", "DOWN"),
                true
        );

        when(analyticsService.getSprintMetricsSummary(sprintId)).thenReturn(summary);
        when(analyticsService.compareSprints(previousSprintId, sprintId)).thenReturn(comparison);
        when(insightsService.getProjectInsights(proyectoId, userId)).thenReturn(List.of());
        when(analyticsService.identifyRisks(proyectoId)).thenReturn(List.of());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        when(geminiService.generate(promptCaptor.capture())).thenReturn("""
                WHAT WENT WELL:
                - N/A
                WHAT COULD IMPROVE:
                - N/A
                RISKS:
                RECOMMENDATIONS:
                - N/A
                QUESTIONS FOR TEAM:
                - N/A
                """);

        service.generateRetrospective(sprintId, userId);

        // Verifica explícitamente el orden correcto de argumentos (anterior, actual) y que
        // NUNCA se llame con el orden invertido (actual, anterior) que tenía el bug.
        verify(analyticsService).compareSprints(previousSprintId, sprintId);
        verify(analyticsService, never()).compareSprints(sprintId, previousSprintId);

        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("-25.00");
        assertThat(prompt).contains("DOWN");
    }

    @Test
    @DisplayName("FASE 12.8: anterior=3, actual=4 → variación +33.33% UP, orden anterior→actual")
    void generateRetrospective_anterior3Actual4_variacionMas33_33PorcientoUp() {
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepository.findByProyectoIdAndUserId(proyectoId, userId)).thenReturn(Optional.of(scrumMaster()));
        when(sprintRepository.findByProyectoIdOrderByNumeroDesc(proyectoId))
                .thenReturn(List.of(sprint, previousSprint));

        SprintMetricsSummaryDto summary = new SprintMetricsSummaryDto(
                sprintId, 5, "Goal", "finalizado",
                LocalDate.now().minusWeeks(2), LocalDate.now().minusWeeks(1),
                14, Map.of("Significado", new BigDecimal("4.00")), 10, true
        );

        // anterior=3.00, actual=4.00: (4-3)/3*100 = +33.33%.
        SprintComparisonDto comparison = new SprintComparisonDto(
                previousSprintId, 4, sprintId, 5,
                Map.of("Significado", new BigDecimal("3.00")),
                Map.of("Significado", new BigDecimal("4.00")),
                Map.of("Significado", new BigDecimal("1.00")),
                Map.of("Significado", new BigDecimal("33.33")),
                Map.of("Significado", "UP"),
                true
        );

        when(analyticsService.getSprintMetricsSummary(sprintId)).thenReturn(summary);
        when(analyticsService.compareSprints(previousSprintId, sprintId)).thenReturn(comparison);
        when(insightsService.getProjectInsights(proyectoId, userId)).thenReturn(List.of());
        when(analyticsService.identifyRisks(proyectoId)).thenReturn(List.of());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        when(geminiService.generate(promptCaptor.capture())).thenReturn("""
                WHAT WENT WELL:
                - N/A
                WHAT COULD IMPROVE:
                - N/A
                RISKS:
                RECOMMENDATIONS:
                - N/A
                QUESTIONS FOR TEAM:
                - N/A
                """);

        service.generateRetrospective(sprintId, userId);

        verify(analyticsService).compareSprints(previousSprintId, sprintId);
        verify(analyticsService, never()).compareSprints(sprintId, previousSprintId);

        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("33.33");
        assertThat(prompt).contains("UP");
    }

    private ProjectMember scrumMaster() {
        ProjectMember m = new ProjectMember();
        m.setProyectoId(proyectoId);
        m.setUserId(userId);
        m.setRol("scrum_master");
        return m;
    }

    private ProjectMember miembro() {
        ProjectMember m = new ProjectMember();
        m.setProyectoId(proyectoId);
        m.setUserId(userId);
        m.setRol("scrum_member");
        return m;
    }
}
