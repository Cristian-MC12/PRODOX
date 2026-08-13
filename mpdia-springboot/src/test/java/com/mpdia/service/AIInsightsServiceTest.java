// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpdia.dto.ai.AIInsightDto;
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

        List<AIInsightDto> resultado = service.generateInsights(proyectoId, userId);

        assertThat(resultado).isEmpty();
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

        List<AIInsightDto> resultado = service.generateInsights(proyectoId, userId);

        assertThat(resultado).isEmpty();
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

        List<AIInsightDto> resultado = service.generateInsights(proyectoId, userId);

        // Debe generar al menos insights de comparación
        verify(analyticsService).compareSprints(any(), any());
        verify(geminiService, atLeastOnce()).generate(anyString());
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
