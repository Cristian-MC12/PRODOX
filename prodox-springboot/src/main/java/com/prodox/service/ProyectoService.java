// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.prodox.dto.CrearProyectoRequest;
import com.prodox.dto.ProyectoDto;
import com.prodox.entity.ProjectMember;
import com.prodox.entity.Proyecto;
import com.prodox.repository.AppUserRepository;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.repository.ProyectoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
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

        validarTimebox(req.timeboxUnidad(), req.timeboxDuracion(), req.horaInicio());

        Proyecto p = new Proyecto();
        p.setNombre(req.nombre());
        p.setDescripcion(req.descripcion());
        p.setMetodo(req.metodo());
        p.setTimeboxUnidad(req.timeboxUnidad());
        p.setTimeboxDuracion(req.timeboxDuracion());
        p.setHoraInicio("HORAS".equals(req.timeboxUnidad()) ? req.horaInicio() : null);
        // Campo legado (ver Proyecto.timeBoxSemanas): lo siguen leyendo
        // AICopilotService, CopilotToolsService y el email de invitación de
        // ProjectMemberService, fuera del alcance de este cambio.
        p.setTimeBoxSemanas(equivalenteEnSemanas(req.timeboxUnidad(), req.timeboxDuracion()));
        p.setNumeroSprints(req.numeroSprints());
        p.setFechaInicio(req.fechaInicio());
        p.setProductGoal(req.productGoal());
        p.setSprintGoal("");
        p.setScrumMasterId(scrumMasterId);

        Proyecto saved = proyectoRepo.save(p);

        projectMemberService.agregarScrumMaster(saved.getId(), scrumMasterId, sm.getEmail());
        sprintService.crearSprintsIniciales(saved.getId(), "Sprint 1", req.numeroSprints(),
                req.timeboxUnidad(), req.timeboxDuracion(), req.fechaInicio(), req.horaInicio());

        return toDto(saved, scrumMasterId);
    }

    /**
     * V41 — valida el timebox de la iteración. Para SEMANAS se conserva
     * EXACTAMENTE el rango histórico (1-4, el mismo que ya exige el CHECK de
     * V5 sobre time_box_semanas); DIAS y HORAS son unidades nuevas, sin un
     * rango previo que respetar, así que se definen topes propios razonables
     * para un timebox de iteración.
     */
    private void validarTimebox(String unidad, Integer duracion, LocalTime horaInicio) {
        if (duracion == null || duracion <= 0) {
            throw new IllegalArgumentException("La duración del timebox debe ser mayor a 0.");
        }
        switch (unidad) {
            case "SEMANAS" -> {
                if (duracion > 4) {
                    throw new IllegalArgumentException("El timebox en semanas debe estar entre 1 y 4.");
                }
            }
            case "DIAS" -> {
                if (duracion > 30) {
                    throw new IllegalArgumentException("El timebox en días debe estar entre 1 y 30.");
                }
            }
            case "HORAS" -> {
                if (duracion > 168) {
                    throw new IllegalArgumentException("El timebox en horas debe estar entre 1 y 168.");
                }
                if (horaInicio == null) {
                    throw new IllegalArgumentException(
                            "Debes indicar la hora de inicio cuando el timebox está en horas.");
                }
            }
            // Defensa en profundidad: el DTO ya bloquea cualquier otro valor
            // con @Pattern, pero este método debe ser seguro de invocar
            // directamente sin depender de esa validación externa.
            default -> throw new IllegalArgumentException(
                    "Unidad de timebox inválida. Debe ser HORAS, DIAS o SEMANAS.");
        }
    }

    /**
     * Equivalente aproximado en semanas, redondeado hacia arriba y acotado a
     * 1-4 para no violar el CHECK histórico de time_box_semanas (V5). Sirve
     * ÚNICAMENTE para mantener con un valor razonable el campo legado que
     * leen AICopilotService/CopilotToolsService/el email de invitación —
     * jamás se usa para calcular fechas reales de sprint (eso vive en
     * SprintService.crearSprintsIniciales, que usa unidad+duración reales).
     */
    private int equivalenteEnSemanas(String unidad, int duracion) {
        int semanas = switch (unidad) {
            case "HORAS" -> (int) Math.ceil(duracion / 168.0);
            case "DIAS"  -> (int) Math.ceil(duracion / 7.0);
            default      -> duracion;
        };
        return Math.max(1, Math.min(4, semanas));
    }

    /** Listar proyectos donde el usuario es miembro (incluye proyectos propios del SM) */
    public List<ProyectoDto> listarMisProyectos(String userId) {
        List<UUID> proyectoIds = memberRepo.findByUserId(userId).stream()
                .map(m -> m.getProyectoId())
                .toList();
        return proyectoRepo.findAllById(proyectoIds).stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(p -> toDto(p, userId))
                .toList();
    }

    /** Obtener proyecto por ID — requiere ser miembro del proyecto */
    public ProyectoDto getById(UUID id, String userId) {
        if (!memberRepo.existsByProyectoIdAndUserId(id, userId)) {
            throw new SecurityException("No tienes acceso a este proyecto");
        }
        return proyectoRepo.findById(id)
                .map(p -> toDto(p, userId))
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
        return toDto(proyectoRepo.save(p), scrumMasterId);
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

    private ProyectoDto toDto(Proyecto p, String userId) {
        String smEmail = userRepo.findById(UUID.fromString(p.getScrumMasterId()))
                .map(u -> u.getEmail()).orElse("—");

        int totalMiembros = memberRepo.findByProyectoId(p.getId()).size();

        // Rol POR PROYECTO del usuario que pidió este DTO (V39) — null si por
        // algún motivo no es miembro (no debería ocurrir en los caminos que
        // llaman a este método, todos ya validan membresía antes).
        String miRol = memberRepo.findByProyectoIdAndUserId(p.getId(), userId)
                .map(ProjectMember::getRol).orElse(null);

        return new ProyectoDto(
                p.getId(), p.getNombre(), p.getDescripcion(),
                p.getMetodo(), p.getTimeBoxSemanas(),
                p.getNumeroSprints(), p.getFechaInicio(),
                p.getProductGoal(), p.getSprintGoal(),
                p.getEstado(), smEmail,
                totalMiembros, p.getCreatedAt(), miRol,
                p.getTimeboxUnidad(), p.getTimeboxDuracion(), p.getHoraInicio()
        );
    }
}
