// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.controller;

import com.mpdia.dto.CrearMetricaIARequest;
import com.mpdia.dto.MetricaIACreadaDto;
import com.mpdia.dto.MetricaIAPropuestaDto;
import com.mpdia.dto.MetricaIAPropuestaRequest;
import com.mpdia.service.MetricaDuplicadaEnCatalogoException;
import com.mpdia.service.MetricaIAService;
import com.mpdia.service.MetricaPosibleDuplicadaException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FASE 15 — "Crear métrica con IA".
 *
 * - POST /propuesta: genera una propuesta (no persiste nada).
 * - POST /crear: crea la Metrica a partir de la propuesta ya confirmada/editada
 *   por el Scrum Master y la asocia al proyecto mediante el flujo existente.
 */
@Slf4j
@RestController
@RequestMapping("/api/metricas-ia")
@RequiredArgsConstructor
public class MetricaIAController {

    private final MetricaIAService metricaIAService;

    @PostMapping("/propuesta")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<MetricaIAPropuestaDto> generarPropuesta(
            @RequestBody @Valid MetricaIAPropuestaRequest request) {
        log.info("Generando propuesta de métrica con IA");
        MetricaIAPropuestaDto propuesta = metricaIAService.generarPropuesta(request.necesidad());
        return ResponseEntity.ok(propuesta);
    }

    @PostMapping("/crear")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Object> crear(
            @RequestBody @Valid CrearMetricaIARequest request) {
        try {
            log.info("Creando métrica desde propuesta de IA en proyecto {}", request.proyectoId());
            MetricaIACreadaDto creada = metricaIAService.crearDesdeConfirmacion(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(creada);
        } catch (MetricaDuplicadaEnCatalogoException e) {
            // No es un error de negocio bloqueante: ya existe en el catálogo
            // global, así que se devuelve esa métrica para que el frontend
            // pueda ofrecer reutilizarla en vez de fallar sin más.
            log.info("Métrica duplicada en el catálogo, se ofrece reutilizar: {}", e.getMessage());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tipo", "duplicado_exacto");
            body.put("error", e.getMessage());
            body.put("metricaExistente", e.getMetricaExistente());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        } catch (MetricaPosibleDuplicadaException e) {
            // FASE 23: no es un duplicado exacto ni un bloqueo — el catálogo tiene
            // una o más métricas que probablemente miden el mismo concepto. Se
            // devuelven los candidatos con su score y razones para que el frontend
            // muestre el aviso de confirmación (reutilizar / crear diferente / cancelar).
            log.info("Posible(s) duplicado(s) conceptual(es) detectado(s): {}", e.getMessage());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tipo", "posible_duplicado");
            body.put("error", e.getMessage());
            body.put("candidatos", e.getCandidatos());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        } catch (IllegalArgumentException e) {
            log.warn("Solicitud inválida: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (IllegalStateException e) {
            log.error("Error de autorización", e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("Error creando métrica desde IA", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
