// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.repository;

import com.mpdia.entity.ResultadoMetrica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResultadoMetricaRepository extends JpaRepository<ResultadoMetrica, UUID> {
    
    /**
     * Obtiene el último resultado calculado para una métrica en un sprint.
     */
    @Query("""
        SELECT r FROM ResultadoMetrica r
        WHERE r.metrica.id = :metricaId
        AND r.sprintId = :sprintId
        ORDER BY r.calculadoAt DESC
        LIMIT 1
        """)
    Optional<ResultadoMetrica> findUltimoResultado(
        @Param("metricaId") UUID metricaId,
        @Param("sprintId") UUID sprintId
    );
    
    /**
     * Obtiene todos los resultados de un sprint.
     */
    List<ResultadoMetrica> findBySprintIdOrderByCalculadoAtDesc(UUID sprintId);
    
    /**
     * Obtiene histórico de resultados de una métrica.
     */
    List<ResultadoMetrica> findByMetrica_IdOrderByCalculadoAtDesc(UUID metricaId);
    
    /**
     * Obtiene resultados de un proyecto y sprint.
     */
    List<ResultadoMetrica> findByProyectoIdAndSprintId(UUID proyectoId, UUID sprintId);
    
    /**
     * Obtiene histórico de resultados de una métrica en un proyecto específico.
     * Ordenado por fecha descendente.
     * Fase 16.9.1: Para métricas académicas.
     */
    List<ResultadoMetrica> findByMetrica_IdAndProyectoIdOrderByCalculadoAtDesc(
        UUID metricaId, 
        UUID proyectoId
    );
}
