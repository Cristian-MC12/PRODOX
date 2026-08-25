// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.controller;

import com.mpdia.dto.CrearSiguienteSprintRequest;
import com.mpdia.dto.SprintDto;
import com.mpdia.repository.ProjectMemberRepository;
import com.mpdia.service.SprintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * FASE 23: ninguno de estos endpoints validaba que el usuario autenticado
 * fuera miembro de {proyectoId} (a diferencia de AIInsightsController) —
 * IDOR confirmado en auditoría FASE 22, incluyendo endpoints que mutan
 * estado (siguiente, reabrir). Se agrega la misma validación usada en
 * AnalyticsController/AIInsightsService, sin tocar SprintService.
 */
@RestController
@RequestMapping("/api/sprints")
@RequiredArgsConstructor
public class SprintController {

    private final SprintService sprintService;
    private final ProjectMemberRepository projectMemberRepository;

    @GetMapping("/{proyectoId}/activo")
    public ResponseEntity<SprintDto> activo(@PathVariable UUID proyectoId, Authentication auth) {
        validarAcceso(proyectoId, auth);
        return ResponseEntity.ok(sprintService.getSprintActivo(proyectoId));
    }

    @GetMapping("/{proyectoId}")
    public ResponseEntity<List<SprintDto>> listar(@PathVariable UUID proyectoId, Authentication auth) {
        validarAcceso(proyectoId, auth);
        return ResponseEntity.ok(sprintService.listarSprints(proyectoId));
    }

    @GetMapping("/detalle/{sprintId}")
    public ResponseEntity<SprintDto> getById(@PathVariable UUID sprintId, Authentication auth) {
        SprintDto sprint = sprintService.getById(sprintId);
        validarAcceso(sprint.proyectoId(), auth);
        return ResponseEntity.ok(sprint);
    }

    @PostMapping("/{proyectoId}/siguiente")
    public ResponseEntity<SprintDto> siguiente(
            @PathVariable UUID proyectoId,
            @Valid @RequestBody CrearSiguienteSprintRequest request,
            Authentication auth) {
        validarAcceso(proyectoId, auth);
        return ResponseEntity.ok(sprintService.cerrarEIniciarSiguiente(proyectoId, request));
    }

    /** Solo miembros del proyecto — reabrir un sprint finalizado */
    @PatchMapping("/{sprintId}/reabrir")
    public ResponseEntity<SprintDto> reabrir(
            @PathVariable UUID sprintId,
            Authentication auth) {
        SprintDto sprintActual = sprintService.getById(sprintId);
        validarAcceso(sprintActual.proyectoId(), auth);
        return ResponseEntity.ok(sprintService.reabrir(sprintId, auth.getName()));
    }

    /** Mismo patrón de autorización que AIInsightsService.validateProjectAccess. */
    private void validarAcceso(UUID proyectoId, Authentication auth) {
        String userId = auth.getName();
        if (!projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)) {
            throw new SecurityException("No tienes acceso a este proyecto");
        }
    }
}
