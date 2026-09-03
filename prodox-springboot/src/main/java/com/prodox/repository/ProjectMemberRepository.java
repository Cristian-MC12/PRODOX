// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.repository;

import com.prodox.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMember.MemberId> {
    List<ProjectMember> findByProyectoId(UUID proyectoId);
    List<ProjectMember> findByUserId(String userId);
    boolean existsByProyectoIdAndUserId(UUID proyectoId, String userId);
    Optional<ProjectMember> findByProyectoIdAndUserId(UUID proyectoId, String userId);

    /** V40 — a lo sumo un Product Owner activo por proyecto (ver también el
     *  índice único parcial de V40 sobre project_members). */
    boolean existsByProyectoIdAndRol(UUID proyectoId, String rol);
}
