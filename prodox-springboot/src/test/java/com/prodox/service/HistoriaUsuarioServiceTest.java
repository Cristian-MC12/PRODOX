// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.prodox.dto.ActualizarHistoriaUsuarioRequest;
import com.prodox.dto.CrearHistoriaUsuarioRequest;
import com.prodox.dto.HistoriaUsuarioDto;
import com.prodox.entity.HistoriaUsuario;
import com.prodox.entity.Sprint;
import com.prodox.repository.HistoriaUsuarioRepository;
import com.prodox.repository.SprintRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HistoriaUsuarioService — pruebas unitarias (V39, Product Owner)")
class HistoriaUsuarioServiceTest {

    @Mock HistoriaUsuarioRepository historiaRepo;
    @Mock SprintRepository sprintRepo;

    @InjectMocks HistoriaUsuarioService service;

    private UUID proyectoId;
    private UUID historiaId;

    @BeforeEach
    void setUp() {
        proyectoId = UUID.randomUUID();
        historiaId = UUID.randomUUID();
    }

    private HistoriaUsuario historia(String prioridad, String estado) {
        HistoriaUsuario h = new HistoriaUsuario();
        h.setId(historiaId);
        h.setProyectoId(proyectoId);
        h.setTitulo("Como usuario quiero X");
        h.setPrioridad(prioridad);
        h.setEstado(estado);
        h.setCreadoPor("po-id");
        return h;
    }

    // ── crear ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("crear: sin prioridad especificada, usa 'media' por defecto")
    void crear_sinPrioridad_usaMediaPorDefecto() {
        when(historiaRepo.save(any(HistoriaUsuario.class))).thenAnswer(i -> i.getArgument(0));

        CrearHistoriaUsuarioRequest req = new CrearHistoriaUsuarioRequest("Título", "Desc", "Criterios", null);
        HistoriaUsuarioDto dto = service.crear(proyectoId, "po-id", req);

        assertThat(dto.prioridad()).isEqualTo("media");
        assertThat(dto.proyectoId()).isEqualTo(proyectoId);
        assertThat(dto.creadoPor()).isEqualTo("po-id");
        assertThat(dto.estado()).isEqualTo("pendiente");
    }

    @Test
    @DisplayName("crear: respeta la prioridad explícita")
    void crear_conPrioridad_respetaValor() {
        when(historiaRepo.save(any(HistoriaUsuario.class))).thenAnswer(i -> i.getArgument(0));

        CrearHistoriaUsuarioRequest req = new CrearHistoriaUsuarioRequest("Título", null, null, "alta");
        HistoriaUsuarioDto dto = service.crear(proyectoId, "po-id", req);

        assertThat(dto.prioridad()).isEqualTo("alta");
    }

    // ── listar (orden por prioridad) ────────────────────────────────────────

    @Test
    @DisplayName("listar: ordena alta > media > baja, y dentro de cada prioridad, más antigua primero")
    void listar_ordenaPorPrioridadYAntiguedad() {
        HistoriaUsuario baja = historia("baja", "pendiente");
        baja.setCreatedAt(java.time.Instant.now().minusSeconds(10));
        HistoriaUsuario altaVieja = historia("alta", "pendiente");
        altaVieja.setId(UUID.randomUUID());
        altaVieja.setCreatedAt(java.time.Instant.now().minusSeconds(100));
        HistoriaUsuario altaNueva = historia("alta", "pendiente");
        altaNueva.setId(UUID.randomUUID());
        altaNueva.setCreatedAt(java.time.Instant.now());

        when(historiaRepo.findByProyectoId(proyectoId)).thenReturn(List.of(baja, altaNueva, altaVieja));

        List<HistoriaUsuarioDto> resultado = service.listar(proyectoId);

        assertThat(resultado).extracting(HistoriaUsuarioDto::id)
                .containsExactly(altaVieja.getId(), altaNueva.getId(), baja.getId());
    }

    // ── actualizar ────────────────────────────────────────────────────────

    @Test
    @DisplayName("actualizar: lanza excepción si la historia no existe")
    void actualizar_historiaInexistente_lanzaExcepcion() {
        when(historiaRepo.findById(historiaId)).thenReturn(Optional.empty());

        ActualizarHistoriaUsuarioRequest req = new ActualizarHistoriaUsuarioRequest("Nuevo título", null, null);

        assertThatThrownBy(() -> service.actualizar(historiaId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Historia no encontrada");
    }

    // ── cambiarPrioridad / cambiarEstado (validación) ───────────────────────

    @Test
    @DisplayName("cambiarPrioridad: rechaza un valor fuera de alta/media/baja")
    void cambiarPrioridad_valorInvalido_lanzaExcepcion() {
        when(historiaRepo.findById(historiaId)).thenReturn(Optional.of(historia("media", "pendiente")));

        assertThatThrownBy(() -> service.cambiarPrioridad(historiaId, "urgente"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prioridad inválida");
        verify(historiaRepo, never()).save(any());
    }

    @Test
    @DisplayName("cambiarEstado: rechaza un valor fuera de pendiente/en_progreso/completada")
    void cambiarEstado_valorInvalido_lanzaExcepcion() {
        when(historiaRepo.findById(historiaId)).thenReturn(Optional.of(historia("media", "pendiente")));

        assertThatThrownBy(() -> service.cambiarEstado(historiaId, "cancelada"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Estado inválido");
        verify(historiaRepo, never()).save(any());
    }

    @Test
    @DisplayName("cambiarEstado: acepta un valor válido y lo persiste")
    void cambiarEstado_valorValido_actualiza() {
        HistoriaUsuario h = historia("media", "pendiente");
        when(historiaRepo.findById(historiaId)).thenReturn(Optional.of(h));
        when(historiaRepo.save(any(HistoriaUsuario.class))).thenAnswer(i -> i.getArgument(0));

        HistoriaUsuarioDto dto = service.cambiarEstado(historiaId, "en_progreso");

        assertThat(dto.estado()).isEqualTo("en_progreso");
    }

    // ── asignarSprint (IDOR: sprint de otro proyecto) ──────────────────────

    @Test
    @DisplayName("asignarSprint: asigna correctamente un sprint del MISMO proyecto")
    void asignarSprint_sprintDelMismoProyecto_asigna() {
        HistoriaUsuario h = historia("media", "pendiente");
        UUID sprintId = UUID.randomUUID();
        Sprint sprint = new Sprint();
        sprint.setId(sprintId);
        sprint.setProyectoId(proyectoId);

        when(historiaRepo.findById(historiaId)).thenReturn(Optional.of(h));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(historiaRepo.save(any(HistoriaUsuario.class))).thenAnswer(i -> i.getArgument(0));

        HistoriaUsuarioDto dto = service.asignarSprint(historiaId, sprintId);

        assertThat(dto.sprintId()).isEqualTo(sprintId);
    }

    @Test
    @DisplayName("asignarSprint: rechaza un sprint que pertenece a OTRO proyecto (manipulación de sprintId)")
    void asignarSprint_sprintDeOtroProyecto_lanzaSecurityException() {
        HistoriaUsuario h = historia("media", "pendiente");
        UUID sprintId = UUID.randomUUID();
        Sprint sprintDeOtroProyecto = new Sprint();
        sprintDeOtroProyecto.setId(sprintId);
        sprintDeOtroProyecto.setProyectoId(UUID.randomUUID()); // otro proyecto

        when(historiaRepo.findById(historiaId)).thenReturn(Optional.of(h));
        when(sprintRepo.findById(sprintId)).thenReturn(Optional.of(sprintDeOtroProyecto));

        assertThatThrownBy(() -> service.asignarSprint(historiaId, sprintId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("no pertenece al proyecto");
        verify(historiaRepo, never()).save(any());
    }

    @Test
    @DisplayName("asignarSprint: sprintId null desasigna la historia (vuelve al backlog)")
    void asignarSprint_sprintIdNull_desasigna() {
        HistoriaUsuario h = historia("media", "pendiente");
        h.setSprintId(UUID.randomUUID());
        when(historiaRepo.findById(historiaId)).thenReturn(Optional.of(h));
        when(historiaRepo.save(any(HistoriaUsuario.class))).thenAnswer(i -> i.getArgument(0));

        HistoriaUsuarioDto dto = service.asignarSprint(historiaId, null);

        assertThat(dto.sprintId()).isNull();
        verify(sprintRepo, never()).findById(any());
    }

    // ── eliminar ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("eliminar: lanza excepción si la historia no existe")
    void eliminar_historiaInexistente_lanzaExcepcion() {
        when(historiaRepo.existsById(historiaId)).thenReturn(false);

        assertThatThrownBy(() -> service.eliminar(historiaId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Historia no encontrada");
        verify(historiaRepo, never()).deleteById(any());
    }

    @Test
    @DisplayName("eliminar: elimina cuando la historia existe")
    void eliminar_historiaExiste_elimina() {
        when(historiaRepo.existsById(historiaId)).thenReturn(true);

        service.eliminar(historiaId);

        verify(historiaRepo).deleteById(historiaId);
    }
}
