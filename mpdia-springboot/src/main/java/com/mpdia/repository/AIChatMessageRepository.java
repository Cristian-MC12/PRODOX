// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.repository;

import com.mpdia.entity.AIChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AIChatMessageRepository extends JpaRepository<AIChatMessage, UUID> {
    
    /** Obtener historial de chat de un usuario en un proyecto */
    List<AIChatMessage> findByUserIdAndProyectoIdOrderByCreatedAtAsc(String userId, UUID proyectoId);
    
    /** Obtener historial de chat de un usuario en un proyecto y sprint específico */
    List<AIChatMessage> findByUserIdAndProyectoIdAndSprintIdOrderByCreatedAtAsc(
            String userId, UUID proyectoId, UUID sprintId);
    
    /** Obtener todos los mensajes de un proyecto (para análisis o admin) */
    List<AIChatMessage> findByProyectoIdOrderByCreatedAtDesc(UUID proyectoId);
    
    /** Eliminar todos los mensajes de un usuario en un proyecto (clear history) */
    void deleteByUserIdAndProyectoId(String userId, UUID proyectoId);
}
