// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.controller;

import com.prodox.dto.ActualizarHistoriaUsuarioRequest;
import com.prodox.dto.AsignarSprintHistoriaRequest;
import com.prodox.dto.CambiarEstadoHistoriaRequest;
import com.prodox.dto.CambiarPrioridadRequest;
import com.prodox.dto.CrearHistoriaUsuarioRequest;
import com.prodox.dto.HistoriaUsuarioDto;
import com.prodox.entity.ProjectMember;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.service.HistoriaUsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Backlog de historias de usuario (V39 — Product Owner). Mismo patrón de
 * autorización que SprintController: validarAcceso (solo membresía, para
 * lectura) y validarProductOwner (membresía + rol product_owner EN ESE
 * proyecto, para escritura) — nunca se confía en el rol global de AppUser.
 *
 * El proyectoId de una historia siempre se resuelve del lado del servidor
 * (vía HistoriaUsuarioService.detalle) antes de autorizar cualquier
 * operación sobre {historiaId}: el cliente nunca puede forzar la
 * autorización pasando un proyectoId propio en el body para una historia que
 * en realidad pertenece a otro proyecto.
 */
@RestController
@RequestMapping("/api/historias")
@RequiredArgsConstructor
public class HistoriaUsuarioController {

    private final HistoriaUsuarioService historiaService;
    private final ProjectMemberRepository projectMemberRepo;

    /** GET /api/historias/{proyectoId} — backlog completo del proyecto (miembros) */
    @GetMapping("/{proyectoId}")
    public ResponseEntity<List<HistoriaUsuarioDto>> listar(@PathVariable UUID proyectoId, Authentication auth) {
        validarAcceso(proyectoId, auth);
        return ResponseEntity.ok(historiaService.listar(proyectoId));
    }

    /** GET /api/historias/detalle/{historiaId} (miembros del proyecto dueño de la historia) */
    @GetMapping("/detalle/{historiaId}")
    public ResponseEntity<HistoriaUsuarioDto> detalle(@PathVariable UUID historiaId, Authentication auth) {
        HistoriaUsuarioDto h = historiaService.detalle(historiaId);
        validarAcceso(h.proyectoId(), auth);
        return ResponseEntity.ok(h);
    }

    /** POST /api/historias/{proyectoId} — crear historia (solo Product Owner del proyecto) */
    @PostMapping("/{proyectoId}")
    public ResponseEntity<HistoriaUsuarioDto> crear(
            @PathVariable UUID proyectoId,
            @Valid @RequestBody CrearHistoriaUsuarioRequest request,
            Authentication auth) {
        validarProductOwner(proyectoId, auth);
        return ResponseEntity.ok(historiaService.crear(proyectoId, auth.getName(), request));
    }

    /** PATCH /api/historias/{historiaId} — editar título/descripción/criterios (solo PO) */
    @PatchMapping("/{historiaId}")
    public ResponseEntity<HistoriaUsuarioDto> actualizar(
            @PathVariable UUID historiaId,
            @Valid @RequestBody ActualizarHistoriaUsuarioRequest request,
            Authentication auth) {
        HistoriaUsuarioDto actual = historiaService.detalle(historiaId);
        validarProductOwner(actual.proyectoId(), auth);
        return ResponseEntity.ok(historiaService.actualizar(historiaId, request));
    }

    /** PATCH /api/historias/{historiaId}/prioridad (solo PO) */
    @PatchMapping("/{historiaId}/prioridad")
    public ResponseEntity<HistoriaUsuarioDto> cambiarPrioridad(
            @PathVariable UUID historiaId,
            @Valid @RequestBody CambiarPrioridadRequest request,
            Authentication auth) {
        HistoriaUsuarioDto actual = historiaService.detalle(historiaId);
        validarProductOwner(actual.proyectoId(), auth);
        return ResponseEntity.ok(historiaService.cambiarPrioridad(historiaId, request.prioridad()));
    }

    /**
     * PATCH /api/historias/{historiaId}/estado — solo Product Owner. El
     * backlog (contenido, prioridad y estado) es responsabilidad exclusiva
     * del PO; Scrum Master y Scrum Member mantienen exactamente los mismos
     * permisos que ya tenían antes de V39 (solo lectura de historias, vía
     * validarAcceso), sin ganar ni perder nada.
     */
    @PatchMapping("/{historiaId}/estado")
    public ResponseEntity<HistoriaUsuarioDto> cambiarEstado(
            @PathVariable UUID historiaId,
            @Valid @RequestBody CambiarEstadoHistoriaRequest request,
            Authentication auth) {
        HistoriaUsuarioDto actual = historiaService.detalle(historiaId);
        validarProductOwner(actual.proyectoId(), auth);
        return ResponseEntity.ok(historiaService.cambiarEstado(historiaId, request.estado()));
    }

    /** PATCH /api/historias/{historiaId}/sprint — asignar/desasignar sprint (solo PO) */
    @PatchMapping("/{historiaId}/sprint")
    public ResponseEntity<HistoriaUsuarioDto> asignarSprint(
            @PathVariable UUID historiaId,
            @RequestBody AsignarSprintHistoriaRequest request,
            Authentication auth) {
        HistoriaUsuarioDto actual = historiaService.detalle(historiaId);
        validarProductOwner(actual.proyectoId(), auth);
        return ResponseEntity.ok(historiaService.asignarSprint(historiaId, request.sprintId()));
    }

    /** DELETE /api/historias/{historiaId} (solo PO) */
    @DeleteMapping("/{historiaId}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID historiaId, Authentication auth) {
        HistoriaUsuarioDto actual = historiaService.detalle(historiaId);
        validarProductOwner(actual.proyectoId(), auth);
        historiaService.eliminar(historiaId);
        return ResponseEntity.noContent().build();
    }

    private void validarAcceso(UUID proyectoId, Authentication auth) {
        if (!projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, auth.getName())) {
            throw new SecurityException("No tienes acceso a este proyecto");
        }
    }

    private void validarProductOwner(UUID proyectoId, Authentication auth) {
        ProjectMember member = projectMemberRepo.findByProyectoIdAndUserId(proyectoId, auth.getName())
                .orElseThrow(() -> new SecurityException("No tienes acceso a este proyecto"));
        if (!ProjectMember.ROL_PRODUCT_OWNER.equals(member.getRol())) {
            throw new SecurityException("Solo el Product Owner del proyecto puede realizar esta acción");
        }
    }
}
