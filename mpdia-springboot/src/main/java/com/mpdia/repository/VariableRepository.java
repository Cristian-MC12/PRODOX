// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.repository;

import com.mpdia.entity.Variable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VariableRepository extends JpaRepository<Variable, UUID> {
    List<Variable> findByProyectoIdAndActivaTrue(UUID proyectoId);
    Optional<Variable> findByProyectoIdAndMetrica_Id(UUID proyectoId, UUID metricaId);
    boolean existsByProyectoIdAndMetrica_Id(UUID proyectoId, UUID metricaId);
}
