// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_members")
@Data
@NoArgsConstructor
@IdClass(ProjectMember.MemberId.class)
public class ProjectMember {

    /**
     * Valores válidos de {@link #rol} para un miembro dentro de UN proyecto
     * (rol por proyecto — no confundir con el rol global inmutable de
     * {@link AppUser#getRole()}). Centralizados acá para que las
     * comparaciones de rol en controllers/servicios ("PRODUCT_OWNER" nuevo
     * desde acá en adelante) no dependan de literales de string repetidos.
     */
    public static final String ROL_SCRUM_MASTER = "scrum_master";
    public static final String ROL_PRODUCT_OWNER = "product_owner";
    public static final String ROL_SCRUM_MEMBER = "scrum_member";

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
