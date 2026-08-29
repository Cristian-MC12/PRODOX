// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.controller;

import com.mpdia.dto.EvaluacionSprintDto;
import com.mpdia.dto.MetricaEvaluacionDetalleDto;
import com.mpdia.entity.Sprint;
import com.mpdia.repository.ProjectMemberRepository;
import com.mpdia.repository.SprintRepository;
import com.mpdia.service.EvaluacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Auditoría transversal: ninguno de estos endpoints validaba membresía al
 * proyecto — cualquier usuario autenticado podía consultar la evaluación de
 * cualquier proyecto conociendo su UUID. EvaluacionService.evaluar()/
 * evaluarSprint()/evaluarDetalle() se dejan intactos porque también los usa
 * internamente AgileAnalyticsService (Copiloto IA), ya autorizado en su
 * propio controller; la autorización para el camino HTTP se agrega aquí,
 * en el borde del controller, sin duplicarla dentro del servicio compartido.
 */
@RestController
@RequestMapping("/api/evaluacion")
@RequiredArgsConstructor
public class EvaluacionController {

    private final EvaluacionService evaluacionService;
    private final ProjectMemberRepository projectMemberRepo;
    private final SprintRepository sprintRepo;

    /** GET /api/evaluacion/proyecto/{proyectoId} — todos los sprints (solo miembros) */
    @GetMapping("/proyecto/{proyectoId}")
    public ResponseEntity<List<EvaluacionSprintDto>> porProyecto(@PathVariable UUID proyectoId, Authentication auth) {
        validarAcceso(auth.getName(), proyectoId);
        return ResponseEntity.ok(evaluacionService.evaluar(proyectoId));
    }

    /** GET /api/evaluacion/sprint/{sprintId} (solo miembros del proyecto del sprint) */
    @GetMapping("/sprint/{sprintId}")
    public ResponseEntity<List<EvaluacionSprintDto>> porSprint(@PathVariable UUID sprintId, Authentication auth) {
        Sprint sprint = sprintRepo.findById(sprintId)
                .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado."));
        validarAcceso(auth.getName(), sprint.getProyectoId());
        return ResponseEntity.ok(evaluacionService.evaluarSprint(sprintId));
    }

    /**
     * GET /api/evaluacion/proyecto/{proyectoId}/detalle — evaluación detallada por variable:
     * todos los registros reales (todos los sprints), estadísticas y desglose por sprint.
     * Fuente de datos de las gráficas de evolución/comparación/análisis en el frontend.
     * Solo miembros del proyecto.
     */
    @GetMapping("/proyecto/{proyectoId}/detalle")
    public ResponseEntity<List<MetricaEvaluacionDetalleDto>> detalle(@PathVariable UUID proyectoId, Authentication auth) {
        validarAcceso(auth.getName(), proyectoId);
        return ResponseEntity.ok(evaluacionService.evaluarDetalle(proyectoId));
    }

    private void validarAcceso(String userId, UUID proyectoId) {
        if (!projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)) {
            throw new SecurityException("No tienes acceso a este proyecto");
        }
    }
}
