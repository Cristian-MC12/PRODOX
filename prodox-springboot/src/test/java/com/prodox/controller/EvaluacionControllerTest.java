// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.controller;

import com.prodox.entity.Sprint;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.repository.SprintRepository;
import com.prodox.service.EvaluacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Auditoría transversal: ninguno de los 3 endpoints validaba membresía al proyecto
 * — cualquier usuario autenticado podía consultar la evaluación de cualquier
 * proyecto (o de un sprint de cualquier proyecto) conociendo su UUID.
 * EvaluacionService se deja intacto (también lo usa AgileAnalyticsService
 * internamente); la autorización se agrega solo en este controller.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EvaluacionController — autorización")
class EvaluacionControllerTest {

    @Mock private EvaluacionService evaluacionService;
    @Mock private ProjectMemberRepository projectMemberRepo;
    @Mock private SprintRepository sprintRepo;

    private EvaluacionController controller;

    private UUID proyectoId;
    private UUID sprintId;
    private Authentication authExterno;
    private Authentication authMiembro;

    @BeforeEach
    void setUp() {
        controller = new EvaluacionController(evaluacionService, projectMemberRepo, sprintRepo);
        proyectoId = UUID.randomUUID();
        sprintId = UUID.randomUUID();
        authExterno = new UsernamePasswordAuthenticationToken("externo", null, List.of());
        authMiembro = new UsernamePasswordAuthenticationToken("miembro-a", null, List.of());
    }

    @Test
    @DisplayName("porProyecto: usuario externo lanza SecurityException")
    void porProyecto_usuarioExterno_lanzaSecurityException() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, "externo")).thenReturn(false);

        assertThatThrownBy(() -> controller.porProyecto(proyectoId, authExterno))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("porProyecto: miembro del proyecto es permitido")
    void porProyecto_miembroDelProyecto_permitido() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, "miembro-a")).thenReturn(true);
        when(evaluacionService.evaluar(proyectoId)).thenReturn(List.of());

        controller.porProyecto(proyectoId, authMiembro);
        // No lanza excepción — delega correctamente en el servicio.
    }

    @Test
    @DisplayName("detalle: usuario externo lanza SecurityException")
    void detalle_usuarioExterno_lanzaSecurityException() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, "externo")).thenReturn(false);

        assertThatThrownBy(() -> controller.detalle(proyectoId, authExterno))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("porSprint: resuelve el proyecto del sprint y valida membresía contra ESE proyecto")
    void porSprint_resuelveProyectoDelSprint_yValidaMembresía() {
        Sprint sprint = new Sprint();
        sprint.setId(sprintId);
        sprint.setProyectoId(proyectoId);
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, "externo")).thenReturn(false);

        assertThatThrownBy(() -> controller.porSprint(sprintId, authExterno))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("porSprint: miembro del proyecto del sprint es permitido")
    void porSprint_miembroDelProyectoDelSprint_permitido() {
        Sprint sprint = new Sprint();
        sprint.setId(sprintId);
        sprint.setProyectoId(proyectoId);
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, "miembro-a")).thenReturn(true);
        when(evaluacionService.evaluarSprint(sprintId)).thenReturn(List.of());

        controller.porSprint(sprintId, authMiembro);
    }

    @Test
    @DisplayName("porSprint: sprint inexistente lanza IllegalArgumentException sin consultar membresía")
    void porSprint_sprintInexistente_lanzaIllegalArgumentException() {
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.porSprint(sprintId, authExterno))
                .isInstanceOf(IllegalArgumentException.class);

        org.mockito.Mockito.verifyNoInteractions(projectMemberRepo);
    }
}
