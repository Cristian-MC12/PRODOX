// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.controller;

import com.mpdia.dto.GuardarValoresRequest;
import com.mpdia.dto.VariablesMetricaResponse;
import com.mpdia.service.VariableDinamicaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Endpoints para captura dinámica de variables desde parametrizaciones aprobadas.
 * Fase 16.7: Formulario dinámico de variables.
 */
@RestController
@RequestMapping("/api/metricas")
@RequiredArgsConstructor
public class VariableDinamicaController {

    private final VariableDinamicaService service;

    /**
     * GET /api/metricas/{metricaId}/variables
     * 
     * Obtiene las variables dinámicas para captura en un sprint.
     * Materializa variables on-demand desde la parametrización aprobada.
     * 
     * @param metricaId ID de la métrica
     * @param proyectoId ID del proyecto
     * @param sprintId ID del sprint
     * @return Variables con sus valores actuales (si existen)
     */
    @GetMapping("/{metricaId}/variables")
    public ResponseEntity<VariablesMetricaResponse> obtenerVariables(
            @PathVariable UUID metricaId,
            @RequestParam UUID proyectoId,
            @RequestParam UUID sprintId,
            Authentication authentication) {
        
        String userId = authentication.getName();
        VariablesMetricaResponse response = service.obtenerVariables(metricaId, proyectoId, sprintId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/metricas/{metricaId}/valores
     * 
     * Guarda valores de variables en un sprint.
     * 
     * @param metricaId ID de la métrica
     * @param request Valores a guardar
     * @return 204 No Content si exitoso
     */
    @PostMapping("/{metricaId}/valores")
    public ResponseEntity<Void> guardarValores(
            @PathVariable UUID metricaId,
            @RequestBody GuardarValoresRequest request,
            Authentication authentication) {
        
        String userId = authentication.getName();
        service.guardarValores(metricaId, request, userId);
        return ResponseEntity.noContent().build();
    }
}
