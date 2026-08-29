// Autor: Cristian Santiago Martinez Cordoba — MPDIA
// Fase 16.9.1: Controller para métricas académicas
package com.mpdia.controller;

import com.mpdia.dto.*;
import com.mpdia.entity.MetricParametrizacion;
import com.mpdia.service.MetricaAcademicaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * API REST para métricas académicas parametrizables.
 * 
 * FASE 16.9.1: Ciclo completo de métricas con respaldo académico.
 * 
 * Endpoints:
 * - POST /propuesta: Genera propuesta de parametrización con IA
 * - POST /guardar-propuesta: Guarda propuesta académica
 * - POST /ejecutar: Ejecuta métrica y calcula resultado
 * - GET /historico: Consulta histórico de resultados
 * - POST /resultados/{id}/interpretar: Solicita interpretación IA
 */
@RestController
@RequestMapping("/api/metricas-academicas")
@RequiredArgsConstructor
public class MetricaAcademicaController {
    
    private final MetricaAcademicaService service;
    
    /**
     * Genera propuesta de parametrización para métrica académica.
     * Gemini asiste en estructurar la parametrización basándose en la fuente académica.
     */
    @PostMapping("/propuesta")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PropuestaParametrizacionDto> generarPropuesta(
            @RequestBody MetricaAcademicaRequest request) {
        
        PropuestaParametrizacionDto propuesta = service.generarPropuestaAcademica(request);
        return ResponseEntity.ok(propuesta);
    }
    
    /**
     * Guarda propuesta de parametrización académica.
     * Estado inicial: "propuesta" (requiere aprobación).
     */
    @PostMapping("/guardar-propuesta")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<MetricParametrizacion> guardarPropuesta(
            @RequestBody GuardarPropuestaAcademicaRequest request) {
        
        // Construir request para métrica académica
        MetricaAcademicaRequest metricaRequest = new MetricaAcademicaRequest(
            request.proyectoId(),
            request.metricaId(),
            null, // codigoMetrica
            null, // nombreMetrica
            null, // definicion
            request.fuenteAcademica(),
            request.formulaAcademica(),
            request.tipoOperacion(),
            request.unidadResultado(),
            request.frecuenciaCaptura()
        );
        
        // Construir propuesta
        PropuestaParametrizacionDto propuesta = new PropuestaParametrizacionDto(
            "Propuesta",
            request.objetivo(),
            request.procedimiento(),
            request.indicadorVariable(),
            request.escala(),
            request.frecuenciaCaptura(),
            request.fuenteAcademica(),
            request.formulaAcademica(),
            request.tipoOperacion(),
            request.unidadResultado(),
            "Propuesta académica",
            null, // nombreVariable: fuera de alcance de este endpoint (Fase 16.9.1, no forma parte del incremento 16.10-E)
            request.escalaTipo(),
            request.escalaMin(),
            request.escalaMax(),
            request.escalaPaso(),
            request.escalaSinLimite(),
            request.escalaDescripcion()
        );
        
        MetricParametrizacion parametrizacion = service.guardarPropuestaAcademica(
            metricaRequest, propuesta);
        
        return ResponseEntity.ok(parametrizacion);
    }
    
    /**
     * Ejecuta una métrica académica en un sprint específico.
     * 
     * Flujo:
     * 1. Validar parametrización aprobada
     * 2. Capturar valores
     * 3. Calcular resultado (motor determinista, NO IA)
     * 4. Persistir con trazabilidad completa
     */
    @PostMapping("/{metricaId}/ejecutar")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ResultadoMetricaDto> ejecutar(
            @PathVariable UUID metricaId,
            @RequestBody EjecutarMetricaAcademicaRequest request) {
        
        ResultadoMetricaDto resultado = service.ejecutarMetricaAcademica(metricaId, request);
        return ResponseEntity.ok(resultado);
    }
    
    /**
     * Obtiene el histórico de resultados de una métrica en un proyecto.
     * Ordenado por fecha descendente.
     */
    @GetMapping("/{metricaId}/historico")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<ResultadoMetricaDto>> obtenerHistorico(
            @PathVariable UUID metricaId,
            @RequestParam UUID proyectoId) {
        
        List<ResultadoMetricaDto> historico = service.obtenerHistorico(metricaId, proyectoId);
        return ResponseEntity.ok(historico);
    }
    
    /**
     * Solicita interpretación IA de un resultado ya calculado.
     * Gemini NO calcula, solo interpreta resultados existentes.
     */
    @PostMapping("/resultados/{resultadoId}/interpretar")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<InterpretacionIADto> interpretar(
            @PathVariable UUID resultadoId) {
        
        InterpretacionIADto interpretacion = service.solicitarInterpretacionIA(resultadoId);
        return ResponseEntity.ok(interpretacion);
    }
}

/**
 * Request para guardar propuesta académica.
 */
record GuardarPropuestaAcademicaRequest(
    UUID proyectoId,
    UUID metricaId,
    String fuenteAcademica,
    String formulaAcademica,
    String tipoOperacion,
    String unidadResultado,
    String objetivo,
    String procedimiento,
    String indicadorVariable,
    String escala,
    String frecuenciaCaptura,
    /** Escala estructurada — ver ParametrizacionService.validarEscalaEstructurada(). */
    String escalaTipo,
    java.math.BigDecimal escalaMin,
    java.math.BigDecimal escalaMax,
    java.math.BigDecimal escalaPaso,
    Boolean escalaSinLimite,
    String escalaDescripcion
) {}
