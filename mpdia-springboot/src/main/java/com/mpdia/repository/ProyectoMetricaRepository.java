// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.repository;

import com.mpdia.entity.ProyectoMetrica;
import com.mpdia.entity.ProyectoMetricaId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProyectoMetricaRepository extends JpaRepository<ProyectoMetrica, ProyectoMetricaId> {
    List<ProyectoMetrica> findByIdProyectoId(UUID proyectoId);
    List<ProyectoMetrica> findByIdProyectoIdAndAprobadaTrue(UUID proyectoId);
    boolean existsByIdProyectoIdAndIdMetricaId(UUID proyectoId, UUID metricaId);
}
