// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.controller;

import com.prodox.dto.CambiarRolMiembroRequest;
import com.prodox.dto.InvitacionEstadoDto;
import com.prodox.dto.InvitarProyectoRequest;
import com.prodox.dto.InvitarProyectoResponse;
import com.prodox.dto.ProjectMemberDto;
import com.prodox.dto.UnirseProyectoRequest;
import com.prodox.service.ProjectMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/project-members")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService service;

    /** GET /api/project-members/{proyectoId} — miembros del proyecto (solo miembros) */
    @GetMapping("/{proyectoId}")
    public ResponseEntity<List<ProjectMemberDto>> listar(@PathVariable UUID proyectoId, Authentication auth) {
        return ResponseEntity.ok(service.listarMiembros(proyectoId, auth.getName()));
    }

    /**
     * GET /api/project-members/invitacion/{codigo} — estado público de una
     * invitación (sin autenticación), para que /invitacion en Angular pueda
     * mostrar "válida/expirada/usada/inexistente" antes de forzar login.
     */
    @GetMapping("/invitacion/{codigo}")
    public ResponseEntity<InvitacionEstadoDto> consultarInvitacion(@PathVariable String codigo) {
        return ResponseEntity.ok(service.consultarInvitacion(codigo));
    }

    /** POST /api/project-members/{proyectoId}/invitar — invitar por email (solo Scrum Master del proyecto) */
    @PostMapping("/{proyectoId}/invitar")
    public ResponseEntity<InvitarProyectoResponse> invitar(
            @PathVariable UUID proyectoId,
            @Valid @RequestBody InvitarProyectoRequest request,
            Authentication auth) {
        return ResponseEntity.ok(service.invitar(proyectoId, auth.getName(), request));
    }

    /** POST /api/project-members/unirse — unirse con código de invitación */
    @PostMapping("/unirse")
    public ResponseEntity<ProjectMemberDto> unirse(
            @Valid @RequestBody UnirseProyectoRequest request,
            Authentication auth) {
        return ResponseEntity.ok(service.unirse(auth.getName(), request));
    }

    /**
     * PATCH /api/project-members/{proyectoId}/{userId}/rol — cambia el rol
     * (product_owner | scrum_member) de un miembro existente. Restringido al
     * Scrum Master del proyecto (V39). El userId de la ruta es el MIEMBRO
     * OBJETIVO, nunca el solicitante — el solicitante se obtiene siempre de
     * `auth.getName()` (JWT ya validado), jamás de un valor enviado por el
     * frontend, así que no hay forma de que el propio Angular determine
     * "quién soy" para esta operación.
     */
    @PatchMapping("/{proyectoId}/{userId}/rol")
    public ResponseEntity<ProjectMemberDto> cambiarRol(
            @PathVariable UUID proyectoId,
            @PathVariable String userId,
            @Valid @RequestBody CambiarRolMiembroRequest request,
            Authentication auth) {
        return ResponseEntity.ok(service.cambiarRol(proyectoId, auth.getName(), userId, request.rol()));
    }
}
