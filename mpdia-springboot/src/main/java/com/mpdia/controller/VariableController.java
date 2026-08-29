// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.controller;

import com.mpdia.dto.ActualizarFormulaRequest;
import com.mpdia.dto.CrearVariableRequest;
import com.mpdia.dto.VariableDto;
import com.mpdia.service.VariableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/proyectos/{proyectoId}/variables")
@RequiredArgsConstructor
public class VariableController {

    private final VariableService variableService;

    /** GET /api/proyectos/{proyectoId}/variables — solo miembros del proyecto */
    @GetMapping
    public ResponseEntity<List<VariableDto>> listar(@PathVariable UUID proyectoId, Authentication auth) {
        return ResponseEntity.ok(variableService.listar(auth.getName(), proyectoId));
    }

    /** POST /api/proyectos/{proyectoId}/variables — solo miembros del proyecto */
    @PostMapping
    public ResponseEntity<VariableDto> crear(
            @PathVariable UUID proyectoId,
            @Valid @RequestBody CrearVariableRequest request,
            Authentication auth) {
        return ResponseEntity.ok(variableService.crear(auth.getName(), proyectoId, request));
    }

    /** PATCH /api/proyectos/{proyectoId}/variables/{variableId}/formula — solo miembros del proyecto */
    @PatchMapping("/{variableId}/formula")
    public ResponseEntity<VariableDto> actualizarFormula(
            @PathVariable UUID proyectoId,
            @PathVariable UUID variableId,
            @RequestBody ActualizarFormulaRequest request,
            Authentication auth) {
        return ResponseEntity.ok(variableService.actualizarFormula(auth.getName(), proyectoId, variableId, request));
    }

    /** DELETE /api/proyectos/{proyectoId}/variables/{variableId} — solo miembros del proyecto */
    @DeleteMapping("/{variableId}")
    public ResponseEntity<Void> desactivar(
            @PathVariable UUID proyectoId,
            @PathVariable UUID variableId,
            Authentication auth) {
        variableService.desactivar(auth.getName(), proyectoId, variableId);
        return ResponseEntity.noContent().build();
    }
}
