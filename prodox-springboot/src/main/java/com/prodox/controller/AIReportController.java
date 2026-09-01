// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.controller;

import com.prodox.dto.ai.AISprintReportDto;
import com.prodox.ratelimit.RateLimitService;
import com.prodox.service.AIReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller REST para reportes ejecutivos de sprint generados por IA.
 * 
 * Endpoints:
 * - POST /api/ai/reports/sprint/{sprintId}/generate - Generar reporte
 * 
 * Nota: No se implementa GET ya que no hay persistencia. Los reportes
 * se generan bajo demanda cada vez que se solicitan.
 * 
 * Requiere autenticación JWT.
 * La autorización sobre el proyecto se valida en AIReportService.
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/reports")
@RequiredArgsConstructor
public class AIReportController {

    private final AIReportService reportService;
    private final RateLimitService rateLimitService;

    /**
     * POST /api/ai/reports/sprint/{sprintId}/generate
     * 
     * Genera un reporte ejecutivo para el sprint especificado.
     * Combina datos objetivos de métricas con interpretación de Gemini.
     * 
     * @param sprintId ID del sprint
     * @param auth Usuario autenticado (obtenido del JWT)
     * @return Reporte ejecutivo generado
     * @throws SecurityException si el usuario no tiene acceso al proyecto (403)
     * @throws IllegalArgumentException si el sprint no existe (400)
     * @throws RuntimeException si se excede el rate limit (429)
     */
    @PostMapping("/sprint/{sprintId}/generate")
    public ResponseEntity<AISprintReportDto> generateReport(
            @PathVariable UUID sprintId,
            Authentication auth) {
        
        String userId = auth.getName();
        log.info("POST generate report para sprint={} usuario={}", sprintId, userId);
        
        // Rate limiting
        if (!rateLimitService.allowRequest(userId)) {
            throw new RuntimeException("Límite de solicitudes excedido. Intente nuevamente en unos momentos.");
        }
        
        AISprintReportDto report = reportService.generateReport(sprintId, userId);
        
        return ResponseEntity.ok(report);
    }
}
