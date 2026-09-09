package com.prodox.controller;

import com.prodox.dto.MetricaSugeridaDto;
import com.prodox.ratelimit.RateLimitException;
import com.prodox.ratelimit.RateLimitService;
import com.prodox.service.CopilotoPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/copiloto-plan")
@RequiredArgsConstructor
public class CopilotoPlanController {

    private final CopilotoPlanService copilotoPlanService;
    private final RateLimitService rateLimitService;

    /**
     * POST /api/copiloto-plan/generar-metricas?factorId=...
     * El Copiloto usa Gemini para generar métricas de planeación
     * basadas en el factor seleccionado.
     *
     * Bloque 10B-2: sin límite previamente — cualquier usuario autenticado
     * podía disparar una llamada a Gemini por request sin restricción.
     * Reutiliza el mismo RateLimitService que protege el resto de
     * endpoints GenAI (verificado antes de invocar al service: si se
     * excede el límite, nunca se llama a Gemini).
     */
    @PostMapping("/generar-metricas")
    public ResponseEntity<List<MetricaSugeridaDto>> generarMetricas(
            @RequestParam UUID factorId,
            Authentication auth) {
        if (!rateLimitService.allowRequest(auth.getName())) {
            throw new RateLimitException(
                "Has alcanzado temporalmente el límite de consultas de IA. " +
                "Intenta nuevamente en unos minutos.");
        }
        return ResponseEntity.ok(copilotoPlanService.generarMetricas(factorId));
    }
}
