// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.controller;

import com.prodox.dto.ai.ChatRequest;
import com.prodox.dto.ai.ChatResponse;
import com.prodox.ratelimit.RateLimitException;
import com.prodox.ratelimit.RateLimitService;
import com.prodox.service.AICopilotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST para el AI Copilot.
 * 
 * Endpoints:
 * - POST /api/ai/copilot/chat — Enviar mensaje al AI Copilot
 * 
 * Requiere autenticación JWT.
 * La autorización sobre el proyecto se valida en AICopilotService.
 * Rate limiting aplicado por usuario autenticado.
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/copilot")
@RequiredArgsConstructor
public class AICopilotController {

    private final AICopilotService copilotService;
    private final RateLimitService rateLimitService;

    /**
     * POST /api/ai/copilot/chat
     * 
     * Envía un mensaje al AI Copilot en el contexto de un proyecto.
     * 
     * Rate Limiting: Límite configurable por usuario autenticado.
     * 
     * @param request ChatRequest con mensaje, proyectoId y sprintId opcional
     * @param auth Usuario autenticado (obtenido del JWT)
     * @return ChatResponse con la respuesta del AI Copilot
     * @throws RateLimitException si el usuario excede el límite (429)
     * @throws SecurityException si el usuario no tiene acceso al proyecto (403)
     * @throws IllegalArgumentException si proyecto/sprint no existe o parámetros inválidos (400)
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            Authentication auth) {
        
        String userId = auth.getName();
        
        // Verificar rate limit ANTES de cualquier lógica de negocio
        if (!rateLimitService.allowRequest(userId)) {
            throw new RateLimitException(
                "Has alcanzado temporalmente el límite de consultas del AI Copilot. " +
                "Intenta nuevamente en unos minutos."
            );
        }
        
        log.info("Chat request de usuario {} para proyecto {}", userId, request.proyectoId());
        
        ChatResponse response = copilotService.chat(request, userId);
        
        return ResponseEntity.ok(response);
    }
}
