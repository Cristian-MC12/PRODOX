// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.controller;

import com.prodox.dto.CalcularMetricaRequest;
import com.prodox.dto.ResultadoMetricaDto;
import com.prodox.entity.ResultadoMetrica;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.repository.ResultadoMetricaRepository;
import com.prodox.service.CalculoMetricaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
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
    private final ResultadoMetricaRepository resultadoMetricaRepository;
    private final ProjectMemberRepository projectMemberRepository;

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
            
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                    "error", "SIN_PERMISOS",
                    "mensaje", e.getMessage()
                ));

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

    /**
     * Histórico de resultados VIGENTES (uno por sprint, V37) de una métrica en
     * un proyecto — fuente para que Evaluación/gráficas muestren el resultado
     * calculado del equipo por período en vez de RegistroValor crudo.
     *
     * GET /api/metricas/{metricaId}/resultados?proyectoId=...
     *
     * Endpoint aditivo: no reemplaza ni renombra /calcular. Ordenado por fecha
     * de cálculo ascendente (coherente con el orden cronológico que ya usan
     * las demás series de Evaluación).
     */
    @GetMapping("/{metricaId}/resultados")
    public ResponseEntity<?> obtenerResultadosVigentes(
            @PathVariable UUID metricaId,
            @RequestParam UUID proyectoId,
            Authentication auth) {

        if (!projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, auth.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "SIN_PERMISOS", "mensaje", "No tienes acceso a este proyecto"));
        }

        List<ResultadoMetrica> resultados = resultadoMetricaRepository
            .findByProyectoIdAndMetrica_IdAndVigenteTrue(proyectoId, metricaId)
            .stream()
            .sorted(Comparator.comparing(ResultadoMetrica::getCalculadoAt))
            .toList();

        return ResponseEntity.ok(resultados.stream().map(r -> new ResultadoMetricaDto(
            r.getId(), metricaId, r.getMetrica().getNombre(), proyectoId, r.getSprintId(),
            r.getParametrizacionId(), r.getParametrizacionVersion(), r.getTipoCalculo(),
            r.getExpresionUtilizada(), r.getValoresUtilizados(), r.getResultado(), r.getUnidad(),
            r.getEstado(), r.getMensajeError(), r.getCalculadoAt()
        )).toList());
    }
}
