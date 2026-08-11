// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.repository;

import com.mpdia.entity.AIInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AIInsightRepository extends JpaRepository<AIInsight, UUID> {
    
    /** Obtener todos los insights de un proyecto */
    List<AIInsight> findByProyectoIdOrderByCreatedAtDesc(UUID proyectoId);
    
    /** Obtener insights no descartados de un proyecto */
    List<AIInsight> findByProyectoIdAndDismissedFalseOrderByCreatedAtDesc(UUID proyectoId);
    
    /** Obtener insights de un sprint específico */
    List<AIInsight> findBySprintIdOrderByCreatedAtDesc(UUID sprintId);
    
    /** Obtener insights no descartados de un sprint */
    List<AIInsight> findBySprintIdAndDismissedFalseOrderByCreatedAtDesc(UUID sprintId);
    
    /** Obtener insights por tipo */
    List<AIInsight> findByProyectoIdAndTipoOrderByCreatedAtDesc(UUID proyectoId, String tipo);
}
