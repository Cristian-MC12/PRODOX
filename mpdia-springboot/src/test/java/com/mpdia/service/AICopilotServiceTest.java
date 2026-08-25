// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.ProyectoMetricaDto;
import com.mpdia.dto.ResultadoMetricaDto;
import com.mpdia.dto.ai.AIRetrospectiveDto;
import com.mpdia.dto.ai.AgentResponse;
import com.mpdia.dto.ai.ChatRequest;
import com.mpdia.dto.ai.ChatResponse;
import com.mpdia.dto.analytics.RiskDto;
import com.mpdia.dto.analytics.SprintComparisonDto;
import com.mpdia.dto.analytics.SprintMetricsSummaryDto;
import com.mpdia.dto.analytics.TrendAnalysisDto;
import com.mpdia.entity.Proyecto;
import com.mpdia.entity.Sprint;
import com.mpdia.repository.AIChatMessageRepository;
import com.mpdia.repository.ProjectMemberRepository;
import com.mpdia.repository.ProyectoRepository;
import com.mpdia.repository.SprintRepository;
import com.mpdia.service.copilot.CopilotDomainGuard;
import com.mpdia.service.copilot.CopilotToolsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * FASE 12.2 — Demuestra que el guardrail de dominio corta el flujo de
 * AICopilotService.chat() ANTES de historial/prompt/LLM para preguntas fuera de dominio,
 * y que las preguntas válidas siguen llegando al LLM exactamente como antes (no regresión).
 */
@ExtendWith(MockitoExtension.class)
class AICopilotServiceTest {

    @Mock private AIAgentService aiAgentService;
    @Mock private CopilotToolsService toolsService;
    @Mock private AIChatMessageRepository chatMessageRepo;
    @Mock private ProjectMemberRepository projectMemberRepo;
    @Mock private ProyectoRepository proyectoRepo;
    @Mock private SprintRepository sprintRepo;
    @Mock private PlaneacionService planeacionService;
    @Mock private MetricaAcademicaService metricaAcademicaService;
    @Mock private AgileAnalyticsService agileAnalyticsService;
    @Mock private GeminiService geminiService;
    @Mock private AIRetrospectiveService aiRetrospectiveService;

    // Guard REAL (no mockeado): es una clase determinística sin dependencias externas,
    // así que se usa la implementación real para que el test demuestre el comportamiento
    // verdadero del guardrail, no una simulación de él.
    private final CopilotDomainGuard domainGuard = new CopilotDomainGuard();

    private AICopilotService service;

    private UUID proyectoId;
    private final String userId = "user-fase12-2";

    @BeforeEach
    void setUp() {
        service = new AICopilotService(
                aiAgentService, toolsService, domainGuard,
                chatMessageRepo, projectMemberRepo, proyectoRepo, sprintRepo,
                planeacionService, metricaAcademicaService, agileAnalyticsService, geminiService,
                aiRetrospectiveService);

        proyectoId = UUID.randomUUID();
        // lenient: no todos los tests (p.ej. el de aislamiento entre proyectos FASE 12.4,
        // que solo opera sobre un proyecto B distinto) ejercitan este proyecto compartido.
        lenient().when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);

        Proyecto proyecto = new Proyecto();
        proyecto.setId(proyectoId);
        proyecto.setNombre("Proyecto de prueba");
        proyecto.setMetodo("scrum");
        proyecto.setTimeBoxSemanas(2);
        lenient().when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
    }

    // ── Test crítico (sección 9): pregunta fuera de dominio NUNCA llega al LLM ──

    @Test
    void mensajeFueraDeDominio_noLlamaAlAgenteNiRecuperaHistorial() {
        ChatRequest request = new ChatRequest("CUANTO ES 2 MAS 2", proyectoId, null);

        ChatResponse response = service.chat(request, userId);

        // La respuesta es la guía local del Copilot, sin datos, sin tools usadas.
        assertThat(response.message()).isEqualTo(CopilotDomainGuard.RESPUESTA_FUERA_DE_DOMINIO);
        assertThat(response.toolsUsed()).isEmpty();
        assertThat(response.hasData()).isFalse();

        // Demuestra que el mensaje NUNCA llegó al LLM ni a su orquestador.
        verifyNoInteractions(aiAgentService);
        verifyNoInteractions(toolsService);

        // Demuestra que tampoco se construyó historial (ni se persistió el mensaje rechazado).
        verify(chatMessageRepo, never()).findByUserIdAndProyectoIdOrderByCreatedAtAsc(any(), any());
        verify(chatMessageRepo, never()).save(any());
    }

    @Test
    void otrasPreguntasFueraDeDominio_tampocoLlamanAlAgente() {
        for (String mensaje : List.of(
                "cuál es la capital de Francia",
                "cuéntame un chiste",
                "escribe un poema"
        )) {
            ChatRequest request = new ChatRequest(mensaje, proyectoId, null);
            ChatResponse response = service.chat(request, userId);
            assertThat(response.message()).isEqualTo(CopilotDomainGuard.RESPUESTA_FUERA_DE_DOMINIO);
        }

        verifyNoInteractions(aiAgentService);
        verifyNoInteractions(toolsService);
    }

    // ── FASE 12.9: aritmética con números en palabras también se rechaza localmente ──
    // (antes de esta fase, "cuanto es dos por dos" no matcheaba ningún patrón de
    // CopilotDomainGuard y, al ser un mensaje corto sin términos de dominio, se colaba como
    // "ambiguo dentro de contexto" y llegaba hasta Gemini/AIAgentService).

    @Test
    void preguntaAritmeticaConNumerosEnPalabras_seRechazaLocalmente_sinLlamarAGeminiNiAIAgentServiceNiTools() {
        ChatRequest request = new ChatRequest("cuanto es dos por dos", proyectoId, null);

        ChatResponse response = service.chat(request, userId);

        assertThat(response.message()).isEqualTo(CopilotDomainGuard.RESPUESTA_FUERA_DE_DOMINIO);
        assertThat(response.toolsUsed()).isEmpty();
        assertThat(response.hasData()).isFalse();

        verifyNoInteractions(aiAgentService);
        verifyNoInteractions(toolsService);
        verifyNoInteractions(geminiService);
        verify(chatMessageRepo, never()).findByUserIdAndProyectoIdOrderByCreatedAtAsc(any(), any());
        verify(chatMessageRepo, never()).save(any());
    }

    // ── Test de no regresión (sección 10): pregunta válida sigue llegando al LLM ──

    @Test
    void mensajeDentroDeDominio_siContinuaHaciaElAgente() {
        when(chatMessageRepo.findByUserIdAndProyectoIdOrderByCreatedAtAsc(userId, proyectoId))
                .thenReturn(List.of());
        when(toolsService.getAvailableTools()).thenReturn(List.of());
        when(aiAgentService.processMessage(any(), any(), any(), any()))
                .thenReturn(new AgentResponse("El último sprint tuvo 3 métricas registradas.",
                        List.of("getActiveSprintMetrics"), true));

        ChatRequest request = new ChatRequest("¿Cómo estuvo el último sprint?", proyectoId, null);
        ChatResponse response = service.chat(request, userId);

        // El flujo normal sí se ejecutó: se consultó historial y se invocó al agente/LLM.
        verify(chatMessageRepo).findByUserIdAndProyectoIdOrderByCreatedAtAsc(userId, proyectoId);
        verify(aiAgentService).processMessage(any(), any(), any(), any());

        assertThat(response.message()).isEqualTo("El último sprint tuvo 3 métricas registradas.");
        assertThat(response.toolsUsed()).containsExactly("getActiveSprintMetrics");
        assertThat(response.hasData()).isTrue();

        // Se guardan ambos mensajes (user + assistant), como en el comportamiento actual.
        verify(chatMessageRepo, times(2)).save(any());
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // FASE 12.4 — Respuestas determinísticas Tipo A (RESULTADO_METRICA / RESULTADO_ULTIMO_SPRINT)
    // ══════════════════════════════════════════════════════════════════════════════

    private ProyectoMetricaDto metricaAprobada(UUID metricaId, String codigo, String nombre) {
        return new ProyectoMetricaDto(metricaId, codigo, nombre, "desc", "Calidad", "factor",
                true, Instant.now(), true, "coordinador", Instant.now(), true);
    }

    private ResultadoMetricaDto resultadoCalculado(UUID metricaId, String nombre, UUID proyectoId,
                                                    UUID sprintId, BigDecimal valor, String unidad) {
        return new ResultadoMetricaDto(UUID.randomUUID(), metricaId, nombre, proyectoId, sprintId,
                UUID.randomUUID(), 1, "formula", "expr", "valores", valor, unidad,
                "calculado", null, Instant.now());
    }

    @Test
    void resultadoMetrica_seResuelveLocalmenteSinLlamarAlAgente() {
        UUID metricaId = UUID.randomUUID();
        UUID sprintId = UUID.randomUUID();

        when(planeacionService.listarMetricasConEstado(proyectoId))
                .thenReturn(List.of(metricaAprobada(metricaId, "DEF", "Defectos")));
        when(metricaAcademicaService.obtenerHistorico(metricaId, proyectoId))
                .thenReturn(List.of(resultadoCalculado(metricaId, "Defectos", proyectoId, sprintId,
                        new BigDecimal("5"), "defectos")));

        Sprint sprint = new Sprint();
        sprint.setId(sprintId);
        sprint.setProyectoId(proyectoId);
        sprint.setNumero(1);
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));

        ChatRequest request = new ChatRequest("¿Cuánto dio Defectos?", proyectoId, null);
        ChatResponse response = service.chat(request, userId);

        assertThat(response.message()).isEqualTo("Defectos: 5 defectos en Sprint 1.");
        assertThat(response.hasData()).isTrue();
        assertThat(response.toolsUsed()).isEmpty();

        // 0 llamadas a Gemini/AIAgentService, 0 tools, 0 historial recuperado ni persistido.
        verifyNoInteractions(aiAgentService);
        verifyNoInteractions(toolsService);
        verify(chatMessageRepo, never()).findByUserIdAndProyectoIdOrderByCreatedAtAsc(any(), any());
        verify(chatMessageRepo, never()).save(any());
    }

    @Test
    void resultadoMetrica_conUnidadPorcentaje_incluyeUnidadEnLaRespuesta() {
        UUID metricaId = UUID.randomUUID();
        UUID sprintId = UUID.randomUUID();

        when(planeacionService.listarMetricasConEstado(proyectoId))
                .thenReturn(List.of(metricaAprobada(metricaId, "FAT", "Aprendizaje organizacional (FAT)")));
        when(metricaAcademicaService.obtenerHistorico(metricaId, proyectoId))
                .thenReturn(List.of(resultadoCalculado(metricaId, "Aprendizaje organizacional (FAT)",
                        proyectoId, sprintId, new BigDecimal("80"), "%")));

        Sprint sprint = new Sprint();
        sprint.setId(sprintId);
        sprint.setProyectoId(proyectoId);
        sprint.setNumero(1);
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));

        ChatRequest request = new ChatRequest("¿Cuál fue el FAT?", proyectoId, null);
        ChatResponse response = service.chat(request, userId);

        assertThat(response.message()).isEqualTo("Aprendizaje organizacional (FAT): 80 % en Sprint 1.");
        verifyNoInteractions(aiAgentService);
    }

    @Test
    void resultadoMetrica_deudaTecnica_seResuelveLocalmente() {
        UUID metricaId = UUID.randomUUID();
        UUID sprintId = UUID.randomUUID();

        when(planeacionService.listarMetricasConEstado(proyectoId))
                .thenReturn(List.of(metricaAprobada(metricaId, "DT", "Deuda técnica gestionada")));
        when(metricaAcademicaService.obtenerHistorico(metricaId, proyectoId))
                .thenReturn(List.of(resultadoCalculado(metricaId, "Deuda técnica gestionada",
                        proyectoId, sprintId, new BigDecimal("75"), "%")));

        Sprint sprint = new Sprint();
        sprint.setId(sprintId);
        sprint.setProyectoId(proyectoId);
        sprint.setNumero(1);
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));

        ChatRequest request = new ChatRequest("¿Cuánta deuda técnica gestionamos?", proyectoId, null);
        ChatResponse response = service.chat(request, userId);

        assertThat(response.message()).isEqualTo("Deuda técnica gestionada: 75 % en Sprint 1.");
        verifyNoInteractions(aiAgentService);
    }

    @Test
    void resultadoMetrica_aislamientoEntreProyectos_soloDevuelveElResultadoDelProyectoActivo() {
        // proyectoA nunca se autoriza ni se consulta en este test: solo existe para probar
        // que el histórico jamás se busca con un proyecto distinto al de la request activa.
        UUID proyectoA = UUID.randomUUID();
        UUID proyectoB = UUID.randomUUID();
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoB, userId)).thenReturn(true);

        Proyecto proyectoBEntity = new Proyecto();
        proyectoBEntity.setId(proyectoB);
        proyectoBEntity.setNombre("Proyecto B");
        proyectoBEntity.setMetodo("scrum");
        proyectoBEntity.setTimeBoxSemanas(2);
        when(proyectoRepo.findById(proyectoB)).thenReturn(Optional.of(proyectoBEntity));

        UUID metricaId = UUID.randomUUID();
        UUID sprintIdB = UUID.randomUUID();

        when(planeacionService.listarMetricasConEstado(proyectoB))
                .thenReturn(List.of(metricaAprobada(metricaId, "DEF", "Defectos")));
        when(metricaAcademicaService.obtenerHistorico(metricaId, proyectoB))
                .thenReturn(List.of(resultadoCalculado(metricaId, "Defectos", proyectoB, sprintIdB,
                        new BigDecimal("2"), "defectos")));

        Sprint sprintB = new Sprint();
        sprintB.setId(sprintIdB);
        sprintB.setProyectoId(proyectoB);
        sprintB.setNumero(3);
        when(sprintRepo.findById(sprintIdB)).thenReturn(Optional.of(sprintB));

        ChatRequest request = new ChatRequest("¿Cuánto dio Defectos?", proyectoB, null);
        ChatResponse response = service.chat(request, userId);

        assertThat(response.message()).isEqualTo("Defectos: 2 defectos en Sprint 3.");

        // El histórico SIEMPRE se consulta con el proyectoId autenticado de la request:
        // nunca con otro proyecto (aunque comparta el mismo nombre de métrica).
        verify(metricaAcademicaService, never()).obtenerHistorico(metricaId, proyectoA);
        verify(metricaAcademicaService).obtenerHistorico(metricaId, proyectoB);
        verifyNoInteractions(aiAgentService);
    }

    @Test
    void resultadoMetrica_nombreInexistente_continuaPorGemini() {
        when(planeacionService.listarMetricasConEstado(proyectoId)).thenReturn(List.of());

        when(chatMessageRepo.findByUserIdAndProyectoIdOrderByCreatedAtAsc(userId, proyectoId))
                .thenReturn(List.of());
        when(toolsService.getAvailableTools()).thenReturn(List.of());
        when(aiAgentService.processMessage(any(), any(), any(), any()))
                .thenReturn(new AgentResponse("No encuentro esa métrica.", List.of(), false));

        ChatRequest request = new ChatRequest("¿Cuánto dio Bugs Criticos?", proyectoId, null);
        ChatResponse response = service.chat(request, userId);

        verify(aiAgentService).processMessage(any(), any(), any(), any());
        verify(metricaAcademicaService, never()).obtenerHistorico(any(), any());
        assertThat(response.message()).isEqualTo("No encuentro esa métrica.");
    }

    @Test
    void resultadoMetrica_nombreAmbiguo_noSeResuelveLocalmenteYContinuaPorGemini() {
        UUID metricaId1 = UUID.randomUUID();
        UUID metricaId2 = UUID.randomUUID();
        when(planeacionService.listarMetricasConEstado(proyectoId)).thenReturn(List.of(
                metricaAprobada(metricaId1, "DEF1", "Defectos criticos"),
                metricaAprobada(metricaId2, "DEF2", "Defectos menores")
        ));

        when(chatMessageRepo.findByUserIdAndProyectoIdOrderByCreatedAtAsc(userId, proyectoId))
                .thenReturn(List.of());
        when(toolsService.getAvailableTools()).thenReturn(List.of());
        when(aiAgentService.processMessage(any(), any(), any(), any()))
                .thenReturn(new AgentResponse("¿Podrías especificar cuál 'Defectos'?", List.of(), false));

        ChatRequest request = new ChatRequest("¿Cuánto dio Defectos?", proyectoId, null);
        service.chat(request, userId);

        verify(aiAgentService).processMessage(any(), any(), any(), any());
        verify(metricaAcademicaService, never()).obtenerHistorico(any(), any());
    }

    @Test
    void resultadoMetrica_sinResultadoCalculadoAun_continuaPorGemini() {
        UUID metricaId = UUID.randomUUID();
        when(planeacionService.listarMetricasConEstado(proyectoId))
                .thenReturn(List.of(metricaAprobada(metricaId, "DEF", "Defectos")));
        when(metricaAcademicaService.obtenerHistorico(metricaId, proyectoId)).thenReturn(List.of());

        when(chatMessageRepo.findByUserIdAndProyectoIdOrderByCreatedAtAsc(userId, proyectoId))
                .thenReturn(List.of());
        when(toolsService.getAvailableTools()).thenReturn(List.of());
        when(aiAgentService.processMessage(any(), any(), any(), any()))
                .thenReturn(new AgentResponse("Aún no hay resultados calculados.", List.of(), false));

        ChatRequest request = new ChatRequest("¿Cuánto dio Defectos?", proyectoId, null);
        service.chat(request, userId);

        verify(aiAgentService).processMessage(any(), any(), any(), any());
    }

    @Test
    void resultadoUltimoSprint_conSprintActivo_seResuelveLocalmenteSinLlamarAlAgente() {
        UUID sprintId = UUID.randomUUID();
        Sprint sprint = new Sprint();
        sprint.setId(sprintId);
        sprint.setProyectoId(proyectoId);
        sprint.setNumero(2);
        sprint.setEstado("finalizado");
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));

        SprintMetricsSummaryDto resumen = new SprintMetricsSummaryDto(
                sprintId, 2, "Meta del sprint", "finalizado", null, null, null,
                Map.of("Calidad", new BigDecimal("8.5")), 10, true);
        when(agileAnalyticsService.getSprintMetricsSummary(sprintId)).thenReturn(resumen);

        ChatRequest request = new ChatRequest("¿Cuál fue el resultado del último sprint?", proyectoId, sprintId);
        ChatResponse response = service.chat(request, userId);

        assertThat(response.message()).isEqualTo("Sprint 2 (finalizado): Calidad: 8.5.");
        assertThat(response.hasData()).isTrue();

        verifyNoInteractions(aiAgentService);
        verifyNoInteractions(toolsService);
        verify(chatMessageRepo, never()).findByUserIdAndProyectoIdOrderByCreatedAtAsc(any(), any());
    }

    @Test
    void resultadoUltimoSprint_sinSprintActivo_noSePuedeIdentificarYContinuaPorGemini() {
        when(chatMessageRepo.findByUserIdAndProyectoIdOrderByCreatedAtAsc(userId, proyectoId))
                .thenReturn(List.of());
        when(toolsService.getAvailableTools()).thenReturn(List.of());
        when(aiAgentService.processMessage(any(), any(), any(), any()))
                .thenReturn(new AgentResponse("No hay un sprint activo para consultar.", List.of(), false));

        ChatRequest request = new ChatRequest("¿Cuál fue el resultado del último sprint?", proyectoId, null);
        service.chat(request, userId);

        verify(aiAgentService).processMessage(any(), any(), any(), any());
        verifyNoInteractions(agileAnalyticsService);
    }

    @Test
    void resultadoUltimoSprint_sinDatosDisponibles_continuaPorGemini() {
        UUID sprintId = UUID.randomUUID();
        Sprint sprint = new Sprint();
        sprint.setId(sprintId);
        sprint.setProyectoId(proyectoId);
        sprint.setNumero(1);
        sprint.setEstado("en_ejecucion");
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));

        SprintMetricsSummaryDto resumenVacio = new SprintMetricsSummaryDto(
                sprintId, 1, "Meta", "en_ejecucion", null, null, null, Map.of(), 0, false);
        when(agileAnalyticsService.getSprintMetricsSummary(sprintId)).thenReturn(resumenVacio);

        when(chatMessageRepo.findByUserIdAndProyectoIdOrderByCreatedAtAsc(userId, proyectoId))
                .thenReturn(List.of());
        when(toolsService.getAvailableTools()).thenReturn(List.of());
        when(aiAgentService.processMessage(any(), any(), any(), any()))
                .thenReturn(new AgentResponse("Aún no hay datos suficientes.", List.of(), false));

        ChatRequest request = new ChatRequest("¿Cuál fue el resultado del último sprint?", proyectoId, sprintId);
        service.chat(request, userId);

        verify(aiAgentService).processMessage(any(), any(), any(), any());
    }

    @Test
    void queDeberiamosMejorar_sinSprintActivoEnLaRequest_continuaPorElFlujoActualDeGemini() {
        // FASE 12.6: esta pregunta ahora clasifica como RECOMENDACIONES, pero sin sprintId en
        // la request no hay forma de identificar sin ambigüedad el sprint activo, así que
        // resolverRecomendaciones retorna vacío y el flujo sigue exactamente igual que antes.
        when(chatMessageRepo.findByUserIdAndProyectoIdOrderByCreatedAtAsc(userId, proyectoId))
                .thenReturn(List.of());
        when(toolsService.getAvailableTools()).thenReturn(List.of());
        when(aiAgentService.processMessage(any(), any(), any(), any()))
                .thenReturn(new AgentResponse("Deberían revisar la calidad.", List.of(), false));

        ChatRequest request = new ChatRequest("¿Qué deberíamos mejorar?", proyectoId, null);
        ChatResponse response = service.chat(request, userId);

        verify(aiAgentService).processMessage(any(), any(), any(), any());
        assertThat(response.message()).isEqualTo("Deberían revisar la calidad.");
        verifyNoInteractions(agileAnalyticsService);
    }

    @Test
    void queRevisarEnRetrospectiva_sinSprintActivoEnLaRequest_continuaPorElFlujoActualDeGemini() {
        // FASE 12.6: clasifica como RETROSPECTIVA, pero sin sprintId no hay sprint que
        // identificar sin ambigüedad, así que cae al flujo actual sin llamar a
        // AIRetrospectiveService.
        when(chatMessageRepo.findByUserIdAndProyectoIdOrderByCreatedAtAsc(userId, proyectoId))
                .thenReturn(List.of());
        when(toolsService.getAvailableTools()).thenReturn(List.of());
        when(aiAgentService.processMessage(any(), any(), any(), any()))
                .thenReturn(new AgentResponse("Revisar impedimentos del sprint.", List.of(), false));

        ChatRequest request = new ChatRequest("¿Qué revisar en retrospectiva?", proyectoId, null);
        service.chat(request, userId);

        verifyNoInteractions(aiRetrospectiveService);

        verify(aiAgentService).processMessage(any(), any(), any(), any());
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // FASE 12.5 — Tipo B: COMPARACION_SPRINTS / TENDENCIAS / RIESGOS
    // Datos deterministas (AgileAnalyticsService) + máximo 1 GeminiService.generate(),
    // 0 AIAgentService, 0 tools, 0 chatWithTools, sin recuperar historial.
    // ══════════════════════════════════════════════════════════════════════════════

    private Sprint sprintDe(UUID id, UUID proyectoId, int numero, String estado) {
        Sprint s = new Sprint();
        s.setId(id);
        s.setProyectoId(proyectoId);
        s.setNumero(numero);
        s.setEstado(estado);
        return s;
    }

    @Test
    void comparacionSprints_seResuelveConDatosDeterministicosYUnaSolaLlamadaGemini() {
        UUID sprintActualId = UUID.randomUUID();
        UUID sprintAnteriorId = UUID.randomUUID();

        Sprint sprintActual = sprintDe(sprintActualId, proyectoId, 2, "en_ejecucion");
        Sprint sprintAnterior = sprintDe(sprintAnteriorId, proyectoId, 1, "finalizado");

        when(sprintRepo.findById(sprintActualId)).thenReturn(Optional.of(sprintActual));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId))
                .thenReturn(List.of(sprintActual, sprintAnterior));

        SprintComparisonDto comparacion = new SprintComparisonDto(
                sprintAnteriorId, 1, sprintActualId, 2,
                Map.of("Calidad", new BigDecimal("5")),
                Map.of("Calidad", new BigDecimal("7")),
                Map.of("Calidad", new BigDecimal("2")),
                Map.of("Calidad", new BigDecimal("40.00")),
                Map.of("Calidad", "UP"),
                true);
        when(agileAnalyticsService.compareSprints(sprintAnteriorId, sprintActualId)).thenReturn(comparacion);
        when(geminiService.generate(anyString())).thenReturn("Defectos aumentaron de 5 a 7 (40%).");

        ChatRequest request = new ChatRequest("¿Mejoramos respecto al sprint anterior?", proyectoId, sprintActualId);
        ChatResponse response = service.chat(request, userId);

        assertThat(response.message()).isEqualTo("Defectos aumentaron de 5 a 7 (40%).");
        assertThat(response.hasData()).isTrue();
        assertThat(response.toolsUsed()).isEmpty();

        verifyNoInteractions(aiAgentService);
        verifyNoInteractions(toolsService);
        verify(chatMessageRepo, never()).findByUserIdAndProyectoIdOrderByCreatedAtAsc(any(), any());
        verify(chatMessageRepo, never()).save(any());
        verify(geminiService, times(1)).generate(anyString());
        verify(geminiService, never()).chatWithTools(any(), any(), any());
    }

    @Test
    void comparacionSprints_datosEnviadosAGeminiSonLosDeterminadosPorJava_sinSegundaLlamadaAunConRespuestaIncorrecta() {
        UUID sprintActualId = UUID.randomUUID();
        UUID sprintAnteriorId = UUID.randomUUID();

        Sprint sprintActual = sprintDe(sprintActualId, proyectoId, 2, "en_ejecucion");
        Sprint sprintAnterior = sprintDe(sprintAnteriorId, proyectoId, 1, "finalizado");

        when(sprintRepo.findById(sprintActualId)).thenReturn(Optional.of(sprintActual));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId))
                .thenReturn(List.of(sprintActual, sprintAnterior));

        SprintComparisonDto comparacion = new SprintComparisonDto(
                sprintAnteriorId, 1, sprintActualId, 2,
                Map.of("Calidad", new BigDecimal("5")),
                Map.of("Calidad", new BigDecimal("7")),
                Map.of("Calidad", new BigDecimal("2")),
                Map.of("Calidad", new BigDecimal("40.00")),
                Map.of("Calidad", "UP"),
                true);
        when(agileAnalyticsService.compareSprints(sprintAnteriorId, sprintActualId)).thenReturn(comparacion);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        // Gemini devuelve una respuesta deliberadamente incorrecta/inventada.
        when(geminiService.generate(promptCaptor.capture()))
                .thenReturn("RESPUESTA DELIBERADAMENTE INCORRECTA E INVENTADA");

        ChatRequest request = new ChatRequest("¿Mejoramos respecto al sprint anterior?", proyectoId, sprintActualId);
        ChatResponse response = service.chat(request, userId);

        // La respuesta final es exactamente lo que devolvió Gemini: no hay una segunda
        // llamada ni ninguna herramienta que "corrija" el resultado.
        assertThat(response.message()).isEqualTo("RESPUESTA DELIBERADAMENTE INCORRECTA E INVENTADA");

        // El prompt enviado contiene los datos calculados por Java, no otros.
        String promptEnviado = promptCaptor.getValue();
        assertThat(promptEnviado).contains("Calidad");
        assertThat(promptEnviado).contains("5"); // valor del sprint anterior, calculado por Java
        assertThat(promptEnviado).contains("7"); // valor del sprint actual, calculado por Java

        verify(geminiService, times(1)).generate(anyString());
        verifyNoInteractions(toolsService);
        verifyNoInteractions(aiAgentService);
    }

    @Test
    void comparacionSprints_sinSprintAnteriorFinalizado_continuaPorGemini() {
        UUID sprintActualId = UUID.randomUUID();
        Sprint sprintActual = sprintDe(sprintActualId, proyectoId, 1, "en_ejecucion");
        when(sprintRepo.findById(sprintActualId)).thenReturn(Optional.of(sprintActual));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprintActual));

        when(chatMessageRepo.findByUserIdAndProyectoIdOrderByCreatedAtAsc(userId, proyectoId))
                .thenReturn(List.of());
        when(toolsService.getAvailableTools()).thenReturn(List.of());
        when(aiAgentService.processMessage(any(), any(), any(), any()))
                .thenReturn(new AgentResponse("Aún no hay un sprint anterior para comparar.", List.of(), false));

        ChatRequest request = new ChatRequest("¿Mejoramos respecto al sprint anterior?", proyectoId, sprintActualId);
        service.chat(request, userId);

        verify(aiAgentService).processMessage(any(), any(), any(), any());
        verify(geminiService, never()).generate(anyString());
        verify(agileAnalyticsService, never()).compareSprints(any(), any());
    }

    @Test
    void comparacionSprints_sinSprintActivoEnLaRequest_continuaPorGemini() {
        when(chatMessageRepo.findByUserIdAndProyectoIdOrderByCreatedAtAsc(userId, proyectoId))
                .thenReturn(List.of());
        when(toolsService.getAvailableTools()).thenReturn(List.of());
        when(aiAgentService.processMessage(any(), any(), any(), any()))
                .thenReturn(new AgentResponse("No hay sprint activo para comparar.", List.of(), false));

        ChatRequest request = new ChatRequest("¿Mejoramos respecto al sprint anterior?", proyectoId, null);
        service.chat(request, userId);

        verify(aiAgentService).processMessage(any(), any(), any(), any());
        verifyNoInteractions(agileAnalyticsService);
        verify(geminiService, never()).generate(anyString());
    }

    @Test
    void comparacionSprints_aislamientoEntreProyectos_soloConsultaElProyectoActivo() {
        UUID proyectoA = UUID.randomUUID();
        UUID proyectoB = UUID.randomUUID();
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoB, userId)).thenReturn(true);

        Proyecto proyectoBEntity = new Proyecto();
        proyectoBEntity.setId(proyectoB);
        proyectoBEntity.setNombre("Proyecto B");
        proyectoBEntity.setMetodo("scrum");
        proyectoBEntity.setTimeBoxSemanas(2);
        when(proyectoRepo.findById(proyectoB)).thenReturn(Optional.of(proyectoBEntity));

        UUID sprintActualB = UUID.randomUUID();
        UUID sprintAnteriorB = UUID.randomUUID();
        Sprint actualB = sprintDe(sprintActualB, proyectoB, 2, "en_ejecucion");
        Sprint anteriorB = sprintDe(sprintAnteriorB, proyectoB, 1, "finalizado");

        when(sprintRepo.findById(sprintActualB)).thenReturn(Optional.of(actualB));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoB)).thenReturn(List.of(actualB, anteriorB));

        SprintComparisonDto comparacionB = new SprintComparisonDto(
                sprintAnteriorB, 1, sprintActualB, 2,
                Map.of("Calidad", new BigDecimal("3")), Map.of("Calidad", new BigDecimal("4")),
                Map.of("Calidad", new BigDecimal("1")), Map.of("Calidad", new BigDecimal("33.33")),
                Map.of("Calidad", "UP"), true);
        when(agileAnalyticsService.compareSprints(sprintAnteriorB, sprintActualB)).thenReturn(comparacionB);
        when(geminiService.generate(anyString())).thenReturn("Comparación de Proyecto B.");

        ChatRequest request = new ChatRequest("¿Mejoramos respecto al sprint anterior?", proyectoB, sprintActualB);
        ChatResponse response = service.chat(request, userId);

        assertThat(response.message()).isEqualTo("Comparación de Proyecto B.");
        verify(sprintRepo).findByProyectoIdOrderByNumeroDesc(proyectoB);
        verify(sprintRepo, never()).findByProyectoIdOrderByNumeroDesc(proyectoA);
        verify(agileAnalyticsService).compareSprints(sprintAnteriorB, sprintActualB);
    }

    @Test
    void tendencias_seResuelveConGetSprintTrendsYUnaSolaLlamadaGemini() {
        List<TrendAnalysisDto> trends = List.of(
                new TrendAnalysisDto(proyectoId, "Calidad", 3,
                        List.of(new TrendAnalysisDto.SprintDataPoint(1, new BigDecimal("5"), "2026-08-01"),
                                new TrendAnalysisDto.SprintDataPoint(2, new BigDecimal("7"), "2026-08-08")),
                        new BigDecimal("6"), new BigDecimal("1"), "UP", new BigDecimal("40.00"), true)
        );
        when(agileAnalyticsService.getSprintTrends(proyectoId, null, 3)).thenReturn(trends);
        when(geminiService.generate(anyString())).thenReturn("Calidad muestra tendencia al alza.");

        ChatRequest request = new ChatRequest("¿Qué métricas han mejorado?", proyectoId, null);
        ChatResponse response = service.chat(request, userId);

        assertThat(response.message()).isEqualTo("Calidad muestra tendencia al alza.");
        assertThat(response.hasData()).isTrue();

        verifyNoInteractions(aiAgentService);
        verifyNoInteractions(toolsService);
        verify(chatMessageRepo, never()).findByUserIdAndProyectoIdOrderByCreatedAtAsc(any(), any());
        verify(geminiService, times(1)).generate(anyString());
        verify(geminiService, never()).chatWithTools(any(), any(), any());
    }

    @Test
    void tendencias_sinSuficientesSprintsFinalizados_continuaPorGemini() {
        when(agileAnalyticsService.getSprintTrends(proyectoId, null, 3)).thenReturn(List.of());

        when(chatMessageRepo.findByUserIdAndProyectoIdOrderByCreatedAtAsc(userId, proyectoId))
                .thenReturn(List.of());
        when(toolsService.getAvailableTools()).thenReturn(List.of());
        when(aiAgentService.processMessage(any(), any(), any(), any()))
                .thenReturn(new AgentResponse("No hay suficientes sprints finalizados.", List.of(), false));

        ChatRequest request = new ChatRequest("¿Qué métricas han mejorado?", proyectoId, null);
        service.chat(request, userId);

        verify(aiAgentService).processMessage(any(), any(), any(), any());
        verify(geminiService, never()).generate(anyString());
    }

    @Test
    void tendencias_aislamientoEntreProyectos_soloConsultaElProyectoActivo() {
        UUID proyectoA = UUID.randomUUID();
        UUID proyectoB = UUID.randomUUID();
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoB, userId)).thenReturn(true);

        Proyecto proyectoBEntity = new Proyecto();
        proyectoBEntity.setId(proyectoB);
        proyectoBEntity.setNombre("Proyecto B");
        proyectoBEntity.setMetodo("scrum");
        proyectoBEntity.setTimeBoxSemanas(2);
        when(proyectoRepo.findById(proyectoB)).thenReturn(Optional.of(proyectoBEntity));

        List<TrendAnalysisDto> trendsB = List.of(new TrendAnalysisDto(proyectoB, "Calidad", 3,
                List.of(), new BigDecimal("6"), new BigDecimal("1"), "UP", new BigDecimal("20.00"), true));
        when(agileAnalyticsService.getSprintTrends(proyectoB, null, 3)).thenReturn(trendsB);
        when(geminiService.generate(anyString())).thenReturn("Tendencia de Proyecto B.");

        ChatRequest request = new ChatRequest("¿Qué métricas han mejorado?", proyectoB, null);
        ChatResponse response = service.chat(request, userId);

        assertThat(response.message()).isEqualTo("Tendencia de Proyecto B.");
        verify(agileAnalyticsService).getSprintTrends(proyectoB, null, 3);
        verify(agileAnalyticsService, never()).getSprintTrends(proyectoA, null, 3);
    }

    @Test
    void riesgos_conRiesgosDetectados_seResuelveConUnaSolaLlamadaGemini() {
        List<RiskDto> riesgos = List.of(new RiskDto(proyectoId, "DECLINING_METRIC", "HIGH",
                "Calidad en descenso", "Calidad disminuyó 25% en los últimos 3 sprints",
                "Calidad", Instant.now()));
        when(agileAnalyticsService.identifyRisks(proyectoId)).thenReturn(riesgos);
        when(geminiService.generate(anyString())).thenReturn("Se detectó un riesgo de calidad en descenso.");

        ChatRequest request = new ChatRequest("¿Qué riesgos detectas?", proyectoId, null);
        ChatResponse response = service.chat(request, userId);

        assertThat(response.message()).isEqualTo("Se detectó un riesgo de calidad en descenso.");
        assertThat(response.hasData()).isTrue();

        verifyNoInteractions(aiAgentService);
        verifyNoInteractions(toolsService);
        verify(chatMessageRepo, never()).findByUserIdAndProyectoIdOrderByCreatedAtAsc(any(), any());
        verify(geminiService, times(1)).generate(anyString());
        verify(geminiService, never()).chatWithTools(any(), any(), any());
    }

    @Test
    void riesgos_sinRiesgosDetectados_respondeLocalmenteSinLlamarAGemini() {
        when(agileAnalyticsService.identifyRisks(proyectoId)).thenReturn(List.of());

        ChatRequest request = new ChatRequest("¿Hay riesgos en el proyecto?", proyectoId, null);
        ChatResponse response = service.chat(request, userId);

        assertThat(response.message())
                .isEqualTo("No se detectaron riesgos según los datos disponibles del proyecto.");
        assertThat(response.hasData()).isTrue();

        verifyNoInteractions(aiAgentService);
        verifyNoInteractions(toolsService);
        verify(geminiService, never()).generate(anyString());
    }

    @Test
    void riesgos_aislamientoEntreProyectos_soloConsultaElProyectoActivo() {
        UUID proyectoA = UUID.randomUUID();
        UUID proyectoB = UUID.randomUUID();
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoB, userId)).thenReturn(true);

        Proyecto proyectoBEntity = new Proyecto();
        proyectoBEntity.setId(proyectoB);
        proyectoBEntity.setNombre("Proyecto B");
        proyectoBEntity.setMetodo("scrum");
        proyectoBEntity.setTimeBoxSemanas(2);
        when(proyectoRepo.findById(proyectoB)).thenReturn(Optional.of(proyectoBEntity));

        List<RiskDto> riesgosB = List.of(new RiskDto(proyectoB, "DECLINING_METRIC", "LOW",
                "Riesgo de B", "evidencia de B", "Calidad", Instant.now()));
        when(agileAnalyticsService.identifyRisks(proyectoB)).thenReturn(riesgosB);
        when(geminiService.generate(anyString())).thenReturn("Riesgo de Proyecto B.");

        ChatRequest request = new ChatRequest("¿Qué riesgos detectas?", proyectoB, null);
        ChatResponse response = service.chat(request, userId);

        assertThat(response.message()).isEqualTo("Riesgo de Proyecto B.");
        verify(agileAnalyticsService).identifyRisks(proyectoB);
        verify(agileAnalyticsService, never()).identifyRisks(proyectoA);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // FASE 12.6 — Tipo C: RECOMENDACIONES / RETROSPECTIVA
    // Datos deterministas (AgileAnalyticsService / AIRetrospectiveService) + máximo 1
    // GeminiService.generate(), 0 AIAgentService, 0 tools, sin recuperar historial.
    // ══════════════════════════════════════════════════════════════════════════════

    @Test
    void recomendaciones_conSoloMetricasActuales_seResuelveConUnaSolaLlamadaGemini() {
        UUID sprintActualId = UUID.randomUUID();
        Sprint sprintActual = sprintDe(sprintActualId, proyectoId, 1, "en_ejecucion");
        when(sprintRepo.findById(sprintActualId)).thenReturn(Optional.of(sprintActual));

        SprintMetricsSummaryDto metricas = new SprintMetricsSummaryDto(
                sprintActualId, 1, "Meta", "en_ejecucion", null, null, null,
                Map.of("Calidad", new BigDecimal("8")), 5, true);
        when(agileAnalyticsService.getSprintMetricsSummary(sprintActualId)).thenReturn(metricas);
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprintActual));
        when(agileAnalyticsService.getSprintTrends(proyectoId, null, 3)).thenReturn(List.of());
        when(agileAnalyticsService.identifyRisks(proyectoId)).thenReturn(List.of());
        when(geminiService.generate(anyString())).thenReturn("Resumen\n- Todo estable.");

        ChatRequest request = new ChatRequest("¿Qué deberíamos mejorar?", proyectoId, sprintActualId);
        ChatResponse response = service.chat(request, userId);

        assertThat(response.message()).isEqualTo("Resumen\n- Todo estable.");
        assertThat(response.hasData()).isTrue();

        verifyNoInteractions(aiAgentService);
        verifyNoInteractions(toolsService);
        verifyNoInteractions(aiRetrospectiveService);
        verify(chatMessageRepo, never()).findByUserIdAndProyectoIdOrderByCreatedAtAsc(any(), any());
        verify(geminiService, times(1)).generate(anyString());
        verify(geminiService, never()).chatWithTools(any(), any(), any());
    }

    @Test
    void recomendaciones_conComparacionTendenciasYRiesgos_incluyeTodoEnElPromptEnviadoAJava() {
        UUID sprintActualId = UUID.randomUUID();
        UUID sprintAnteriorId = UUID.randomUUID();
        Sprint sprintActual = sprintDe(sprintActualId, proyectoId, 2, "en_ejecucion");
        Sprint sprintAnterior = sprintDe(sprintAnteriorId, proyectoId, 1, "finalizado");

        when(sprintRepo.findById(sprintActualId)).thenReturn(Optional.of(sprintActual));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId))
                .thenReturn(List.of(sprintActual, sprintAnterior));

        SprintMetricsSummaryDto metricas = new SprintMetricsSummaryDto(
                sprintActualId, 2, "Meta", "en_ejecucion", null, null, null,
                Map.of("Calidad", new BigDecimal("7")), 5, true);
        when(agileAnalyticsService.getSprintMetricsSummary(sprintActualId)).thenReturn(metricas);

        SprintComparisonDto comparacion = new SprintComparisonDto(
                sprintAnteriorId, 1, sprintActualId, 2,
                Map.of("Calidad", new BigDecimal("5")), Map.of("Calidad", new BigDecimal("7")),
                Map.of("Calidad", new BigDecimal("2")), Map.of("Calidad", new BigDecimal("40.00")),
                Map.of("Calidad", "UP"), true);
        when(agileAnalyticsService.compareSprints(sprintAnteriorId, sprintActualId)).thenReturn(comparacion);

        List<TrendAnalysisDto> tendencias = List.of(new TrendAnalysisDto(
                proyectoId, "Calidad", 3, List.of(), new BigDecimal("6"),
                new BigDecimal("1"), "UP", new BigDecimal("40.00"), true));
        when(agileAnalyticsService.getSprintTrends(proyectoId, null, 3)).thenReturn(tendencias);

        List<RiskDto> riesgos = List.of(new RiskDto(proyectoId, "HIGH_VARIABILITY", "MEDIUM",
                "Variabilidad alta en Calidad", "Desviación estándar elevada", "Calidad", Instant.now()));
        when(agileAnalyticsService.identifyRisks(proyectoId)).thenReturn(riesgos);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        when(geminiService.generate(promptCaptor.capture()))
                .thenReturn("Resumen\n- Calidad subió de 5 a 7.");

        ChatRequest request = new ChatRequest("¿Qué recomendamos?", proyectoId, sprintActualId);
        ChatResponse response = service.chat(request, userId);

        assertThat(response.message()).isEqualTo("Resumen\n- Calidad subió de 5 a 7.");

        String prompt = promptCaptor.getValue();
        // El prompt contiene los datos determinados por Java: comparación, tendencia y riesgo.
        assertThat(prompt).contains("Calidad");
        assertThat(prompt).contains("5"); // valor del sprint anterior
        assertThat(prompt).contains("7"); // valor del sprint actual
        assertThat(prompt).contains("Variabilidad alta en Calidad");
        // La regla de no asumir "mejor/peor" está en la instrucción enviada a Gemini.
        assertThat(prompt).contains("NUNCA concluyas que el equipo 'mejoró' o 'empeoró'");

        verify(geminiService, times(1)).generate(anyString());
        verifyNoInteractions(aiAgentService);
        verifyNoInteractions(toolsService);
    }

    @Test
    void recomendaciones_sinDatosDisponiblesEnSprintActual_continuaPorGemini() {
        UUID sprintActualId = UUID.randomUUID();
        Sprint sprintActual = sprintDe(sprintActualId, proyectoId, 1, "en_ejecucion");
        when(sprintRepo.findById(sprintActualId)).thenReturn(Optional.of(sprintActual));

        SprintMetricsSummaryDto sinDatos = new SprintMetricsSummaryDto(
                sprintActualId, 1, "Meta", "en_ejecucion", null, null, null, Map.of(), 0, false);
        when(agileAnalyticsService.getSprintMetricsSummary(sprintActualId)).thenReturn(sinDatos);

        when(chatMessageRepo.findByUserIdAndProyectoIdOrderByCreatedAtAsc(userId, proyectoId))
                .thenReturn(List.of());
        when(toolsService.getAvailableTools()).thenReturn(List.of());
        when(aiAgentService.processMessage(any(), any(), any(), any()))
                .thenReturn(new AgentResponse("Aún no hay datos suficientes.", List.of(), false));

        ChatRequest request = new ChatRequest("¿Qué deberíamos mejorar?", proyectoId, sprintActualId);
        service.chat(request, userId);

        verify(aiAgentService).processMessage(any(), any(), any(), any());
        verify(geminiService, never()).generate(anyString());
    }

    @Test
    void recomendaciones_aislamientoEntreProyectos_soloConsultaElProyectoActivo() {
        UUID proyectoA = UUID.randomUUID();
        UUID proyectoB = UUID.randomUUID();
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoB, userId)).thenReturn(true);

        Proyecto proyectoBEntity = new Proyecto();
        proyectoBEntity.setId(proyectoB);
        proyectoBEntity.setNombre("Proyecto B");
        proyectoBEntity.setMetodo("scrum");
        proyectoBEntity.setTimeBoxSemanas(2);
        when(proyectoRepo.findById(proyectoB)).thenReturn(Optional.of(proyectoBEntity));

        UUID sprintActualB = UUID.randomUUID();
        Sprint actualB = sprintDe(sprintActualB, proyectoB, 1, "en_ejecucion");
        when(sprintRepo.findById(sprintActualB)).thenReturn(Optional.of(actualB));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoB)).thenReturn(List.of(actualB));

        SprintMetricsSummaryDto metricasB = new SprintMetricsSummaryDto(
                sprintActualB, 1, "Meta", "en_ejecucion", null, null, null,
                Map.of("Calidad", new BigDecimal("9")), 5, true);
        when(agileAnalyticsService.getSprintMetricsSummary(sprintActualB)).thenReturn(metricasB);
        when(agileAnalyticsService.getSprintTrends(proyectoB, null, 3)).thenReturn(List.of());
        when(agileAnalyticsService.identifyRisks(proyectoB)).thenReturn(List.of());
        when(geminiService.generate(anyString())).thenReturn("Recomendación de Proyecto B.");

        ChatRequest request = new ChatRequest("¿Qué deberíamos mejorar?", proyectoB, sprintActualB);
        ChatResponse response = service.chat(request, userId);

        assertThat(response.message()).isEqualTo("Recomendación de Proyecto B.");
        verify(agileAnalyticsService).getSprintMetricsSummary(sprintActualB);
        verify(agileAnalyticsService).identifyRisks(proyectoB);
        verify(agileAnalyticsService, never()).identifyRisks(proyectoA);
    }

    @Test
    void recomendaciones_alucinacionDeGemini_noAlteraLosDatosEnviadosNiGeneraSegundaLlamada() {
        UUID sprintActualId = UUID.randomUUID();
        Sprint sprintActual = sprintDe(sprintActualId, proyectoId, 1, "en_ejecucion");
        when(sprintRepo.findById(sprintActualId)).thenReturn(Optional.of(sprintActual));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(sprintActual));

        SprintMetricsSummaryDto metricas = new SprintMetricsSummaryDto(
                sprintActualId, 1, "Meta", "en_ejecucion", null, null, null,
                Map.of("Calidad", new BigDecimal("8")), 5, true);
        when(agileAnalyticsService.getSprintMetricsSummary(sprintActualId)).thenReturn(metricas);
        when(agileAnalyticsService.getSprintTrends(proyectoId, null, 3)).thenReturn(List.of());
        when(agileAnalyticsService.identifyRisks(proyectoId)).thenReturn(List.of());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        // Gemini "alucina" un sprint, un riesgo y valores que no existen en los datos reales.
        when(geminiService.generate(promptCaptor.capture())).thenReturn(
                "Resumen\n- El Sprint 99 tuvo Defectos: 500 y un riesgo crítico de seguridad inventado.");

        ChatRequest request = new ChatRequest("¿En qué deberíamos enfocarnos?", proyectoId, sprintActualId);
        ChatResponse response = service.chat(request, userId);

        // La respuesta final es literalmente la de Gemini (no se corrige ni se reintenta)...
        assertThat(response.message()).contains("Sprint 99");
        // ...pero el prompt que LE ENVIAMOS nosotros nunca contuvo ese sprint ni ese riesgo:
        // la alucinación es de Gemini, no de la capa determinística.
        String prompt = promptCaptor.getValue();
        assertThat(prompt).doesNotContain("Sprint 99");
        assertThat(prompt).doesNotContain("500");
        assertThat(prompt).doesNotContain("riesgo crítico de seguridad");
        assertThat(prompt).contains("No se detectaron riesgos según los datos disponibles");

        verify(geminiService, times(1)).generate(anyString());
        verifyNoInteractions(toolsService);
        verifyNoInteractions(aiAgentService);
    }

    @Test
    void retrospectiva_seResuelveDelegandoEnAIRetrospectiveServiceExistente() {
        UUID sprintActualId = UUID.randomUUID();
        Sprint sprintActual = sprintDe(sprintActualId, proyectoId, 3, "en_ejecucion");
        when(sprintRepo.findById(sprintActualId)).thenReturn(Optional.of(sprintActual));

        AIRetrospectiveDto retro = new AIRetrospectiveDto(
                sprintActualId, 3, "Meta del sprint",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7),
                List.of("El equipo cumplió el objetivo"),
                List.of("Registrar más métricas"),
                List.of("Riesgo de deuda técnica"),
                List.of("Revisar el proceso de QA"),
                List.of("¿Qué bloqueó al equipo?"),
                Instant.now());
        when(aiRetrospectiveService.generateRetrospective(sprintActualId, userId)).thenReturn(retro);

        ChatRequest request = new ChatRequest("¿Qué revisar en retrospectiva?", proyectoId, sprintActualId);
        ChatResponse response = service.chat(request, userId);

        assertThat(response.message()).contains("Retrospectiva del Sprint 3");
        assertThat(response.message()).contains("El equipo cumplió el objetivo");
        assertThat(response.message()).contains("Registrar más métricas");
        assertThat(response.message()).contains("Riesgo de deuda técnica");
        assertThat(response.message()).contains("Revisar el proceso de QA");
        assertThat(response.message()).contains("¿Qué bloqueó al equipo?");

        verify(aiRetrospectiveService, times(1)).generateRetrospective(sprintActualId, userId);
        verifyNoInteractions(aiAgentService);
        verifyNoInteractions(toolsService);
        verifyNoInteractions(geminiService); // la llamada a Gemini, si existe, ocurre DENTRO de
                                              // AIRetrospectiveService (ya mockeado), nunca aquí.
        verify(chatMessageRepo, never()).findByUserIdAndProyectoIdOrderByCreatedAtAsc(any(), any());
    }

    @Test
    void retrospectiva_siAIRetrospectiveServiceFalla_continuaPorElFlujoActual() {
        UUID sprintActualId = UUID.randomUUID();
        Sprint sprintActual = sprintDe(sprintActualId, proyectoId, 1, "en_ejecucion");
        when(sprintRepo.findById(sprintActualId)).thenReturn(Optional.of(sprintActual));
        when(aiRetrospectiveService.generateRetrospective(sprintActualId, userId))
                .thenThrow(new IllegalArgumentException("Sprint no encontrado"));

        when(chatMessageRepo.findByUserIdAndProyectoIdOrderByCreatedAtAsc(userId, proyectoId))
                .thenReturn(List.of());
        when(toolsService.getAvailableTools()).thenReturn(List.of());
        when(aiAgentService.processMessage(any(), any(), any(), any()))
                .thenReturn(new AgentResponse("Continuemos por el flujo normal.", List.of(), false));

        ChatRequest request = new ChatRequest("¿Qué revisar en retrospectiva?", proyectoId, sprintActualId);
        ChatResponse response = service.chat(request, userId);

        assertThat(response.message()).isEqualTo("Continuemos por el flujo normal.");
        verify(aiAgentService).processMessage(any(), any(), any(), any());
    }

    @Test
    void retrospectiva_aislamientoPorProyectoYSprint_soloUsaElSprintDeLaRequestActiva() {
        UUID proyectoB = UUID.randomUUID();
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoB, userId)).thenReturn(true);

        Proyecto proyectoBEntity = new Proyecto();
        proyectoBEntity.setId(proyectoB);
        proyectoBEntity.setNombre("Proyecto B");
        proyectoBEntity.setMetodo("scrum");
        proyectoBEntity.setTimeBoxSemanas(2);
        when(proyectoRepo.findById(proyectoB)).thenReturn(Optional.of(proyectoBEntity));

        UUID sprintB = UUID.randomUUID();
        Sprint sprintDeB = sprintDe(sprintB, proyectoB, 1, "en_ejecucion");
        when(sprintRepo.findById(sprintB)).thenReturn(Optional.of(sprintDeB));

        AIRetrospectiveDto retroB = new AIRetrospectiveDto(
                sprintB, 1, "Meta B", LocalDate.now(), LocalDate.now().plusDays(6),
                List.of("Bien en B"), List.of("Mejorar en B"), List.of(), List.of("Sugerencia B"),
                List.of("¿Pregunta B?"), Instant.now());
        when(aiRetrospectiveService.generateRetrospective(sprintB, userId)).thenReturn(retroB);

        ChatRequest request = new ChatRequest("¿Qué revisar en retrospectiva?", proyectoB, sprintB);
        ChatResponse response = service.chat(request, userId);

        assertThat(response.message()).contains("Bien en B");
        verify(aiRetrospectiveService).generateRetrospective(sprintB, userId);
        verify(aiRetrospectiveService, times(1)).generateRetrospective(any(), any());
    }
}
