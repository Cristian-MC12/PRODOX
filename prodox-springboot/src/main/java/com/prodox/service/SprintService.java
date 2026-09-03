// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.prodox.dto.CrearSiguienteSprintRequest;
import com.prodox.dto.SprintDto;
import com.prodox.entity.Proyecto;
import com.prodox.entity.Sprint;
import com.prodox.repository.ProyectoRepository;
import com.prodox.repository.SprintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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
     *
     * V41 — timebox real por unidad:
     * <ul>
     *   <li>SEMANAS: misma fórmula EXACTA que antes de V41 (sin cambios de
     *       comportamiento para proyectos en semanas, la inmensa mayoría).</li>
     *   <li>DIAS: misma fórmula que semanas, pero con {@code plusDays}.</li>
     *   <li>HORAS: fecha Y hora reales ({@code LocalDateTime.plusHours}),
     *       que respeta correctamente el cambio de día — un timebox de 10
     *       horas iniciado a las 16:00 termina a las 02:00 del día
     *       siguiente, nunca se limita a las 23:59 del mismo día. Además de
     *       fechaHoraInicio/fechaHoraFin (Instant, precisión real), se
     *       completan también fechaInicio/fechaFin (solo la parte de fecha)
     *       para que todo el código existente que ya lee esos dos campos
     *       (evaluación, ejecución, dashboard) siga funcionando sin cambios.</li>
     * </ul>
     */
    @Transactional
    public void crearSprintsIniciales(UUID proyectoId, String sprintGoal, int numeroSprints,
                                      String timeboxUnidad, int timeboxDuracion,
                                      LocalDate fechaInicio, LocalTime horaInicio) {
        for (int i = 1; i <= numeroSprints; i++) {
            Sprint s = new Sprint();
            s.setProyectoId(proyectoId);
            s.setNumero(i);
            s.setSprintGoal(i == 1 ? sprintGoal : "Sprint " + i);
            s.setEstado(i == 1 ? "en_ejecucion" : "pendiente");

            if ("HORAS".equals(timeboxUnidad)) {
                LocalDateTime inicioLdt = LocalDateTime.of(fechaInicio, horaInicio)
                        .plusHours((long) (i - 1) * timeboxDuracion);
                LocalDateTime finLdt = inicioLdt.plusHours(timeboxDuracion);
                ZoneId zona = ZoneId.systemDefault();
                s.setFechaHoraInicio(inicioLdt.atZone(zona).toInstant());
                s.setFechaHoraFin(finLdt.atZone(zona).toInstant());
                s.setFechaInicio(inicioLdt.toLocalDate());
                s.setFechaFin(finLdt.toLocalDate());
            } else if ("DIAS".equals(timeboxUnidad)) {
                LocalDate inicio = fechaInicio.plusDays((long) (i - 1) * timeboxDuracion);
                LocalDate fin = inicio.plusDays(timeboxDuracion).minusDays(1);
                s.setFechaInicio(inicio);
                s.setFechaFin(fin);
            } else { // SEMANAS
                LocalDate inicio = fechaInicio.plusWeeks((long) (i - 1) * timeboxDuracion);
                LocalDate fin = inicio.plusWeeks(timeboxDuracion).minusDays(1);
                s.setFechaInicio(inicio);
                s.setFechaFin(fin);
            }
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
            marcarFinalizado(s);
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

    /**
     * Vuelve a cerrar un sprint que había sido reabierto — la transición
     * reabierto → finalizado que faltaba (un sprint reabierto no tenía forma
     * de volver a "finalizado", quedando excluido para siempre de las
     * analíticas que filtran por ese estado). Reutiliza la misma lógica de
     * cierre que cerrarEIniciarSiguiente() (marcarFinalizado), pero opera
     * sobre ESTE sprint específico por ID — a diferencia de esa acción, no
     * toca el sprint actualmente en ejecución ni abre ningún sprint
     * pendiente: reabrir/re-finalizar un sprint viejo es independiente del
     * avance normal de sprints del proyecto.
     */
    @Transactional
    public SprintDto finalizarReabierto(UUID sprintId) {
        Sprint s = sprintRepo.findById(sprintId)
                .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado."));
        if (!"reabierto".equals(s.getEstado())) {
            throw new IllegalArgumentException("Solo se pueden finalizar sprints reabiertos con esta acción.");
        }
        marcarFinalizado(s);
        Proyecto p = getProyecto(s.getProyectoId());
        return toDto(sprintRepo.save(s), p);
    }

    /**
     * Cierra el sprint actualmente en ejecución sin iniciar uno nuevo.
     * Útil cuando se quiere finalizar el último sprint del proyecto o cuando
     * no hay más sprints pendientes por iniciar.
     */
    @Transactional
    public SprintDto cerrarSprintActual(UUID sprintId) {
        Sprint s = sprintRepo.findById(sprintId)
                .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado."));
        if (!"en_ejecucion".equals(s.getEstado())) {
            throw new IllegalArgumentException("Solo se pueden cerrar sprints que están en ejecución.");
        }
        marcarFinalizado(s);
        sprintRepo.save(s);
        
        // Activar el siguiente sprint pendiente (igual que hace el scheduler)
        sprintRepo.findFirstByProyectoIdAndEstadoOrderByNumeroAsc(s.getProyectoId(), "pendiente")
                .ifPresent(siguiente -> {
                    siguiente.setEstado("en_ejecucion");
                    sprintRepo.save(siguiente);
                });
        
        Proyecto p = getProyecto(s.getProyectoId());
        return toDto(s, p);
    }

    /**
     * Elimina un sprint pendiente.
     * Solo permite eliminar sprints en estado "pendiente".
     */
    @Transactional
    public void eliminarSprint(UUID sprintId) {
        Sprint s = sprintRepo.findById(sprintId)
                .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado."));
        if (!"pendiente".equals(s.getEstado())) {
            throw new IllegalArgumentException("Solo se pueden eliminar sprints en estado pendiente.");
        }
        sprintRepo.delete(s);
    }

    private void marcarFinalizado(Sprint s) {
        s.setEstado("finalizado");
        s.setCerradoAt(Instant.now());
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
                s.getCreatedAt(),
                p.getTimeboxUnidad(), p.getTimeboxDuracion(),
                s.getFechaHoraInicio(), s.getFechaHoraFin());
    }
}
