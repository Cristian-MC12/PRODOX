// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.GuardarParametrizacionRequest;
import com.mpdia.dto.MetricParametrizacionDto;
import com.mpdia.dto.RankingMetricaDto;
import com.mpdia.dto.TopParametrizacionDto;
import com.mpdia.entity.Factor;
import com.mpdia.entity.MetricParametrizacion;
import com.mpdia.entity.MetricUsoRanking;
import com.mpdia.repository.FactorRepository;
import com.mpdia.repository.MetricParametrizacionRepository;
import com.mpdia.repository.MetricUsoRankingRepository;
import com.mpdia.repository.MetricaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetricRankingService {

    private final MetricParametrizacionRepository parametrizacionRepo;
    private final MetricUsoRankingRepository      rankingRepo;
    private final FactorRepository                factorRepo;
    private final MetricaRepository               metricaRepo;
    private final PlaneacionService               planeacionService;

    /**
     * Scrum Master aprueba o rechaza una parametrización.
     * Solo usuarios con rol scrum_master pueden llamar esto.
     */
    @Transactional
    public MetricParametrizacionDto verificar(com.mpdia.dto.VerificarParametrizacionRequest req,
                                              String revisadoPor) {
        MetricParametrizacion p = parametrizacionRepo.findById(req.parametrizacionId())
                .orElseThrow(() -> new IllegalArgumentException("Parametrización no encontrada."));

        if ("aprobar".equals(req.accion())) {
            p.setStatus("aprobada");
            // Si la parametrización tiene metricaId y proyectoId, aprobar la métrica
            // en Planeación para generar la variable de Ejecución
            if (p.getMetricaId() != null && p.getProyectoId() != null) {
                try {
                    // Verificar si ya está aprobada antes de intentar aprobar
                    var existing = planeacionService.listarSeleccionadas(p.getProyectoId()).stream()
                            .filter(m -> m.metricaId().equals(p.getMetricaId()))
                            .findFirst();
                    
                    if (existing.isEmpty() || !existing.get().aprobada()) {
                        planeacionService.aprobar(p.getProyectoId(), p.getMetricaId(), revisadoPor);
                    }
                } catch (Exception e) {
                    // Log para diagnosticar si falla la generación de variable
                    System.err.println("[VERIFICAR] Error al aprobar métrica en Planeación: "
                            + e.getMessage() + " | metricaId=" + p.getMetricaId()
                            + " | proyectoId=" + p.getProyectoId());
                }
            }
        } else if ("rechazar".equals(req.accion())) {
            if (req.motivoRechazo() == null || req.motivoRechazo().isBlank()) {
                throw new IllegalArgumentException("El motivo de rechazo es obligatorio.");
            }
            p.setStatus("rechazada");
            p.setMotivoRechazo(req.motivoRechazo());
        } else {
            throw new IllegalArgumentException("Acción inválida. Use 'aprobar' o 'rechazar'.");
        }
        p.setRevisadoPor(revisadoPor);
        p.setRevisadoAt(java.time.Instant.now());

        return toDto(parametrizacionRepo.save(p), p.getFactor());
    }

    /**
     * Lista todas las parametrizaciones pendientes de verificación (para el Scrum Master).
     */
    public List<MetricParametrizacionDto> getPendientes() {
        return parametrizacionRepo.findByStatusOrderByCreatedAtDesc("pendiente").stream()
                .map(p -> toDto(p, p.getFactor()))
                .toList();
    }

    /**
     * Lista parametrizaciones pendientes filtradas por proyecto.
     */
    public List<MetricParametrizacionDto> getPendientesPorProyecto(UUID proyectoId) {
        return parametrizacionRepo.findByStatusOrderByCreatedAtDesc("pendiente").stream()
                .filter(p -> proyectoId == null || proyectoId.equals(p.getProyectoId()))
                .map(p -> toDto(p, p.getFactor()))
                .toList();
    }

    /**
     * Guarda o actualiza la parametrización del usuario para una métrica+proyecto.
     * Si el mismo usuario ya guardó una para la misma métrica en el mismo proyecto,
     * se actualiza en lugar de insertar (evita duplicados en el Top 3).
     */
    @Transactional
    public MetricParametrizacionDto guardar(GuardarParametrizacionRequest req,
                                            String userId, String userEmail) {
        final Factor factor;
        if (req.factorId() != null) {
            factor = factorRepo.findById(req.factorId()).orElse(null);
        } else {
            factor = null;
        }

        // Buscar parametrización existente del mismo usuario para la misma métrica (global, sin filtro por proyecto)
        MetricParametrizacion p = null;
        if (req.metricaId() != null) {
            p = parametrizacionRepo
                    .findByUserIdAndMetricaId(userId, req.metricaId())
                    .orElse(null);
        } else if (req.factorId() != null) {
            p = parametrizacionRepo
                    .findByUserIdAndFactor_Id(userId, req.factorId())
                    .orElse(null);
        }

        if (p == null) {
            p = new MetricParametrizacion();
            p.setFactor(factor);
            p.setUserId(userId);
            p.setUserEmail(userEmail);
            p.setProyectoId(req.proyectoId());
            // Solo asignar metricaId si realmente existe en la tabla metricas
            UUID metricaId = req.metricaId() != null && metricaRepo.existsById(req.metricaId())
                    ? req.metricaId() : null;
            p.setMetricaId(metricaId);
        }

        // Actualizar campos (upsert)
        p.setObjetivo(req.objetivo());
        p.setProcedimiento(req.procedimiento());
        p.setIndicadorVariable(req.indicadorVariable());
        p.setEscala(req.escala());
        p.setMetricaBaseId(req.metricaBaseId());
        p.setStatus("pendiente");
        p.setRevisadoPor(null);
        p.setRevisadoAt(null);
        p.setMotivoRechazo(null);

        MetricParametrizacion saved = parametrizacionRepo.save(p);

        // Actualizar ranking solo si hay factor y evitar conflictos
        if (factor != null) {
            try {
                MetricUsoRanking ranking = rankingRepo.findById(factor.getId())
                        .orElseGet(() -> {
                            MetricUsoRanking r = new MetricUsoRanking();
                            r.setFactor(factor);
                            r.setUsos(0);
                            return r;
                        });
                ranking.setParametrizacionId(saved.getId());
                ranking.setUsos(ranking.getUsos() + 1);
                ranking.setUpdatedAt(Instant.now());
                rankingRepo.save(ranking);
            } catch (Exception e) {
                // Ignorar errores de ranking para no bloquear la parametrización
                System.err.println("[GUARDAR] Error al actualizar ranking: " + e.getMessage());
            }
        }

        return toDto(saved, factor);
    }

    /**
     * Incrementa el contador de usos de la métrica base cuando otro usuario la selecciona.
     */
    @Transactional
    public void incrementarUso(UUID factorId) {
        rankingRepo.findById(factorId).ifPresent(r -> {
            r.setUsos(r.getUsos() + 1);
            r.setUpdatedAt(Instant.now());
            rankingRepo.save(r);
        });
    }

    /**
     * Devuelve la parametrización base más reciente de un factor.
     */
    public Optional<MetricParametrizacionDto> getBase(UUID factorId) {
        return parametrizacionRepo
                .findTopByFactor_IdAndMetricaBaseIdIsNullOrderByCreatedAtDesc(factorId)
                .map(p -> toDto(p, p.getFactor()));
    }

    /**
     * Top 3 parametrizaciones de un factor, ordenadas por fecha de creación descendente.
     */
    public List<TopParametrizacionDto> getTop3(UUID factorId) {
        Map<UUID, Integer> usosMap = rankingRepo.findAll().stream()
                .filter(r -> r.getParametrizacionId() != null)
                .collect(Collectors.toMap(
                        MetricUsoRanking::getParametrizacionId,
                        MetricUsoRanking::getUsos,
                        (a, b) -> a
                ));

        return parametrizacionRepo.findTop3BaseByFactorId(factorId).stream()
                .map(p -> new TopParametrizacionDto(
                        p.getId(),
                        p.getUserEmail(),
                        p.getObjetivo(),
                        p.getProcedimiento(),
                        p.getIndicadorVariable(),
                        p.getEscala(),
                        usosMap.getOrDefault(p.getId(), 0),
                        p.getCreatedAt()
                ))
                .toList();
    }

    /**
     * Top 3 parametrizaciones por metricaId (flujo desde Planeación).
     * Los "usos" se calculan como la cantidad total de parametrizaciones
     * guardadas para esta métrica (indica popularidad).
     */
    public List<TopParametrizacionDto> getTop3ByMetricaId(UUID metricaId) {
        List<MetricParametrizacion> todas = parametrizacionRepo.findTop3ByMetricaId(metricaId);
        // Contar usos totales para esta métrica (todas las parametrizaciones de todos los usuarios)
        long usosTotales = parametrizacionRepo.countByMetricaId(metricaId);

        return todas.stream()
                .map(p -> new TopParametrizacionDto(
                        p.getId(),
                        p.getUserEmail(),
                        p.getObjetivo(),
                        p.getProcedimiento(),
                        p.getIndicadorVariable(),
                        p.getEscala(),
                        (int) usosTotales,
                        p.getCreatedAt()
                ))
                .toList();
    }

    /**
     * Parametrización base más reciente por metricaId.
     */
    public Optional<MetricParametrizacionDto> getBaseByMetricaId(UUID metricaId) {
        return parametrizacionRepo
                .findTopByMetricaIdOrderByCreatedAtDesc(metricaId)
                .map(p -> toDto(p, p.getFactor()));
    }

    /**
     * Top 5 factores más usados para mostrar en la pantalla de Selección.
     */
    public List<RankingMetricaDto> getRanking() {
        return rankingRepo.findTop5ByOrderByUsosDesc().stream()
                .map(r -> new RankingMetricaDto(
                        r.getFactor().getId(),
                        r.getFactor().getName(),
                        r.getFactor().getCategory(),
                        r.getUsos(),
                        r.getParametrizacionId()
                ))
                .toList();
    }

    private MetricParametrizacionDto toDto(MetricParametrizacion p, Factor f) {
        UUID   fId        = f != null ? f.getId()       : null;
        String fNombre;
        String fCategoria;

        if (f != null) {
            fNombre    = f.getName();
            fCategoria = f.getCategory();
        } else if (p.getMetricaId() != null) {
            // Flujo desde Planeación: buscar nombre y categoría de la métrica
            var metrica = metricaRepo.findById(p.getMetricaId()).orElse(null);
            fNombre    = metrica != null ? metrica.getNombre()                    : "Métrica";
            fCategoria = metrica != null ? metrica.getCategoria().getNombre()     : "—";
        } else {
            fNombre    = "—";
            fCategoria = "—";
        }

        return new MetricParametrizacionDto(
                p.getId(),
                fId,
                fNombre,
                fCategoria,
                p.getUserEmail(),
                p.getObjetivo(),
                p.getProcedimiento(),
                p.getIndicadorVariable(),
                p.getEscala(),
                p.getMetricaBaseId(),
                p.getStatus(),
                p.getRevisadoPor(),
                p.getRevisadoAt(),
                p.getMotivoRechazo(),
                p.getProyectoId(),
                p.getCreatedAt()
        );
    }
}
