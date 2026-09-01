// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.controller;

import com.prodox.repository.ProjectMemberRepository;
import com.prodox.service.PlaneacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Auditoría transversal: ningún endpoint de Planeación validaba membresía al
 * proyecto — cualquier usuario autenticado podía consultar, seleccionar, aprobar
 * o desaprobar métricas de cualquier proyecto conociendo su UUID. PlaneacionService
 * se deja intacto (también lo usan internamente AICopilotService, MetricRankingService
 * y MetricaIAService, cada uno ya autorizado en su propio borde); la autorización
 * del camino HTTP se agrega solo en este controller. No se exige rol de Scrum
 * Master porque el código no lo establece — solo membresía.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlaneacionController — autorización")
class PlaneacionControllerTest {

    @Mock private PlaneacionService planeacionService;
    @Mock private ProjectMemberRepository projectMemberRepo;

    private PlaneacionController controller;

    private UUID proyectoId;
    private UUID metricaId;
    private Authentication authExterno;
    private Authentication authMiembro;

    @BeforeEach
    void setUp() {
        controller = new PlaneacionController(planeacionService, projectMemberRepo);
        proyectoId = UUID.randomUUID();
        metricaId = UUID.randomUUID();
        authExterno = new UsernamePasswordAuthenticationToken("externo", null, List.of());
        authMiembro = new UsernamePasswordAuthenticationToken("miembro-a", null, List.of());
    }

    @Test
    @DisplayName("metricas: usuario externo lanza SecurityException")
    void metricas_usuarioExterno_lanzaSecurityException() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, "externo")).thenReturn(false);

        assertThatThrownBy(() -> controller.metricas(proyectoId, authExterno))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("metricas: miembro del proyecto es permitido")
    void metricas_miembroDelProyecto_permitido() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, "miembro-a")).thenReturn(true);
        when(planeacionService.listarMetricasConEstado(proyectoId)).thenReturn(List.of());

        controller.metricas(proyectoId, authMiembro);
    }

    @Test
    @DisplayName("seleccionar: usuario externo lanza SecurityException sin llegar al servicio")
    void seleccionar_usuarioExterno_lanzaSecurityException() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, "externo")).thenReturn(false);

        assertThatThrownBy(() -> controller.seleccionar(proyectoId, metricaId, authExterno))
                .isInstanceOf(SecurityException.class);

        org.mockito.Mockito.verifyNoInteractions(planeacionService);
    }

    @Test
    @DisplayName("aprobar: usuario externo lanza SecurityException sin llegar al servicio")
    void aprobar_usuarioExterno_lanzaSecurityException() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, "externo")).thenReturn(false);

        assertThatThrownBy(() -> controller.aprobar(proyectoId, metricaId, authExterno))
                .isInstanceOf(SecurityException.class);

        org.mockito.Mockito.verifyNoInteractions(planeacionService);
    }

    @Test
    @DisplayName("variables: usuario externo lanza SecurityException")
    void variables_usuarioExterno_lanzaSecurityException() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, "externo")).thenReturn(false);

        assertThatThrownBy(() -> controller.variables(proyectoId, authExterno))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("sincronizar: usuario externo lanza SecurityException sin llegar al servicio")
    void sincronizar_usuarioExterno_lanzaSecurityException() {
        when(projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, "externo")).thenReturn(false);

        assertThatThrownBy(() -> controller.sincronizar(proyectoId, authExterno))
                .isInstanceOf(SecurityException.class);

        org.mockito.Mockito.verifyNoInteractions(planeacionService);
    }
}
