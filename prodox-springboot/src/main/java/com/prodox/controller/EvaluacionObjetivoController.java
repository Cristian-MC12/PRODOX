// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.controller;

import com.prodox.dto.CrearEvaluacionObjetivoRequest;
import com.prodox.dto.EvaluacionObjetivoDto;
import com.prodox.service.EvaluacionObjetivoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controlador para gestionar evaluaciones manuales del cumplimiento del objetivo del sprint.
 * 
 * FASE 6B: Implementación de evaluación manual sin criterios cuantitativos automáticos.
 * 
 * Endpoints:
 * - POST /api/sprints/{sprintId}/evaluacion-objetivo: crear evaluación (Scrum Master)
 * - GET /api/sprints/{sprintId}/evaluacion-objetivo: consultar evaluación (miembros)
 * - PATCH /api/sprints/{sprintId}/evaluacion-objetivo: actualizar evaluación (Scrum Master)
 * 
 * Autorización:
 * - Crear/actualizar: solo Scrum Master del proyecto
 * - Consultar: cualquier miembro del proyecto
 */
@RestController
@RequestMapping("/api/sprints")
@RequiredArgsConstructor
public class EvaluacionObjetivoController {

    private final EvaluacionObjetivoService evaluacionService;

    /**
     * Crea una evaluación para el objetivo de un sprint.
     * 
     * Restricciones:
     * - Solo Scrum Master del proyecto
     * - Sprint debe estar finalizado o reabierto
     * - No debe existir evaluación previa (use PATCH para actualizar)
     * 
     * @param sprintId ID del sprint a evaluar
     * @param request datos de la evaluación
     * @param auth usuario autenticado
     * @return 201 Created con la evaluación creada
     * @throws IllegalArgumentException (400) si sprint no existe o no está finalizado
     * @throws SecurityException (403) si usuario no es Scrum Master
     * @throws IllegalStateException (409) si ya existe evaluación
     */
    @PostMapping("/{sprintId}/evaluacion-objetivo")
    public ResponseEntity<EvaluacionObjetivoDto> crear(
            @PathVariable UUID sprintId,
            @Valid @RequestBody CrearEvaluacionObjetivoRequest request,
            Authentication auth) {
        
        String userId = auth.getName();
        EvaluacionObjetivoDto evaluacion = evaluacionService.crear(sprintId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(evaluacion);
    }

    /**
     * Obtiene la evaluación del objetivo de un sprint.
     * 
     * Restricciones:
     * - Usuario debe ser miembro del proyecto
     * - Sprint debe tener evaluación
     * 
     * @param sprintId ID del sprint
     * @param auth usuario autenticado
     * @return 200 OK con la evaluación
     * @throws IllegalArgumentException (404) si sprint no existe o no tiene evaluación
     * @throws SecurityException (403) si usuario no tiene acceso al proyecto
     */
    @GetMapping("/{sprintId}/evaluacion-objetivo")
    public ResponseEntity<EvaluacionObjetivoDto> obtener(
            @PathVariable UUID sprintId,
            Authentication auth) {
        
        String userId = auth.getName();
        EvaluacionObjetivoDto evaluacion = evaluacionService.obtener(sprintId, userId);
        return ResponseEntity.ok(evaluacion);
    }

    /**
     * Actualiza una evaluación existente del objetivo de un sprint.
     * 
     * Restricciones:
     * - Solo Scrum Master del proyecto
     * - Debe existir evaluación previa (use POST para crear)
     * 
     * @param sprintId ID del sprint
     * @param request nuevos datos de la evaluación
     * @param auth usuario autenticado
     * @return 200 OK con la evaluación actualizada
     * @throws IllegalArgumentException (404) si sprint no existe o no tiene evaluación
     * @throws SecurityException (403) si usuario no es Scrum Master
     */
    @PatchMapping("/{sprintId}/evaluacion-objetivo")
    public ResponseEntity<EvaluacionObjetivoDto> actualizar(
            @PathVariable UUID sprintId,
            @Valid @RequestBody CrearEvaluacionObjetivoRequest request,
            Authentication auth) {
        
        String userId = auth.getName();
        EvaluacionObjetivoDto evaluacion = evaluacionService.actualizar(sprintId, request, userId);
        return ResponseEntity.ok(evaluacion);
    }

    /**
     * Manejo centralizado de excepciones para este controlador.
     * 
     * IllegalArgumentException → 400 Bad Request (validaciones de negocio)
     * SecurityException → 403 Forbidden (autorización)
     * IllegalStateException → 409 Conflict (duplicados)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<String> handleSecurity(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
}
