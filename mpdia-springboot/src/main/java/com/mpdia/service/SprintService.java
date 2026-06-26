// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.CrearSiguienteSprintRequest;
import com.mpdia.dto.SprintDto;
import com.mpdia.entity.Proyecto;
import com.mpdia.entity.Sprint;
import com.mpdia.repository.ProyectoRepository;
import com.mpdia.repository.SprintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SprintService {

    private final SprintRepository   sprintRepo;
    private final ProyectoRepository proyectoRepo;

    // ── Creación inicial ──────────────────────────────────────────────────

    /**
     * Crea todos los sprints del proyecto con sus fechas calculadas.
     * Sprint 1 → en_ejecucion, los demás → pendiente.
     */
    @Transactional
    public void crearSprintsIniciales(UUID proyectoId, String sprintGoal,
                                      int numeroSprints, int timeBoxSemanas,
                                      LocalDate fechaInicio) {
        for (int i = 1; i <= numeroSprints; i++) {
            LocalDate inicio = fechaInicio.plusWeeks((long) (i - 1) * timeBoxSemanas);
            LocalDate fin    = inicio.plusWeeks(timeBoxSemanas).minusDays(1);

            Sprint s = new Sprint();
            s.setProyectoId(proyectoId);
            s.setNumero(i);
            s.setSprintGoal(i == 1 ? sprintGoal : "Sprint " + i);
            s.setEstado(i == 1 ? "en_ejecucion" : "pendiente");
            s.setFechaInicio(inicio);
            s.setFechaFin(fin);
            sprintRepo.save(s);
        }
    }

    /** Compatibilidad: crea un único sprint inicial (proyectos sin numeroSprints) */
    @Transactional
    public SprintDto crearSprintInicial(UUID proyectoId, String sprintGoal) {
        Proyecto p = getProyecto(proyectoId);
        Sprint s = buildSprint(proyectoId, 1, sprintGoal, "en_ejecucion",
                LocalDate.now(), LocalDate.now().plusWeeks(p.getTimeBoxSemanas()));
        return toDto(sprintRepo.save(s), p);
    }

    // ── Consultas ─────────────────────────────────────────────────────────

    public SprintDto getSprintActivo(UUID proyectoId) {
        Proyecto p = getProyecto(proyectoId);
        return sprintRepo.findByProyectoIdAndEstado(proyectoId, "en_ejecucion")
                .map(s -> toDto(s, p))
                .orElseThrow(() -> new IllegalArgumentException("No hay sprint en ejecución."));
    }

    public SprintDto getById(UUID sprintId) {
        Sprint s = sprintRepo.findById(sprintId)
                .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado."));
        Proyecto p = getProyecto(s.getProyectoId());
        return toDto(s, p);
    }

    public List<SprintDto> listarSprints(UUID proyectoId) {
        Proyecto p = getProyecto(proyectoId);
        return sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)
                .stream().map(s -> toDto(s, p)).toList();
    }

    // ── Transiciones de estado ────────────────────────────────────────────

    /** Cierra el sprint en ejecución e inicia el siguiente pendiente */
    @Transactional
    public SprintDto cerrarEIniciarSiguiente(UUID proyectoId, CrearSiguienteSprintRequest req) {
        Proyecto p = getProyecto(proyectoId);

        sprintRepo.findByProyectoIdAndEstado(proyectoId, "en_ejecucion").ifPresent(s -> {
            s.setEstado("finalizado");
            s.setCerradoAt(Instant.now());
            sprintRepo.save(s);
        });

        Sprint siguiente = sprintRepo
                .findFirstByProyectoIdAndEstadoOrderByNumeroAsc(proyectoId, "pendiente")
                .orElseThrow(() -> new IllegalArgumentException("No hay sprints pendientes."));

        if (req.sprintGoal() != null && !req.sprintGoal().isBlank()) {
            siguiente.setSprintGoal(req.sprintGoal());
        }
        siguiente.setEstado("en_ejecucion");
        return toDto(sprintRepo.save(siguiente), p);
    }

    /** Solo admin puede reabrir un sprint finalizado */
    @Transactional
    public SprintDto reabrir(UUID sprintId, String adminId) {
        Sprint s = sprintRepo.findById(sprintId)
                .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado."));
        if (!"finalizado".equals(s.getEstado())) {
            throw new IllegalArgumentException("Solo se pueden reabrir sprints finalizados.");
        }
        s.setEstado("reabierto");
        s.setReabiertoPor(adminId);
        s.setReabiertaAt(Instant.now());
        Proyecto p = getProyecto(s.getProyectoId());
        return toDto(sprintRepo.save(s), p);
    }

    // ── Scheduler: cierre automático diario ──────────────────────────────

    @Scheduled(cron = "0 0 1 * * *") // 01:00 AM cada día
    @Transactional
    public void cerrarSprintsVencidos() {
        List<Sprint> vencidos = sprintRepo.findVencidos(LocalDate.now());
        for (Sprint s : vencidos) {
            s.setEstado("finalizado");
            s.setCerradoAt(Instant.now());
            s.setCerradoPor("scheduler");
            sprintRepo.save(s);

            // Activar el siguiente sprint pendiente del mismo proyecto
            sprintRepo.findFirstByProyectoIdAndEstadoOrderByNumeroAsc(s.getProyectoId(), "pendiente")
                    .ifPresent(siguiente -> {
                        siguiente.setEstado("en_ejecucion");
                        sprintRepo.save(siguiente);
                    });
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Sprint buildSprint(UUID proyectoId, int numero, String goal,
                                String estado, LocalDate inicio, LocalDate fin) {
        Sprint s = new Sprint();
        s.setProyectoId(proyectoId);
        s.setNumero(numero);
        s.setSprintGoal(goal);
        s.setEstado(estado);
        s.setFechaInicio(inicio);
        s.setFechaFin(fin);
        return s;
    }

    private Proyecto getProyecto(UUID id) {
        return proyectoRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));
    }

    private SprintDto toDto(Sprint s, Proyecto p) {
        return new SprintDto(
                s.getId(), s.getProyectoId(), p.getNombre(),
                p.getMetodo(), p.getTimeBoxSemanas(),
                s.getNumero(), s.getSprintGoal(), s.getEstado(),
                s.getFechaInicio(), s.getFechaFin(),
                s.getCerradoPor(), s.getCerradoAt(),
                s.getCreatedAt());
    }
}
