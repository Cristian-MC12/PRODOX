// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.controller;

import com.mpdia.dto.EvaluacionSprintDto;
import com.mpdia.dto.MetricaEvaluacionDetalleDto;
import com.mpdia.service.EvaluacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/evaluacion")
@RequiredArgsConstructor
public class EvaluacionController {

    private final EvaluacionService evaluacionService;

    /** GET /api/evaluacion/proyecto/{proyectoId} — todos los sprints */
    @GetMapping("/proyecto/{proyectoId}")
    public ResponseEntity<List<EvaluacionSprintDto>> porProyecto(@PathVariable UUID proyectoId) {
        return ResponseEntity.ok(evaluacionService.evaluar(proyectoId));
    }

    /** GET /api/evaluacion/sprint/{sprintId} */
    @GetMapping("/sprint/{sprintId}")
    public ResponseEntity<List<EvaluacionSprintDto>> porSprint(@PathVariable UUID sprintId) {
        return ResponseEntity.ok(evaluacionService.evaluarSprint(sprintId));
    }

    /**
     * GET /api/evaluacion/proyecto/{proyectoId}/detalle — evaluación detallada por variable:
     * todos los registros reales (todos los sprints), estadísticas y desglose por sprint.
     * Fuente de datos de las gráficas de evolución/comparación/análisis en el frontend.
     */
    @GetMapping("/proyecto/{proyectoId}/detalle")
    public ResponseEntity<List<MetricaEvaluacionDetalleDto>> detalle(@PathVariable UUID proyectoId) {
        return ResponseEntity.ok(evaluacionService.evaluarDetalle(proyectoId));
    }
}
