// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.controller;

import com.prodox.dto.CrearSiguienteSprintRequest;
import com.prodox.dto.SprintDto;
import com.prodox.entity.ProjectMember;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.service.SprintService;
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

    /** Finalizar el sprint activo e iniciar el siguiente es una acción restringida al Scrum Master del proyecto. */
    @PostMapping("/{proyectoId}/siguiente")
    public ResponseEntity<SprintDto> siguiente(
            @PathVariable UUID proyectoId,
            @Valid @RequestBody CrearSiguienteSprintRequest request,
            Authentication auth) {
        validarScrumMaster(proyectoId, auth);
        return ResponseEntity.ok(sprintService.cerrarEIniciarSiguiente(proyectoId, request));
    }

    /** Reabrir un sprint finalizado es una acción restringida al Scrum Master del proyecto. */
    @PatchMapping("/{sprintId}/reabrir")
    public ResponseEntity<SprintDto> reabrir(
            @PathVariable UUID sprintId,
            Authentication auth) {
        SprintDto sprintActual = sprintService.getById(sprintId);
        validarScrumMaster(sprintActual.proyectoId(), auth);
        return ResponseEntity.ok(sprintService.reabrir(sprintId, auth.getName()));
    }

    /**
     * Vuelve a finalizar un sprint que había sido reabierto (transición
     * reabierto → finalizado). Restringida al Scrum Master del proyecto,
     * mismo patrón que reabrir(). No afecta al sprint actualmente en
     * ejecución del proyecto.
     */
    @PatchMapping("/{sprintId}/finalizar")
    public ResponseEntity<SprintDto> finalizarReabierto(
            @PathVariable UUID sprintId,
            Authentication auth) {
        SprintDto sprintActual = sprintService.getById(sprintId);
        validarScrumMaster(sprintActual.proyectoId(), auth);
        return ResponseEntity.ok(sprintService.finalizarReabierto(sprintId));
    }

    /**
     * Cierra el sprint actualmente en ejecución sin iniciar uno nuevo.
     * Restringida al Scrum Master del proyecto. Útil cuando se quiere
     * finalizar el último sprint sin crear uno nuevo.
     */
    @PatchMapping("/{sprintId}/cerrar")
    public ResponseEntity<SprintDto> cerrarActual(
            @PathVariable UUID sprintId,
            Authentication auth) {
        SprintDto sprintActual = sprintService.getById(sprintId);
        validarScrumMaster(sprintActual.proyectoId(), auth);
        return ResponseEntity.ok(sprintService.cerrarSprintActual(sprintId));
    }

    /** Mismo patrón de autorización que AIInsightsService.validateProjectAccess. */
    private void validarAcceso(UUID proyectoId, Authentication auth) {
        String userId = auth.getName();
        if (!projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId)) {
            throw new SecurityException("No tienes acceso a este proyecto");
        }
    }

    /**
     * Valida que el usuario sea miembro del proyecto Y su Scrum Master (rol de liderazgo
     * a nivel de proyecto). Usado solo para las acciones restringidas de finalizar/reabrir
     * un sprint — mismo patrón que AIInsightsService.validateScrumMasterAccess.
     */
    private void validarScrumMaster(UUID proyectoId, Authentication auth) {
        String userId = auth.getName();
        ProjectMember member = projectMemberRepository.findByProyectoIdAndUserId(proyectoId, userId)
                .orElseThrow(() -> new SecurityException("No tienes acceso a este proyecto"));
        if (!"scrum_master".equals(member.getRol())) {
            throw new SecurityException("Solo el Scrum Master del proyecto puede realizar esta acción");
        }
    }
}
