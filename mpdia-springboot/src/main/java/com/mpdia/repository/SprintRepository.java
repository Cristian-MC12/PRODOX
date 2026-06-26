// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.repository;

import com.mpdia.entity.Sprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SprintRepository extends JpaRepository<Sprint, UUID> {
    List<Sprint> findByProyectoIdOrderByNumeroDesc(UUID proyectoId);
    Optional<Sprint> findByProyectoIdAndEstado(UUID proyectoId, String estado);
    Optional<Sprint> findTopByProyectoIdOrderByNumeroDesc(UUID proyectoId);

    /** Sprints en ejecución cuya fecha_fin ya pasó (para cierre automático) */
    @Query("SELECT s FROM Sprint s WHERE s.estado = 'en_ejecucion' AND s.fechaFin < :hoy")
    List<Sprint> findVencidos(LocalDate hoy);

    /** Próximo sprint pendiente del proyecto */
    Optional<Sprint> findFirstByProyectoIdAndEstadoOrderByNumeroAsc(UUID proyectoId, String estado);
}
