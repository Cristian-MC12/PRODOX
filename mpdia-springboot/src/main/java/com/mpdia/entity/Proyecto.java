// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "proyectos")
@Getter @Setter @NoArgsConstructor
public class Proyecto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    /** Método ágil: "scrum" o "xp" */
    @Column(nullable = false, length = 10)
    private String metodo;

    /** Duración de cada iteración en semanas (1-4) */
    @Column(name = "time_box_semanas", nullable = false)
    private Integer timeBoxSemanas;

    @Column(name = "product_goal", nullable = false, columnDefinition = "TEXT")
    private String productGoal;

    @Column(name = "sprint_goal", nullable = false, columnDefinition = "TEXT")
    private String sprintGoal;

    /** Número total de sprints planificados */
    @Column(name = "numero_sprints", nullable = false)
    private Integer numeroSprints = 3;

    /** Fecha de inicio del proyecto (base para calcular fechas de sprints) */
    @Column(name = "fecha_inicio")
    private java.time.LocalDate fechaInicio;

    /** Estado: "activo" | "finalizado" */
    @Column(nullable = false, length = 20)
    private String estado = "activo";

    /** UUID del Scrum Master como String */
    @Column(name = "scrum_master_id", nullable = false)
    private String scrumMasterId;

    /** Equipo asociado (opcional al crear, se puede vincular después) */
    // teamId removed — membership is now managed per-project via ProjectMember

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
