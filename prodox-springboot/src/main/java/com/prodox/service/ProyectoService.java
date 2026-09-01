// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.prodox.dto.CrearProyectoRequest;
import com.prodox.dto.ProyectoDto;
import com.prodox.entity.Proyecto;
import com.prodox.repository.AppUserRepository;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.repository.ProyectoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProyectoService {

    private final ProyectoRepository      proyectoRepo;
    private final AppUserRepository       userRepo;
    private final ProjectMemberRepository memberRepo;
    private final SprintService           sprintService;
    private final ProjectMemberService    projectMemberService;

    /** Crear proyecto — solo Scrum Master */
    @Transactional
    public ProyectoDto crear(String scrumMasterId, CrearProyectoRequest req) {
        var sm = userRepo.findById(UUID.fromString(scrumMasterId))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        if (!"scrum_master".equals(sm.getRole())) {
            throw new IllegalArgumentException("Solo el Scrum Master puede crear proyectos.");
        }

        Proyecto p = new Proyecto();
        p.setNombre(req.nombre());
        p.setDescripcion(req.descripcion());
        p.setMetodo(req.metodo());
        p.setTimeBoxSemanas(req.timeBoxSemanas());
        p.setNumeroSprints(req.numeroSprints());
        p.setFechaInicio(req.fechaInicio());
        p.setProductGoal(req.productGoal());
        p.setSprintGoal("");
        p.setScrumMasterId(scrumMasterId);

        Proyecto saved = proyectoRepo.save(p);

        projectMemberService.agregarScrumMaster(saved.getId(), scrumMasterId, sm.getEmail());
        sprintService.crearSprintsIniciales(saved.getId(), "Sprint 1",
                req.numeroSprints(), req.timeBoxSemanas(), req.fechaInicio());

        return toDto(saved);
    }

    /** Listar proyectos donde el usuario es miembro (incluye proyectos propios del SM) */
    public List<ProyectoDto> listarMisProyectos(String userId) {
        List<UUID> proyectoIds = memberRepo.findByUserId(userId).stream()
                .map(m -> m.getProyectoId())
                .toList();
        return proyectoRepo.findAllById(proyectoIds).stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toDto)
                .toList();
    }

    /** Obtener proyecto por ID — requiere ser miembro del proyecto */
    public ProyectoDto getById(UUID id, String userId) {
        if (!memberRepo.existsByProyectoIdAndUserId(id, userId)) {
            throw new SecurityException("No tienes acceso a este proyecto");
        }
        return proyectoRepo.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));
    }

    /** Finalizar proyecto */
    @Transactional
    public ProyectoDto finalizar(UUID id, String scrumMasterId) {
        Proyecto p = proyectoRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));
        if (!p.getScrumMasterId().equals(scrumMasterId)) {
            throw new IllegalArgumentException("Solo el Scrum Master del proyecto puede finalizarlo.");
        }
        p.setEstado("finalizado");
        p.setUpdatedAt(Instant.now());
        return toDto(proyectoRepo.save(p));
    }

    /**
     * Eliminar proyecto — solo el Scrum Master dueño del proyecto (FASE 21).
     * La autorización se valida acá en el backend (no solo ocultando el botón
     * en Angular): se exige rol scrum_master Y que sea el dueño de ESTE
     * proyecto, mismo criterio que finalizar(). El borrado en cascada de
     * sprints, miembros, invitaciones, variables y resultados está a cargo
     * de las FK ON DELETE CASCADE definidas en las migraciones.
     */
    @Transactional
    public void eliminar(UUID id, String userId) {
        Proyecto p = proyectoRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));

        var user = userRepo.findById(UUID.fromString(userId))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        if (!"scrum_master".equals(user.getRole()) || !p.getScrumMasterId().equals(userId)) {
            throw new IllegalArgumentException("Solo el Scrum Master del proyecto puede eliminarlo.");
        }

        proyectoRepo.delete(p);
    }

    private ProyectoDto toDto(Proyecto p) {
        String smEmail = userRepo.findById(UUID.fromString(p.getScrumMasterId()))
                .map(u -> u.getEmail()).orElse("—");

        int totalMiembros = memberRepo.findByProyectoId(p.getId()).size();

        return new ProyectoDto(
                p.getId(), p.getNombre(), p.getDescripcion(),
                p.getMetodo(), p.getTimeBoxSemanas(),
                p.getNumeroSprints(), p.getFechaInicio(),
                p.getProductGoal(), p.getSprintGoal(),
                p.getEstado(), smEmail,
                totalMiembros, p.getCreatedAt()
        );
    }
}
