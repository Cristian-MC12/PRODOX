package com.mpdia.service;

import com.mpdia.dto.InvitarProyectoRequest;
import com.mpdia.dto.ProjectMemberDto;
import com.mpdia.dto.UnirseProyectoRequest;
import com.mpdia.entity.AppUser;
import com.mpdia.entity.ProjectInvitacion;
import com.mpdia.entity.ProjectMember;
import com.mpdia.entity.Proyecto;
import com.mpdia.repository.AppUserRepository;
import com.mpdia.repository.ProjectInvitacionRepository;
import com.mpdia.repository.ProjectMemberRepository;
import com.mpdia.repository.ProyectoRepository;
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
@DisplayName("ProjectMemberService — pruebas unitarias")
class ProjectMemberServiceTest {

    @Mock ProjectMemberRepository     memberRepo;
    @Mock ProjectInvitacionRepository invRepo;
    @Mock ProyectoRepository          proyectoRepo;
    @Mock AppUserRepository           userRepo;

    @InjectMocks ProjectMemberService service;

    private UUID      proyectoId;
    private UUID      smId;
    private UUID      memberId;
    private Proyecto  proyecto;
    private AppUser   memberUser;

    @BeforeEach
    void setUp() {
        proyectoId = UUID.randomUUID();
        smId       = UUID.randomUUID();
        memberId   = UUID.randomUUID();

        proyecto = new Proyecto();
        proyecto.setId(proyectoId);
        proyecto.setNombre("Proyecto Test");
        proyecto.setMetodo("scrum");
        proyecto.setTimeBoxSemanas(2);
        proyecto.setScrumMasterId(smId.toString());

        memberUser = new AppUser();
        memberUser.setId(memberId);
        memberUser.setEmail("member@mpdia.com");
        memberUser.setRole("scrum_member");
    }

    // ── agregarScrumMaster ────────────────────────────────────────────────

    @Test
    @DisplayName("agregarScrumMaster: agrega SM si aún no es miembro")
    void agregarScrumMaster_noExisteMembresía_guarda() {
        when(memberRepo.existsByProyectoIdAndUserId(proyectoId, smId.toString())).thenReturn(false);

        service.agregarScrumMaster(proyectoId, smId.toString(), "sm@mpdia.com");

        verify(memberRepo).save(argThat(m ->
                "scrum_master".equals(m.getRol()) &&
                smId.toString().equals(m.getUserId())
        ));
    }

    @Test
    @DisplayName("agregarScrumMaster: no duplica si ya es miembro")
    void agregarScrumMaster_yaEsMiembro_noGuarda() {
        when(memberRepo.existsByProyectoIdAndUserId(proyectoId, smId.toString())).thenReturn(true);

        service.agregarScrumMaster(proyectoId, smId.toString(), "sm@mpdia.com");

        verify(memberRepo, never()).save(any());
    }

    // ── listarMiembros ────────────────────────────────────────────────────

    @Test
    @DisplayName("listarMiembros: retorna lista de ProjectMemberDto")
    void listarMiembros_conMiembros_retornaLista() {
        ProjectMember m = new ProjectMember();
        m.setProyectoId(proyectoId);
        m.setUserId(smId.toString());
        m.setUserEmail("sm@mpdia.com");
        m.setRol("scrum_master");

        when(memberRepo.findByProyectoId(proyectoId)).thenReturn(List.of(m));

        List<ProjectMemberDto> resultado = service.listarMiembros(proyectoId);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).userEmail()).isEqualTo("sm@mpdia.com");
        assertThat(resultado.get(0).rol()).isEqualTo("scrum_master");
    }

    // ── invitar ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("invitar: SM genera código de invitación")
    void invitar_scrumMasterValido_retornaCodigo() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(invRepo.save(any(ProjectInvitacion.class))).thenAnswer(i -> i.getArgument(0));

        InvitarProyectoRequest req = new InvitarProyectoRequest("member@mpdia.com");
        String codigo = service.invitar(proyectoId, smId.toString(), req);

        assertThat(codigo).startsWith("PRJ-");
        assertThat(codigo).hasSize(10); // "PRJ-" + 6 chars
        verify(invRepo).save(any(ProjectInvitacion.class));
    }

    @Test
    @DisplayName("invitar: lanza excepción si no es el SM del proyecto")
    void invitar_noEsScrumMaster_lanzaExcepcion() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));

        String otroId = UUID.randomUUID().toString();
        InvitarProyectoRequest req = new InvitarProyectoRequest("alguien@mpdia.com");

        assertThatThrownBy(() -> service.invitar(proyectoId, otroId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Solo el Scrum Master puede invitar");
    }

    // ── unirse ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("unirse: usuario se une al proyecto con código válido")
    void unirse_codigoValido_retornaMiembro() {
        ProjectInvitacion inv = new ProjectInvitacion();
        inv.setProyectoId(proyectoId);
        inv.setEmail("member@mpdia.com");
        inv.setCodigo("PRJ-ABC123");
        inv.setUsado(false);

        when(invRepo.findByCodigoAndUsadoFalse("PRJ-ABC123")).thenReturn(Optional.of(inv));
        when(userRepo.findById(memberId)).thenReturn(Optional.of(memberUser));
        when(memberRepo.existsByProyectoIdAndUserId(proyectoId, memberId.toString())).thenReturn(false);
        when(memberRepo.save(any(ProjectMember.class))).thenAnswer(i -> i.getArgument(0));

        UnirseProyectoRequest req = new UnirseProyectoRequest("PRJ-ABC123");
        ProjectMemberDto dto = service.unirse(memberId.toString(), req);

        assertThat(dto.userEmail()).isEqualTo("member@mpdia.com");
        assertThat(dto.rol()).isEqualTo("scrum_member");
        assertThat(inv.getUsado()).isTrue();
        verify(memberRepo).save(any(ProjectMember.class));
    }

    @Test
    @DisplayName("unirse: lanza excepción con código inválido")
    void unirse_codigoInvalido_lanzaExcepcion() {
        when(invRepo.findByCodigoAndUsadoFalse("INVALIDO")).thenReturn(Optional.empty());

        UnirseProyectoRequest req = new UnirseProyectoRequest("INVALIDO");

        assertThatThrownBy(() -> service.unirse(memberId.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Código inválido");
    }

    @Test
    @DisplayName("unirse: lanza excepción si ya es miembro del proyecto")
    void unirse_yaesMiembro_lanzaExcepcion() {
        ProjectInvitacion inv = new ProjectInvitacion();
        inv.setProyectoId(proyectoId);
        inv.setEmail("member@mpdia.com");
        inv.setCodigo("PRJ-ABC123");
        inv.setUsado(false);

        when(invRepo.findByCodigoAndUsadoFalse("PRJ-ABC123")).thenReturn(Optional.of(inv));
        when(userRepo.findById(memberId)).thenReturn(Optional.of(memberUser));
        when(memberRepo.existsByProyectoIdAndUserId(proyectoId, memberId.toString())).thenReturn(true);

        UnirseProyectoRequest req = new UnirseProyectoRequest("PRJ-ABC123");

        assertThatThrownBy(() -> service.unirse(memberId.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya eres miembro");
    }
}
