// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_invitaciones")
@Getter @Setter @NoArgsConstructor
public class ProjectInvitacion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "proyecto_id", nullable = false)
    private UUID proyectoId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(nullable = false, length = 20)
    private String codigo;

    @Column(nullable = false)
    private Boolean usado = false;

    /** Nullable: invitaciones creadas antes de V35 no la tienen y se tratan
     *  como no-expirables (ver ProjectMemberService). */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
