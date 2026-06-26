// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.controller;

import com.mpdia.dto.ProyectoMetricaDto;
import com.mpdia.dto.VariableDto;
import com.mpdia.service.PlaneacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/planeacion/{proyectoId}")
@RequiredArgsConstructor
public class PlaneacionController {

    private final PlaneacionService planeacionService;

    /** GET /api/planeacion/{proyectoId}/metricas — catálogo con estado por proyecto */
    @GetMapping("/metricas")
    public ResponseEntity<List<ProyectoMetricaDto>> metricas(@PathVariable UUID proyectoId) {
        return ResponseEntity.ok(planeacionService.listarMetricasConEstado(proyectoId));
    }

    /** GET /api/planeacion/{proyectoId}/metricas/seleccionadas */
    @GetMapping("/metricas/seleccionadas")
    public ResponseEntity<List<ProyectoMetricaDto>> seleccionadas(@PathVariable UUID proyectoId) {
        return ResponseEntity.ok(planeacionService.listarSeleccionadas(proyectoId));
    }

    /** POST /api/planeacion/{proyectoId}/metricas/{metricaId}/seleccionar */
    @PostMapping("/metricas/{metricaId}/seleccionar")
    public ResponseEntity<Void> seleccionar(
            @PathVariable UUID proyectoId,
            @PathVariable UUID metricaId) {
        planeacionService.seleccionar(proyectoId, metricaId);
        return ResponseEntity.ok().build();
    }

    /** DELETE /api/planeacion/{proyectoId}/metricas/{metricaId}/seleccionar */
    @DeleteMapping("/metricas/{metricaId}/seleccionar")
    public ResponseEntity<Void> deseleccionar(
            @PathVariable UUID proyectoId,
            @PathVariable UUID metricaId) {
        planeacionService.deseleccionar(proyectoId, metricaId);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/planeacion/{proyectoId}/metricas/{metricaId}/aprobar → genera variable */
    @PostMapping("/metricas/{metricaId}/aprobar")
    public ResponseEntity<VariableDto> aprobar(
            @PathVariable UUID proyectoId,
            @PathVariable UUID metricaId,
            Authentication auth) {
        return ResponseEntity.ok(planeacionService.aprobar(proyectoId, metricaId, auth.getName()));
    }

    /** DELETE /api/planeacion/{proyectoId}/metricas/{metricaId}/aprobar */
    @DeleteMapping("/metricas/{metricaId}/aprobar")
    public ResponseEntity<Void> desaprobar(
            @PathVariable UUID proyectoId,
            @PathVariable UUID metricaId) {
        planeacionService.desaprobar(proyectoId, metricaId);
        return ResponseEntity.noContent().build();
    }

    /** GET /api/planeacion/{proyectoId}/variables — variables generadas */
    @GetMapping("/variables")
    public ResponseEntity<List<VariableDto>> variables(@PathVariable UUID proyectoId) {
        return ResponseEntity.ok(planeacionService.listarVariables(proyectoId));
    }

    /**
     * POST /api/planeacion/{proyectoId}/variables/sincronizar
     * Regenera variables faltantes para métricas ya aprobadas.
     */
    @PostMapping("/variables/sincronizar")
    public ResponseEntity<List<VariableDto>> sincronizar(@PathVariable UUID proyectoId) {
        return ResponseEntity.ok(planeacionService.sincronizarVariables(proyectoId));
    }
}
