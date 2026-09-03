// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.controller;

import com.prodox.dto.ai.AIInsightDto;
import com.prodox.dto.ai.GenerateInsightsResultDto;
import com.prodox.dto.ai.UpdateInsightDto;
import com.prodox.service.AIInsightsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller REST para AI Insights.
 * 
 * Endpoints:
 * - GET /api/ai/insights/{proyectoId} — Obtener insights existentes
 * - POST /api/ai/insights/generate/{proyectoId} — Generar nuevos insights
 * - POST /api/ai/insights/{insightId}/dismiss — Descartar un insight
 * 
 * Requiere autenticación JWT.
 * La autorización sobre el proyecto se valida en AIInsightsService.
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/insights")
@RequiredArgsConstructor
public class AIInsightsController {

    private final AIInsightsService insightsService;

    /**
     * GET /api/ai/insights/{proyectoId}
     * 
     * Obtiene todos los insights activos (no descartados) de un proyecto.
     * NO genera nuevos insights automáticamente.
     * 
     * @param proyectoId ID del proyecto
     * @param auth Usuario autenticado (obtenido del JWT)
     * @return Lista de insights activos ordenados por fecha descendente
     * @throws SecurityException si el usuario no tiene acceso al proyecto (403)
     * @throws IllegalArgumentException si el proyecto no existe (400)
     */
    @GetMapping("/{proyectoId}")
    public ResponseEntity<List<AIInsightDto>> getInsights(
            @PathVariable UUID proyectoId,
            Authentication auth) {
        
        String userId = auth.getName();
        log.info("GET insights para proyecto={} usuario={}", proyectoId, userId);
        
        List<AIInsightDto> insights = insightsService.getProjectInsights(proyectoId, userId);
        
        return ResponseEntity.ok(insights);
    }

    /**
     * POST /api/ai/insights/generate/{proyectoId}
     * 
     * Genera nuevos insights para el proyecto basándose en análisis de métricas.
     * Analiza tendencias, anomalías, riesgos y comparaciones.
     * 
     * @param proyectoId ID del proyecto
     * @param auth Usuario autenticado (obtenido del JWT)
     * @return Insights recién generados junto con el estado real de la corrida
     *         (COMPLETE/PARTIAL/FAILED/SIN_SENALES/SIN_DATOS — FASE 23, ver
     *         GenerateInsightsResultDto) — antes solo se devolvía la lista,
     *         sin forma de distinguir una generación completa de una parcial.
     * @throws SecurityException si el usuario no tiene acceso al proyecto (403)
     * @throws IllegalArgumentException si el proyecto no existe (400)
     */
    @PostMapping("/generate/{proyectoId}")
    public ResponseEntity<GenerateInsightsResultDto> generateInsights(
            @PathVariable UUID proyectoId,
            Authentication auth) {

        String userId = auth.getName();
        log.info("POST generate insights para proyecto={} usuario={}", proyectoId, userId);

        GenerateInsightsResultDto result = insightsService.generateInsights(proyectoId, userId);

        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/ai/insights/{insightId}/dismiss
     * 
     * Marca un insight como descartado. El insight ya no aparecerá en las
     * consultas de insights activos.
     * 
     * @param insightId ID del insight a descartar
     * @param auth Usuario autenticado (obtenido del JWT)
     * @return 204 No Content
     * @throws SecurityException si el usuario no tiene acceso al proyecto del insight (403)
     * @throws IllegalArgumentException si el insight no existe (400)
     */
    @PostMapping("/{insightId}/dismiss")
    public ResponseEntity<Void> dismissInsight(
            @PathVariable UUID insightId,
            Authentication auth) {
        
        String userId = auth.getName();
        log.info("POST dismiss insight={} usuario={}", insightId, userId);
        
        insightsService.dismissInsight(insightId, userId);
        
        return ResponseEntity.noContent().build();
    }

    /**
     * PUT /api/ai/insights/{insightId}
     * 
     * Actualiza los campos editables de un insight (title, description, recommendation).
     * Permite al usuario refinar o corregir insights generados automáticamente.
     * 
     * @param insightId ID del insight a actualizar
     * @param updateDto DTO con los campos a actualizar
     * @param auth Usuario autenticado (obtenido del JWT)
     * @return Insight actualizado
     * @throws SecurityException si el usuario no tiene acceso al proyecto del insight (403)
     * @throws IllegalArgumentException si el insight no existe (400)
     */
    @PutMapping("/{insightId}")
    public ResponseEntity<AIInsightDto> updateInsight(
            @PathVariable UUID insightId,
            @RequestBody UpdateInsightDto updateDto,
            Authentication auth) {
        
        String userId = auth.getName();
        log.info("PUT update insight={} usuario={}", insightId, userId);
        
        AIInsightDto updated = insightsService.updateInsight(insightId, updateDto, userId);
        
        return ResponseEntity.ok(updated);
    }
}
