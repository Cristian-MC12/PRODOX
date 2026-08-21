// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.controller;

import com.mpdia.dto.CalcularMetricaRequest;
import com.mpdia.dto.ResultadoMetricaDto;
import com.mpdia.service.CalculoMetricaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * API REST para cálculo de métricas.
 * Fase 16.8: Motor determinista de cálculo.
 */
@RestController
@RequestMapping("/api/metricas")
@RequiredArgsConstructor
public class CalculoMetricaController {
    
    private final CalculoMetricaService calculoService;
    
    /**
     * Calcula una métrica para un sprint específico.
     * 
     * POST /api/metricas/{metricaId}/calcular
     * 
     * Body: {
     *   "proyectoId": "uuid",
     *   "sprintId": "uuid"
     * }
     * 
     * Respuesta 200: ResultadoMetricaDto
     * Respuesta 400: Datos incompletos, división por cero, etc.
     * Respuesta 403: Sin permisos
     * Respuesta 404: Métrica/proyecto/sprint no encontrado
     */
    @PostMapping("/{metricaId}/calcular")
    public ResponseEntity<?> calcularMetrica(
            @PathVariable UUID metricaId,
            @RequestBody CalcularMetricaRequest request,
            Authentication auth) {
        
        try {
            String userId = auth.getName();
            
            ResultadoMetricaDto resultado = calculoService.calcularMetrica(
                metricaId, request, userId);
            
            return ResponseEntity.ok(resultado);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "error", "DATOS_INVALIDOS",
                    "mensaje", e.getMessage()
                ));
                
        } catch (ArithmeticException e) {
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "error", "ERROR_ARITMETICO",
                    "mensaje", e.getMessage()
                ));
                
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "error", "ERROR_INTERNO",
                    "mensaje", "Error calculando métrica"
                ));
        }
    }
}
