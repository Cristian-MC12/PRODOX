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

        InvitarProyectoRequest req = new InvitarProyectoRequest("member@prodox.com");
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

        InvitarProyectoRequest req = new InvitarProyectoRequest("member@prodox.com");
        InvitarProyectoResponse res = service.invitar(proyectoId, smId.toString(), req);

        assertThat(res.codigo()).startsWith("PRJ-");
        assertThat(res.emailEnviado()).isFalse();
        verify(invRepo).save(any(ProjectInvitacion.class));
    }

    @Test
    @DisplayName("invitar: lanza excepción si el proyecto no existe")
    void invitar_proyectoInexistente_lanzaExcepcion() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.empty());

        InvitarProyectoRequest req = new InvitarProyectoRequest("alguien@prodox.com");

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
        InvitarProyectoRequest req = new InvitarProyectoRequest("alguien@prodox.com");

        assertThatThrownBy(() -> service.invitar(proyectoId, otroId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Solo el Scrum Master puede invitar");
        verify(invRepo, never()).save(any());
        verify(emailService, never()).enviar(anyString(), anyString(), anyString());
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
