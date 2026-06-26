// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_members")
@Getter @Setter @NoArgsConstructor
@IdClass(ProjectMember.MemberId.class)
public class ProjectMember {

    @Id
    @Column(name = "proyecto_id")
    private UUID proyectoId;

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(nullable = false, length = 50)
    private String rol = "scrum_member";

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    public static class MemberId implements Serializable {
        private UUID   proyectoId;
        private String userId;
        public MemberId() {}
        public MemberId(UUID proyectoId, String userId) {
            this.proyectoId = proyectoId;
            this.userId = userId;
        }
    }
}
