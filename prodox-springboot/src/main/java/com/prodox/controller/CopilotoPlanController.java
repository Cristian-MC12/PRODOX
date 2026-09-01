package com.prodox.controller;

import com.prodox.dto.MetricaSugeridaDto;
import com.prodox.service.CopilotoPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/copiloto-plan")
@RequiredArgsConstructor
public class CopilotoPlanController {

    private final CopilotoPlanService copilotoPlanService;

    /**
     * POST /api/copiloto-plan/generar-metricas?factorId=...
     * El Copiloto usa Gemini para generar métricas de planeación
     * basadas en el factor seleccionado.
     */
    @PostMapping("/generar-metricas")
    public ResponseEntity<List<MetricaSugeridaDto>> generarMetricas(
            @RequestParam UUID factorId) {
        return ResponseEntity.ok(copilotoPlanService.generarMetricas(factorId));
    }
}
