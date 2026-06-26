// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.CrearProyectoRequest;
import com.mpdia.dto.ProyectoDto;
import com.mpdia.entity.Proyecto;
import com.mpdia.repository.AppUserRepository;
import com.mpdia.repository.ProjectMemberRepository;
import com.mpdia.repository.ProyectoRepository;
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

    /** Obtener proyecto por ID */
    public ProyectoDto getById(UUID id) {
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
