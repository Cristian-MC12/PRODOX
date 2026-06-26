// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.controller;

import com.mpdia.dto.CrearSiguienteSprintRequest;
import com.mpdia.dto.SprintDto;
import com.mpdia.service.SprintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sprints")
@RequiredArgsConstructor
public class SprintController {

    private final SprintService sprintService;

    @GetMapping("/{proyectoId}/activo")
    public ResponseEntity<SprintDto> activo(@PathVariable UUID proyectoId) {
        return ResponseEntity.ok(sprintService.getSprintActivo(proyectoId));
    }

    @GetMapping("/{proyectoId}")
    public ResponseEntity<List<SprintDto>> listar(@PathVariable UUID proyectoId) {
        return ResponseEntity.ok(sprintService.listarSprints(proyectoId));
    }

    @GetMapping("/detalle/{sprintId}")
    public ResponseEntity<SprintDto> getById(@PathVariable UUID sprintId) {
        return ResponseEntity.ok(sprintService.getById(sprintId));
    }

    @PostMapping("/{proyectoId}/siguiente")
    public ResponseEntity<SprintDto> siguiente(
            @PathVariable UUID proyectoId,
            @Valid @RequestBody CrearSiguienteSprintRequest request) {
        return ResponseEntity.ok(sprintService.cerrarEIniciarSiguiente(proyectoId, request));
    }

    /** Solo admin — reabrir un sprint finalizado */
    @PatchMapping("/{sprintId}/reabrir")
    public ResponseEntity<SprintDto> reabrir(
            @PathVariable UUID sprintId,
            Authentication auth) {
        return ResponseEntity.ok(sprintService.reabrir(sprintId, auth.getName()));
    }
}
