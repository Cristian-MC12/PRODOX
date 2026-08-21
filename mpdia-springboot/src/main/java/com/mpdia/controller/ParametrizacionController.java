// Autor: Cristian Santiago Martinez Cordoba — MPDIA
// Fase 16.9.2-A: Controller para parametrizaciones
// Fase 16.9.4: Integración completa del agente GenAI
package com.mpdia.controller;

import com.mpdia.dto.AprobarParametrizacionRequest;
import com.mpdia.dto.GuardarPropuestaRequest;
import com.mpdia.dto.ParametrizacionRequest;
import com.mpdia.dto.PropuestaParametrizacionDto;
import com.mpdia.entity.MetricParametrizacion;
import com.mpdia.repository.MetricParametrizacionRepository;
import com.mpdia.service.NombreVariableInvalidoException;
import com.mpdia.service.ParametrizacionService;
import com.mpdia.service.TipoOperacionInvalidoException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * API REST para gestión de parametrizaciones.
 * 
 * FASE 16.9.2-A: Endpoint mínimo necesario para frontend de métricas académicas.
 * FASE 16.9.4: Integración completa del agente de parametrización GenAI.
 * 
 * Endpoints:
 * - GET /ultima-aprobada: Obtiene la última parametrización aprobada
 * - POST /generar-propuestas: Genera propuestas usando Gemini
 * - POST /guardar-propuesta: Guarda una propuesta en estado "propuesta"
 * - POST /{id}/aprobar: Aprueba formalmente una parametrización
 */
@Slf4j
@RestController
@RequestMapping("/api/parametrizacion")
@RequiredArgsConstructor
public class ParametrizacionController {
    
    private final MetricParametrizacionRepository parametrizacionRepository;
    private final ParametrizacionService parametrizacionService;
    
    /**
     * Obtiene la última versión aprobada de una parametrización.
     * 
     * Esencial para:
     * - Mostrar estado de parametrización en frontend
     * - Verificar si se puede ejecutar una métrica
     * - Obtener configuración para cálculos
     * 
     * @param metricaId UUID de la métrica
     * @param proyectoId UUID del proyecto
     * @return Parametrización aprobada o 204 No Content si no existe
     */
    @GetMapping("/ultima-aprobada")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<MetricParametrizacion> obtenerUltimaAprobada(
            @RequestParam UUID metricaId,
            @RequestParam UUID proyectoId) {
        
        return parametrizacionRepository
                .findUltimaVersionAprobada(metricaId, proyectoId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
    
    /**
     * Genera propuestas de parametrización usando Gemini.
     * 
     * FASE 16.9.4: Expone el asistente GenAI por REST.
     * 
     * La IA genera UNA propuesta (no 3) que actúa como asistente experto.
     * En caso de error con Gemini, retorna una propuesta genérica (nunca falla).
     * 
     * @param request Datos de la métrica para generar la propuesta
     * @return Lista con 1 propuesta generada por IA
     */
    @PostMapping("/propuestas")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<PropuestaParametrizacionDto>> generarPropuestas(
            @RequestBody @Valid ParametrizacionRequest request) {
        
        try {
            log.info("Generando propuesta con Gemini para métrica: {}", request.metricaNombre());
            List<PropuestaParametrizacionDto> propuestas = parametrizacionService.generarPropuestas(request);
            return ResponseEntity.ok(propuestas);
        } catch (Exception e) {
            log.error("Error generando propuestas con Gemini", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(List.of());
        }
    }
    
    /**
     * Guarda una propuesta de parametrización generada por IA.
     * 
     * FASE 16.9.4: Permite guardar propuesta sin aprobar automáticamente.
     * 
     * La propuesta queda en estado "propuesta" hasta que el usuario la apruebe
     * explícitamente mediante el endpoint /aprobar.
     * 
     * @param request Datos de la propuesta a guardar
     * @return Parametrización guardada con estado "propuesta"
     */
    @PostMapping("/guardar-propuesta")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<MetricParametrizacion> guardarPropuesta(
            @RequestBody @Valid GuardarPropuestaRequest request) {
        
        try {
            log.info("Guardando propuesta para métrica {} en proyecto {}", 
                     request.metricaId(), request.proyectoId());
            MetricParametrizacion parametrizacion = parametrizacionService.guardarPropuesta(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(parametrizacion);
        } catch (TipoOperacionInvalidoException e) {
            log.warn("Propuesta rechazada: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (NombreVariableInvalidoException e) {
            log.warn("Propuesta rechazada: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (IllegalStateException e) {
            log.error("Error de autorización", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("Error guardando propuesta", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Aprueba formalmente una parametrización.
     * 
     * FASE 16.9.4: Cambia el estado de "propuesta" a "aprobada".
     * 
     * - Guarda snapshot de configuración aprobada (reproducibilidad)
     * - Marca versiones anteriores como INACTIVA
     * - Registra quién y cuándo aprobó
     * 
     * @param parametrizacionId ID de la parametrización a aprobar
     * @param request Configuración final aprobada
     * @return Parametrización aprobada con versionado
     */
    @PostMapping("/{id}/aprobar")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<MetricParametrizacion> aprobarParametrizacion(
            @PathVariable("id") UUID parametrizacionId,
            @RequestBody @Valid AprobarParametrizacionRequest request) {
        
        try {
            log.info("Aprobando parametrización {}", parametrizacionId);
            MetricParametrizacion parametrizacion = parametrizacionService.aprobarParametrizacion(
                    parametrizacionId, request);
            return ResponseEntity.ok(parametrizacion);
        } catch (TipoOperacionInvalidoException e) {
            log.warn("Aprobación rechazada: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (NombreVariableInvalidoException e) {
            log.warn("Aprobación rechazada: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (IllegalArgumentException e) {
            log.error("Parametrización no encontrada", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException e) {
            log.error("Error de estado o autorización", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("Error aprobando parametrización", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
