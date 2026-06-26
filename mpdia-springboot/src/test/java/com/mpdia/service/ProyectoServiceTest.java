package com.mpdia.service;

import com.mpdia.dto.CrearProyectoRequest;
import com.mpdia.dto.ProyectoDto;
import com.mpdia.entity.AppUser;
import com.mpdia.entity.Proyecto;
import com.mpdia.entity.ProjectMember;
import com.mpdia.repository.AppUserRepository;
import com.mpdia.repository.ProjectMemberRepository;
import com.mpdia.repository.ProyectoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProyectoService — pruebas unitarias")
class ProyectoServiceTest {

    @Mock ProyectoRepository      proyectoRepo;
    @Mock AppUserRepository       userRepo;
    @Mock ProjectMemberRepository memberRepo;
    @Mock SprintService           sprintService;
    @Mock ProjectMemberService    projectMemberService;

    @InjectMocks ProyectoService proyectoService;

    private AppUser scrumMaster;
    private UUID    smId;
    private Proyecto proyecto;
    private UUID    proyectoId;

    @BeforeEach
    void setUp() {
        smId = UUID.randomUUID();
        scrumMaster = new AppUser();
        scrumMaster.setId(smId);
        scrumMaster.setEmail("sm@mpdia.com");
        scrumMaster.setRole("scrum_master");

        proyectoId = UUID.randomUUID();
        proyecto = new Proyecto();
        proyecto.setId(proyectoId);
        proyecto.setNombre("Sistema MPDIA");
        proyecto.setDescripcion("Descripción");
        proyecto.setMetodo("scrum");
        proyecto.setTimeBoxSemanas(2);
        proyecto.setNumeroSprints(3);
        proyecto.setFechaInicio(java.time.LocalDate.now());
        proyecto.setProductGoal("Medir productividad");
        proyecto.setSprintGoal("Sprint 1 goal");
        proyecto.setEstado("activo");
        proyecto.setScrumMasterId(smId.toString());
        proyecto.setCreatedAt(Instant.now());
        proyecto.setUpdatedAt(Instant.now());
    }

    // ── crear ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("crear: Scrum Master puede crear proyecto")
    void crear_scrumMasterValido_retornaProyectoDto() {
        when(userRepo.findById(smId)).thenReturn(Optional.of(scrumMaster));
        when(proyectoRepo.save(any(Proyecto.class))).thenReturn(proyecto);
        when(memberRepo.findByProyectoId(proyectoId)).thenReturn(List.of());

        CrearProyectoRequest req = new CrearProyectoRequest(
                "Sistema MPDIA", "Descripción", "scrum", 2, 3,
                LocalDate.now(), "Medir productividad");

        ProyectoDto dto = proyectoService.crear(smId.toString(), req);

        assertThat(dto.nombre()).isEqualTo("Sistema MPDIA");
        assertThat(dto.metodo()).isEqualTo("scrum");
        assertThat(dto.estado()).isEqualTo("activo");
        verify(proyectoRepo).save(any(Proyecto.class));
        verify(projectMemberService).agregarScrumMaster(eq(proyectoId), eq(smId.toString()), eq("sm@mpdia.com"));
        verify(sprintService).crearSprintsIniciales(eq(proyectoId), eq("Sprint 1"),
                eq(3), eq(2), any(LocalDate.class));
    }

    @Test
    @DisplayName("crear: lanza excepción si el usuario no es Scrum Master")
    void crear_usuarioNoEsScrumMaster_lanzaExcepcion() {
        scrumMaster.setRole("scrum_member");
        when(userRepo.findById(smId)).thenReturn(Optional.of(scrumMaster));

        CrearProyectoRequest req = new CrearProyectoRequest(
                "Proyecto", null, "scrum", 1, 3, LocalDate.now(), "Goal");

        assertThatThrownBy(() -> proyectoService.crear(smId.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Solo el Scrum Master");
    }

    @Test
    @DisplayName("crear: lanza excepción si el usuario no existe")
    void crear_usuarioNoExiste_lanzaExcepcion() {
        when(userRepo.findById(smId)).thenReturn(Optional.empty());

        CrearProyectoRequest req = new CrearProyectoRequest(
                "Proyecto", null, "scrum", 1, 3, LocalDate.now(), "Goal");

        assertThatThrownBy(() -> proyectoService.crear(smId.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    // ── listarMisProyectos ────────────────────────────────────────────────

    @Test
    @DisplayName("listarMisProyectos: retorna proyectos donde el usuario es miembro")
    void listarMisProyectos_conMembresías_retornaLista() {
        ProjectMember member = new ProjectMember();
        member.setProyectoId(proyectoId);
        member.setUserId(smId.toString());

        when(memberRepo.findByUserId(smId.toString())).thenReturn(List.of(member));
        when(proyectoRepo.findAllById(List.of(proyectoId))).thenReturn(List.of(proyecto));
        when(userRepo.findById(smId)).thenReturn(Optional.of(scrumMaster));
        when(memberRepo.findByProyectoId(proyectoId)).thenReturn(List.of(member));

        List<ProyectoDto> resultado = proyectoService.listarMisProyectos(smId.toString());

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).nombre()).isEqualTo("Sistema MPDIA");
    }

    @Test
    @DisplayName("listarMisProyectos: retorna lista vacía si no hay membresías")
    void listarMisProyectos_sinMembresías_retornaVacia() {
        when(memberRepo.findByUserId(smId.toString())).thenReturn(List.of());
        when(proyectoRepo.findAllById(List.of())).thenReturn(List.of());

        List<ProyectoDto> resultado = proyectoService.listarMisProyectos(smId.toString());

        assertThat(resultado).isEmpty();
    }

    // ── finalizar ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("finalizar: Scrum Master puede finalizar su proyecto")
    void finalizar_scrumMasterCorrecto_cambiaEstado() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(proyectoRepo.save(any(Proyecto.class))).thenReturn(proyecto);
        when(userRepo.findById(smId)).thenReturn(Optional.of(scrumMaster));
        when(memberRepo.findByProyectoId(proyectoId)).thenReturn(List.of());

        ProyectoDto dto = proyectoService.finalizar(proyectoId, smId.toString());

        assertThat(dto.estado()).isEqualTo("finalizado");
        verify(proyectoRepo).save(argThat(p -> "finalizado".equals(p.getEstado())));
    }

    @Test
    @DisplayName("finalizar: lanza excepción si no es el Scrum Master del proyecto")
    void finalizar_otroUsuario_lanzaExcepcion() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));

        String otroUserId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> proyectoService.finalizar(proyectoId, otroUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Solo el Scrum Master del proyecto");
    }
}
