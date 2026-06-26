// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "sprints")
@Getter @Setter @NoArgsConstructor
public class Sprint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "proyecto_id", nullable = false)
    private UUID proyectoId;

    @Column(nullable = false)
    private Integer numero;

    @Column(name = "sprint_goal", nullable = false, columnDefinition = "TEXT")
    private String sprintGoal;

    /** Estados: pendiente | en_ejecucion | finalizado | reabierto */
    @Column(nullable = false, length = 20)
    private String estado = "en_ejecucion";

    @Column(name = "cerrado_por")
    private String cerradoPor;

    @Column(name = "cerrado_at")
    private java.time.Instant cerradoAt;

    @Column(name = "reabierto_por")
    private String reabiertoPor;

    @Column(name = "reabierto_at")
    private java.time.Instant reabiertaAt;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio = LocalDate.now();

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
