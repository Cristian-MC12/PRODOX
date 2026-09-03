// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "historias_usuario")
@Getter @Setter @NoArgsConstructor
public class HistoriaUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "proyecto_id", nullable = false)
    private UUID proyectoId;

    /** Nullable: una historia puede vivir en el backlog sin sprint asignado. */
    @Column(name = "sprint_id")
    private UUID sprintId;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "criterios_aceptacion", columnDefinition = "TEXT")
    private String criteriosAceptacion;

    /** alta | media | baja */
    @Column(nullable = false, length = 10)
    private String prioridad = "media";

    /** pendiente | en_progreso | completada */
    @Column(nullable = false, length = 20)
    private String estado = "pendiente";

    @Column(name = "creado_por", nullable = false)
    private String creadoPor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
