// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.repository;

import com.mpdia.entity.ProjectMember;
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
}
