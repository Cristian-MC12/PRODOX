// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.controller;

import com.mpdia.dto.InvitarProyectoRequest;
import com.mpdia.dto.ProjectMemberDto;
import com.mpdia.dto.UnirseProyectoRequest;
import com.mpdia.service.ProjectMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/project-members")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService service;

    /** GET /api/project-members/{proyectoId} — miembros del proyecto */
    @GetMapping("/{proyectoId}")
    public ResponseEntity<List<ProjectMemberDto>> listar(@PathVariable UUID proyectoId) {
        return ResponseEntity.ok(service.listarMiembros(proyectoId));
    }

    /** POST /api/project-members/{proyectoId}/invitar — invitar por email */
    @PostMapping("/{proyectoId}/invitar")
    public ResponseEntity<Map<String, String>> invitar(
            @PathVariable UUID proyectoId,
            @Valid @RequestBody InvitarProyectoRequest request,
            Authentication auth) {
        String codigo = service.invitar(proyectoId, auth.getName(), request);
        return ResponseEntity.ok(Map.of("codigo", codigo));
    }

    /** POST /api/project-members/unirse — unirse con código */
    @PostMapping("/unirse")
    public ResponseEntity<ProjectMemberDto> unirse(
            @Valid @RequestBody UnirseProyectoRequest request,
            Authentication auth) {
        return ResponseEntity.ok(service.unirse(auth.getName(), request));
    }
}
