// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.repository;

import com.prodox.entity.EvaluacionObjetivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio para evaluaciones de objetivos de sprints.
 * FASE 6B: evaluación manual del cumplimiento del objetivo del sprint.
 */
@Repository
public interface EvaluacionObjetivoRepository extends JpaRepository<EvaluacionObjetivo, UUID> {
    
    /**
     * Busca la evaluación de un sprint específico.
     * Un sprint tiene como máximo una evaluación (relación 1:1).
     */
    Optional<EvaluacionObjetivo> findBySprintId(UUID sprintId);
    
    /**
     * Verifica si ya existe una evaluación para un sprint.
     * Útil para prevenir duplicados antes de crear.
     */
    boolean existsBySprintId(UUID sprintId);
}
