// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.controller;

import com.prodox.dto.analytics.ProjectOverviewDto;
import com.prodox.dto.analytics.RiskDto;
import com.prodox.dto.analytics.TrendAnalysisDto;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.service.AgileAnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * FASE 21 — Tests de AnalyticsController.
 *
 * Cubre la corrección del defecto "Error cargando dashboard / No se pudo obtener
 * la información del proyecto": el controller no existía, por lo que
 * GET /api/analytics/project/{id}/overview devolvía 404 y el frontend interpretaba
 * cualquier error de red como "no se pudo obtener información del proyecto".
 * Este test confirma que el controller ahora delega correctamente en
 * AgileAnalyticsService (ya existente, sin modificar) para overview/risks/trends,
 * y que un fallo del servicio se propaga (no se traga silenciosamente).
 *
 * FASE 23: se agrega la validación de membresía de proyecto (IDOR detectado
 * en auditoría FASE 22 — este controller no verificaba que el usuario
 * autenticado perteneciera a {proyectoId}). Los tests de "delega
 * correctamente" ahora también configuran un usuario miembro autorizado; se
 * suman casos explícitos de usuario no miembro y usuario autenticado sin
 * acceso.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    @Mock AgileAnalyticsService analyticsService;
    @Mock ProjectMemberRepository projectMemberRepository;

    AnalyticsController controller;

    UUID proyectoId;
    String userId;
    Authentication authMiembro;

    @BeforeEach
    void setUp() {
        controller = new AnalyticsController(analyticsService, projectMemberRepository);
        proyectoId = UUID.randomUUID();
        userId = UUID.randomUUID().toString();
        authMiembro = new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    void overview_usuarioAutorizado_delegaEnAgileAnalyticsServiceYDevuelveDatosReales() {
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        ProjectOverviewDto dto = new ProjectOverviewDto(
                proyectoId, "Sandbox FASE 21", 5, 3, 4,
                Map.of("Significado", java.math.BigDecimal.valueOf(66.67)),
                new ProjectOverviewDto.SprintPerformance(2, java.math.BigDecimal.valueOf(70), "Score más alto"),
                new ProjectOverviewDto.SprintPerformance(1, java.math.BigDecimal.valueOf(65), "Score más bajo"),
                true
        );
        when(analyticsService.getProjectOverview(proyectoId)).thenReturn(dto);

        ResponseEntity<ProjectOverviewDto> respuesta = controller.overview(proyectoId, authMiembro);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isEqualTo(dto);
        assertThat(respuesta.getBody().datosDisponibles()).isTrue();
        verify(analyticsService).getProjectOverview(proyectoId);
    }

    @Test
    void overview_siElServicioFalla_laExcepcionSePropagaYNoSeOculta() {
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        when(analyticsService.getProjectOverview(proyectoId))
                .thenThrow(new IllegalArgumentException("Proyecto no encontrado"));

        assertThatThrownBy(() -> controller.overview(proyectoId, authMiembro))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Proyecto no encontrado");
    }

    @Test
    void overview_usuarioNoMiembroDelProyecto_lanzaSecurityException() {
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(false);

        assertThatThrownBy(() -> controller.overview(proyectoId, authMiembro))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("No tienes acceso a este proyecto");

        verifyNoInteractions(analyticsService);
    }

    @Test
    void risks_usuarioAutorizado_delegaEnAgileAnalyticsService() {
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        RiskDto risk = new RiskDto(proyectoId, "DECLINING_METRIC", "CRITICAL",
                "Impacto en descenso sostenido", "Impacto disminuyó 75,0%", "Impacto", Instant.now());
        when(analyticsService.identifyRisks(proyectoId)).thenReturn(List.of(risk));

        ResponseEntity<List<RiskDto>> respuesta = controller.risks(proyectoId, authMiembro);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).containsExactly(risk);
    }

    @Test
    void risks_usuarioNoMiembroDelProyecto_lanzaSecurityException() {
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(false);

        assertThatThrownBy(() -> controller.risks(proyectoId, authMiembro))
                .isInstanceOf(SecurityException.class);

        verifyNoInteractions(analyticsService);
    }

    @Test
    void trends_usuarioAutorizado_delegaEnAgileAnalyticsServiceConParametrosCorrectos() {
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        TrendAnalysisDto trend = new TrendAnalysisDto(proyectoId, "Impacto", 3, List.of(),
                java.math.BigDecimal.valueOf(50), java.math.BigDecimal.valueOf(24.49),
                "DOWN", java.math.BigDecimal.valueOf(-75), true);
        when(analyticsService.getSprintTrends(proyectoId, null, 3)).thenReturn(List.of(trend));

        ResponseEntity<List<TrendAnalysisDto>> respuesta = controller.trends(proyectoId, 3, null, authMiembro);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).containsExactly(trend);
        verify(analyticsService).getSprintTrends(proyectoId, null, 3);
    }

    @Test
    void trends_sinNumberOfSprints_usaValorPorDefecto5() {
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(true);
        when(analyticsService.getSprintTrends(eq(proyectoId), isNull(), eq(5))).thenReturn(List.of());

        controller.trends(proyectoId, 5, null, authMiembro);

        verify(analyticsService).getSprintTrends(proyectoId, null, 5);
    }

    @Test
    void trends_usuarioNoMiembroDelProyecto_lanzaSecurityException() {
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)).thenReturn(false);

        assertThatThrownBy(() -> controller.trends(proyectoId, 5, null, authMiembro))
                .isInstanceOf(SecurityException.class);

        verifyNoInteractions(analyticsService);
    }

    @Test
    void overview_usuarioAutenticadoSinAccesoAOtroProyecto_noPuedeLeerDatosAjenos() {
        // Usuario válido/autenticado, pero consultando un proyecto distinto al suyo.
        UUID proyectoAjeno = UUID.randomUUID();
        when(projectMemberRepository.existsByProyectoIdAndUserId(proyectoAjeno, userId)).thenReturn(false);

        assertThatThrownBy(() -> controller.overview(proyectoAjeno, authMiembro))
                .isInstanceOf(SecurityException.class);

        verifyNoInteractions(analyticsService);
    }
}
