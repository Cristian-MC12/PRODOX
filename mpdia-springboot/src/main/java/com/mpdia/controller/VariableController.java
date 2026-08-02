// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.controller;

import com.mpdia.dto.ActualizarFormulaRequest;
import com.mpdia.dto.CrearVariableRequest;
import com.mpdia.dto.VariableDto;
import com.mpdia.service.VariableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/proyectos/{proyectoId}/variables")
@RequiredArgsConstructor
public class VariableController {

    private final VariableService variableService;

    /** GET /api/proyectos/{proyectoId}/variables */
    @GetMapping
    public ResponseEntity<List<VariableDto>> listar(@PathVariable UUID proyectoId) {
        return ResponseEntity.ok(variableService.listar(proyectoId));
    }

    /** POST /api/proyectos/{proyectoId}/variables */
    @PostMapping
    public ResponseEntity<VariableDto> crear(
            @PathVariable UUID proyectoId,
            @Valid @RequestBody CrearVariableRequest request) {
        return ResponseEntity.ok(variableService.crear(proyectoId, request));
    }

    /** PATCH /api/proyectos/{proyectoId}/variables/{variableId}/formula */
    @PatchMapping("/{variableId}/formula")
    public ResponseEntity<VariableDto> actualizarFormula(
            @PathVariable UUID proyectoId,
            @PathVariable UUID variableId,
            @RequestBody ActualizarFormulaRequest request) {
        return ResponseEntity.ok(variableService.actualizarFormula(variableId, request));
    }

    /** DELETE /api/proyectos/{proyectoId}/variables/{variableId} */
    @DeleteMapping("/{variableId}")
    public ResponseEntity<Void> desactivar(
            @PathVariable UUID proyectoId,
            @PathVariable UUID variableId) {
        variableService.desactivar(variableId);
        return ResponseEntity.noContent().build();
    }
}
