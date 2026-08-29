// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.controller;

import com.mpdia.dto.RegistrarValorRequest;
import com.mpdia.dto.RegistroValorDto;
import com.mpdia.service.EjecucionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ejecucion")
@RequiredArgsConstructor
public class EjecucionController {

    private final EjecucionService ejecucionService;

    /** GET /api/ejecucion/sprint/{sprintId} — valores registrados en un sprint (solo miembros del proyecto) */
    @GetMapping("/sprint/{sprintId}")
    public ResponseEntity<List<RegistroValorDto>> porSprint(@PathVariable UUID sprintId, Authentication auth) {
        return ResponseEntity.ok(ejecucionService.listarPorSprint(auth.getName(), sprintId));
    }

    /** GET /api/ejecucion/variable/{variableId}/sprint/{sprintId} — solo miembros del proyecto */
    @GetMapping("/variable/{variableId}/sprint/{sprintId}")
    public ResponseEntity<List<RegistroValorDto>> porVariable(
            @PathVariable UUID variableId,
            @PathVariable UUID sprintId,
            Authentication auth) {
        return ResponseEntity.ok(ejecucionService.listarPorVariable(auth.getName(), variableId, sprintId));
    }

    /** POST /api/ejecucion — registrar un valor */
    @PostMapping
    public ResponseEntity<RegistroValorDto> registrar(
            @Valid @RequestBody RegistrarValorRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ejecucionService.registrar(auth.getName(), request));
    }
}
