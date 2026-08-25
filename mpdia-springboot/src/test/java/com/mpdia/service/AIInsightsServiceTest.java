// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpdia.dto.ai.AIInsightDto;
import com.mpdia.dto.ai.GenerateInsightsResultDto;
import com.mpdia.dto.analytics.*;
import com.mpdia.entity.AIInsight;
import com.mpdia.entity.Proyecto;
import com.mpdia.entity.Sprint;
import com.mpdia.repository.AIInsightRepository;
import com.mpdia.repository.ProjectMemberRepository;
import com.mpdia.repository.ProyectoRepository;
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
@DisplayName("AIInsightsService — pruebas unitarias")
class AIInsightsServiceTest {

    @Mock AgileAnalyticsService analyticsService;
    @Mock GeminiService geminiService;
    @Mock AIInsightRepository insightRepo;
    @Mock ProyectoRepository proyectoRepo;
    @Mock SprintRepository sprintRepo;
    @Mock ProjectMemberRepository projectMemberRepo;
    @Mock ObjectMapper objectMapper;

    @InjectMocks AIInsightsService service;

    private UUID proyectoId;
    private UUID sprintId;
    private String userId;
    private Proyecto proyecto;
    private Sprint sprint;

    @BeforeEach
    void setUp() {
        proyectoId = UUID.randomUUID();
        sprintId = UUID.randomUUID();
        userId = UUID.randomUUID().toString();

        proyecto = new Proyecto();
        proyecto.setId(proyectoId);
        proyecto.setNombre("Proyecto Test");
        proyecto.setMetodo("scrum");

        sprint = new Sprint();
        sprint.setId(sprintId);
        sprint.setProyectoId(proyectoId);
        sprint.setNumero(3);
        sprint.setEstado("finalizado");
        sprint.setFechaInicio(LocalDate.now().minusWeeks(2));
        sprint.setFechaFin(LocalDate.now().minusWeeks(1));
    }

    // ── Validación de acceso ─────────────────────────────────────────────

    @Test
    @DisplayName("getProjectInsights: usuario no autorizado lanza SecurityException")
    void getProjectInsights_usuarioNoAutorizado_lanzaSecurityException() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(false);

        assertThatThrownBy(() -> service.getProjectInsights(proyectoId, userId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("No tienes acceso a este proyecto");

        verify(projectMemberRepo).existsByProyectoIdAndUserId(proyectoId, userId);
        verifyNoInteractions(insightRepo);
    }

    @Test
    @DisplayName("generateInsights: usuario no autorizado lanza SecurityException")
    void generateInsights_usuarioNoAutorizado_lanzaSecurityException() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(false);

        assertThatThrownBy(() -> service.generateInsights(proyectoId, userId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("No tienes acceso a este proyecto");

        verify(projectMemberRepo).existsByProyectoIdAndUserId(proyectoId, userId);
        verifyNoInteractions(analyticsService);
        verifyNoInteractions(geminiService);
    }

    // ── Datos insuficientes ───────────────────────────────────────────────

    @Test
    @DisplayName("generateInsights: proyecto sin sprints finalizados retorna lista vacía")
    void generateInsights_sinSprintsFinalizados_retornaVacio() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of());

        GenerateInsightsResultDto resultado = service.generateInsights(proyectoId, userId);

        assertThat(resultado.insights()).isEmpty();
        assertThat(resultado.status()).isEqualTo("SIN_DATOS");
        verify(analyticsService, never()).getSprintTrends(any(), any(), any());
        verify(geminiService, never()).generate(anyString());
        verify(insightRepo, never()).save(any());
    }

    @Test
    @DisplayName("generateInsights: proyecto con 1 sprint retorna lista vacía")
    void generateInsights_unSprint_retornaVacio() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        
        Sprint sprint1 = new Sprint();
        sprint1.setId(UUID.randomUUID());
        sprint1.setProyectoId(proyectoId);
        sprint1.setNumero(1);
        sprint1.setEstado("finalizado");
        
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint1));
        when(analyticsService.getSprintTrends(any(), any(), any())).thenReturn(List.of());

        GenerateInsightsResultDto resultado = service.generateInsights(proyectoId, userId);

        assertThat(resultado.insights()).isEmpty();
        assertThat(resultado.status()).isEqualTo("SIN_SENALES");
        verify(analyticsService, times(1)).getSprintTrends(eq(proyectoId), isNull(), eq(3));
        verify(geminiService, never()).generate(anyString());
    }

    // ── Consultas ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getProjectInsights: usuario autorizado retorna insights")
    void getProjectInsights_usuarioAutorizado_retornaInsights() throws Exception {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);

        AIInsight insight1 = new AIInsight();
        insight1.setId(UUID.randomUUID());
        insight1.setProyectoId(proyectoId);
        insight1.setTipo("TREND");
        insight1.setTitulo("Tendencia positiva en Calidad");
        insight1.setDescripcion("La calidad ha mejorado un 15%");
        insight1.setSeveridad("LOW");
        insight1.setConfianza("HIGH");
        insight1.setDismissed(false);

        when(insightRepo.findByProyectoIdAndDismissedFalseOrderByCreatedAtDesc(proyectoId))
                .thenReturn(List.of(insight1));

        List<AIInsightDto> resultado = service.getProjectInsights(proyectoId, userId);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).title()).isEqualTo("Tendencia positiva en Calidad");
        assertThat(resultado.get(0).type()).isEqualTo("TREND");
        assertThat(resultado.get(0).severity()).isEqualTo("LOW");
    }

    @Test
    @DisplayName("getProjectInsights: proyecto sin insights retorna lista vacía")
    void getProjectInsights_sinInsights_retornaVacio() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        when(insightRepo.findByProyectoIdAndDismissedFalseOrderByCreatedAtDesc(proyectoId))
                .thenReturn(List.of());

        List<AIInsightDto> resultado = service.getProjectInsights(proyectoId, userId);

        assertThat(resultado).isEmpty();
    }

    // ── Dismiss ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("dismissInsight: usuario autorizado marca insight como descartado")
    void dismissInsight_usuarioAutorizado_marcaComoDismissed() {
        UUID insightId = UUID.randomUUID();
        AIInsight insight = new AIInsight();
        insight.setId(insightId);
        insight.setProyectoId(proyectoId);
        insight.setDismissed(false);

        when(insightRepo.findById(insightId)).thenReturn(Optional.of(insight));
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);

        service.dismissInsight(insightId, userId);

        assertThat(insight.getDismissed()).isTrue();
        assertThat(insight.getDismissedAt()).isNotNull();
        verify(insightRepo).save(insight);
    }

    @Test
    @DisplayName("dismissInsight: insight inexistente lanza IllegalArgumentException")
    void dismissInsight_insightInexistente_lanzaException() {
        UUID insightId = UUID.randomUUID();
        when(insightRepo.findById(insightId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.dismissInsight(insightId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insight no encontrado");

        verify(insightRepo, never()).save(any());
    }

    @Test
    @DisplayName("dismissInsight: usuario no autorizado lanza SecurityException")
    void dismissInsight_usuarioNoAutorizado_lanzaSecurityException() {
        UUID insightId = UUID.randomUUID();
        AIInsight insight = new AIInsight();
        insight.setId(insightId);
        insight.setProyectoId(proyectoId);

        when(insightRepo.findById(insightId)).thenReturn(Optional.of(insight));
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(false);

        assertThatThrownBy(() -> service.dismissInsight(insightId, userId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("No tienes acceso a este proyecto");

        verify(insightRepo, never()).save(any());
    }

    // ── Generación de insights (casos básicos) ────────────────────────────

    @Test
    @DisplayName("generateInsights: proyecto con 2 sprints genera insights de comparación")
    void generateInsights_dosSprintsFinalizados_generaComparacion() throws Exception {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));

        Sprint sprint1 = crearSprint(1, "finalizado");
        Sprint sprint2 = crearSprint(2, "finalizado");

        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId))
                .thenReturn(List.of(sprint2, sprint1));

        // Mock de AgileAnalyticsService
        when(analyticsService.getSprintTrends(eq(proyectoId), isNull(), eq(3)))
                .thenReturn(List.of());

        SprintComparisonDto comparison = new SprintComparisonDto(
                sprint1.getId(), 1,
                sprint2.getId(), 2,
                Map.of("Calidad", new BigDecimal("7.5")),
                Map.of("Calidad", new BigDecimal("8.5")),
                Map.of("Calidad", new BigDecimal("1.0")),
                Map.of("Calidad", new BigDecimal("13.33")),
                Map.of("Calidad", "UP"),
                true
        );

        when(analyticsService.compareSprints(sprint2.getId(), sprint1.getId()))
                .thenReturn(comparison);

        // No hay insights previos de esta señal -> no es duplicado
        when(insightRepo.findByProyectoIdAndTipoAndCategoriaAfectadaAndDismissedFalse(any(), any(), any()))
                .thenReturn(List.of());

        // Mock Gemini responses
        when(geminiService.generate(anyString()))
                .thenReturn("TÍTULO: Mejora en Calidad\nDESCRIPCIÓN: La calidad aumentó 13.33%\nRECOMENDACIÓN: Mantener prácticas");

        // Mock de serialización (necesario para toDto)
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        // Mock completo de ObjectMapper para evitar NPE en toDto()
        com.fasterxml.jackson.databind.type.TypeFactory typeFactory = mock(com.fasterxml.jackson.databind.type.TypeFactory.class);
        when(objectMapper.getTypeFactory()).thenReturn(typeFactory);
        when(typeFactory.constructCollectionType(any(Class.class), any(Class.class))).thenReturn(null);
        when(objectMapper.readValue(anyString(), (com.fasterxml.jackson.databind.JavaType) isNull())).thenReturn(List.of());

        // Mock del save para retornar el insight guardado
        when(insightRepo.save(any(AIInsight.class))).thenAnswer(invocation -> {
            AIInsight insight = invocation.getArgument(0);
            if (insight.getId() == null) {
                insight.setId(UUID.randomUUID());
            }
            return insight;
        });

        GenerateInsightsResultDto resultado = service.generateInsights(proyectoId, userId);

        // Debe generar al menos insights de comparación
        assertThat(resultado.status()).isEqualTo("COMPLETE");
        assertThat(resultado.senalesNuevas()).isGreaterThanOrEqualTo(1);
        verify(analyticsService).compareSprints(any(), any());
        verify(geminiService, atLeastOnce()).generate(anyString());
    }

    // ── FASE 23: deduplicación (sección 2) ─────────────────────────────────
    // Usa el generador de COMPARISON como vehículo porque solo requiere 2
    // sprints finalizados (el mecanismo de deduplicación —
    // esDuplicadoDeSenialExistente/GeneratorOutcome— es el mismo para los
    // 4 generadores).

    private final ObjectMapper mapperReal = new ObjectMapper();

    /**
     * Deja pasar la serialización real (no el mock fijo "[]") para que la huella
     * refleje los datos. lenient(): la parte de toDto() (getTypeFactory/readValue)
     * solo se ejecuta si al menos un insight se guarda y se mapea a DTO — en los
     * escenarios de "todo falló" o "todo es duplicado" ningún insight llega a
     * guardarse, así que esos stubs quedarían sin usar (comportamiento correcto,
     * no un test mal escrito) y Mockito los marcaría como stubbing innecesario en
     * modo estricto por defecto.
     */
    private void mockSerializacionRealDeEvidencia() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenAnswer(inv -> mapperReal.writeValueAsString(inv.getArgument(0)));
        com.fasterxml.jackson.databind.type.TypeFactory typeFactory = mock(com.fasterxml.jackson.databind.type.TypeFactory.class);
        lenient().when(objectMapper.getTypeFactory()).thenReturn(typeFactory);
        lenient().when(typeFactory.constructCollectionType(any(Class.class), any(Class.class))).thenReturn(null);
        lenient().when(objectMapper.readValue(anyString(), (com.fasterxml.jackson.databind.JavaType) isNull())).thenReturn(List.of());
    }

    private void mockDosSprintsConComparacion(SprintComparisonDto comparison, Sprint sprint1, Sprint sprint2) {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint2, sprint1));
        when(analyticsService.getSprintTrends(eq(proyectoId), isNull(), eq(3))).thenReturn(List.of());
        when(analyticsService.compareSprints(sprint2.getId(), sprint1.getId())).thenReturn(comparison);
        // lenient(): no se invoca cuando la señal falla en Gemini o resulta duplicada.
        lenient().when(insightRepo.save(any(AIInsight.class))).thenAnswer(invocation -> {
            AIInsight insight = invocation.getArgument(0);
            if (insight.getId() == null) insight.setId(UUID.randomUUID());
            return insight;
        });
    }

    private SprintComparisonDto comparisonDeCalidad(Sprint sprint1, Sprint sprint2, String variacion, String tendencia) {
        return new SprintComparisonDto(
                sprint1.getId(), 1, sprint2.getId(), 2,
                Map.of("Calidad", new BigDecimal("7.5")),
                Map.of("Calidad", new BigDecimal("8.5")),
                Map.of("Calidad", new BigDecimal("1.0")),
                Map.of("Calidad", new BigDecimal(variacion)),
                Map.of("Calidad", tendencia),
                true
        );
    }

    @Test
    @DisplayName("dedup: primera generación crea el insight (no hay nada previo que comparar)")
    void generateInsights_primeraGeneracion_creaInsightNuevo() throws Exception {
        Sprint sprint1 = crearSprint(1, "finalizado");
        Sprint sprint2 = crearSprint(2, "finalizado");
        SprintComparisonDto comparison = comparisonDeCalidad(sprint1, sprint2, "13.33", "UP");
        mockDosSprintsConComparacion(comparison, sprint1, sprint2);
        mockSerializacionRealDeEvidencia();
        when(insightRepo.findByProyectoIdAndTipoAndCategoriaAfectadaAndDismissedFalse(any(), any(), any()))
                .thenReturn(List.of());
        when(geminiService.generate(anyString()))
                .thenReturn("TÍTULO: Mejora en Calidad\nDESCRIPCIÓN: La calidad aumentó 13.33%\nRECOMENDACIÓN: Mantener prácticas");

        GenerateInsightsResultDto resultado = service.generateInsights(proyectoId, userId);

        assertThat(resultado.status()).isEqualTo("COMPLETE");
        assertThat(resultado.senalesNuevas()).isEqualTo(1);
        assertThat(resultado.senalesOmitidasPorDuplicado()).isEqualTo(0);
        verify(insightRepo, times(1)).save(any(AIInsight.class));
    }

    @Test
    @DisplayName("dedup: segunda generación con exactamente los mismos datos NO crea un duplicado")
    void generateInsights_segundaGeneracionMismosDatos_noDuplica() throws Exception {
        Sprint sprint1 = crearSprint(1, "finalizado");
        Sprint sprint2 = crearSprint(2, "finalizado");
        SprintComparisonDto comparison = comparisonDeCalidad(sprint1, sprint2, "13.33", "UP");
        mockDosSprintsConComparacion(comparison, sprint1, sprint2);
        mockSerializacionRealDeEvidencia();

        // Ya existe un insight activo con la MISMA huella (misma evidencia determinística)
        // que produciría esta corrida para (COMPARISON, Calidad, sprint2.getId()).
        com.mpdia.dto.ai.InsightEvidenceDto evidenciaExistente = new com.mpdia.dto.ai.InsightEvidenceDto(
                "Calidad", new BigDecimal("8.5"), new BigDecimal("7.5"), null, null,
                new BigDecimal("13.33"), "UP", 2, Map.of());
        AIInsight existente = new AIInsight();
        existente.setProyectoId(proyectoId);
        existente.setSprintId(sprint2.getId());
        existente.setTipo("COMPARISON");
        existente.setCategoriaAfectada("Calidad");
        existente.setEvidenceJson(mapperReal.writeValueAsString(List.of(evidenciaExistente)));
        existente.setDismissed(false);

        when(insightRepo.findByProyectoIdAndTipoAndCategoriaAfectadaAndDismissedFalse(proyectoId, "COMPARISON", "Calidad"))
                .thenReturn(List.of(existente));

        GenerateInsightsResultDto resultado = service.generateInsights(proyectoId, userId);

        assertThat(resultado.status()).isEqualTo("COMPLETE"); // no es un fallo, la señal ya está cubierta
        assertThat(resultado.senalesNuevas()).isEqualTo(0);
        assertThat(resultado.senalesOmitidasPorDuplicado()).isEqualTo(1);
        verify(insightRepo, never()).save(any());
        verify(geminiService, never()).generate(anyString()); // se evita incluso la llamada a Gemini
    }

    @Test
    @DisplayName("dedup: si los datos realmente cambiaron (variación distinta), SÍ se genera un insight nuevo")
    void generateInsights_datosRealmenteCambiaron_generaInsightNuevo() throws Exception {
        Sprint sprint1 = crearSprint(1, "finalizado");
        Sprint sprint2 = crearSprint(2, "finalizado");
        SprintComparisonDto comparison = comparisonDeCalidad(sprint1, sprint2, "13.33", "UP");
        mockDosSprintsConComparacion(comparison, sprint1, sprint2);
        mockSerializacionRealDeEvidencia();

        // Insight previo con una variación DISTINTA (ej. de una corrida anterior con otros datos)
        com.mpdia.dto.ai.InsightEvidenceDto evidenciaVieja = new com.mpdia.dto.ai.InsightEvidenceDto(
                "Calidad", new BigDecimal("6.0"), new BigDecimal("7.5"), null, null,
                new BigDecimal("-20.0"), "DOWN", 2, Map.of());
        AIInsight existente = new AIInsight();
        existente.setProyectoId(proyectoId);
        existente.setSprintId(sprint2.getId());
        existente.setTipo("COMPARISON");
        existente.setCategoriaAfectada("Calidad");
        existente.setEvidenceJson(mapperReal.writeValueAsString(List.of(evidenciaVieja)));
        existente.setDismissed(false);

        when(insightRepo.findByProyectoIdAndTipoAndCategoriaAfectadaAndDismissedFalse(proyectoId, "COMPARISON", "Calidad"))
                .thenReturn(List.of(existente));
        when(geminiService.generate(anyString()))
                .thenReturn("TÍTULO: Mejora en Calidad\nDESCRIPCIÓN: La calidad aumentó 13.33%\nRECOMENDACIÓN: Mantener prácticas");

        GenerateInsightsResultDto resultado = service.generateInsights(proyectoId, userId);

        assertThat(resultado.senalesNuevas()).isEqualTo(1);
        assertThat(resultado.senalesOmitidasPorDuplicado()).isEqualTo(0);
        verify(insightRepo, times(1)).save(any(AIInsight.class));
    }

    @Test
    @DisplayName("dedup: varias señales distintas en una misma generación se evalúan de forma independiente")
    void generateInsights_variasSenalesDistintas_seEvaluanIndependientemente() throws Exception {
        Sprint sprint1 = crearSprint(1, "finalizado");
        Sprint sprint2 = crearSprint(2, "finalizado");
        SprintComparisonDto comparison = new SprintComparisonDto(
                sprint1.getId(), 1, sprint2.getId(), 2,
                Map.of("Calidad", new BigDecimal("7.5"), "Productividad", new BigDecimal("5.0")),
                Map.of("Calidad", new BigDecimal("8.5"), "Productividad", new BigDecimal("6.5")),
                Map.of("Calidad", new BigDecimal("1.0"), "Productividad", new BigDecimal("1.5")),
                Map.of("Calidad", new BigDecimal("13.33"), "Productividad", new BigDecimal("30.0")),
                Map.of("Calidad", "UP", "Productividad", "UP"),
                true
        );
        mockDosSprintsConComparacion(comparison, sprint1, sprint2);
        mockSerializacionRealDeEvidencia();
        // "Calidad" ya cubierta (duplicado), "Productividad" es señal nueva
        com.mpdia.dto.ai.InsightEvidenceDto evidenciaCalidad = new com.mpdia.dto.ai.InsightEvidenceDto(
                "Calidad", new BigDecimal("8.5"), new BigDecimal("7.5"), null, null,
                new BigDecimal("13.33"), "UP", 2, Map.of());
        AIInsight existenteCalidad = new AIInsight();
        existenteCalidad.setProyectoId(proyectoId);
        existenteCalidad.setSprintId(sprint2.getId());
        existenteCalidad.setTipo("COMPARISON");
        existenteCalidad.setCategoriaAfectada("Calidad");
        existenteCalidad.setEvidenceJson(mapperReal.writeValueAsString(List.of(evidenciaCalidad)));

        when(insightRepo.findByProyectoIdAndTipoAndCategoriaAfectadaAndDismissedFalse(proyectoId, "COMPARISON", "Calidad"))
                .thenReturn(List.of(existenteCalidad));
        when(insightRepo.findByProyectoIdAndTipoAndCategoriaAfectadaAndDismissedFalse(proyectoId, "COMPARISON", "Productividad"))
                .thenReturn(List.of());
        when(geminiService.generate(anyString()))
                .thenReturn("TÍTULO: Mejora\nDESCRIPCIÓN: Cambio detectado\nRECOMENDACIÓN: Continuar");

        GenerateInsightsResultDto resultado = service.generateInsights(proyectoId, userId);

        assertThat(resultado.senalesDetectadas()).isEqualTo(2); // Calidad + Productividad
        assertThat(resultado.senalesNuevas()).isEqualTo(1);     // solo Productividad
        assertThat(resultado.senalesOmitidasPorDuplicado()).isEqualTo(1); // Calidad
    }

    // ── FASE 23: fallos parciales de Gemini (sección 3) ────────────────────

    @Test
    @DisplayName("fallos parciales: 1 de 2 señales falla en Gemini -> PARTIAL, conserva la exitosa e informa el error")
    void generateInsights_unaDeDosSenalesFallaEnGemini_statusPartial() throws Exception {
        Sprint sprint1 = crearSprint(1, "finalizado");
        Sprint sprint2 = crearSprint(2, "finalizado");
        SprintComparisonDto comparison = new SprintComparisonDto(
                sprint1.getId(), 1, sprint2.getId(), 2,
                Map.of("Calidad", new BigDecimal("7.5"), "Productividad", new BigDecimal("5.0")),
                Map.of("Calidad", new BigDecimal("8.5"), "Productividad", new BigDecimal("6.5")),
                Map.of("Calidad", new BigDecimal("1.0"), "Productividad", new BigDecimal("1.5")),
                Map.of("Calidad", new BigDecimal("13.33"), "Productividad", new BigDecimal("30.0")),
                Map.of("Calidad", "UP", "Productividad", "UP"),
                true
        );
        mockDosSprintsConComparacion(comparison, sprint1, sprint2);
        mockSerializacionRealDeEvidencia();
        when(insightRepo.findByProyectoIdAndTipoAndCategoriaAfectadaAndDismissedFalse(any(), any(), any()))
                .thenReturn(List.of());

        // Gemini responde bien para la primera señal y falla (cuota agotada) para la segunda
        when(geminiService.generate(anyString()))
                .thenReturn("TÍTULO: Mejora en Calidad\nDESCRIPCIÓN: Sube 13.33%\nRECOMENDACIÓN: Mantener")
                .thenThrow(new RuntimeException("Gemini error 429 TOO_MANY_REQUESTS"));

        GenerateInsightsResultDto resultado = service.generateInsights(proyectoId, userId);

        assertThat(resultado.status()).isEqualTo("PARTIAL");
        assertThat(resultado.senalesDetectadas()).isEqualTo(2);
        assertThat(resultado.senalesNuevas()).isEqualTo(1);
        assertThat(resultado.errores()).hasSize(1);
        assertThat(resultado.errores().get(0)).contains("COMPARISON");
    }

    @Test
    @DisplayName("fallos parciales: Gemini falla para todas las señales -> FAILED, no presenta la corrida como exitosa")
    void generateInsights_todasLasSenalesFallanEnGemini_statusFailed() throws Exception {
        Sprint sprint1 = crearSprint(1, "finalizado");
        Sprint sprint2 = crearSprint(2, "finalizado");
        SprintComparisonDto comparison = comparisonDeCalidad(sprint1, sprint2, "13.33", "UP");
        mockDosSprintsConComparacion(comparison, sprint1, sprint2);
        mockSerializacionRealDeEvidencia();
        when(insightRepo.findByProyectoIdAndTipoAndCategoriaAfectadaAndDismissedFalse(any(), any(), any()))
                .thenReturn(List.of());
        when(geminiService.generate(anyString()))
                .thenThrow(new RuntimeException("Gemini error 429 TOO_MANY_REQUESTS"));

        GenerateInsightsResultDto resultado = service.generateInsights(proyectoId, userId);

        assertThat(resultado.status()).isEqualTo("FAILED");
        assertThat(resultado.insights()).isEmpty();
        assertThat(resultado.senalesNuevas()).isEqualTo(0);
        assertThat(resultado.errores()).isNotEmpty();
        verify(insightRepo, never()).save(any());
    }

    @Test
    @DisplayName("fallos parciales: reintento posterior (tras un FAILED) funciona correctamente")
    void generateInsights_reintentoTrasFailed_funcionaCorrectamente() throws Exception {
        Sprint sprint1 = crearSprint(1, "finalizado");
        Sprint sprint2 = crearSprint(2, "finalizado");
        SprintComparisonDto comparison = comparisonDeCalidad(sprint1, sprint2, "13.33", "UP");
        mockDosSprintsConComparacion(comparison, sprint1, sprint2);
        mockSerializacionRealDeEvidencia();
        // Ningún intento anterior dejó nada persistido (el primero fue FAILED), así que
        // la segunda corrida tampoco encuentra duplicados.
        when(insightRepo.findByProyectoIdAndTipoAndCategoriaAfectadaAndDismissedFalse(any(), any(), any()))
                .thenReturn(List.of());
        when(geminiService.generate(anyString()))
                .thenThrow(new RuntimeException("Gemini error 429 TOO_MANY_REQUESTS"))
                .thenReturn("TÍTULO: Mejora en Calidad\nDESCRIPCIÓN: Sube 13.33%\nRECOMENDACIÓN: Mantener");

        GenerateInsightsResultDto primerIntento = service.generateInsights(proyectoId, userId);
        assertThat(primerIntento.status()).isEqualTo("FAILED");

        GenerateInsightsResultDto reintento = service.generateInsights(proyectoId, userId);
        assertThat(reintento.status()).isEqualTo("COMPLETE");
        assertThat(reintento.senalesNuevas()).isEqualTo(1);
    }

    // ── FASE 23: robustez del parser TÍTULO/DESCRIPCIÓN/RECOMENDACIÓN (sección 4) ──
    // Usa el generador de TREND como vehículo (solo requiere 1 sprint finalizado
    // y una tendencia no-STABLE mockeada directamente desde AgileAnalyticsService).

    private void mockUnSprintConTendencia(TrendAnalysisDto trend) {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprint));
        when(analyticsService.getSprintTrends(eq(proyectoId), isNull(), eq(3))).thenReturn(List.of(trend));
        when(insightRepo.findByProyectoIdAndTipoAndCategoriaAfectadaAndDismissedFalse(any(), any(), any()))
                .thenReturn(List.of());
        when(insightRepo.save(any(AIInsight.class))).thenAnswer(invocation -> {
            AIInsight insight = invocation.getArgument(0);
            if (insight.getId() == null) insight.setId(UUID.randomUUID());
            return insight;
        });
    }

    private TrendAnalysisDto trendDeImpacto() {
        return new TrendAnalysisDto(
                proyectoId, "Impacto", 3,
                List.of(new TrendAnalysisDto.SprintDataPoint(1, new BigDecimal("80"), "2026-01-01"),
                        new TrendAnalysisDto.SprintDataPoint(3, new BigDecimal("20"), "2026-01-15")),
                new BigDecimal("50"), new BigDecimal("24.49"), "DOWN", new BigDecimal("-75"), true
        );
    }

    @Test
    @DisplayName("parser: respuesta normal (TÍTULO/DESCRIPCIÓN/RECOMENDACIÓN en orden) se parsea correctamente")
    void parser_respuestaNormal_extraeTituloDescripcionRecomendacion() throws Exception {
        mockSerializacionRealDeEvidencia();
        mockUnSprintConTendencia(trendDeImpacto());
        when(geminiService.generate(anyString())).thenReturn(
                "TÍTULO: Caída sostenida de Impacto\nDESCRIPCIÓN: El impacto bajó 75%.\nRECOMENDACIÓN: Revisar causas en la retro.");

        GenerateInsightsResultDto resultado = service.generateInsights(proyectoId, userId);

        assertThat(resultado.senalesNuevas()).isEqualTo(1);
        AIInsight guardado = capturarInsightGuardado();
        assertThat(guardado.getTitulo()).isEqualTo("Caída sostenida de Impacto");
        assertThat(guardado.getDescripcion()).isEqualTo("El impacto bajó 75%.");
        assertThat(guardado.getRecomendacion()).isEqualTo("Revisar causas en la retro.");
    }

    @Test
    @DisplayName("parser: respuesta con preámbulo + Markdown antes de TÍTULO: NO deja el bloque crudo como título (bug FASE 22)")
    void parser_respuestaConPreambuloYMarkdown_noUsaBloqueCrudoComoTitulo() throws Exception {
        mockSerializacionRealDeEvidencia();
        mockUnSprintConTendencia(trendDeImpacto());
        // Reproduce el caso real observado en FASE 22: Gemini antepone un
        // preámbulo con Markdown antes del formato pedido.
        when(geminiService.generate(anyString())).thenReturn(
                "Aquí tienes la comparación de desempeño y el insight generado:\n\n---\n\n**** Alerta de Desempeño: Caída del 75% en el Impacto ****\n\n" +
                        "TÍTULO: Caída crítica de Impacto\nDESCRIPCIÓN: El impacto cayó 75% en 3 sprints.\nRECOMENDACIÓN: Investigar causa raíz.");

        GenerateInsightsResultDto resultado = service.generateInsights(proyectoId, userId);

        assertThat(resultado.senalesNuevas()).isEqualTo(1);
        AIInsight guardado = capturarInsightGuardado();
        assertThat(guardado.getTitulo()).isEqualTo("Caída crítica de Impacto");
        assertThat(guardado.getTitulo()).doesNotContain("Aquí tienes la comparación");
        assertThat(guardado.getTitulo().length()).isLessThanOrEqualTo(200);
    }

    @Test
    @DisplayName("parser: respuesta sin NINGÚN marcador reconocible usa un título corto derivado de datos determinísticos")
    void parser_respuestaSinMarcadores_usaTituloFallbackDeterministico() throws Exception {
        mockSerializacionRealDeEvidencia();
        mockUnSprintConTendencia(trendDeImpacto());
        when(geminiService.generate(anyString())).thenReturn(
                "El equipo mostró una caída significativa en el indicador de impacto durante los últimos sprints, lo cual amerita revisión.");

        GenerateInsightsResultDto resultado = service.generateInsights(proyectoId, userId);

        assertThat(resultado.senalesNuevas()).isEqualTo(1);
        AIInsight guardado = capturarInsightGuardado();
        assertThat(guardado.getTitulo()).isEqualTo("Tendencia detectada en Impacto");
        assertThat(guardado.getTitulo().length()).isLessThanOrEqualTo(200);
    }

    @Test
    @DisplayName("parser: respuesta imperfecta pero recuperable (falta RECOMENDACIÓN:) conserva título y descripción")
    void parser_respuestaImperfectaPeroRecuperable_conservaTituloYDescripcion() throws Exception {
        mockSerializacionRealDeEvidencia();
        mockUnSprintConTendencia(trendDeImpacto());
        when(geminiService.generate(anyString())).thenReturn(
                "TÍTULO: Caída sostenida de Impacto\nDESCRIPCIÓN: El impacto bajó 75% en los últimos 3 sprints.");

        GenerateInsightsResultDto resultado = service.generateInsights(proyectoId, userId);

        assertThat(resultado.senalesNuevas()).isEqualTo(1);
        AIInsight guardado = capturarInsightGuardado();
        assertThat(guardado.getTitulo()).isEqualTo("Caída sostenida de Impacto");
        assertThat(guardado.getDescripcion()).contains("bajó 75%");
        assertThat(guardado.getRecomendacion()).isNull(); // no inventa una recomendación que Gemini no dio
    }

    private AIInsight capturarInsightGuardado() {
        org.mockito.ArgumentCaptor<AIInsight> captor = org.mockito.ArgumentCaptor.forClass(AIInsight.class);
        verify(insightRepo, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Sprint crearSprint(int numero, String estado) {
        Sprint s = new Sprint();
        s.setId(UUID.randomUUID());
        s.setProyectoId(proyectoId);
        s.setNumero(numero);
        s.setEstado(estado);
        s.setFechaInicio(LocalDate.now().minusWeeks(numero * 2L));
        s.setFechaFin(LocalDate.now().minusWeeks((numero * 2L) - 1));
        return s;
    }
}
