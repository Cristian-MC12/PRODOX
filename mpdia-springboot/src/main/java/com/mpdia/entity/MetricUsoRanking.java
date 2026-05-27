// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Contador de usos por métrica (factor).
 * Solo se incrementa cuando alguien selecciona una métrica que ya tiene parametrización base.
 * El ranking siempre apunta a la parametrización base original, no a las copias.
 */
@Entity
@Table(name = "metric_uso_ranking")
@Getter @Setter @NoArgsConstructor
public class MetricUsoRanking {

    @Id
    @Column(name = "factor_id")
    private UUID factorId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "factor_id")
    private Factor factor;

    /** La parametrización base que se usa como referencia para este factor */
    @Column(name = "parametrizacion_id")
    private UUID parametrizacionId;

    @Column(nullable = false)
    private Integer usos = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
