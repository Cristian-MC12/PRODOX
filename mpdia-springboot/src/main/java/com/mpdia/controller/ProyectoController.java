// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.controller;

import com.mpdia.dto.CrearProyectoRequest;
import com.mpdia.dto.ProyectoDto;
import com.mpdia.service.ProyectoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/proyectos")
@RequiredArgsConstructor
public class ProyectoController {

    private final ProyectoService proyectoService;

    /** POST /api/proyectos — Crear proyecto (solo SM) */
    @PostMapping
    public ResponseEntity<ProyectoDto> crear(
            @Valid @RequestBody CrearProyectoRequest request,
            Authentication auth) {
        return ResponseEntity.ok(proyectoService.crear(auth.getName(), request));
    }

    /**
     * GET /api/proyectos/mios — Proyectos donde el usuario es miembro.
     * Funciona para SM y Scrum Member.
     */
    @GetMapping("/mios")
    public ResponseEntity<List<ProyectoDto>> mios(Authentication auth) {
        return ResponseEntity.ok(proyectoService.listarMisProyectos(auth.getName()));
    }

    /** GET /api/proyectos/{id} — Detalle de un proyecto */
    @GetMapping("/{id}")
    public ResponseEntity<ProyectoDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(proyectoService.getById(id));
    }

    /** PATCH /api/proyectos/{id}/finalizar — Finalizar proyecto */
    @PatchMapping("/{id}/finalizar")
    public ResponseEntity<ProyectoDto> finalizar(
            @PathVariable UUID id,
            Authentication auth) {
        return ResponseEntity.ok(proyectoService.finalizar(id, auth.getName()));
    }
}
