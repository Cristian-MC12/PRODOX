// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.repository;

import com.mpdia.entity.RegistroValor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RegistroValorRepository extends JpaRepository<RegistroValor, UUID> {
    List<RegistroValor> findBySprintId(UUID sprintId);
    List<RegistroValor> findByVariable_IdAndSprintId(UUID variableId, UUID sprintId);
    List<RegistroValor> findBySprintIdAndUserId(UUID sprintId, String userId);
}
