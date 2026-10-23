// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodox.dto.CrearEvaluacionObjetivoRequest;
import com.prodox.dto.EvaluacionObjetivoDto;
import com.prodox.entity.EvaluacionObjetivo;
import com.prodox.entity.ProjectMember;
import com.prodox.entity.Sprint;
import com.prodox.repository.EvaluacionObjetivoRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para EvaluacionObjetivoService.
 * 
 * FASE 6B: evaluación manual del cumplimiento del objetivo del sprint.
 * Tests puros con Mockito, sin iniciar Spring Boot ni conectar a base de datos.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EvaluacionObjetivoService — pruebas unitarias")
class EvaluacionObjetivoServiceTest {

    @Mock SprintRepository sprintRepo;
    @Mock ProjectMemberRepository projectMemberRepo;
    @Mock EvaluacionObjetivoRepository evaluacionRepo;

    EvaluacionObjetivoService service;
    ObjectMapper objectMapper;

    private UUID proyectoId;
    private UUID sprintId;
    private String userId;
    private Sprint sprint;
    private ProjectMember scrumMaster;

    @BeforeEach
    void setUp() {
        proyectoId = UUID.randomUUID();
        sprintId = UUID.randomUUID();
        userId = "user-scrum-master-1";

        sprint = new Sprint();
        sprint.setId(sprintId);
        sprint.setProyectoId(proyectoId);
        sprint.setNumero(1);
        sprint.setSprintGoal("Objetivo del sprint 1");
        sprint.setEstado("finalizado");

        scrumMaster = new ProjectMember();
        scrumMaster.setProyectoId(proyectoId);
        scrumMaster.setUserId(userId);
        scrumMaster.setRol(ProjectMember.ROL_SCRUM_MASTER);

        // Usar ObjectMapper real en lugar de mock para evitar NullPointerException
        // ObjectMapper es una dependencia estable que no necesita ser mockeada
        objectMapper = new ObjectMapper();
        service = new EvaluacionObjetivoService(
                evaluacionRepo,
                sprintRepo,
                projectMemberRepo,
                objectMapper
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CREAR EVALUACIÓN
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("crear: crea evaluación con estado cumplido y justificación")
    void crear_cumplido_creaCorrectamente() throws Exception {
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, userId))
                .thenReturn(Optional.of(scrumMaster));
        when(evaluacionRepo.existsBySprintId(sprintId)).thenReturn(false);

        EvaluacionObjetivo evaluacionGuardada = new EvaluacionObjetivo();
        evaluacionGuardada.setId(UUID.randomUUID());
        evaluacionGuardada.setSprintId(sprintId);
        evaluacionGuardada.setEstado("cumplido");
        evaluacionGuardada.setJustificacion("Se completaron todas las historias planificadas");
        evaluacionGuardada.setEvaluadoPor(userId);
        evaluacionGuardada.setEvaluadoAt(Instant.now());

        when(evaluacionRepo.save(any(EvaluacionObjetivo.class))).thenReturn(evaluacionGuardada);

        CrearEvaluacionObjetivoRequest request = new CrearEvaluacionObjetivoRequest(
                "cumplido",
                "Se completaron todas las historias planificadas",
                null
        );

        EvaluacionObjetivoDto resultado = service.crear(sprintId, request, userId);

        assertThat(resultado).isNotNull();
        assertThat(resultado.sprintId()).isEqualTo(sprintId);
        assertThat(resultado.estado()).isEqualTo("cumplido");
        assertThat(resultado.justificacion()).isEqualTo("Se completaron todas las historias planificadas");
        assertThat(resultado.evaluadoPor()).isEqualTo(userId);

        ArgumentCaptor<EvaluacionObjetivo> captor = ArgumentCaptor.forClass(EvaluacionObjetivo.class);
        verify(evaluacionRepo).save(captor.capture());
        
        EvaluacionObjetivo evaluacionGuardadaCapturada = captor.getValue();
        assertThat(evaluacionGuardadaCapturada.getSprintId()).isEqualTo(sprintId);
        assertThat(evaluacionGuardadaCapturada.getEstado()).isEqualTo("cumplido");
        assertThat(evaluacionGuardadaCapturada.getEvaluadoPor()).isEqualTo(userId);
    }

    @Test
    @DisplayName("crear: crea evaluación parcialmente_cumplido con evidencias")
    void crear_parcialmenteCumplido_conEvidencias() throws Exception {
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, userId))
                .thenReturn(Optional.of(scrumMaster));
        when(evaluacionRepo.existsBySprintId(sprintId)).thenReturn(false);

        List<String> evidencias = List.of("https://demo.example.com", "https://docs.example.com");

        EvaluacionObjetivo evaluacionGuardada = new EvaluacionObjetivo();
        evaluacionGuardada.setId(UUID.randomUUID());
        evaluacionGuardada.setSprintId(sprintId);
        evaluacionGuardada.setEstado("parcialmente_cumplido");
        evaluacionGuardada.setJustificacion("Se logró 80% del objetivo");
        evaluacionGuardada.setEvidenciaUrls("[\"https://demo.example.com\",\"https://docs.example.com\"]");
        evaluacionGuardada.setEvaluadoPor(userId);
        evaluacionGuardada.setEvaluadoAt(Instant.now());

        when(evaluacionRepo.save(any(EvaluacionObjetivo.class))).thenReturn(evaluacionGuardada);

        CrearEvaluacionObjetivoRequest request = new CrearEvaluacionObjetivoRequest(
                "parcialmente_cumplido",
                "Se logró 80% del objetivo",
                evidencias
        );

        EvaluacionObjetivoDto resultado = service.crear(sprintId, request, userId);

        assertThat(resultado.estado()).isEqualTo("parcialmente_cumplido");
        assertThat(resultado.evidenciaUrls()).hasSize(2);
        assertThat(resultado.evidenciaUrls()).contains("https://demo.example.com", "https://docs.example.com");
    }

    @Test
    @DisplayName("crear: lanza exception si sprint no existe")
    void crear_sprintInexistente_lanzaException() {
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.empty());

        CrearEvaluacionObjetivoRequest request = new CrearEvaluacionObjetivoRequest(
                "cumplido",
                "Justificación válida",
                null
        );

        assertThatThrownBy(() -> service.crear(sprintId, request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sprint no encontrado");

        verify(sprintRepo).findById(sprintId);
        verifyNoInteractions(evaluacionRepo);
    }

    @Test
    @DisplayName("crear: lanza exception si sprint no está finalizado")
    void crear_sprintEnEjecucion_lanzaException() {
        sprint.setEstado("en_ejecucion");
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));

        CrearEvaluacionObjetivoRequest request = new CrearEvaluacionObjetivoRequest(
                "cumplido",
                "Justificación válida",
                null
        );

        assertThatThrownBy(() -> service.crear(sprintId, request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Solo se pueden evaluar sprints finalizados o reabiertos");

        verify(sprintRepo).findById(sprintId);
        verifyNoInteractions(projectMemberRepo);
        verifyNoInteractions(evaluacionRepo);
    }

    @Test
    @DisplayName("crear: permite evaluar sprint reabierto")
    void crear_sprintReabierto_permite() throws Exception {
        sprint.setEstado("reabierto");
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, userId))
                .thenReturn(Optional.of(scrumMaster));
        when(evaluacionRepo.existsBySprintId(sprintId)).thenReturn(false);

        EvaluacionObjetivo evaluacionGuardada = new EvaluacionObjetivo();
        evaluacionGuardada.setId(UUID.randomUUID());
        evaluacionGuardada.setSprintId(sprintId);
        evaluacionGuardada.setEstado("no_cumplido");
        evaluacionGuardada.setJustificacion("Hubo impedimentos técnicos");
        evaluacionGuardada.setEvaluadoPor(userId);
        evaluacionGuardada.setEvaluadoAt(Instant.now());

        when(evaluacionRepo.save(any(EvaluacionObjetivo.class))).thenReturn(evaluacionGuardada);

        CrearEvaluacionObjetivoRequest request = new CrearEvaluacionObjetivoRequest(
                "no_cumplido",
                "Hubo impedimentos técnicos",
                null
        );

        EvaluacionObjetivoDto resultado = service.crear(sprintId, request, userId);

        assertThat(resultado.estado()).isEqualTo("no_cumplido");
        verify(evaluacionRepo).save(any(EvaluacionObjetivo.class));
    }

    @Test
    @DisplayName("crear: lanza exception si usuario no es Scrum Master")
    void crear_usuarioNoEsScrumMaster_lanzaException() {
        ProjectMember member = new ProjectMember();
        member.setProyectoId(proyectoId);
        member.setUserId(userId);
        member.setRol(ProjectMember.ROL_SCRUM_MEMBER); // No es Scrum Master

        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, userId))
                .thenReturn(Optional.of(member));

        CrearEvaluacionObjetivoRequest request = new CrearEvaluacionObjetivoRequest(
                "cumplido",
                "Justificación válida",
                null
        );

        assertThatThrownBy(() -> service.crear(sprintId, request, userId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Solo el Scrum Master del proyecto puede evaluar");

        verify(projectMemberRepo).findByProyectoIdAndUserId(proyectoId, userId);
        verifyNoInteractions(evaluacionRepo);
    }

    @Test
    @DisplayName("crear: lanza exception si usuario no es miembro del proyecto")
    void crear_usuarioNoEsMiembro_lanzaException() {
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, userId))
                .thenReturn(Optional.empty());

        CrearEvaluacionObjetivoRequest request = new CrearEvaluacionObjetivoRequest(
                "cumplido",
                "Justificación válida",
                null
        );

        assertThatThrownBy(() -> service.crear(sprintId, request, userId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("No tienes acceso a este proyecto");

        verify(projectMemberRepo).findByProyectoIdAndUserId(proyectoId, userId);
        verifyNoInteractions(evaluacionRepo);
    }

    @Test
    @DisplayName("crear: lanza exception si ya existe evaluación")
    void crear_evaluacionDuplicada_lanzaException() {
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, userId))
                .thenReturn(Optional.of(scrumMaster));
        when(evaluacionRepo.existsBySprintId(sprintId)).thenReturn(true);

        CrearEvaluacionObjetivoRequest request = new CrearEvaluacionObjetivoRequest(
                "cumplido",
                "Justificación válida",
                null
        );

        assertThatThrownBy(() -> service.crear(sprintId, request, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ya existe una evaluación para este sprint");

        verify(evaluacionRepo).existsBySprintId(sprintId);
        verify(evaluacionRepo, never()).save(any());
    }

    @Test
    @DisplayName("crear: estado no_evaluable con justificación adecuada")
    void crear_noEvaluable_creaCorrectamente() throws Exception {
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, userId))
                .thenReturn(Optional.of(scrumMaster));
        when(evaluacionRepo.existsBySprintId(sprintId)).thenReturn(false);

        EvaluacionObjetivo evaluacionGuardada = new EvaluacionObjetivo();
        evaluacionGuardada.setId(UUID.randomUUID());
        evaluacionGuardada.setSprintId(sprintId);
        evaluacionGuardada.setEstado("no_evaluable");
        evaluacionGuardada.setJustificacion("El contexto cambió drásticamente a mitad del sprint");
        evaluacionGuardada.setEvaluadoPor(userId);
        evaluacionGuardada.setEvaluadoAt(Instant.now());

        when(evaluacionRepo.save(any(EvaluacionObjetivo.class))).thenReturn(evaluacionGuardada);

        CrearEvaluacionObjetivoRequest request = new CrearEvaluacionObjetivoRequest(
                "no_evaluable",
                "El contexto cambió drásticamente a mitad del sprint",
                null
        );

        EvaluacionObjetivoDto resultado = service.crear(sprintId, request, userId);

        assertThat(resultado.estado()).isEqualTo("no_evaluable");
        assertThat(resultado.justificacion()).contains("contexto cambió");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ACTUALIZAR EVALUACIÓN
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("actualizar: actualiza evaluación existente conservando creador")
    void actualizar_evaluacionExistente_actualizaCorrectamente() throws Exception {
        EvaluacionObjetivo evaluacionExistente = new EvaluacionObjetivo();
        evaluacionExistente.setId(UUID.randomUUID());
        evaluacionExistente.setSprintId(sprintId);
        evaluacionExistente.setEstado("parcialmente_cumplido");
        evaluacionExistente.setJustificacion("Justificación original");
        evaluacionExistente.setEvaluadoPor("usuario-original");
        evaluacionExistente.setEvaluadoAt(Instant.now().minusSeconds(3600));

        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, userId))
                .thenReturn(Optional.of(scrumMaster));
        when(evaluacionRepo.findBySprintId(sprintId)).thenReturn(Optional.of(evaluacionExistente));

        EvaluacionObjetivo evaluacionActualizada = new EvaluacionObjetivo();
        evaluacionActualizada.setId(evaluacionExistente.getId());
        evaluacionActualizada.setSprintId(sprintId);
        evaluacionActualizada.setEstado("cumplido");
        evaluacionActualizada.setJustificacion("Nueva justificación");
        evaluacionActualizada.setEvaluadoPor("usuario-original"); // Conservado
        evaluacionActualizada.setEvaluadoAt(evaluacionExistente.getEvaluadoAt()); // Conservado
        evaluacionActualizada.setActualizadoPor(userId);
        evaluacionActualizada.setActualizadoAt(Instant.now());

        when(evaluacionRepo.save(any(EvaluacionObjetivo.class))).thenReturn(evaluacionActualizada);

        CrearEvaluacionObjetivoRequest request = new CrearEvaluacionObjetivoRequest(
                "cumplido",
                "Nueva justificación",
                null
        );

        EvaluacionObjetivoDto resultado = service.actualizar(sprintId, request, userId);

        assertThat(resultado.estado()).isEqualTo("cumplido");
        assertThat(resultado.justificacion()).isEqualTo("Nueva justificación");
        assertThat(resultado.evaluadoPor()).isEqualTo("usuario-original"); // Conservado
        assertThat(resultado.actualizadoPor()).isEqualTo(userId);

        ArgumentCaptor<EvaluacionObjetivo> captor = ArgumentCaptor.forClass(EvaluacionObjetivo.class);
        verify(evaluacionRepo).save(captor.capture());
        
        EvaluacionObjetivo evaluacionGuardada = captor.getValue();
        assertThat(evaluacionGuardada.getEstado()).isEqualTo("cumplido");
        assertThat(evaluacionGuardada.getEvaluadoPor()).isEqualTo("usuario-original");
        assertThat(evaluacionGuardada.getActualizadoPor()).isEqualTo(userId);
    }

    @Test
    @DisplayName("actualizar: lanza exception si no existe evaluación previa")
    void actualizar_sinEvaluacionPrevia_lanzaException() {
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, userId))
                .thenReturn(Optional.of(scrumMaster));
        when(evaluacionRepo.findBySprintId(sprintId)).thenReturn(Optional.empty());

        CrearEvaluacionObjetivoRequest request = new CrearEvaluacionObjetivoRequest(
                "cumplido",
                "Nueva justificación",
                null
        );

        assertThatThrownBy(() -> service.actualizar(sprintId, request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No existe evaluación para este sprint");

        verify(evaluacionRepo).findBySprintId(sprintId);
        verify(evaluacionRepo, never()).save(any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // OBTENER EVALUACIÓN
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("obtener: devuelve evaluación para miembro del proyecto")
    void obtener_miembroProyecto_devuelveEvaluacion() throws Exception {
        EvaluacionObjetivo evaluacion = new EvaluacionObjetivo();
        evaluacion.setId(UUID.randomUUID());
        evaluacion.setSprintId(sprintId);
        evaluacion.setEstado("cumplido");
        evaluacion.setJustificacion("Objetivo cumplido");
        evaluacion.setEvaluadoPor("scrum-master-user");
        evaluacion.setEvaluadoAt(Instant.now());

        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        when(evaluacionRepo.findBySprintId(sprintId)).thenReturn(Optional.of(evaluacion));

        EvaluacionObjetivoDto resultado = service.obtener(sprintId, userId);

        assertThat(resultado).isNotNull();
        assertThat(resultado.estado()).isEqualTo("cumplido");
        verify(projectMemberRepo).existsByProyectoIdAndUserId(proyectoId, userId);
    }

    @Test
    @DisplayName("obtener: lanza exception si usuario no es miembro")
    void obtener_usuarioNoMiembro_lanzaException() {
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(false);

        assertThatThrownBy(() -> service.obtener(sprintId, userId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("No tienes acceso a este proyecto");

        verify(projectMemberRepo).existsByProyectoIdAndUserId(proyectoId, userId);
        verifyNoInteractions(evaluacionRepo);
    }

    @Test
    @DisplayName("obtener: lanza exception si sprint no tiene evaluación")
    void obtener_sinEvaluacion_lanzaException() {
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        when(evaluacionRepo.findBySprintId(sprintId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtener(sprintId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No existe evaluación para este sprint");

        verify(evaluacionRepo).findBySprintId(sprintId);
    }
}
