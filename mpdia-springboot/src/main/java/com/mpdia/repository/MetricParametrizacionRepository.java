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

    /**
     * Top 3 parametrizaciones de un factor, ordenadas por fecha de creación descendente.
     * Muestra todas las versiones guardadas (base y derivadas) para que el usuario
     * pueda ver las diferentes formas en que se ha parametrizado esta métrica.
     */
    @Query("""
        SELECT p FROM MetricParametrizacion p
        WHERE p.factor.id = :factorId
        ORDER BY p.createdAt DESC
        LIMIT 3
        """)
    List<MetricParametrizacion> findTop3BaseByFactorId(@Param("factorId") UUID factorId);
}
