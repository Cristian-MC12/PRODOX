// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.controller;

import com.prodox.dto.ActualizarHistoriaUsuarioRequest;
import com.prodox.dto.AsignarSprintHistoriaRequest;
import com.prodox.dto.CambiarEstadoHistoriaRequest;
import com.prodox.dto.CambiarPrioridadRequest;
import com.prodox.dto.CrearHistoriaUsuarioRequest;
import com.prodox.dto.HistoriaUsuarioDto;
import com.prodox.entity.ProjectMember;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.service.HistoriaUsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Backlog de historias de usuario (V39 — Product Owner). Mismo estilo que
 * PlaneacionControllerTest/SprintController: instancia el controller
 * directamente con mocks, sin levantar contexto Spring. Cubre
 * específicamente que el proyectoId de una historia SIEMPRE se resuelve del
 * lado del servidor (vía historiaService.detalle) antes de autorizar — un
 * cliente no puede forzar la autorización pasando datos propios para una
 * historia que en realidad pertenece a otro proyecto (IDOR/BOLA).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HistoriaUsuarioController — autorización (V39, Product Owner)")
class HistoriaUsuarioControllerTest {

    @Mock private HistoriaUsuarioService historiaService;
    @Mock private ProjectMemberRepository projectMemberRepo;

    private HistoriaUsuarioController controller;

    private UUID proyectoId;
    private UUID historiaId;
    private Authentication authExterno;
    private Authentication authScrumMember;
    private Authentication authProductOwner;
    private Authentication authScrumMaster;

    @BeforeEach
    void setUp() {
        controller = new HistoriaUsuarioController(historiaService, projectMemberRepo);
        proyectoId = UUID.randomUUID();
        historiaId = UUID.randomUUID();
        authExterno = new UsernamePasswordAuthenticationToken("externo", null, List.of());
        authScrumMember = new UsernamePasswordAuthenticationToken("miembro", null, List.of());
        authProductOwner = new UsernamePasswordAuthenticationToken("product-owner", null, List.of());
        authScrumMaster = new UsernamePasswordAuthenticationToken("scrum-master", null, List.of());
    }

    private ProjectMember miembro(String rol) {
        ProjectMember m = new ProjectMember();
        m.setProyectoId(proyectoId);
        m.setRol(rol);
        return m;
    }

    private HistoriaUsuarioDto dtoDe(UUID proyecto) {
        return new HistoriaUsuarioDto(historiaId, proyecto, null, "Título", "Desc", "Criterios",
                "media", "pendiente", "creador", Instant.now(), Instant.now());
    }

    // ── listar / detalle: cualquier miembro puede leer ─────────────────────

    @Test
    @DisplayName("listar: usuario externo (no miembro) lanza SecurityException")
    void listar_usuarioExterno_lanzaSecurityException() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, "externo")).thenReturn(false);

        assertThatThrownBy(() -> controller.listar(proyectoId, authExterno))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(historiaService);
    }

    @Test
    @DisplayName("listar: un Scrum Member (no PO) puede consultar el backlog — es solo lectura")
    void listar_scrumMember_permitido() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, "miembro")).thenReturn(true);
        when(historiaService.listar(proyectoId)).thenReturn(List.of());

        controller.listar(proyectoId, authScrumMember);

        verify(historiaService).listar(proyectoId);
    }

    @Test
    @DisplayName("detalle: usuario de OTRO proyecto no puede acceder a la historia (proyectoId resuelto server-side)")
    void detalle_usuarioDeOtroProyecto_lanzaSecurityException() {
        UUID proyectoReal = UUID.randomUUID();
        when(historiaService.detalle(historiaId)).thenReturn(dtoDe(proyectoReal));
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoReal, "externo")).thenReturn(false);

        assertThatThrownBy(() -> controller.detalle(historiaId, authExterno))
                .isInstanceOf(SecurityException.class);
    }

    // ── escritura: exclusiva de Product Owner ──────────────────────────────

    @Test
    @DisplayName("crear: Product Owner del proyecto puede crear historias")
    void crear_productOwner_permitido() {
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, "product-owner"))
                .thenReturn(Optional.of(miembro("product_owner")));
        when(historiaService.crear(eq(proyectoId), eq("product-owner"), any())).thenReturn(dtoDe(proyectoId));

        CrearHistoriaUsuarioRequest req = new CrearHistoriaUsuarioRequest("Título", null, null, null);
        controller.crear(proyectoId, req, authProductOwner);

        verify(historiaService).crear(proyectoId, "product-owner", req);
    }

    @Test
    @DisplayName("crear: Scrum Member no puede crear historias (operación exclusiva del PO)")
    void crear_scrumMember_lanzaSecurityException() {
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, "miembro"))
                .thenReturn(Optional.of(miembro("scrum_member")));

        CrearHistoriaUsuarioRequest req = new CrearHistoriaUsuarioRequest("Título", null, null, null);

        assertThatThrownBy(() -> controller.crear(proyectoId, req, authScrumMember))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Product Owner");
        verifyNoInteractions(historiaService);
    }

    @Test
    @DisplayName("crear: el Scrum Master del proyecto NO obtiene automáticamente permisos de Product Owner")
    void crear_scrumMaster_lanzaSecurityException() {
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, "scrum-master"))
                .thenReturn(Optional.of(miembro("scrum_master")));

        CrearHistoriaUsuarioRequest req = new CrearHistoriaUsuarioRequest("Título", null, null, null);

        assertThatThrownBy(() -> controller.crear(proyectoId, req, authScrumMaster))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Product Owner");
        verifyNoInteractions(historiaService);
    }

    @Test
    @DisplayName("crear: un usuario sin autorización (no miembro) recibe SecurityException (403)")
    void crear_noMiembro_lanzaSecurityException() {
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, "externo")).thenReturn(Optional.empty());

        CrearHistoriaUsuarioRequest req = new CrearHistoriaUsuarioRequest("Título", null, null, null);

        assertThatThrownBy(() -> controller.crear(proyectoId, req, authExterno))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(historiaService);
    }

    @Test
    @DisplayName("crear: Product Owner de OTRO proyecto no puede crear historias en este proyecto")
    void crear_productOwnerDeOtroProyecto_lanzaSecurityException() {
        // "product-owner" es PO del proyecto X, pero acá intenta crear en proyectoId (proyecto Y):
        // no tiene fila de ProjectMember para proyectoId, así que no aparece como miembro.
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, "product-owner")).thenReturn(Optional.empty());

        CrearHistoriaUsuarioRequest req = new CrearHistoriaUsuarioRequest("Título", null, null, null);

        assertThatThrownBy(() -> controller.crear(proyectoId, req, authProductOwner))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(historiaService);
    }

    @Test
    @DisplayName("actualizar: el proyectoId de la historia se resuelve server-side — un PO no puede editar historias de otro proyecto")
    void actualizar_historiaDeOtroProyecto_lanzaSecurityException() {
        UUID otroProyecto = UUID.randomUUID();
        when(historiaService.detalle(historiaId)).thenReturn(dtoDe(otroProyecto));
        when(projectMemberRepo.findByProyectoIdAndUserId(otroProyecto, "product-owner")).thenReturn(Optional.empty());

        ActualizarHistoriaUsuarioRequest req = new ActualizarHistoriaUsuarioRequest("Nuevo título", null, null);

        assertThatThrownBy(() -> controller.actualizar(historiaId, req, authProductOwner))
                .isInstanceOf(SecurityException.class);
        verify(historiaService, never()).actualizar(any(), any());
    }

    @Test
    @DisplayName("cambiarPrioridad: Product Owner puede cambiar la prioridad")
    void cambiarPrioridad_productOwner_permitido() {
        when(historiaService.detalle(historiaId)).thenReturn(dtoDe(proyectoId));
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, "product-owner"))
                .thenReturn(Optional.of(miembro("product_owner")));
        when(historiaService.cambiarPrioridad(historiaId, "alta")).thenReturn(dtoDe(proyectoId));

        controller.cambiarPrioridad(historiaId, new CambiarPrioridadRequest("alta"), authProductOwner);

        verify(historiaService).cambiarPrioridad(historiaId, "alta");
    }

    @Test
    @DisplayName("cambiarPrioridad: Scrum Member no puede cambiar la prioridad")
    void cambiarPrioridad_scrumMember_lanzaSecurityException() {
        when(historiaService.detalle(historiaId)).thenReturn(dtoDe(proyectoId));
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, "miembro"))
                .thenReturn(Optional.of(miembro("scrum_member")));

        assertThatThrownBy(() -> controller.cambiarPrioridad(historiaId, new CambiarPrioridadRequest("alta"), authScrumMember))
                .isInstanceOf(SecurityException.class);
        verify(historiaService, never()).cambiarPrioridad(any(), any());
    }

    @Test
    @DisplayName("cambiarEstado: exclusivo de Product Owner")
    void cambiarEstado_scrumMember_lanzaSecurityException() {
        when(historiaService.detalle(historiaId)).thenReturn(dtoDe(proyectoId));
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, "miembro"))
                .thenReturn(Optional.of(miembro("scrum_member")));

        assertThatThrownBy(() -> controller.cambiarEstado(historiaId, new CambiarEstadoHistoriaRequest("en_progreso"), authScrumMember))
                .isInstanceOf(SecurityException.class);
        verify(historiaService, never()).cambiarEstado(any(), any());
    }

    @Test
    @DisplayName("asignarSprint: Product Owner puede asignar/desasignar sprint")
    void asignarSprint_productOwner_permitido() {
        when(historiaService.detalle(historiaId)).thenReturn(dtoDe(proyectoId));
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, "product-owner"))
                .thenReturn(Optional.of(miembro("product_owner")));
        UUID sprintId = UUID.randomUUID();
        when(historiaService.asignarSprint(historiaId, sprintId)).thenReturn(dtoDe(proyectoId));

        controller.asignarSprint(historiaId, new AsignarSprintHistoriaRequest(sprintId), authProductOwner);

        verify(historiaService).asignarSprint(historiaId, sprintId);
    }

    @Test
    @DisplayName("eliminar: exclusivo de Product Owner")
    void eliminar_scrumMaster_lanzaSecurityException() {
        when(historiaService.detalle(historiaId)).thenReturn(dtoDe(proyectoId));
        when(projectMemberRepo.findByProyectoIdAndUserId(proyectoId, "scrum-master"))
                .thenReturn(Optional.of(miembro("scrum_master")));

        assertThatThrownBy(() -> controller.eliminar(historiaId, authScrumMaster))
                .isInstanceOf(SecurityException.class);
        verify(historiaService, never()).eliminar(any());
    }
}
