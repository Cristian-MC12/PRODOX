// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.repository;

import com.prodox.entity.ProjectInvitacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectInvitacionRepository extends JpaRepository<ProjectInvitacion, UUID> {
    Optional<ProjectInvitacion> findByTokenAndUsadoFalse(String token);
    Optional<ProjectInvitacion> findByCodigoAndUsadoFalse(String codigo);

    /** Sin filtrar por "usado": necesario para poder distinguir
     *  "no existe" de "ya utilizada" al consultar el estado de una invitación. */
    Optional<ProjectInvitacion> findByCodigo(String codigo);

    /**
     * V40 — invitaciones de un rol dado todavía no aceptadas para un proyecto
     * (usadas para detectar una invitación de Product Owner pendiente). No
     * filtra por expiración acá: una invitación expirada pero nunca marcada
     * "usada" no debería seguir bloqueando nuevas invitaciones, así que
     * ProjectMemberService filtra expiresAt en memoria antes de decidir.
     */
    List<ProjectInvitacion> findByProyectoIdAndRolAndUsadoFalse(UUID proyectoId, String rol);
}
