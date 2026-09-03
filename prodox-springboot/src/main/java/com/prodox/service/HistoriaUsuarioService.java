// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.prodox.dto.ActualizarHistoriaUsuarioRequest;
import com.prodox.dto.CrearHistoriaUsuarioRequest;
import com.prodox.dto.HistoriaUsuarioDto;
import com.prodox.entity.HistoriaUsuario;
import com.prodox.entity.Sprint;
import com.prodox.repository.HistoriaUsuarioRepository;
import com.prodox.repository.SprintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Backlog de historias de usuario (V39 — Product Owner). La autorización
 * (membresía + rol product_owner) se valida en HistoriaUsuarioController,
 * igual que el resto del backend (SprintController, PlaneacionController,
 * EvaluacionController): este servicio asume que quien lo llama ya pasó esa
 * validación y se concentra en las reglas de negocio del backlog.
 */
@Service
@RequiredArgsConstructor
public class HistoriaUsuarioService {

    private static final List<String> PRIORIDADES = List.of("alta", "media", "baja");
    private static final List<String> ESTADOS = List.of("pendiente", "en_progreso", "completada");

    private final HistoriaUsuarioRepository historiaRepo;
    private final SprintRepository sprintRepo;

    /** Backlog del proyecto: prioridad alta→media→baja y, dentro de cada una, más antigua primero. */
    public List<HistoriaUsuarioDto> listar(UUID proyectoId) {
        return historiaRepo.findByProyectoId(proyectoId).stream()
                .sorted(Comparator
                        .comparingInt((HistoriaUsuario h) -> rankPrioridad(h.getPrioridad()))
                        .thenComparing(HistoriaUsuario::getCreatedAt))
                .map(this::toDto)
                .toList();
    }

    public HistoriaUsuarioDto detalle(UUID historiaId) {
        return toDto(obtener(historiaId));
    }

    @Transactional
    public HistoriaUsuarioDto crear(UUID proyectoId, String creadoPor, CrearHistoriaUsuarioRequest req) {
        HistoriaUsuario h = new HistoriaUsuario();
        h.setProyectoId(proyectoId);
        h.setTitulo(req.titulo());
        h.setDescripcion(req.descripcion());
        h.setCriteriosAceptacion(req.criteriosAceptacion());
        h.setPrioridad(validarPrioridad(req.prioridad() != null ? req.prioridad() : "media"));
        h.setCreadoPor(creadoPor);
        return toDto(historiaRepo.save(h));
    }

    @Transactional
    public HistoriaUsuarioDto actualizar(UUID historiaId, ActualizarHistoriaUsuarioRequest req) {
        HistoriaUsuario h = obtener(historiaId);
        h.setTitulo(req.titulo());
        h.setDescripcion(req.descripcion());
        h.setCriteriosAceptacion(req.criteriosAceptacion());
        h.setUpdatedAt(Instant.now());
        return toDto(historiaRepo.save(h));
    }

    @Transactional
    public HistoriaUsuarioDto cambiarPrioridad(UUID historiaId, String prioridad) {
        HistoriaUsuario h = obtener(historiaId);
        h.setPrioridad(validarPrioridad(prioridad));
        h.setUpdatedAt(Instant.now());
        return toDto(historiaRepo.save(h));
    }

    @Transactional
    public HistoriaUsuarioDto cambiarEstado(UUID historiaId, String estado) {
        HistoriaUsuario h = obtener(historiaId);
        h.setEstado(validarEstado(estado));
        h.setUpdatedAt(Instant.now());
        return toDto(historiaRepo.save(h));
    }

    /**
     * Asigna (sprintId != null) o desasigna (sprintId == null, vuelve al
     * backlog) una historia a un sprint. Si se asigna, el sprint DEBE
     * pertenecer al mismo proyecto que la historia — sin esta validación, un
     * sprintId de otro proyecto (adivinado o copiado desde otra pestaña)
     * quedaría igual de "aceptado" que uno legítimo.
     */
    @Transactional
    public HistoriaUsuarioDto asignarSprint(UUID historiaId, UUID sprintId) {
        HistoriaUsuario h = obtener(historiaId);
        if (sprintId != null) {
            Sprint sprint = sprintRepo.findById(sprintId)
                    .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado."));
            if (!sprint.getProyectoId().equals(h.getProyectoId())) {
                throw new SecurityException("El sprint no pertenece al proyecto de esta historia.");
            }
        }
        h.setSprintId(sprintId);
        h.setUpdatedAt(Instant.now());
        return toDto(historiaRepo.save(h));
    }

    @Transactional
    public void eliminar(UUID historiaId) {
        if (!historiaRepo.existsById(historiaId)) {
            throw new IllegalArgumentException("Historia no encontrada.");
        }
        historiaRepo.deleteById(historiaId);
    }

    private HistoriaUsuario obtener(UUID historiaId) {
        return historiaRepo.findById(historiaId)
                .orElseThrow(() -> new IllegalArgumentException("Historia no encontrada."));
    }

    private String validarPrioridad(String prioridad) {
        if (!PRIORIDADES.contains(prioridad)) {
            throw new IllegalArgumentException("Prioridad inválida. Debe ser: alta, media o baja.");
        }
        return prioridad;
    }

    private String validarEstado(String estado) {
        if (!ESTADOS.contains(estado)) {
            throw new IllegalArgumentException("Estado inválido. Debe ser: pendiente, en_progreso o completada.");
        }
        return estado;
    }

    private int rankPrioridad(String prioridad) {
        return switch (prioridad) {
            case "alta" -> 0;
            case "media" -> 1;
            default -> 2;
        };
    }

    private HistoriaUsuarioDto toDto(HistoriaUsuario h) {
        return new HistoriaUsuarioDto(
                h.getId(), h.getProyectoId(), h.getSprintId(),
                h.getTitulo(), h.getDescripcion(), h.getCriteriosAceptacion(),
                h.getPrioridad(), h.getEstado(), h.getCreadoPor(),
                h.getCreatedAt(), h.getUpdatedAt());
    }
}
