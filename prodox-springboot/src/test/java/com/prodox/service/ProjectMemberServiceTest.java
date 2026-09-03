package com.prodox.service;

import com.prodox.dto.InvitacionEstadoDto;
import com.prodox.dto.InvitarProyectoRequest;
import com.prodox.dto.InvitarProyectoResponse;
import com.prodox.dto.ProjectMemberDto;
import com.prodox.dto.UnirseProyectoRequest;
import com.prodox.entity.AppUser;
import com.prodox.entity.ProjectInvitacion;
import com.prodox.entity.ProjectMember;
import com.prodox.entity.Proyecto;
import com.prodox.repository.AppUserRepository;
import com.prodox.repository.ProjectInvitacionRepository;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.repository.ProyectoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    @Mock EmailService                emailService;

    @InjectMocks ProjectMemberService service;

    private UUID      proyectoId;
    private UUID      smId;
    private UUID      memberId;
    private Proyecto  proyecto;
    private AppUser   memberUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "appUrl", "http://localhost:4200");
        ReflectionTestUtils.setField(service, "expirationDays", 7L);

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
        memberUser.setEmail("member@prodox.com");
        memberUser.setRole("scrum_member");
    }

    // ── agregarScrumMaster ────────────────────────────────────────────────

    @Test
    @DisplayName("agregarScrumMaster: agrega SM si aún no es miembro")
    void agregarScrumMaster_noExisteMembresía_guarda() {
        when(memberRepo.existsByProyectoIdAndUserId(proyectoId, smId.toString())).thenReturn(false);

        service.agregarScrumMaster(proyectoId, smId.toString(), "sm@prodox.com");

        verify(memberRepo).save(argThat(m ->
                "scrum_master".equals(m.getRol()) &&
                smId.toString().equals(m.getUserId())
        ));
    }

    @Test
    @DisplayName("agregarScrumMaster: no duplica si ya es miembro")
    void agregarScrumMaster_yaEsMiembro_noGuarda() {
        when(memberRepo.existsByProyectoIdAndUserId(proyectoId, smId.toString())).thenReturn(true);

        service.agregarScrumMaster(proyectoId, smId.toString(), "sm@prodox.com");

        verify(memberRepo, never()).save(any());
    }

    // ── listarMiembros ────────────────────────────────────────────────────
    // Auditoría transversal: este endpoint no validaba membresía — cualquier
    // usuario autenticado podía consultar los miembros de cualquier proyecto
    // conociendo su UUID.

    @Test
    @DisplayName("listarMiembros: miembro del proyecto obtiene la lista")
    void listarMiembros_miembroDelProyecto_retornaLista() {
        ProjectMember m = new ProjectMember();
        m.setProyectoId(proyectoId);
        m.setUserId(smId.toString());
        m.setUserEmail("sm@prodox.com");
        m.setRol("scrum_master");

        when(memberRepo.existsByProyectoIdAndUserId(proyectoId, smId.toString())).thenReturn(true);
        when(memberRepo.findByProyectoId(proyectoId)).thenReturn(List.of(m));

        List<ProjectMemberDto> resultado = service.listarMiembros(proyectoId, smId.toString());

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).userEmail()).isEqualTo("sm@prodox.com");
        assertThat(resultado.get(0).rol()).isEqualTo("scrum_master");
    }

    @Test
    @DisplayName("listarMiembros: usuario externo lanza SecurityException")
    void listarMiembros_usuarioExterno_lanzaSecurityException() {
        String externoId = UUID.randomUUID().toString();
        when(memberRepo.existsByProyectoIdAndUserId(proyectoId, externoId)).thenReturn(false);

        assertThatThrownBy(() -> service.listarMiembros(proyectoId, externoId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("No tienes acceso a este proyecto");

        verify(memberRepo, never()).findByProyectoId(any());
    }

    // ── invitar ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("invitar: SM genera código de invitación y el correo se envía correctamente")
    void invitar_scrumMasterValido_retornaCodigoYEmailEnviado() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(invRepo.save(any(ProjectInvitacion.class))).thenAnswer(i -> i.getArgument(0));
        when(emailService.enviar(eq("member@prodox.com"), anyString(), anyString())).thenReturn(true);

        InvitarProyectoRequest req = new InvitarProyectoRequest("member@prodox.com", null);
        InvitarProyectoResponse res = service.invitar(proyectoId, smId.toString(), req);

        assertThat(res.codigo()).startsWith("PRJ-");
        assertThat(res.codigo()).hasSize(10); // "PRJ-" + 6 chars
        assertThat(res.emailEnviado()).isTrue();

        ArgumentCaptor<ProjectInvitacion> captor = ArgumentCaptor.forClass(ProjectInvitacion.class);
        verify(invRepo).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt()).isAfter(Instant.now());
        assertThat(captor.getValue().getExpiresAt()).isBefore(Instant.now().plus(8, ChronoUnit.DAYS));

        // Corrección: el enlace apuntaba a /proyectos/unirse, una ruta que
        // nunca existió en Angular — el usuario terminaba en /proyectos
        // ("No estás en ningún proyecto todavía"). Ahora apunta a la ruta
        // dedicada /invitacion que sí acepta y procesa el código.
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviar(eq("member@prodox.com"), anyString(), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("/invitacion?codigo=" + res.codigo());
        assertThat(bodyCaptor.getValue()).doesNotContain("/proyectos/unirse");
    }

    @Test
    @DisplayName("invitar: si el envío de correo falla, igual genera y persiste el código (no rompe la funcionalidad existente)")
    void invitar_correoFallaAlEnviarse_generaCodigoIgual() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(invRepo.save(any(ProjectInvitacion.class))).thenAnswer(i -> i.getArgument(0));
        when(emailService.enviar(anyString(), anyString(), anyString())).thenReturn(false);

        InvitarProyectoRequest req = new InvitarProyectoRequest("member@prodox.com", null);
        InvitarProyectoResponse res = service.invitar(proyectoId, smId.toString(), req);

        assertThat(res.codigo()).startsWith("PRJ-");
        assertThat(res.emailEnviado()).isFalse();
        verify(invRepo).save(any(ProjectInvitacion.class));
    }

    @Test
    @DisplayName("invitar: lanza excepción si el proyecto no existe")
    void invitar_proyectoInexistente_lanzaExcepcion() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.empty());

        InvitarProyectoRequest req = new InvitarProyectoRequest("alguien@prodox.com", null);

        assertThatThrownBy(() -> service.invitar(proyectoId, smId.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Proyecto no encontrado");
        verify(invRepo, never()).save(any());
        verify(emailService, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("invitar: lanza excepción si no es el SM del proyecto (no autorizado a invitar)")
    void invitar_noEsScrumMaster_lanzaExcepcion() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));

        String otroId = UUID.randomUUID().toString();
        InvitarProyectoRequest req = new InvitarProyectoRequest("alguien@prodox.com", null);

        assertThatThrownBy(() -> service.invitar(proyectoId, otroId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Solo el Scrum Master puede invitar");
        verify(invRepo, never()).save(any());
        verify(emailService, never()).enviar(anyString(), anyString(), anyString());
    }

    // ── invitar con rol (V39 — Product Owner) ──────────────────────────────

    @Test
    @DisplayName("invitar: SM puede invitar con rol product_owner — la invitación queda marcada con ese rol")
    void invitar_conRolProductOwner_persisteRolEnLaInvitacion() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(invRepo.save(any(ProjectInvitacion.class))).thenAnswer(i -> i.getArgument(0));
        when(emailService.enviar(anyString(), anyString(), anyString())).thenReturn(true);

        InvitarProyectoRequest req = new InvitarProyectoRequest("po@prodox.com", "product_owner");
        service.invitar(proyectoId, smId.toString(), req);

        ArgumentCaptor<ProjectInvitacion> captor = ArgumentCaptor.forClass(ProjectInvitacion.class);
        verify(invRepo).save(captor.capture());
        assertThat(captor.getValue().getRol()).isEqualTo("product_owner");
    }

    @Test
    @DisplayName("invitar: sin rol especificado, la invitación conserva el comportamiento previo a V39 (scrum_member)")
    void invitar_sinRol_persisteScrumMemberPorDefecto() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(invRepo.save(any(ProjectInvitacion.class))).thenAnswer(i -> i.getArgument(0));
        when(emailService.enviar(anyString(), anyString(), anyString())).thenReturn(true);

        InvitarProyectoRequest req = new InvitarProyectoRequest("member@prodox.com", null);
        service.invitar(proyectoId, smId.toString(), req);

        ArgumentCaptor<ProjectInvitacion> captor = ArgumentCaptor.forClass(ProjectInvitacion.class);
        verify(invRepo).save(captor.capture());
        assertThat(captor.getValue().getRol()).isEqualTo("scrum_member");
    }

    @Test
    @DisplayName("unirse: una invitación marcada como product_owner crea el ProjectMember con ese rol")
    void unirse_invitacionProductOwner_creaMiembroProductOwner() {
        ProjectInvitacion inv = new ProjectInvitacion();
        inv.setProyectoId(proyectoId);
        inv.setEmail("member@prodox.com");
        inv.setCodigo("PRJ-ABC123");
        inv.setUsado(false);
        inv.setRol("product_owner");

        when(invRepo.findByCodigoAndUsadoFalse("PRJ-ABC123")).thenReturn(Optional.of(inv));
        when(userRepo.findById(memberId)).thenReturn(Optional.of(memberUser));
        when(memberRepo.existsByProyectoIdAndUserId(proyectoId, memberId.toString())).thenReturn(false);
        when(memberRepo.save(any(ProjectMember.class))).thenAnswer(i -> i.getArgument(0));

        ProjectMemberDto dto = service.unirse(memberId.toString(), new UnirseProyectoRequest("PRJ-ABC123"));

        assertThat(dto.rol()).isEqualTo("product_owner");
    }

    // ── cambiarRol (V39 — Product Owner) ───────────────────────────────────

    @Test
    @DisplayName("cambiarRol: el Scrum Master puede convertir un Scrum Member en Product Owner")
    void cambiarRol_smConvierteScrumMemberEnProductOwner() {
        ProjectMember sm = new ProjectMember();
        sm.setProyectoId(proyectoId); sm.setUserId(smId.toString()); sm.setRol("scrum_master");
        ProjectMember target = new ProjectMember();
        target.setProyectoId(proyectoId); target.setUserId(memberId.toString());
        target.setUserEmail("member@prodox.com"); target.setRol("scrum_member");

        when(memberRepo.findByProyectoIdAndUserId(proyectoId, smId.toString())).thenReturn(Optional.of(sm));
        when(memberRepo.findByProyectoIdAndUserId(proyectoId, memberId.toString())).thenReturn(Optional.of(target));
        when(memberRepo.save(any(ProjectMember.class))).thenAnswer(i -> i.getArgument(0));

        ProjectMemberDto dto = service.cambiarRol(proyectoId, smId.toString(), memberId.toString(), "product_owner");

        assertThat(dto.rol()).isEqualTo("product_owner");
    }

    @Test
    @DisplayName("cambiarRol: el Scrum Master puede devolver un Product Owner a Scrum Member")
    void cambiarRol_smConvierteProductOwnerEnScrumMember() {
        ProjectMember sm = new ProjectMember();
        sm.setProyectoId(proyectoId); sm.setUserId(smId.toString()); sm.setRol("scrum_master");
        ProjectMember target = new ProjectMember();
        target.setProyectoId(proyectoId); target.setUserId(memberId.toString());
        target.setUserEmail("po@prodox.com"); target.setRol("product_owner");

        when(memberRepo.findByProyectoIdAndUserId(proyectoId, smId.toString())).thenReturn(Optional.of(sm));
        when(memberRepo.findByProyectoIdAndUserId(proyectoId, memberId.toString())).thenReturn(Optional.of(target));
        when(memberRepo.save(any(ProjectMember.class))).thenAnswer(i -> i.getArgument(0));

        ProjectMemberDto dto = service.cambiarRol(proyectoId, smId.toString(), memberId.toString(), "scrum_member");

        assertThat(dto.rol()).isEqualTo("scrum_member");
    }

    @Test
    @DisplayName("cambiarRol: un Product Owner no puede cambiar roles de otros miembros")
    void cambiarRol_solicitanteProductOwner_lanzaSecurityException() {
        ProjectMember solicitantePO = new ProjectMember();
        solicitantePO.setProyectoId(proyectoId); solicitantePO.setUserId(memberId.toString()); solicitantePO.setRol("product_owner");

        when(memberRepo.findByProyectoIdAndUserId(proyectoId, memberId.toString())).thenReturn(Optional.of(solicitantePO));

        assertThatThrownBy(() -> service.cambiarRol(proyectoId, memberId.toString(), smId.toString(), "scrum_member"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Solo el Scrum Master");
        verify(memberRepo, never()).save(any());
    }

    @Test
    @DisplayName("cambiarRol: un Scrum Member no puede cambiar roles de otros miembros")
    void cambiarRol_solicitanteScrumMember_lanzaSecurityException() {
        ProjectMember solicitanteMember = new ProjectMember();
        solicitanteMember.setProyectoId(proyectoId); solicitanteMember.setUserId(memberId.toString()); solicitanteMember.setRol("scrum_member");

        when(memberRepo.findByProyectoIdAndUserId(proyectoId, memberId.toString())).thenReturn(Optional.of(solicitanteMember));

        assertThatThrownBy(() -> service.cambiarRol(proyectoId, memberId.toString(), smId.toString(), "product_owner"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Solo el Scrum Master");
        verify(memberRepo, never()).save(any());
    }

    @Test
    @DisplayName("cambiarRol: intentar asignar scrum_master mediante este endpoint se rechaza (no existe mecanismo de reasignación de SM)")
    void cambiarRol_intentaAsignarScrumMaster_lanzaIllegalArgumentException() {
        ProjectMember sm = new ProjectMember();
        sm.setProyectoId(proyectoId); sm.setUserId(smId.toString()); sm.setRol("scrum_master");

        when(memberRepo.findByProyectoIdAndUserId(proyectoId, smId.toString())).thenReturn(Optional.of(sm));

        assertThatThrownBy(() -> service.cambiarRol(proyectoId, smId.toString(), memberId.toString(), "scrum_master"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rol inválido");
        verify(memberRepo, never()).save(any());
    }

    @Test
    @DisplayName("cambiarRol: no se puede cambiar el rol del propio Scrum Master del proyecto (evita dejarlo sin SM y bloquea la auto-reasignación)")
    void cambiarRol_targetEsElScrumMaster_lanzaIllegalArgumentException() {
        ProjectMember sm = new ProjectMember();
        sm.setProyectoId(proyectoId); sm.setUserId(smId.toString()); sm.setRol("scrum_master");

        when(memberRepo.findByProyectoIdAndUserId(proyectoId, smId.toString())).thenReturn(Optional.of(sm));

        assertThatThrownBy(() -> service.cambiarRol(proyectoId, smId.toString(), smId.toString(), "product_owner"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Scrum Master");
        verify(memberRepo, never()).save(any());
    }

    @Test
    @DisplayName("cambiarRol: un usuario externo al proyecto (no miembro) recibe SecurityException")
    void cambiarRol_solicitanteExterno_lanzaSecurityException() {
        String externoId = UUID.randomUUID().toString();
        when(memberRepo.findByProyectoIdAndUserId(proyectoId, externoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cambiarRol(proyectoId, externoId, memberId.toString(), "product_owner"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("No tienes acceso a este proyecto");
        verify(memberRepo, never()).save(any());
    }

    @Test
    @DisplayName("cambiarRol: el usuario objetivo debe ser miembro de ESTE proyecto (no de otro)")
    void cambiarRol_targetNoEsMiembroDeEsteProyecto_lanzaIllegalArgumentException() {
        ProjectMember sm = new ProjectMember();
        sm.setProyectoId(proyectoId); sm.setUserId(smId.toString()); sm.setRol("scrum_master");
        String targetDeOtroProyecto = UUID.randomUUID().toString();

        when(memberRepo.findByProyectoIdAndUserId(proyectoId, smId.toString())).thenReturn(Optional.of(sm));
        when(memberRepo.findByProyectoIdAndUserId(proyectoId, targetDeOtroProyecto)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cambiarRol(proyectoId, smId.toString(), targetDeOtroProyecto, "product_owner"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no es miembro de este proyecto");
        verify(memberRepo, never()).save(any());
    }

    // ── V40: a lo sumo un Product Owner activo por proyecto ────────────────

    @Test
    @DisplayName("invitar: proyecto sin PO ni invitación pendiente — invitar product_owner es permitido")
    void invitar_proyectoSinProductOwner_permiteInvitarProductOwner() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(memberRepo.existsByProyectoIdAndRol(proyectoId, "product_owner")).thenReturn(false);
        when(invRepo.findByProyectoIdAndRolAndUsadoFalse(proyectoId, "product_owner")).thenReturn(List.of());
        when(invRepo.save(any(ProjectInvitacion.class))).thenAnswer(i -> i.getArgument(0));
        when(emailService.enviar(anyString(), anyString(), anyString())).thenReturn(true);

        InvitarProyectoResponse res = service.invitar(proyectoId, smId.toString(),
                new InvitarProyectoRequest("po@prodox.com", "product_owner"));

        assertThat(res.codigo()).startsWith("PRJ-");
        verify(invRepo).save(any(ProjectInvitacion.class));
    }

    @Test
    @DisplayName("invitar: rechaza invitar product_owner si el proyecto YA tiene un Product Owner activo (Caso E)")
    void invitar_proyectoConProductOwnerActivo_rechazaNuevaInvitacionProductOwner() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(memberRepo.existsByProyectoIdAndRol(proyectoId, "product_owner")).thenReturn(true);

        assertThatThrownBy(() -> service.invitar(proyectoId, smId.toString(),
                new InvitarProyectoRequest("otro-po@prodox.com", "product_owner")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya tiene un Product Owner");
        verify(invRepo, never()).save(any());
    }

    @Test
    @DisplayName("invitar: rechaza invitar product_owner si ya existe una invitación de PO pendiente (Caso C)")
    void invitar_invitacionProductOwnerPendiente_rechazaNuevaInvitacionProductOwner() {
        ProjectInvitacion pendiente = new ProjectInvitacion();
        pendiente.setProyectoId(proyectoId);
        pendiente.setRol("product_owner");
        pendiente.setUsado(false);
        pendiente.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(memberRepo.existsByProyectoIdAndRol(proyectoId, "product_owner")).thenReturn(false);
        when(invRepo.findByProyectoIdAndRolAndUsadoFalse(proyectoId, "product_owner")).thenReturn(List.of(pendiente));

        assertThatThrownBy(() -> service.invitar(proyectoId, smId.toString(),
                new InvitarProyectoRequest("segundo-po@prodox.com", "product_owner")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invitación de Product Owner pendiente");
        verify(invRepo, never()).save(any(ProjectInvitacion.class));
    }

    @Test
    @DisplayName("invitar: una invitación de PO YA EXPIRADA no cuenta como pendiente — permite generar una nueva")
    void invitar_invitacionProductOwnerExpirada_noBloqueaNuevaInvitacion() {
        ProjectInvitacion expirada = new ProjectInvitacion();
        expirada.setProyectoId(proyectoId);
        expirada.setRol("product_owner");
        expirada.setUsado(false);
        expirada.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(memberRepo.existsByProyectoIdAndRol(proyectoId, "product_owner")).thenReturn(false);
        when(invRepo.findByProyectoIdAndRolAndUsadoFalse(proyectoId, "product_owner")).thenReturn(List.of(expirada));
        when(invRepo.save(any(ProjectInvitacion.class))).thenAnswer(i -> i.getArgument(0));
        when(emailService.enviar(anyString(), anyString(), anyString())).thenReturn(true);

        InvitarProyectoResponse res = service.invitar(proyectoId, smId.toString(),
                new InvitarProyectoRequest("nuevo-po@prodox.com", "product_owner"));

        assertThat(res.codigo()).startsWith("PRJ-");
        verify(invRepo).save(any(ProjectInvitacion.class));
    }

    @Test
    @DisplayName("invitar: la invitación de scrum_member no dispara ninguna validación de unicidad de Product Owner")
    void invitar_invitacionScrumMember_noValidaUnicidadDeProductOwner() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));
        when(invRepo.save(any(ProjectInvitacion.class))).thenAnswer(i -> i.getArgument(0));
        when(emailService.enviar(anyString(), anyString(), anyString())).thenReturn(true);

        service.invitar(proyectoId, smId.toString(), new InvitarProyectoRequest("member@prodox.com", "scrum_member"));

        verify(memberRepo, never()).existsByProyectoIdAndRol(any(), any());
        verify(invRepo, never()).findByProyectoIdAndRolAndUsadoFalse(any(), any());
    }

    @Test
    @DisplayName("invitar: defensa en profundidad — rechaza rol scrum_master aunque se invoque el servicio directamente (sin pasar por @Valid del DTO)")
    void invitar_rolScrumMaster_lanzaIllegalArgumentException() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));

        assertThatThrownBy(() -> service.invitar(proyectoId, smId.toString(),
                new InvitarProyectoRequest("intruso@prodox.com", "scrum_master")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rol de invitación inválido");
        verify(invRepo, never()).save(any());
        verify(emailService, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("unirse: acepta invitación de PO cuando el proyecto todavía no tiene uno (Caso A)")
    void unirse_invitacionProductOwnerSinPoActivo_creaProductOwner() {
        ProjectInvitacion inv = new ProjectInvitacion();
        inv.setProyectoId(proyectoId);
        inv.setEmail("member@prodox.com");
        inv.setCodigo("PRJ-ABC123");
        inv.setUsado(false);
        inv.setRol("product_owner");

        when(invRepo.findByCodigoAndUsadoFalse("PRJ-ABC123")).thenReturn(Optional.of(inv));
        when(userRepo.findById(memberId)).thenReturn(Optional.of(memberUser));
        when(memberRepo.existsByProyectoIdAndUserId(proyectoId, memberId.toString())).thenReturn(false);
        when(memberRepo.existsByProyectoIdAndRol(proyectoId, "product_owner")).thenReturn(false);
        when(memberRepo.save(any(ProjectMember.class))).thenAnswer(i -> i.getArgument(0));

        ProjectMemberDto dto = service.unirse(memberId.toString(), new UnirseProyectoRequest("PRJ-ABC123"));

        assertThat(dto.rol()).isEqualTo("product_owner");
    }

    @Test
    @DisplayName("unirse: respaldo ante condición de carrera — rechaza aceptar una invitación de PO si el proyecto YA tiene uno activo")
    void unirse_invitacionProductOwnerPeroYaHayPoActivo_rechaza() {
        ProjectInvitacion inv = new ProjectInvitacion();
        inv.setProyectoId(proyectoId);
        inv.setEmail("member@prodox.com");
        inv.setCodigo("PRJ-ABC123");
        inv.setUsado(false);
        inv.setRol("product_owner");

        when(invRepo.findByCodigoAndUsadoFalse("PRJ-ABC123")).thenReturn(Optional.of(inv));
        when(userRepo.findById(memberId)).thenReturn(Optional.of(memberUser));
        when(memberRepo.existsByProyectoIdAndUserId(proyectoId, memberId.toString())).thenReturn(false);
        when(memberRepo.existsByProyectoIdAndRol(proyectoId, "product_owner")).thenReturn(true);

        assertThatThrownBy(() -> service.unirse(memberId.toString(), new UnirseProyectoRequest("PRJ-ABC123")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya tiene un Product Owner");
        verify(memberRepo, never()).save(any());
        verify(invRepo, never()).save(any());
    }

    @Test
    @DisplayName("cambiarRol: rechaza promover a otro miembro a Product Owner si el proyecto ya tiene uno (Caso B)")
    void cambiarRol_yaHayProductOwner_rechazaPromoverAOtroMiembro() {
        ProjectMember sm = new ProjectMember();
        sm.setProyectoId(proyectoId); sm.setUserId(smId.toString()); sm.setRol("scrum_master");
        ProjectMember target = new ProjectMember();
        target.setProyectoId(proyectoId); target.setUserId(memberId.toString()); target.setRol("scrum_member");

        when(memberRepo.findByProyectoIdAndUserId(proyectoId, smId.toString())).thenReturn(Optional.of(sm));
        when(memberRepo.findByProyectoIdAndUserId(proyectoId, memberId.toString())).thenReturn(Optional.of(target));
        when(memberRepo.existsByProyectoIdAndRol(proyectoId, "product_owner")).thenReturn(true);

        assertThatThrownBy(() -> service.cambiarRol(proyectoId, smId.toString(), memberId.toString(), "product_owner"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya tiene un Product Owner");
        verify(memberRepo, never()).save(any());
    }

    @Test
    @DisplayName("cambiarRol: liberar al Product Owner actual (a Scrum Member) permite delegar uno nuevo después (Caso D)")
    void cambiarRol_liberaProductOwnerYPermiteDelegarUnoNuevo() {
        ProjectMember sm = new ProjectMember();
        sm.setProyectoId(proyectoId); sm.setUserId(smId.toString()); sm.setRol("scrum_master");
        ProjectMember poActual = new ProjectMember();
        poActual.setProyectoId(proyectoId); poActual.setUserId(memberId.toString()); poActual.setRol("product_owner");

        when(memberRepo.findByProyectoIdAndUserId(proyectoId, smId.toString())).thenReturn(Optional.of(sm));
        when(memberRepo.findByProyectoIdAndUserId(proyectoId, memberId.toString())).thenReturn(Optional.of(poActual));
        when(memberRepo.save(any(ProjectMember.class))).thenAnswer(i -> i.getArgument(0));

        // Paso 1: PO actual -> Scrum Member (libera el rol; no debe consultar unicidad, va HACIA scrum_member).
        ProjectMemberDto dto = service.cambiarRol(proyectoId, smId.toString(), memberId.toString(), "scrum_member");
        assertThat(dto.rol()).isEqualTo("scrum_member");
        verify(memberRepo, never()).existsByProyectoIdAndRol(any(), any());

        // Paso 2: ahora que el proyecto quedó sin PO, delegar uno nuevo debe ser posible.
        UUID otroUserId = UUID.randomUUID();
        ProjectMember nuevoCandidato = new ProjectMember();
        nuevoCandidato.setProyectoId(proyectoId); nuevoCandidato.setUserId(otroUserId.toString()); nuevoCandidato.setRol("scrum_member");
        when(memberRepo.findByProyectoIdAndUserId(proyectoId, otroUserId.toString())).thenReturn(Optional.of(nuevoCandidato));
        when(memberRepo.existsByProyectoIdAndRol(proyectoId, "product_owner")).thenReturn(false);

        ProjectMemberDto dto2 = service.cambiarRol(proyectoId, smId.toString(), otroUserId.toString(), "product_owner");
        assertThat(dto2.rol()).isEqualTo("product_owner");
    }

    @Test
    @DisplayName("cambiarRol: 'promover' a quien YA es Product Owner es un no-op permitido, no choca contra sí mismo")
    void cambiarRol_targetYaEsProductOwner_noValidaContraSiMismo() {
        ProjectMember sm = new ProjectMember();
        sm.setProyectoId(proyectoId); sm.setUserId(smId.toString()); sm.setRol("scrum_master");
        ProjectMember target = new ProjectMember();
        target.setProyectoId(proyectoId); target.setUserId(memberId.toString()); target.setRol("product_owner");

        when(memberRepo.findByProyectoIdAndUserId(proyectoId, smId.toString())).thenReturn(Optional.of(sm));
        when(memberRepo.findByProyectoIdAndUserId(proyectoId, memberId.toString())).thenReturn(Optional.of(target));
        when(memberRepo.save(any(ProjectMember.class))).thenAnswer(i -> i.getArgument(0));

        ProjectMemberDto dto = service.cambiarRol(proyectoId, smId.toString(), memberId.toString(), "product_owner");

        assertThat(dto.rol()).isEqualTo("product_owner");
        verify(memberRepo, never()).existsByProyectoIdAndRol(any(), any());
    }

    @Test
    @DisplayName("V39/V40: un mismo usuario puede ser Product Owner en un proyecto y Scrum Member en otro (rol por proyecto, no global)")
    void mismoUsuario_productOwnerEnUnProyecto_scrumMemberEnOtro() {
        UUID proyectoBId = UUID.randomUUID();

        ProjectInvitacion invA = new ProjectInvitacion();
        invA.setProyectoId(proyectoId); invA.setEmail("member@prodox.com"); invA.setCodigo("PRJ-AAA111");
        invA.setUsado(false); invA.setRol("product_owner");

        ProjectInvitacion invB = new ProjectInvitacion();
        invB.setProyectoId(proyectoBId); invB.setEmail("member@prodox.com"); invB.setCodigo("PRJ-BBB222");
        invB.setUsado(false); invB.setRol("scrum_member");

        when(invRepo.findByCodigoAndUsadoFalse("PRJ-AAA111")).thenReturn(Optional.of(invA));
        when(invRepo.findByCodigoAndUsadoFalse("PRJ-BBB222")).thenReturn(Optional.of(invB));
        when(userRepo.findById(memberId)).thenReturn(Optional.of(memberUser));
        when(memberRepo.existsByProyectoIdAndUserId(any(), eq(memberId.toString()))).thenReturn(false);
        when(memberRepo.existsByProyectoIdAndRol(proyectoId, "product_owner")).thenReturn(false);
        when(memberRepo.save(any(ProjectMember.class))).thenAnswer(i -> i.getArgument(0));

        ProjectMemberDto dtoA = service.unirse(memberId.toString(), new UnirseProyectoRequest("PRJ-AAA111"));
        ProjectMemberDto dtoB = service.unirse(memberId.toString(), new UnirseProyectoRequest("PRJ-BBB222"));

        assertThat(dtoA.proyectoId()).isEqualTo(proyectoId);
        assertThat(dtoA.rol()).isEqualTo("product_owner");
        assertThat(dtoB.proyectoId()).isEqualTo(proyectoBId);
        assertThat(dtoB.rol()).isEqualTo("scrum_member");
    }

    // ── unirse ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("unirse: usuario se une al proyecto con código válido")
    void unirse_codigoValido_retornaMiembro() {
        ProjectInvitacion inv = new ProjectInvitacion();
        inv.setProyectoId(proyectoId);
        inv.setEmail("member@prodox.com");
        inv.setCodigo("PRJ-ABC123");
        inv.setUsado(false);

        when(invRepo.findByCodigoAndUsadoFalse("PRJ-ABC123")).thenReturn(Optional.of(inv));
        when(userRepo.findById(memberId)).thenReturn(Optional.of(memberUser));
        when(memberRepo.existsByProyectoIdAndUserId(proyectoId, memberId.toString())).thenReturn(false);
        when(memberRepo.save(any(ProjectMember.class))).thenAnswer(i -> i.getArgument(0));

        UnirseProyectoRequest req = new UnirseProyectoRequest("PRJ-ABC123");
        ProjectMemberDto dto = service.unirse(memberId.toString(), req);

        assertThat(dto.userEmail()).isEqualTo("member@prodox.com");
        assertThat(dto.rol()).isEqualTo("scrum_member");
        assertThat(inv.getUsado()).isTrue();
        verify(memberRepo).save(any(ProjectMember.class));

        // La invitación nunca modifica el rol global del usuario — solo se
        // lee su email, jamás se guarda ni se toca el AppUser.
        verify(userRepo, never()).save(any(AppUser.class));
    }

    // ── unirse: validación de que el correo autenticado sea el invitado ───

    @Test
    @DisplayName("unirse: el correo invitado coincide (sin distinguir mayúsculas) con el de la cuenta autenticada: acepta normalmente")
    void unirse_correoCoincideIgnorandoMayusculas_aceptaNormalmente() {
        ProjectInvitacion inv = new ProjectInvitacion();
        inv.setProyectoId(proyectoId);
        inv.setEmail("Member@Mpdia.com"); // tal como se guardó al invitar
        inv.setCodigo("PRJ-ABC123");
        inv.setUsado(false);

        when(invRepo.findByCodigoAndUsadoFalse("PRJ-ABC123")).thenReturn(Optional.of(inv));
        when(userRepo.findById(memberId)).thenReturn(Optional.of(memberUser)); // email: member@prodox.com
        when(memberRepo.existsByProyectoIdAndUserId(proyectoId, memberId.toString())).thenReturn(false);
        when(memberRepo.save(any(ProjectMember.class))).thenAnswer(i -> i.getArgument(0));

        ProjectMemberDto dto = service.unirse(memberId.toString(), new UnirseProyectoRequest("PRJ-ABC123"));

        assertThat(dto.rol()).isEqualTo("scrum_member");
        assertThat(inv.getUsado()).isTrue();
    }

    @Test
    @DisplayName("unirse: correo de la cuenta autenticada distinto al invitado — rechaza, NO agrega al proyecto y NO consume la invitación")
    void unirse_correoDistintoAlInvitado_rechazaSinConsumirNiAgregar() {
        ProjectInvitacion inv = new ProjectInvitacion();
        inv.setProyectoId(proyectoId);
        inv.setEmail("usuario@gmail.com"); // invitación destinada a este correo
        inv.setCodigo("PRJ-ABC123");
        inv.setUsado(false);

        AppUser otroUsuario = new AppUser();
        UUID otroId = UUID.randomUUID();
        otroUsuario.setId(otroId);
        otroUsuario.setEmail("otro@gmail.com"); // intenta aceptar con un correo distinto
        otroUsuario.setRole("scrum_master");

        when(invRepo.findByCodigoAndUsadoFalse("PRJ-ABC123")).thenReturn(Optional.of(inv));
        when(userRepo.findById(otroId)).thenReturn(Optional.of(otroUsuario));

        assertThatThrownBy(() -> service.unirse(otroId.toString(), new UnirseProyectoRequest("PRJ-ABC123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("otro correo");

        // Ni se agrega como miembro ni se consume el código — la invitación
        // sigue disponible para que usuario@gmail.com la use.
        verify(memberRepo, never()).save(any());
        verify(invRepo, never()).save(any());
        assertThat(inv.getUsado()).isFalse();
        verify(userRepo, never()).save(any(AppUser.class));
    }

    @Test
    @DisplayName("unirse: un Scrum Master global que acepta una invitación ajena queda como scrum_member SOLO en ese proyecto, sin perder su rol global")
    void unirse_scrumMasterGlobalAceptaInvitacion_quedaComoScrumMemberEnEseProyectoSinPerderRolGlobal() {
        AppUser smGlobal = new AppUser();
        UUID smGlobalId = UUID.randomUUID();
        smGlobal.setId(smGlobalId);
        smGlobal.setEmail("otroscrummaster@prodox.com");
        smGlobal.setRole("scrum_master"); // ya es SM de sus propios proyectos

        ProjectInvitacion inv = new ProjectInvitacion();
        inv.setProyectoId(proyectoId);
        inv.setEmail("otroscrummaster@prodox.com");
        inv.setCodigo("PRJ-ABC123");
        inv.setUsado(false);

        when(invRepo.findByCodigoAndUsadoFalse("PRJ-ABC123")).thenReturn(Optional.of(inv));
        when(userRepo.findById(smGlobalId)).thenReturn(Optional.of(smGlobal));
        when(memberRepo.existsByProyectoIdAndUserId(proyectoId, smGlobalId.toString())).thenReturn(false);
        when(memberRepo.save(any(ProjectMember.class))).thenAnswer(i -> i.getArgument(0));

        ProjectMemberDto dto = service.unirse(smGlobalId.toString(), new UnirseProyectoRequest("PRJ-ABC123"));

        assertThat(dto.rol()).isEqualTo("scrum_member"); // rol EN ESE proyecto
        assertThat(smGlobal.getRole()).isEqualTo("scrum_master"); // rol global intacto
        verify(userRepo, never()).save(any(AppUser.class));
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
    @DisplayName("unirse: una invitación ya utilizada (usado=true) no se encuentra por findByCodigoAndUsadoFalse y se rechaza igual que un código inválido")
    void unirse_invitacionYaUtilizada_lanzaExcepcion() {
        // findByCodigoAndUsadoFalse filtra usado=false: una invitación ya usada
        // simplemente no aparece — este es el mecanismo real de "un solo uso".
        when(invRepo.findByCodigoAndUsadoFalse("PRJ-YAUSAD")).thenReturn(Optional.empty());

        UnirseProyectoRequest req = new UnirseProyectoRequest("PRJ-YAUSAD");

        assertThatThrownBy(() -> service.unirse(memberId.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Código inválido");
        verify(memberRepo, never()).save(any());
    }

    @Test
    @DisplayName("unirse: lanza excepción si la invitación está expirada")
    void unirse_invitacionExpirada_lanzaExcepcion() {
        ProjectInvitacion inv = new ProjectInvitacion();
        inv.setProyectoId(proyectoId);
        inv.setEmail("member@prodox.com");
        inv.setCodigo("PRJ-EXPIRA");
        inv.setUsado(false);
        inv.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));

        when(invRepo.findByCodigoAndUsadoFalse("PRJ-EXPIRA")).thenReturn(Optional.of(inv));

        UnirseProyectoRequest req = new UnirseProyectoRequest("PRJ-EXPIRA");

        assertThatThrownBy(() -> service.unirse(memberId.toString(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiró");
        verify(memberRepo, never()).save(any());
    }

    @Test
    @DisplayName("unirse: una invitación sin expiresAt (creada antes de V35) no se trata como expirada")
    void unirse_invitacionSinExpiresAt_noSeRechazaPorExpiracion() {
        ProjectInvitacion inv = new ProjectInvitacion();
        inv.setProyectoId(proyectoId);
        inv.setEmail("member@prodox.com");
        inv.setCodigo("PRJ-ABC123");
        inv.setUsado(false);
        inv.setExpiresAt(null);

        when(invRepo.findByCodigoAndUsadoFalse("PRJ-ABC123")).thenReturn(Optional.of(inv));
        when(userRepo.findById(memberId)).thenReturn(Optional.of(memberUser));
        when(memberRepo.existsByProyectoIdAndUserId(proyectoId, memberId.toString())).thenReturn(false);
        when(memberRepo.save(any(ProjectMember.class))).thenAnswer(i -> i.getArgument(0));

        UnirseProyectoRequest req = new UnirseProyectoRequest("PRJ-ABC123");
        ProjectMemberDto dto = service.unirse(memberId.toString(), req);

        assertThat(dto.rol()).isEqualTo("scrum_member");
    }

    @Test
    @DisplayName("unirse: lanza excepción si ya es miembro del proyecto")
    void unirse_yaesMiembro_lanzaExcepcion() {
        ProjectInvitacion inv = new ProjectInvitacion();
        inv.setProyectoId(proyectoId);
        inv.setEmail("member@prodox.com");
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

    // ── consultarInvitacion (estado público, sin autenticación) ───────────

    @Test
    @DisplayName("consultarInvitacion: código válido y sin usar retorna estado 'valida' con el nombre del proyecto")
    void consultarInvitacion_valida_retornaEstadoValida() {
        ProjectInvitacion inv = new ProjectInvitacion();
        inv.setProyectoId(proyectoId);
        inv.setCodigo("PRJ-ABC123");
        inv.setUsado(false);
        inv.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));

        when(invRepo.findByCodigo("PRJ-ABC123")).thenReturn(Optional.of(inv));
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));

        InvitacionEstadoDto dto = service.consultarInvitacion("prj-abc123");

        assertThat(dto.estado()).isEqualTo("valida");
        assertThat(dto.proyectoNombre()).isEqualTo("Proyecto Test");
    }

    @Test
    @DisplayName("consultarInvitacion: código inexistente retorna estado 'no_existe'")
    void consultarInvitacion_noExiste_retornaEstadoNoExiste() {
        when(invRepo.findByCodigo("PRJ-NOEXISTE")).thenReturn(Optional.empty());

        InvitacionEstadoDto dto = service.consultarInvitacion("PRJ-NOEXISTE");

        assertThat(dto.estado()).isEqualTo("no_existe");
        assertThat(dto.proyectoNombre()).isNull();
    }

    @Test
    @DisplayName("consultarInvitacion: código expirado retorna estado 'expirada'")
    void consultarInvitacion_expirada_retornaEstadoExpirada() {
        ProjectInvitacion inv = new ProjectInvitacion();
        inv.setProyectoId(proyectoId);
        inv.setCodigo("PRJ-EXPIRA");
        inv.setUsado(false);
        inv.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));

        when(invRepo.findByCodigo("PRJ-EXPIRA")).thenReturn(Optional.of(inv));
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));

        InvitacionEstadoDto dto = service.consultarInvitacion("PRJ-EXPIRA");

        assertThat(dto.estado()).isEqualTo("expirada");
    }

    @Test
    @DisplayName("consultarInvitacion: código ya utilizado retorna estado 'usada' (prevalece sobre la expiración)")
    void consultarInvitacion_usada_retornaEstadoUsada() {
        ProjectInvitacion inv = new ProjectInvitacion();
        inv.setProyectoId(proyectoId);
        inv.setCodigo("PRJ-USADA1");
        inv.setUsado(true);
        inv.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));

        when(invRepo.findByCodigo("PRJ-USADA1")).thenReturn(Optional.of(inv));
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(proyecto));

        InvitacionEstadoDto dto = service.consultarInvitacion("PRJ-USADA1");

        assertThat(dto.estado()).isEqualTo("usada");
    }
}
