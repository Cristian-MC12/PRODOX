// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.controller;

import com.mpdia.dto.ProyectoMetricaDto;
import com.mpdia.dto.VariableDto;
import com.mpdia.repository.ProjectMemberRepository;
import com.mpdia.service.PlaneacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Auditoría transversal: ningún endpoint validaba membresía al proyecto —
 * cualquier usuario autenticado podía consultar, seleccionar, aprobar o
 * desaprobar métricas de cualquier proyecto conociendo su UUID.
 * PlaneacionService se deja intacto porque también lo usan internamente
 * AICopilotService, MetricRankingService y MetricaIAService, cada uno ya
 * autorizado en su propio borde antes de delegar aquí; la autorización del
 * camino HTTP se agrega solo en este controller, sin duplicarla dentro del
 * servicio compartido. No se exige rol de Scrum Master porque el código no
 * lo establece — solo membresía, igual que el resto del módulo.
 */
@RestController
@RequestMapping("/api/planeacion/{proyectoId}")
@RequiredArgsConstructor
public class PlaneacionController {

    private final PlaneacionService planeacionService;
    private final ProjectMemberRepository projectMemberRepo;

    /** GET /api/planeacion/{proyectoId}/metricas — catálogo con estado por proyecto (solo miembros) */
    @GetMapping("/metricas")
    public ResponseEntity<List<ProyectoMetricaDto>> metricas(@PathVariable UUID proyectoId, Authentication auth) {
        validarAcceso(auth.getName(), proyectoId);
        return ResponseEntity.ok(planeacionService.listarMetricasConEstado(proyectoId));
    }

    /** GET /api/planeacion/{proyectoId}/metricas/seleccionadas (solo miembros) */
    @GetMapping("/metricas/seleccionadas")
    public ResponseEntity<List<ProyectoMetricaDto>> seleccionadas(@PathVariable UUID proyectoId, Authentication auth) {
        validarAcceso(auth.getName(), proyectoId);
        return ResponseEntity.ok(planeacionService.listarSeleccionadas(proyectoId));
    }

    /** POST /api/planeacion/{proyectoId}/metricas/{metricaId}/seleccionar (solo miembros) */
    @PostMapping("/metricas/{metricaId}/seleccionar")
    public ResponseEntity<Void> seleccionar(
            @PathVariable UUID proyectoId,
            @PathVariable UUID metricaId,
            Authentication auth) {
        validarAcceso(auth.getName(), proyectoId);
        planeacionService.seleccionar(proyectoId, metricaId);
        return ResponseEntity.ok().build();
    }

    /** DELETE /api/planeacion/{proyectoId}/metricas/{metricaId}/seleccionar (solo miembros) */
    @DeleteMapping("/metricas/{metricaId}/seleccionar")
    public ResponseEntity<Void> deseleccionar(
            @PathVariable UUID proyectoId,
            @PathVariable UUID metricaId,
            Authentication auth) {
        validarAcceso(auth.getName(), proyectoId);
        planeacionService.deseleccionar(proyectoId, metricaId);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/planeacion/{proyectoId}/metricas/{metricaId}/aprobar → genera variable (solo miembros) */
    @PostMapping("/metricas/{metricaId}/aprobar")
    public ResponseEntity<VariableDto> aprobar(
            @PathVariable UUID proyectoId,
            @PathVariable UUID metricaId,
            Authentication auth) {
        validarAcceso(auth.getName(), proyectoId);
        return ResponseEntity.ok(planeacionService.aprobar(proyectoId, metricaId, auth.getName()));
    }

    /** DELETE /api/planeacion/{proyectoId}/metricas/{metricaId}/aprobar (solo miembros) */
    @DeleteMapping("/metricas/{metricaId}/aprobar")
    public ResponseEntity<Void> desaprobar(
            @PathVariable UUID proyectoId,
            @PathVariable UUID metricaId,
            Authentication auth) {
        validarAcceso(auth.getName(), proyectoId);
        planeacionService.desaprobar(proyectoId, metricaId);
        return ResponseEntity.noContent().build();
    }

    /** GET /api/planeacion/{proyectoId}/variables — variables generadas (solo miembros) */
    @GetMapping("/variables")
    public ResponseEntity<List<VariableDto>> variables(@PathVariable UUID proyectoId, Authentication auth) {
        validarAcceso(auth.getName(), proyectoId);
        return ResponseEntity.ok(planeacionService.listarVariables(proyectoId));
    }

    /**
     * POST /api/planeacion/{proyectoId}/variables/sincronizar
     * Regenera variables faltantes para métricas ya aprobadas. Solo miembros.
     */
    @PostMapping("/variables/sincronizar")
    public ResponseEntity<List<VariableDto>> sincronizar(@PathVariable UUID proyectoId, Authentication auth) {
        validarAcceso(auth.getName(), proyectoId);
        return ResponseEntity.ok(planeacionService.sincronizarVariables(proyectoId));
    }

    private void validarAcceso(String userId, UUID proyectoId) {
        if (!projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)) {
            throw new SecurityException("No tienes acceso a este proyecto");
        }
    }
}
