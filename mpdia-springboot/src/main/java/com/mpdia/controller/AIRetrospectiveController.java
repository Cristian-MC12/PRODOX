// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.controller;

import com.mpdia.dto.ai.AIRetrospectiveDto;
import com.mpdia.ratelimit.RateLimitService;
import com.mpdia.service.AIRetrospectiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller REST para retrospectivas inteligentes de sprint generadas por IA.
 * 
 * Endpoints:
 * - POST /api/ai/retrospectives/sprint/{sprintId}/generate - Generar retrospectiva
 * 
 * Nota: No se implementa GET ya que no hay persistencia. Las retrospectivas
 * se generan bajo demanda cada vez que se solicitan.
 * 
 * Requiere autenticación JWT.
 * La autorización sobre el proyecto se valida en AIRetrospectiveService.
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/retrospectives")
@RequiredArgsConstructor
public class AIRetrospectiveController {

    private final AIRetrospectiveService retrospectiveService;
    private final RateLimitService rateLimitService;

    /**
     * POST /api/ai/retrospectives/sprint/{sprintId}/generate
     * 
     * Genera una retrospectiva inteligente para el sprint especificado.
     * Compara con sprint anterior y genera observaciones y preguntas.
     * 
     * @param sprintId ID del sprint
     * @param auth Usuario autenticado (obtenido del JWT)
     * @return Retrospectiva generada
     * @throws SecurityException si el usuario no tiene acceso al proyecto (403)
     * @throws IllegalArgumentException si el sprint no existe (400)
     * @throws RuntimeException si se excede el rate limit (429)
     */
    @PostMapping("/sprint/{sprintId}/generate")
    public ResponseEntity<AIRetrospectiveDto> generateRetrospective(
            @PathVariable UUID sprintId,
            Authentication auth) {
        
        String userId = auth.getName();
        log.info("POST generate retrospective para sprint={} usuario={}", sprintId, userId);
        
        // Rate limiting
        if (!rateLimitService.allowRequest(userId)) {
            throw new RuntimeException("Límite de solicitudes excedido. Intente nuevamente en unos momentos.");
        }
        
        AIRetrospectiveDto retrospective = retrospectiveService.generateRetrospective(sprintId, userId);
        
        return ResponseEntity.ok(retrospective);
    }
}
