// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.repository;

import com.prodox.entity.MetricParametrizacion;
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

    /** Contar total de parametrizaciones para una métrica (popularidad/ranking) */
    long countByMetricaId(UUID metricaId);

    /** Verificar si existe parametrización para una métrica en un proyecto */
    boolean existsByMetricaIdAndProyectoId(UUID metricaId, UUID proyectoId);
    
    /**
     * Obtener la última versión aprobada de una parametrización para una métrica y proyecto.
     * Esencial para cálculos reproducibles.
     */
    @Query("""
        SELECT p FROM MetricParametrizacion p
        WHERE p.metricaId = :metricaId
          AND p.proyectoId = :proyectoId
          AND p.status = 'aprobada'
        ORDER BY p.version DESC
        LIMIT 1
        """)
    Optional<MetricParametrizacion> findUltimaVersionAprobada(
        @Param("metricaId") UUID metricaId,
        @Param("proyectoId") UUID proyectoId
    );
    
    /**
     * Obtener todas las versiones de una parametrización (historial).
     */
    @Query("""
        SELECT p FROM MetricParametrizacion p
        WHERE p.metricaId = :metricaId
          AND p.proyectoId = :proyectoId
        ORDER BY p.version DESC
        """)
    List<MetricParametrizacion> findHistorialVersiones(
        @Param("metricaId") UUID metricaId,
        @Param("proyectoId") UUID proyectoId
    );
    
    /**
     * Obtener una versión específica de una parametrización.
     */
    Optional<MetricParametrizacion> findByMetricaIdAndProyectoIdAndVersion(
        UUID metricaId,
        UUID proyectoId,
        Integer version
    );
    
    /**
     * Verificar si existe una versión aprobada para una métrica en un proyecto.
     */
    boolean existsByMetricaIdAndProyectoIdAndStatus(
        UUID metricaId,
        UUID proyectoId,
        String status
    );

    /**
     * Cuenta parametrizaciones de un proyecto por status (persistente en BD, no en
     * memoria de sesión). Usado por el resumen de Verificación (Fase 10).
     */
    long countByProyectoIdAndStatus(UUID proyectoId, String status);
}
