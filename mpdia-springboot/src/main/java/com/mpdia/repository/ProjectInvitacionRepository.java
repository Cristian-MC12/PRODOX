// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.repository;

import com.mpdia.entity.ProjectInvitacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectInvitacionRepository extends JpaRepository<ProjectInvitacion, UUID> {
    Optional<ProjectInvitacion> findByTokenAndUsadoFalse(String token);
    Optional<ProjectInvitacion> findByCodigoAndUsadoFalse(String codigo);
}
