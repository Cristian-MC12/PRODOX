// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.repository;

import com.mpdia.entity.MetricParametrizacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MetricParametrizacionRepository extends JpaRepository<MetricParametrizacion, UUID> {

    /** Última parametrización base (sin padre) para un factor dado */
    Optional<MetricParametrizacion> findTopByFactor_IdAndMetricaBaseIdIsNullOrderByCreatedAtDesc(UUID factorId);

    /** Todas las parametrizaciones con un estado dado, ordenadas por fecha */
    List<MetricParametrizacion> findByStatusOrderByCreatedAtDesc(String status);

    /** Parametrización existente del mismo usuario para la misma métrica (ranking global) */
    Optional<MetricParametrizacion> findByUserIdAndMetricaId(String userId, UUID metricaId);

    /** Parametrización existente del mismo usuario para el mismo factor (ranking global) */
    Optional<MetricParametrizacion> findByUserIdAndFactor_Id(String userId, UUID factorId);

    /** Parametrización existente del mismo usuario para la misma métrica y proyecto (legacy) */
    Optional<MetricParametrizacion> findByUserIdAndMetricaIdAndProyectoId(String userId, UUID metricaId, UUID proyectoId);

    /** Parametrización existente del mismo usuario para el mismo factor y proyecto (legacy) */
    Optional<MetricParametrizacion> findByUserIdAndFactor_IdAndProyectoId(String userId, UUID factorId, UUID proyectoId);

    /**
     * Top 3 parametrizaciones de un factor — una por usuario (la más reciente de cada uno).
     */
    @Query("""
        SELECT p FROM MetricParametrizacion p
        WHERE p.factor.id = :factorId
          AND p.createdAt = (
              SELECT MAX(p2.createdAt) FROM MetricParametrizacion p2
              WHERE p2.factor.id = :factorId AND p2.userId = p.userId
          )
        ORDER BY p.createdAt DESC
        LIMIT 3
        """)
    List<MetricParametrizacion> findTop3BaseByFactorId(@Param("factorId") UUID factorId);

    /**
     * Top 3 parametrizaciones por metricaId — una por usuario (la más reciente de cada uno).
     */
    @Query("""
        SELECT p FROM MetricParametrizacion p
        WHERE p.metricaId = :metricaId
          AND p.createdAt = (
              SELECT MAX(p2.createdAt) FROM MetricParametrizacion p2
              WHERE p2.metricaId = :metricaId AND p2.userId = p.userId
          )
        ORDER BY p.createdAt DESC
        LIMIT 3
        """)
    List<MetricParametrizacion> findTop3ByMetricaId(@Param("metricaId") UUID metricaId);

    /** Última parametrización por metricaId */
    Optional<MetricParametrizacion> findTopByMetricaIdOrderByCreatedAtDesc(UUID metricaId);
}
