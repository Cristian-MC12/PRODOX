// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Parametrización de una métrica creada por un usuario.
 * Es INMUTABLE: nunca se actualiza, solo se inserta.
 * metricaBaseId apunta a la parametrización original si esta es una copia.
 */
@Entity
@Table(name = "metric_parametrizaciones")
@Getter @Setter @NoArgsConstructor
public class MetricParametrizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "factor_id", nullable = true)
    private Factor factor;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String objetivo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String procedimiento;

    @Column(name = "indicador_variable", nullable = false, length = 500)
    private String indicadorVariable;

    @Column(nullable = false, length = 255)
    private String escala;

    /** Si esta parametrización fue copiada de otra, apunta a la original */
    @Column(name = "metrica_base_id")
    private UUID metricaBaseId;

    /** Métrica del catálogo asociada (para búsqueda desde el flujo de Planeación) */
    @Column(name = "metrica_id")
    private UUID metricaId;

    @Column(nullable = false, length = 30)
    private String status = "pendiente"; // pendiente | aprobada | rechazada

    @Column(name = "revisado_por")
    private String revisadoPor;

    @Column(name = "revisado_at")
    private Instant revisadoAt;

    @Column(name = "motivo_rechazo", columnDefinition = "TEXT")
    private String motivoRechazo;

    @Column(name = "proyecto_id")
    private UUID proyectoId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
