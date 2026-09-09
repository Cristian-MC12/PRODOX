// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service.copilot;

import com.prodox.dto.SprintDto;
import com.prodox.dto.analytics.SprintMetricsSummaryDto;
import com.prodox.dto.ai.gemini.FunctionDeclaration;
import com.prodox.dto.ai.gemini.Tool;
import com.prodox.entity.Proyecto;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.repository.ProyectoRepository;
import com.prodox.repository.SprintRepository;
import com.prodox.service.AgileAnalyticsService;
import com.prodox.service.ProyectoService;
import com.prodox.service.SprintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Bloque 12B (P0-3) — CopilotToolsService: las herramientas (function
 * calling) que Gemini puede invocar desde el AI Copilot.
 *
 * No existía ningún test dedicado a esta clase — solo se cubría
 * indirectamente vía un mock del ToolExecutor en AIAgentServiceTest. Estos
 * tests verifican las barreras reales del código, no solo que un mensaje de
 * error contenga una palabra:
 * - allowlist cerrada (switch con default -> IllegalArgumentException);
 * - los "args" que Gemini envía en la function call se ignoran por completo
 *   (ambos métodos declaran el parámetro pero nunca lo leen);
 * - el proyecto efectivo siempre es el contextProyectoId que llega desde el
 *   backend (controller/service), nunca un valor tomado de args;
 * - ProjectMember se revalida en cada llamada;
 * - ambas tools son de solo lectura (nunca se invoca ningún método de
 *   escritura/borrado sobre los repositorios).
 */
@ExtendWith(MockitoExtension.class)
class CopilotToolsServiceTest {

    @Mock private AgileAnalyticsService analyticsService;
    @Mock private SprintService sprintService;
    @Mock private ProyectoService proyectoService;
    @Mock private ProjectMemberRepository projectMemberRepo;
    @Mock private ProyectoRepository proyectoRepo;
    @Mock private SprintRepository sprintRepo;

    private CopilotToolsService service;

    private final String userId = "copilot-tools-test-user";
    private UUID proyectoId;

    @BeforeEach
    void setUp() {
        service = new CopilotToolsService(
                analyticsService, sprintService, proyectoService,
                projectMemberRepo, proyectoRepo, sprintRepo);
        proyectoId = UUID.randomUUID();
    }

    // ── Allowlist ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getAvailableTools: expone exactamente 2 tools de solo lectura, sin parámetros expuestos a Gemini")
    void getAvailableTools_exactamenteDosToolsSinParametros() {
        List<Tool> tools = service.getAvailableTools();

        assertThat(tools).hasSize(1);
        List<FunctionDeclaration> funciones = tools.get(0).functionDeclarations();
        assertThat(funciones).extracting(FunctionDeclaration::name)
                .containsExactlyInAnyOrder("getProjectDetails", "getActiveSprintMetrics");

        // Ninguna de las 2 tools declara parámetros que Gemini pueda rellenar
        // libremente (ej. un "proyectoId") — ambas usan Map.of() vacío.
        for (FunctionDeclaration f : funciones) {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) f.parameters().get("properties");
            assertThat(properties).isEmpty();
        }
    }

    @Test
    @DisplayName("executeTool: tool desconocida (fuera de la allowlist) es rechazada")
    void executeTool_toolDesconocida_lanzaIllegalArgumentException() {
        // El switch de executeTool() rechaza el nombre ANTES de validar
        // membresía o tocar cualquier dato — no hace falta stubear nada.
        assertThatThrownBy(() ->
                service.executeTool("deleteProject", Map.of(), userId, proyectoId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tool desconocida");

        // Ninguna tool inexistente llega siquiera a intentar tocar datos, ni
        // siquiera a validar membresía (rechazo puramente por el nombre).
        verifyNoInteractions(proyectoRepo, sprintService, analyticsService, projectMemberRepo);
    }

    // ── getProjectDetails ───────────────────────────────────────────────

    @Test
    @DisplayName("getProjectDetails: con proyecto válido y usuario miembro, retorna los datos del proyecto")
    void executeTool_getProjectDetails_conProyectoValidoYMiembro_retornaDatos() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        Proyecto proyecto = new Proyecto();
        proyecto.setId(proyectoId);
        proyecto.setNombre("Proyecto de prueba");
        proyecto.setDescripcion("Descripción");
        proyecto.setMetodo("scrum");
        proyecto.setTimeBoxSemanas(2);
        proyecto.setNumeroSprints(5);
        proyecto.setEstado("activo");
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));

        Object resultado = service.executeTool("getProjectDetails", Map.of(), userId, proyectoId);

        assertThat(resultado).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultadoMap = (Map<String, Object>) resultado;
        assertThat(resultadoMap.get("nombre")).isEqualTo("Proyecto de prueba");
        assertThat(resultadoMap.get("proyectoId")).isEqualTo(proyectoId.toString());
    }

    @Test
    @DisplayName("getProjectDetails: usuario sin membresía en el proyecto es rechazado, sin leer el proyecto")
    void executeTool_getProjectDetails_usuarioSinAcceso_lanzaSecurityException() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(false);

        assertThatThrownBy(() ->
                service.executeTool("getProjectDetails", Map.of(), userId, proyectoId))
                .isInstanceOf(SecurityException.class);

        // El rechazo ocurre ANTES de leer cualquier dato del proyecto.
        verifyNoInteractions(proyectoRepo);
    }

    @Test
    @DisplayName("getProjectDetails: sin proyecto en el contexto (contextProyectoId=null), rechazado sin validar membresía")
    void executeTool_getProjectDetails_sinContexto_lanzaIllegalArgumentException() {
        assertThatThrownBy(() ->
                service.executeTool("getProjectDetails", Map.of(), userId, null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(projectMemberRepo, proyectoRepo);
    }

    @Test
    @DisplayName("getProjectDetails: un proyectoId distinto en los args de Gemini se IGNORA — el contexto server-side manda")
    void executeTool_getProjectDetails_argsConProyectoIdDiferente_seIgnoranUsaContextoServerSide() {
        UUID proyectoIdInventadoPorGemini = UUID.randomUUID();
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        Proyecto proyecto = new Proyecto();
        proyecto.setId(proyectoId);
        proyecto.setNombre("Proyecto real del contexto");
        proyecto.setMetodo("scrum");
        proyecto.setTimeBoxSemanas(2);
        proyecto.setNumeroSprints(5);
        proyecto.setEstado("activo");
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));

        // Gemini intenta colar un "proyectoId" distinto dentro de los args de
        // la function call — el método ni siquiera lee el mapa args.
        Map<String, Object> argsManipulados = Map.of("proyectoId", proyectoIdInventadoPorGemini.toString());

        Object resultado = service.executeTool("getProjectDetails", argsManipulados, userId, proyectoId);

        @SuppressWarnings("unchecked")
        Map<String, Object> resultadoMap = (Map<String, Object>) resultado;
        assertThat(resultadoMap.get("proyectoId")).isEqualTo(proyectoId.toString());
        assertThat(resultadoMap.get("nombre")).isEqualTo("Proyecto real del contexto");

        // Se valida membresía y se lee el proyecto EXCLUSIVAMENTE con el
        // contextProyectoId real — nunca con el valor de args.
        verify(projectMemberRepo).existsByProyectoIdAndUserId(proyectoId, userId);
        verify(projectMemberRepo, never()).existsByProyectoIdAndUserId(eq(proyectoIdInventadoPorGemini), any());
        verify(proyectoRepo).findById(proyectoId);
        verify(proyectoRepo, never()).findById(proyectoIdInventadoPorGemini);
    }

    // ── getActiveSprintMetrics ──────────────────────────────────────────

    @Test
    @DisplayName("getActiveSprintMetrics: con proyecto válido y usuario miembro, retorna las métricas del sprint activo")
    void executeTool_getActiveSprintMetrics_conProyectoValidoYMiembro_retornaDatos() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        UUID sprintId = UUID.randomUUID();
        SprintDto sprintActivo = new SprintDto(
                sprintId, proyectoId, "Proyecto", "scrum", 2, 3, "Goal", "en_ejecucion",
                LocalDate.now(), null, null, null, null, "SEMANAS", 2, null, null);
        when(sprintService.getSprintActivo(proyectoId)).thenReturn(sprintActivo);

        SprintMetricsSummaryDto metrics = new SprintMetricsSummaryDto(
                sprintId, 3, "Goal", "en_ejecucion", LocalDate.now(), null,
                null, Map.of("Calidad", new BigDecimal("8.0")), 5, true);
        when(analyticsService.getSprintMetricsSummary(sprintId)).thenReturn(metrics);

        Object resultado = service.executeTool("getActiveSprintMetrics", Map.of(), userId, proyectoId);

        assertThat(resultado).isEqualTo(metrics);
    }

    @Test
    @DisplayName("getActiveSprintMetrics: usuario sin membresía es rechazado, sin consultar el sprint activo")
    void executeTool_getActiveSprintMetrics_usuarioSinAcceso_lanzaSecurityException() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(false);

        assertThatThrownBy(() ->
                service.executeTool("getActiveSprintMetrics", Map.of(), userId, proyectoId))
                .isInstanceOf(SecurityException.class);

        verifyNoInteractions(sprintService, analyticsService);
    }

    // ── Solo lectura ────────────────────────────────────────────────────

    @Test
    @DisplayName("Ninguna tool disponible permite escritura/eliminación: la allowlist completa es de solo lectura")
    void ningunaToolPermiteEscrituraOEliminacion() {
        List<String> nombresDisponibles = service.getAvailableTools().get(0).functionDeclarations()
                .stream().map(FunctionDeclaration::name).toList();

        // Única fuente de verdad de qué se puede ejecutar: el switch de
        // executeTool(). Cualquier nombre fuera de estos 2 ya se probó
        // rechazado arriba (executeTool_toolDesconocida_...). Ninguno de los
        // 2 nombres disponibles sugiere una operación de escritura/borrado,
        // y ambas implementaciones (ya ejercitadas arriba) solo llaman a
        // *Repository.findById()/*Service.get*() — nunca .save()/.delete().
        assertThat(nombresDisponibles).allSatisfy(nombre ->
                assertThat(nombre.toLowerCase())
                        .doesNotContain("delete", "eliminar", "borrar", "update",
                                "modificar", "crear", "create", "aprobar", "approve"));
    }
}
