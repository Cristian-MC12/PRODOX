package com.prodox.service;

import com.prodox.dto.CrearSiguienteSprintRequest;
import com.prodox.dto.SprintDto;
import com.prodox.entity.Proyecto;
import com.prodox.entity.Sprint;
import com.prodox.repository.ProyectoRepository;
import com.prodox.repository.SprintRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SprintService — pruebas unitarias")
class SprintServiceTest {

    @Mock SprintRepository   sprintRepo;
    @Mock ProyectoRepository proyectoRepo;

    @InjectMocks SprintService sprintService;

    private UUID     proyectoId;
    private Proyecto proyecto;
    private Sprint   sprintEnEjecucion;

    @BeforeEach
    void setUp() {
        proyectoId = UUID.randomUUID();

        proyecto = new Proyecto();
        proyecto.setId(proyectoId);
        proyecto.setNombre("Proyecto PRODOX");
        proyecto.setMetodo("scrum");
        proyecto.setTimeBoxSemanas(2);
        proyecto.setNumeroSprints(3);
        proyecto.setFechaInicio(LocalDate.now());

        sprintEnEjecucion = new Sprint();
        sprintEnEjecucion.setId(UUID.randomUUID());
        sprintEnEjecucion.setProyectoId(proyectoId);
        sprintEnEjecucion.setNumero(1);
        sprintEnEjecucion.setSprintGoal("Goal Sprint 1");
        sprintEnEjecucion.setEstado("en_ejecucion");
        sprintEnEjecucion.setFechaInicio(LocalDate.now().minusWeeks(1));
        sprintEnEjecucion.setFechaFin(LocalDate.now().plusWeeks(1));
    }

    // ── V41: crearSprintsIniciales (timebox en horas/días/semanas) ─────────

    @Test
    @DisplayName("crearSprintsIniciales: SEMANAS conserva EXACTAMENTE la fórmula previa a V41")
    void crearSprintsIniciales_semanas_calculaFechasIgualQueAntes() {
        when(sprintRepo.save(any(Sprint.class))).thenAnswer(i -> i.getArgument(0));

        sprintService.crearSprintsIniciales(proyectoId, "Meta", 2,
                "SEMANAS", 2, LocalDate.of(2026, 1, 1), null);

        ArgumentCaptor<Sprint> captor = ArgumentCaptor.forClass(Sprint.class);
        verify(sprintRepo, times(2)).save(captor.capture());
        List<Sprint> guardados = captor.getAllValues();

        assertThat(guardados.get(0).getFechaInicio()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(guardados.get(0).getFechaFin()).isEqualTo(LocalDate.of(2026, 1, 14));
        assertThat(guardados.get(0).getEstado()).isEqualTo("en_ejecucion");
        assertThat(guardados.get(0).getFechaHoraInicio()).isNull();

        assertThat(guardados.get(1).getFechaInicio()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(guardados.get(1).getFechaFin()).isEqualTo(LocalDate.of(2026, 1, 28));
        assertThat(guardados.get(1).getEstado()).isEqualTo("pendiente");
    }

    @Test
    @DisplayName("crearSprintsIniciales: DIAS calcula fechas análogas a semanas, pero por días")
    void crearSprintsIniciales_dias_calculaFechasAnalogo() {
        when(sprintRepo.save(any(Sprint.class))).thenAnswer(i -> i.getArgument(0));

        sprintService.crearSprintsIniciales(proyectoId, "Meta", 2,
                "DIAS", 3, LocalDate.of(2026, 1, 1), null);

        ArgumentCaptor<Sprint> captor = ArgumentCaptor.forClass(Sprint.class);
        verify(sprintRepo, times(2)).save(captor.capture());
        List<Sprint> guardados = captor.getAllValues();

        assertThat(guardados.get(0).getFechaInicio()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(guardados.get(0).getFechaFin()).isEqualTo(LocalDate.of(2026, 1, 3));
        assertThat(guardados.get(1).getFechaInicio()).isEqualTo(LocalDate.of(2026, 1, 4));
        assertThat(guardados.get(1).getFechaFin()).isEqualTo(LocalDate.of(2026, 1, 6));
    }

    @Test
    @DisplayName("crearSprintsIniciales: HORAS calcula fecha/hora de fin respetando la duración exacta (mismo día)")
    void crearSprintsIniciales_horas_calculaFechaHoraFinMismoDia() {
        when(sprintRepo.save(any(Sprint.class))).thenAnswer(i -> i.getArgument(0));

        sprintService.crearSprintsIniciales(proyectoId, "Meta", 1,
                "HORAS", 8, LocalDate.of(2026, 9, 2), LocalTime.of(8, 0));

        ArgumentCaptor<Sprint> captor = ArgumentCaptor.forClass(Sprint.class);
        verify(sprintRepo).save(captor.capture());
        Sprint s = captor.getValue();

        ZoneId zona = ZoneId.systemDefault();
        assertThat(s.getFechaHoraInicio()).isEqualTo(LocalDateTime.of(2026, 9, 2, 8, 0).atZone(zona).toInstant());
        assertThat(s.getFechaHoraFin()).isEqualTo(LocalDateTime.of(2026, 9, 2, 16, 0).atZone(zona).toInstant());
        // fechaInicio/fechaFin (DATE) también se completan, para el código existente que ya los lee.
        assertThat(s.getFechaInicio()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(s.getFechaFin()).isEqualTo(LocalDate.of(2026, 9, 2));
    }

    @Test
    @DisplayName("crearSprintsIniciales: HORAS respeta el cambio de día — 10 horas desde las 16:00 terminan a las 02:00 del día siguiente, sin limitarse a las 23:59")
    void crearSprintsIniciales_horas_respetaElCambioDeDia() {
        when(sprintRepo.save(any(Sprint.class))).thenAnswer(i -> i.getArgument(0));

        sprintService.crearSprintsIniciales(proyectoId, "Meta", 1,
                "HORAS", 10, LocalDate.of(2026, 9, 2), LocalTime.of(16, 0));

        ArgumentCaptor<Sprint> captor = ArgumentCaptor.forClass(Sprint.class);
        verify(sprintRepo).save(captor.capture());
        Sprint s = captor.getValue();

        ZoneId zona = ZoneId.systemDefault();
        assertThat(s.getFechaHoraFin()).isEqualTo(LocalDateTime.of(2026, 9, 3, 2, 0).atZone(zona).toInstant());
        assertThat(s.getFechaFin()).isEqualTo(LocalDate.of(2026, 9, 3));
    }

    @Test
    @DisplayName("crearSprintsIniciales: HORAS con múltiples sprints avanza secuencialmente respetando la duración de cada uno")
    void crearSprintsIniciales_horas_multiplesSprintsAvanzanSecuencialmente() {
        when(sprintRepo.save(any(Sprint.class))).thenAnswer(i -> i.getArgument(0));

        sprintService.crearSprintsIniciales(proyectoId, "Meta", 2,
                "HORAS", 8, LocalDate.of(2026, 9, 2), LocalTime.of(8, 0));

        ArgumentCaptor<Sprint> captor = ArgumentCaptor.forClass(Sprint.class);
        verify(sprintRepo, times(2)).save(captor.capture());
        List<Sprint> guardados = captor.getAllValues();

        ZoneId zona = ZoneId.systemDefault();
        // Sprint 1: 08:00 -> 16:00 del mismo día.
        assertThat(guardados.get(0).getFechaHoraInicio()).isEqualTo(LocalDateTime.of(2026, 9, 2, 8, 0).atZone(zona).toInstant());
        assertThat(guardados.get(0).getFechaHoraFin()).isEqualTo(LocalDateTime.of(2026, 9, 2, 16, 0).atZone(zona).toInstant());
        // Sprint 2: continúa exactamente donde terminó el 1 -> 16:00 a medianoche.
        assertThat(guardados.get(1).getFechaHoraInicio()).isEqualTo(LocalDateTime.of(2026, 9, 2, 16, 0).atZone(zona).toInstant());
        assertThat(guardados.get(1).getFechaHoraFin()).isEqualTo(LocalDateTime.of(2026, 9, 3, 0, 0).atZone(zona).toInstant());
    }

    // ── crearSprintInicial ────────────────────────────────────────────────

    @Test
    @DisplayName("crearSprintInicial: crea Sprint 1 en estado en_ejecucion")
    void crearSprintInicial_proyectoValido_retornaSprintDto() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.save(any(Sprint.class))).thenReturn(sprintEnEjecucion);

        SprintDto dto = sprintService.crearSprintInicial(proyectoId, "Goal Sprint 1");

        assertThat(dto.numero()).isEqualTo(1);
        assertThat(dto.estado()).isEqualTo("en_ejecucion");
        assertThat(dto.sprintGoal()).isEqualTo("Goal Sprint 1");
        verify(sprintRepo).save(any(Sprint.class));
    }

    @Test
    @DisplayName("crearSprintInicial: lanza excepción si el proyecto no existe")
    void crearSprintInicial_proyectoNoExiste_lanzaExcepcion() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sprintService.crearSprintInicial(proyectoId, "Goal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Proyecto no encontrado");
    }

    // ── getSprintActivo ───────────────────────────────────────────────────

    @Test
    @DisplayName("getSprintActivo: retorna sprint en ejecución del proyecto")
    void getSprintActivo_sprintExiste_retornaDto() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findByProyectoIdAndEstado(proyectoId, "en_ejecucion"))
                .thenReturn(Optional.of(sprintEnEjecucion));

        SprintDto dto = sprintService.getSprintActivo(proyectoId);

        assertThat(dto.numero()).isEqualTo(1);
        assertThat(dto.estado()).isEqualTo("en_ejecucion");
    }

    @Test
    @DisplayName("getSprintActivo: lanza excepción si no hay sprint en ejecución")
    void getSprintActivo_sinSprintActivo_lanzaExcepcion() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findByProyectoIdAndEstado(proyectoId, "en_ejecucion"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sprintService.getSprintActivo(proyectoId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No hay sprint en ejecución");
    }

    // ── listarSprints ─────────────────────────────────────────────────────

    @Test
    @DisplayName("listarSprints: retorna todos los sprints del proyecto")
    void listarSprints_conSprints_retornaLista() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId))
                .thenReturn(List.of(sprintEnEjecucion));

        List<SprintDto> lista = sprintService.listarSprints(proyectoId);

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).numero()).isEqualTo(1);
    }

    // ── cerrarEIniciarSiguiente ───────────────────────────────────────────

    @Test
    @DisplayName("cerrarEIniciarSiguiente: cierra sprint en ejecución e inicia el pendiente")
    void cerrarEIniciarSiguiente_sprintActivo_iniciaSiguiente() {
        Sprint sprint2 = new Sprint();
        sprint2.setId(UUID.randomUUID());
        sprint2.setProyectoId(proyectoId);
        sprint2.setNumero(2);
        sprint2.setSprintGoal("Goal Sprint 2");
        sprint2.setEstado("pendiente");
        sprint2.setFechaInicio(LocalDate.now());
        sprint2.setFechaFin(LocalDate.now().plusWeeks(2));

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findByProyectoIdAndEstado(proyectoId, "en_ejecucion"))
                .thenReturn(Optional.of(sprintEnEjecucion));
        when(sprintRepo.findFirstByProyectoIdAndEstadoOrderByNumeroAsc(proyectoId, "pendiente"))
                .thenReturn(Optional.of(sprint2));
        when(sprintRepo.save(any(Sprint.class))).thenReturn(sprint2);

        CrearSiguienteSprintRequest req = new CrearSiguienteSprintRequest("Goal Sprint 2");
        SprintDto dto = sprintService.cerrarEIniciarSiguiente(proyectoId, req);

        assertThat(dto.numero()).isEqualTo(2);
        assertThat(dto.sprintGoal()).isEqualTo("Goal Sprint 2");
        assertThat(sprintEnEjecucion.getEstado()).isEqualTo("finalizado");
    }

    @Test
    @DisplayName("cerrarEIniciarSiguiente: lanza excepción si no hay sprints pendientes")
    void cerrarEIniciarSiguiente_sinSiguiente_lanzaExcepcion() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findByProyectoIdAndEstado(proyectoId, "en_ejecucion"))
                .thenReturn(Optional.of(sprintEnEjecucion));
        when(sprintRepo.findFirstByProyectoIdAndEstadoOrderByNumeroAsc(proyectoId, "pendiente"))
                .thenReturn(Optional.empty());
        when(sprintRepo.save(any(Sprint.class))).thenReturn(sprintEnEjecucion);

        CrearSiguienteSprintRequest req = new CrearSiguienteSprintRequest("Goal Sprint 2");

        assertThatThrownBy(() -> sprintService.cerrarEIniciarSiguiente(proyectoId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No hay sprints pendientes");
    }

    // ── finalizarReabierto (transición reabierto → finalizado) ─────────────

    @Test
    @DisplayName("finalizarReabierto: cierra un sprint reabierto sin tocar el sprint en ejecución del proyecto")
    void finalizarReabierto_sprintReabierto_loFinaliza() {
        UUID sprintId = UUID.randomUUID();
        Sprint reabierto = new Sprint();
        reabierto.setId(sprintId);
        reabierto.setProyectoId(proyectoId);
        reabierto.setNumero(1);
        reabierto.setEstado("reabierto");
        reabierto.setReabiertoPor("sm-user");
        reabierto.setFechaInicio(LocalDate.now().minusWeeks(2));
        reabierto.setFechaFin(LocalDate.now().minusWeeks(1));

        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(reabierto));
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.save(any(Sprint.class))).thenReturn(reabierto);

        SprintDto dto = sprintService.finalizarReabierto(sprintId);

        assertThat(dto.estado()).isEqualTo("finalizado");
        assertThat(reabierto.getEstado()).isEqualTo("finalizado");
        // reabiertoPor se conserva como rastro histórico de que fue reabierto antes de re-cerrarse.
        assertThat(reabierto.getReabiertoPor()).isEqualTo("sm-user");
        verify(sprintRepo, times(1)).save(reabierto);
        // No debe tocar ningún otro sprint del proyecto (no abre "siguiente").
        verify(sprintRepo, never()).findFirstByProyectoIdAndEstadoOrderByNumeroAsc(any(), any());
    }

    @Test
    @DisplayName("finalizarReabierto: lanza excepción si el sprint no está en estado reabierto")
    void finalizarReabierto_sprintNoReabierto_lanzaExcepcion() {
        UUID sprintId = UUID.randomUUID();
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintEnEjecucion));

        assertThatThrownBy(() -> sprintService.finalizarReabierto(sprintId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Solo se pueden finalizar sprints reabiertos");

        verify(sprintRepo, never()).save(any());
    }

    @Test
    @DisplayName("finalizarReabierto: lanza excepción si el sprint no existe")
    void finalizarReabierto_sprintInexistente_lanzaExcepcion() {
        UUID sprintId = UUID.randomUUID();
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sprintService.finalizarReabierto(sprintId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sprint no encontrado");
    }

    @Test
    @DisplayName("Ciclo completo: en_ejecucion -> finalizado -> reabierto -> finalizado")
    void cicloCompleto_enEjecucionFinalizadoReabiertoFinalizado() {
        UUID sprintId = sprintEnEjecucion.getId();
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintEnEjecucion));
        when(sprintRepo.save(any(Sprint.class))).thenReturn(sprintEnEjecucion);

        // en_ejecucion -> finalizado (reabrir requiere estado finalizado)
        sprintEnEjecucion.setEstado("finalizado");
        assertThat(sprintEnEjecucion.getEstado()).isEqualTo("finalizado");

        // finalizado -> reabierto
        SprintDto reabierto = sprintService.reabrir(sprintId, "sm-user");
        assertThat(reabierto.estado()).isEqualTo("reabierto");
        assertThat(sprintEnEjecucion.getEstado()).isEqualTo("reabierto");

        // reabierto -> finalizado
        SprintDto finalizadoOtraVez = sprintService.finalizarReabierto(sprintId);
        assertThat(finalizadoOtraVez.estado()).isEqualTo("finalizado");
        assertThat(sprintEnEjecucion.getEstado()).isEqualTo("finalizado");
    }
}
