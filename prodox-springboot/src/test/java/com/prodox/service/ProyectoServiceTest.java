package com.prodox.service;

import com.prodox.dto.CrearProyectoRequest;
import com.prodox.dto.ProyectoDto;
import com.prodox.entity.AppUser;
import com.prodox.entity.Proyecto;
import com.prodox.entity.ProjectMember;
import com.prodox.repository.AppUserRepository;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.repository.ProyectoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
        scrumMaster.setEmail("sm@prodox.com");
        scrumMaster.setRole("scrum_master");

        proyectoId = UUID.randomUUID();
        proyecto = new Proyecto();
        proyecto.setId(proyectoId);
        proyecto.setNombre("Sistema PRODOX");
        proyecto.setDescripcion("Descripción");
        proyecto.setMetodo("scrum");
        proyecto.setTimeBoxSemanas(2);
        proyecto.setTimeboxUnidad("SEMANAS");
        proyecto.setTimeboxDuracion(2);
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
                "Sistema PRODOX", "Descripción", "scrum", "SEMANAS", 2, null, 3,
                LocalDate.now(), "Medir productividad");

        ProyectoDto dto = proyectoService.crear(smId.toString(), req);

        assertThat(dto.nombre()).isEqualTo("Sistema PRODOX");
        assertThat(dto.metodo()).isEqualTo("scrum");
        assertThat(dto.estado()).isEqualTo("activo");
        assertThat(dto.timeboxUnidad()).isEqualTo("SEMANAS");
        assertThat(dto.timeboxDuracion()).isEqualTo(2);
        verify(proyectoRepo).save(any(Proyecto.class));
        verify(projectMemberService).agregarScrumMaster(eq(proyectoId), eq(smId.toString()), eq("sm@prodox.com"));
        verify(sprintService).crearSprintsIniciales(eq(proyectoId), eq("Sprint 1"), eq(3),
                eq("SEMANAS"), eq(2), any(LocalDate.class), isNull());
    }

    @Test
    @DisplayName("crear: lanza excepción si el usuario no es Scrum Master")
    void crear_usuarioNoEsScrumMaster_lanzaExcepcion() {
        scrumMaster.setRole("scrum_member");
        when(userRepo.findById(smId)).thenReturn(Optional.of(scrumMaster));

        CrearProyectoRequest req = new CrearProyectoRequest(
                "Proyecto", null, "scrum", "SEMANAS", 1, null, 3, LocalDate.now(), "Goal");

        assertThatThrownBy(() -> proyectoService.crear(smId.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Solo el Scrum Master");
    }

    @Test
    @DisplayName("crear: lanza excepción si el usuario no existe")
    void crear_usuarioNoExiste_lanzaExcepcion() {
        when(userRepo.findById(smId)).thenReturn(Optional.empty());

        CrearProyectoRequest req = new CrearProyectoRequest(
                "Proyecto", null, "scrum", "SEMANAS", 1, null, 3, LocalDate.now(), "Goal");

        assertThatThrownBy(() -> proyectoService.crear(smId.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    // ── V41: Timebox de la iteración (horas/días/semanas) ──────────────────

    private void mockeaCreacionExitosa() {
        when(userRepo.findById(smId)).thenReturn(Optional.of(scrumMaster));
        when(proyectoRepo.save(any(Proyecto.class))).thenAnswer(i -> i.getArgument(0));
        when(memberRepo.findByProyectoId(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("crear: timebox en HORAS crea el proyecto con hora de inicio y llama a SprintService con esa unidad")
    void crear_timeboxHoras_creaProyectoYPropagaASprintService() {
        mockeaCreacionExitosa();

        CrearProyectoRequest req = new CrearProyectoRequest(
                "Proyecto Horas", null, "scrum", "HORAS", 8, LocalTime.of(8, 0),
                3, LocalDate.of(2026, 9, 2), "Goal");

        ProyectoDto dto = proyectoService.crear(smId.toString(), req);

        assertThat(dto.timeboxUnidad()).isEqualTo("HORAS");
        assertThat(dto.timeboxDuracion()).isEqualTo(8);
        assertThat(dto.horaInicio()).isEqualTo(LocalTime.of(8, 0));
        // Campo legado acotado a 1-4 (168h = 1 semana; 8h redondea hacia arriba a 1 semana).
        assertThat(dto.timeBoxSemanas()).isEqualTo(1);
        verify(sprintService).crearSprintsIniciales(any(), eq("Sprint 1"), eq(3),
                eq("HORAS"), eq(8), eq(LocalDate.of(2026, 9, 2)), eq(LocalTime.of(8, 0)));
    }

    @Test
    @DisplayName("crear: timebox en DIAS crea el proyecto y llama a SprintService con esa unidad, sin hora de inicio")
    void crear_timeboxDias_creaProyectoYPropagaASprintService() {
        mockeaCreacionExitosa();

        CrearProyectoRequest req = new CrearProyectoRequest(
                "Proyecto Días", null, "scrum", "DIAS", 3, null,
                4, LocalDate.of(2026, 9, 2), "Goal");

        ProyectoDto dto = proyectoService.crear(smId.toString(), req);

        assertThat(dto.timeboxUnidad()).isEqualTo("DIAS");
        assertThat(dto.timeboxDuracion()).isEqualTo(3);
        assertThat(dto.horaInicio()).isNull();
        verify(sprintService).crearSprintsIniciales(any(), eq("Sprint 1"), eq(4),
                eq("DIAS"), eq(3), eq(LocalDate.of(2026, 9, 2)), isNull());
    }

    @Test
    @DisplayName("crear: timebox en SEMANAS conserva exactamente el comportamiento previo a V41")
    void crear_timeboxSemanas_conservaComportamientoPrevio() {
        mockeaCreacionExitosa();

        CrearProyectoRequest req = new CrearProyectoRequest(
                "Proyecto Semanas", null, "scrum", "SEMANAS", 2, null,
                3, LocalDate.of(2026, 9, 2), "Goal");

        ProyectoDto dto = proyectoService.crear(smId.toString(), req);

        assertThat(dto.timeboxUnidad()).isEqualTo("SEMANAS");
        assertThat(dto.timeboxDuracion()).isEqualTo(2);
        assertThat(dto.timeBoxSemanas()).isEqualTo(2); // idéntico a timeboxDuracion, sin aproximación
        verify(sprintService).crearSprintsIniciales(any(), eq("Sprint 1"), eq(3),
                eq("SEMANAS"), eq(2), eq(LocalDate.of(2026, 9, 2)), isNull());
    }

    @Test
    @DisplayName("crear: rechaza duración de timebox vacía (null)")
    void crear_timeboxDuracionNull_lanzaExcepcion() {
        when(userRepo.findById(smId)).thenReturn(Optional.of(scrumMaster));

        CrearProyectoRequest req = new CrearProyectoRequest(
                "Proyecto", null, "scrum", "SEMANAS", null, null, 3, LocalDate.now(), "Goal");

        assertThatThrownBy(() -> proyectoService.crear(smId.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mayor a 0");
        verify(proyectoRepo, never()).save(any());
    }

    @Test
    @DisplayName("crear: rechaza duración de timebox en 0")
    void crear_timeboxDuracionCero_lanzaExcepcion() {
        when(userRepo.findById(smId)).thenReturn(Optional.of(scrumMaster));

        CrearProyectoRequest req = new CrearProyectoRequest(
                "Proyecto", null, "scrum", "DIAS", 0, null, 3, LocalDate.now(), "Goal");

        assertThatThrownBy(() -> proyectoService.crear(smId.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mayor a 0");
        verify(proyectoRepo, never()).save(any());
    }

    @Test
    @DisplayName("crear: rechaza duración de timebox negativa")
    void crear_timeboxDuracionNegativa_lanzaExcepcion() {
        when(userRepo.findById(smId)).thenReturn(Optional.of(scrumMaster));

        CrearProyectoRequest req = new CrearProyectoRequest(
                "Proyecto", null, "scrum", "HORAS", -5, LocalTime.of(8, 0), 3, LocalDate.now(), "Goal");

        assertThatThrownBy(() -> proyectoService.crear(smId.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mayor a 0");
        verify(proyectoRepo, never()).save(any());
    }

    @Test
    @DisplayName("crear: rechaza unidad de timebox inválida (defensa en profundidad, sin pasar por @Pattern del DTO)")
    void crear_timeboxUnidadInvalida_lanzaExcepcion() {
        when(userRepo.findById(smId)).thenReturn(Optional.of(scrumMaster));

        CrearProyectoRequest req = new CrearProyectoRequest(
                "Proyecto", null, "scrum", "MESES", 1, null, 3, LocalDate.now(), "Goal");

        assertThatThrownBy(() -> proyectoService.crear(smId.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unidad de timebox inválida");
        verify(proyectoRepo, never()).save(any());
    }

    @Test
    @DisplayName("crear: rechaza timebox en SEMANAS fuera del rango histórico (>4)")
    void crear_timeboxSemanasFueraDeRango_lanzaExcepcion() {
        when(userRepo.findById(smId)).thenReturn(Optional.of(scrumMaster));

        CrearProyectoRequest req = new CrearProyectoRequest(
                "Proyecto", null, "scrum", "SEMANAS", 5, null, 3, LocalDate.now(), "Goal");

        assertThatThrownBy(() -> proyectoService.crear(smId.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 1 y 4");
        verify(proyectoRepo, never()).save(any());
    }

    @Test
    @DisplayName("crear: rechaza timebox en DIAS fuera de rango (>30)")
    void crear_timeboxDiasFueraDeRango_lanzaExcepcion() {
        when(userRepo.findById(smId)).thenReturn(Optional.of(scrumMaster));

        CrearProyectoRequest req = new CrearProyectoRequest(
                "Proyecto", null, "scrum", "DIAS", 31, null, 3, LocalDate.now(), "Goal");

        assertThatThrownBy(() -> proyectoService.crear(smId.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 1 y 30");
        verify(proyectoRepo, never()).save(any());
    }

    @Test
    @DisplayName("crear: rechaza timebox en HORAS fuera de rango (>168)")
    void crear_timeboxHorasFueraDeRango_lanzaExcepcion() {
        when(userRepo.findById(smId)).thenReturn(Optional.of(scrumMaster));

        CrearProyectoRequest req = new CrearProyectoRequest(
                "Proyecto", null, "scrum", "HORAS", 200, LocalTime.of(8, 0), 3, LocalDate.now(), "Goal");

        assertThatThrownBy(() -> proyectoService.crear(smId.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 1 y 168");
        verify(proyectoRepo, never()).save(any());
    }

    @Test
    @DisplayName("crear: rechaza timebox en HORAS sin hora de inicio (fecha/hora de inicio inválida)")
    void crear_timeboxHorasSinHoraInicio_lanzaExcepcion() {
        when(userRepo.findById(smId)).thenReturn(Optional.of(scrumMaster));

        CrearProyectoRequest req = new CrearProyectoRequest(
                "Proyecto", null, "scrum", "HORAS", 8, null, 3, LocalDate.now(), "Goal");

        assertThatThrownBy(() -> proyectoService.crear(smId.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hora de inicio");
        verify(proyectoRepo, never()).save(any());
    }

    @Test
    @DisplayName("crear: un Scrum Member no puede crear proyecto (y por lo tanto no puede fijar el Timebox) ni por llamada directa al servicio")
    void crear_scrumMemberIntentaFijarTimebox_lanzaExcepcion() {
        scrumMaster.setRole("scrum_member");
        when(userRepo.findById(smId)).thenReturn(Optional.of(scrumMaster));

        CrearProyectoRequest req = new CrearProyectoRequest(
                "Proyecto", null, "scrum", "HORAS", 8, LocalTime.of(8, 0), 3, LocalDate.now(), "Goal");

        assertThatThrownBy(() -> proyectoService.crear(smId.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Solo el Scrum Master");
        verify(proyectoRepo, never()).save(any());
        verify(sprintService, never()).crearSprintsIniciales(any(), any(), anyInt(), any(), anyInt(), any(), any());
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
        assertThat(resultado.get(0).nombre()).isEqualTo("Sistema PRODOX");
    }

    @Test
    @DisplayName("listarMisProyectos: retorna lista vacía si no hay membresías")
    void listarMisProyectos_sinMembresías_retornaVacia() {
        when(memberRepo.findByUserId(smId.toString())).thenReturn(List.of());
        when(proyectoRepo.findAllById(List.of())).thenReturn(List.of());

        List<ProyectoDto> resultado = proyectoService.listarMisProyectos(smId.toString());

        assertThat(resultado).isEmpty();
    }

    // ── getById ───────────────────────────────────────────────────────────
    // Auditoría transversal: antes no validaba membresía — cualquier usuario
    // autenticado podía consultar el detalle de cualquier proyecto conociendo su UUID.

    @Test
    @DisplayName("getById: miembro del proyecto obtiene el detalle")
    void getById_miembroDelProyecto_retornaProyecto() {
        when(memberRepo.existsByProyectoIdAndUserId(proyectoId, smId.toString())).thenReturn(true);
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(userRepo.findById(smId)).thenReturn(Optional.of(scrumMaster));
        when(memberRepo.findByProyectoId(proyectoId)).thenReturn(List.of());

        ProyectoDto dto = proyectoService.getById(proyectoId, smId.toString());

        assertThat(dto.nombre()).isEqualTo("Sistema PRODOX");
    }

    @Test
    @DisplayName("getById: miRol refleja el rol POR PROYECTO (ProjectMember.rol), no el rol global de AppUser")
    void getById_miRolReflejaProjectMemberRolNoRolGlobal() {
        // El usuario global es "scrum_master" (scrumMaster.setRole en setUp), pero en
        // ESTE proyecto es product_owner — miRol debe reflejar el rol por proyecto.
        ProjectMember comoProductOwner = new ProjectMember();
        comoProductOwner.setProyectoId(proyectoId);
        comoProductOwner.setUserId(smId.toString());
        comoProductOwner.setRol("product_owner");

        when(memberRepo.existsByProyectoIdAndUserId(proyectoId, smId.toString())).thenReturn(true);
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(userRepo.findById(smId)).thenReturn(Optional.of(scrumMaster));
        when(memberRepo.findByProyectoId(proyectoId)).thenReturn(List.of());
        when(memberRepo.findByProyectoIdAndUserId(proyectoId, smId.toString())).thenReturn(Optional.of(comoProductOwner));

        ProyectoDto dto = proyectoService.getById(proyectoId, smId.toString());

        assertThat(dto.miRol()).isEqualTo("product_owner");
        assertThat(scrumMaster.getRole()).isEqualTo("scrum_master"); // rol global sin cambios
    }

    @Test
    @DisplayName("getById: usuario externo al proyecto lanza SecurityException")
    void getById_usuarioExterno_lanzaSecurityException() {
        String externoId = UUID.randomUUID().toString();
        when(memberRepo.existsByProyectoIdAndUserId(proyectoId, externoId)).thenReturn(false);

        assertThatThrownBy(() -> proyectoService.getById(proyectoId, externoId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("No tienes acceso a este proyecto");

        verify(proyectoRepo, never()).findById(any());
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

    // ── eliminar ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("eliminar: Scrum Master dueño puede eliminar su proyecto")
    void eliminar_scrumMasterDueño_eliminaProyecto() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(userRepo.findById(smId)).thenReturn(Optional.of(scrumMaster));

        proyectoService.eliminar(proyectoId, smId.toString());

        verify(proyectoRepo).delete(proyecto);
    }

    @Test
    @DisplayName("eliminar: lanza excepción si el usuario no tiene rol scrum_master")
    void eliminar_usuarioNoEsScrumMaster_lanzaExcepcion() {
        scrumMaster.setRole("scrum_member");
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(userRepo.findById(smId)).thenReturn(Optional.of(scrumMaster));

        assertThatThrownBy(() -> proyectoService.eliminar(proyectoId, smId.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Solo el Scrum Master del proyecto");
        verify(proyectoRepo, never()).delete(any());
    }

    @Test
    @DisplayName("eliminar: lanza excepción si el usuario no es el dueño del proyecto")
    void eliminar_otroScrumMaster_lanzaExcepcion() {
        UUID otroSmId = UUID.randomUUID();
        AppUser otroSm = new AppUser();
        otroSm.setId(otroSmId);
        otroSm.setEmail("otro-sm@prodox.com");
        otroSm.setRole("scrum_master");

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(userRepo.findById(otroSmId)).thenReturn(Optional.of(otroSm));

        assertThatThrownBy(() -> proyectoService.eliminar(proyectoId, otroSmId.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Solo el Scrum Master del proyecto");
        verify(proyectoRepo, never()).delete(any());
    }

    @Test
    @DisplayName("eliminar: lanza excepción si el proyecto no existe")
    void eliminar_proyectoNoExiste_lanzaExcepcion() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> proyectoService.eliminar(proyectoId, smId.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Proyecto no encontrado");
    }
}
