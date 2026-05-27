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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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
     * Guarda una parametrización nueva (inmutable).
     * Cada parametrización guardada se registra en el ranking con 0 usos iniciales,
     * independientemente de si es base o derivada.
     */
    @Transactional
    public MetricParametrizacionDto guardar(GuardarParametrizacionRequest req,
                                            String userId, String userEmail) {
        Factor factor = factorRepo.findById(req.factorId())
                .orElseThrow(() -> new IllegalArgumentException("Factor no encontrado."));

        MetricParametrizacion p = new MetricParametrizacion();
        p.setFactor(factor);
        p.setUserId(userId);
        p.setUserEmail(userEmail);
        p.setObjetivo(req.objetivo());
        p.setProcedimiento(req.procedimiento());
        p.setIndicadorVariable(req.indicadorVariable());
        p.setEscala(req.escala());
        p.setMetricaBaseId(req.metricaBaseId());

        MetricParametrizacion saved = parametrizacionRepo.save(p);

        // Registrar en ranking con 0 usos — cada versión tiene su propio contador
        // El ranking del factor apunta siempre a la parametrización más reciente
        MetricUsoRanking ranking = rankingRepo.findById(factor.getId())
                .orElseGet(() -> {
                    MetricUsoRanking r = new MetricUsoRanking();
                    r.setFactor(factor);
                    r.setUsos(0);
                    return r;
                });
        ranking.setParametrizacionId(saved.getId());
        ranking.setUpdatedAt(Instant.now());
        rankingRepo.save(ranking);

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
     * Muestra todas las versiones guardadas para que el usuario vea las distintas formas
     * en que se ha parametrizado esta métrica.
     */
    public List<TopParametrizacionDto> getTop3(UUID factorId) {
        // Mapa de usos por parametrizacionId desde el ranking
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
        return new MetricParametrizacionDto(
                p.getId(),
                f.getId(),
                f.getName(),
                f.getCategory(),
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
                p.getCreatedAt()
        );
    }
}
