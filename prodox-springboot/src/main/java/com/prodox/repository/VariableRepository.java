// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.repository;

import com.prodox.entity.Variable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VariableRepository extends JpaRepository<Variable, UUID> {
    List<Variable> findByProyectoIdAndActivaTrue(UUID proyectoId);
    Optional<Variable> findByProyectoIdAndMetrica_Id(UUID proyectoId, UUID metricaId);
    boolean existsByProyectoIdAndMetrica_Id(UUID proyectoId, UUID metricaId);

    // FASE 11: métricas FORMULA con más de una variable (FAT, Deuda técnica) tienen varias
    // filas para el mismo proyecto+métrica — findByProyectoIdAndMetrica_Id (Optional, arriba)
    // lanza IncorrectResultSizeDataAccessException en ese caso. Usar esta variante en el
    // legado de PlaneacionService, que solo necesita "alguna" variable representativa.
    List<Variable> findAllByProyectoIdAndMetrica_Id(UUID proyectoId, UUID metricaId);
    
    // Fase 16.7: Búsqueda por parametrización
    List<Variable> findByParametrizacionIdAndParametrizacionVersion(UUID parametrizacionId, Integer version);
    Optional<Variable> findByProyectoIdAndMetrica_IdAndParametrizacionIdAndParametrizacionVersion(
        UUID proyectoId, UUID metricaId, UUID parametrizacionId, Integer version);
}

