// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpdia.dto.CalcularMetricaRequest;
import com.mpdia.entity.Proyecto;
import com.mpdia.entity.Sprint;
import com.mpdia.formula.FormulaEvaluator;
import com.mpdia.repository.MetricaRepository;
import com.mpdia.repository.MetricParametrizacionRepository;
import com.mpdia.repository.ProjectMemberRepository;
import com.mpdia.repository.ProyectoRepository;
import com.mpdia.repository.RegistroValorRepository;
import com.mpdia.repository.ResultadoMetricaRepository;
import com.mpdia.repository.SprintRepository;
import com.mpdia.repository.VariableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Auditoría transversal: calcularMetrica() recibía un parámetro userId que nunca se
 * usaba — cualquier usuario autenticado podía calcular una métrica de cualquier
 * proyecto conociendo su UUID, pese a que el sprint↔proyecto ya se validaba
 * correctamente entre sí. Estas pruebas cubren solo el nuevo chequeo de membresía;
 * el motor de cálculo en sí ya tiene cobertura propia y no se modifica aquí.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CalculoMetricaService — autorización")
class CalculoMetricaServiceTest {

    @Mock private MetricaRepository metricaRepo;
    @Mock private ProyectoRepository proyectoRepo;
    @Mock private SprintRepository sprintRepo;
    @Mock private MetricParametrizacionRepository parametrizacionRepo;
    @Mock private VariableRepository variableRepo;
    @Mock private RegistroValorRepository registroRepo;
    @Mock private ResultadoMetricaRepository resultadoRepo;
    @Mock private ProjectMemberRepository projectMemberRepo;

    private CalculoMetricaService service;

    private UUID metricaId;
    private UUID proyectoId;
    private UUID sprintId;

    @BeforeEach
    void setUp() {
        service = new CalculoMetricaService(
                metricaRepo, proyectoRepo, sprintRepo, parametrizacionRepo,
                variableRepo, registroRepo, resultadoRepo, projectMemberRepo,
                new FormulaEvaluator(), new ObjectMapper());

        metricaId  = UUID.randomUUID();
        proyectoId = UUID.randomUUID();
        sprintId   = UUID.randomUUID();

        Proyecto proyecto = new Proyecto();
        proyecto.setId(proyectoId);
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));

        Sprint sprint = new Sprint();
        sprint.setId(sprintId);
        sprint.setProyectoId(proyectoId);
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));

        when(metricaRepo.findById(metricaId)).thenReturn(Optional.of(new com.mpdia.entity.Metrica()));
    }

    @Test
    @DisplayName("calcularMetrica: usuario externo al proyecto lanza SecurityException")
    void calcularMetrica_usuarioExterno_lanzaSecurityException() {
        String externoId = "externo-1";
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, externoId)).thenReturn(false);

        CalcularMetricaRequest req = new CalcularMetricaRequest(proyectoId, sprintId);

        assertThatThrownBy(() -> service.calcularMetrica(metricaId, req, externoId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("No tienes acceso a este proyecto");

        // Nunca llega a buscar la parametrización — la autorización corta el flujo antes.
        org.mockito.Mockito.verifyNoInteractions(parametrizacionRepo);
    }

    @Test
    @DisplayName("calcularMetrica: miembro del proyecto pasa la validación de autorización")
    void calcularMetrica_miembroDelProyecto_pasaValidacion() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, "user-a")).thenReturn(true);
        when(parametrizacionRepo.findUltimaVersionAprobada(metricaId, proyectoId))
                .thenReturn(Optional.empty());

        CalcularMetricaRequest req = new CalcularMetricaRequest(proyectoId, sprintId);

        // Pasa la autorización y llega a la siguiente validación de negocio
        // (parametrización no encontrada), sin lanzar SecurityException.
        assertThatThrownBy(() -> service.calcularMetrica(metricaId, req, "user-a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No existe parametrización aprobada");
    }

    @Test
    @DisplayName("calcularMetrica: sprint de otro proyecto se rechaza antes de validar membresía")
    void calcularMetrica_sprintDeOtroProyecto_seRechazaAntesDeMembresía() {
        UUID otroProyecto = UUID.randomUUID();
        Sprint sprintDeOtroProyecto = new Sprint();
        sprintDeOtroProyecto.setId(sprintId);
        sprintDeOtroProyecto.setProyectoId(otroProyecto);
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintDeOtroProyecto));

        CalcularMetricaRequest req = new CalcularMetricaRequest(proyectoId, sprintId);

        assertThatThrownBy(() -> service.calcularMetrica(metricaId, req, "cualquiera"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El sprint no pertenece al proyecto");

        org.mockito.Mockito.verifyNoInteractions(projectMemberRepo);
    }
}
