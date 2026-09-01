// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.repository;

import com.prodox.entity.ProyectoMetrica;
import com.prodox.entity.ProyectoMetricaId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProyectoMetricaRepository extends JpaRepository<ProyectoMetrica, ProyectoMetricaId> {
    List<ProyectoMetrica> findByIdProyectoId(UUID proyectoId);
    List<ProyectoMetrica> findByIdProyectoIdAndAprobadaTrue(UUID proyectoId);
    boolean existsByIdProyectoIdAndIdMetricaId(UUID proyectoId, UUID metricaId);
}
