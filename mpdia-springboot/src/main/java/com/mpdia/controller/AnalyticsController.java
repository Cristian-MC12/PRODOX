// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.controller;

import com.mpdia.dto.analytics.ProjectOverviewDto;
import com.mpdia.dto.analytics.RiskDto;
import com.mpdia.dto.analytics.TrendAnalysisDto;
import com.mpdia.repository.ProjectMemberRepository;
import com.mpdia.service.AgileAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * FASE 21 — Controller REST que expone AgileAnalyticsService.
 *
 * Causa raíz del defecto "Error cargando dashboard / No se pudo obtener la
 * información del proyecto": AgileAnalyticsService ya tenía getProjectOverview(),
 * identifyRisks() y getSprintTrends() completamente implementados, pero nunca
 * existió un @RestController que los expusiera vía HTTP — el frontend
 * (AnalyticsService, analytics.service.ts) siempre llamó a
 * GET /api/analytics/project/{id}/overview, una ruta que jamás existió
 * (confirmado: HTTP 404 "Recurso no encontrado"). Este controller es
 * exclusivamente la wiring que faltaba: no agrega ninguna lógica de negocio
 * nueva, solo delega en el servicio ya existente.
 *
 * FASE 23: los tres endpoints no validaban que el usuario autenticado fuera
 * miembro de {proyectoId} (a diferencia de AIInsightsController, que sí lo
 * hace vía AIInsightsService.validateProjectAccess) — cualquier usuario
 * autenticado podía leer overview/risks/trends de un proyecto ajeno (IDOR
 * confirmado en auditoría FASE 22). Se agrega la misma validación aquí, sin
 * tocar AgileAnalyticsService (fuera de alcance de FASE 23).
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AgileAnalyticsService analyticsService;
    private final ProjectMemberRepository projectMemberRepository;

    /** GET /api/analytics/project/{proyectoId}/overview */
    @GetMapping("/project/{proyectoId}/overview")
    public ResponseEntity<ProjectOverviewDto> overview(@PathVariable UUID proyectoId, Authentication auth) {
        validarAcceso(proyectoId, auth);
        return ResponseEntity.ok(analyticsService.getProjectOverview(proyectoId));
    }

    /** GET /api/analytics/project/{proyectoId}/risks */
    @GetMapping("/project/{proyectoId}/risks")
    public ResponseEntity<List<RiskDto>> risks(@PathVariable UUID proyectoId, Authentication auth) {
        validarAcceso(proyectoId, auth);
        return ResponseEntity.ok(analyticsService.identifyRisks(proyectoId));
    }

    /** GET /api/analytics/project/{proyectoId}/trends?numberOfSprints=&categoria= */
    @GetMapping("/project/{proyectoId}/trends")
    public ResponseEntity<List<TrendAnalysisDto>> trends(
            @PathVariable UUID proyectoId,
            @RequestParam(required = false, defaultValue = "5") Integer numberOfSprints,
            @RequestParam(required = false) String categoria,
            Authentication auth) {
        validarAcceso(proyectoId, auth);
        return ResponseEntity.ok(analyticsService.getSprintTrends(proyectoId, categoria, numberOfSprints));
    }

    /** Mismo patrón de autorización que AIInsightsService.validateProjectAccess. */
    private void validarAcceso(UUID proyectoId, Authentication auth) {
        String userId = auth.getName();
        if (!projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)) {
            throw new SecurityException("No tienes acceso a este proyecto");
        }
    }
}
