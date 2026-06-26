// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "proyecto_metricas")
@Getter @Setter @NoArgsConstructor
public class ProyectoMetrica {

    @EmbeddedId
    private ProyectoMetricaId id = new ProyectoMetricaId();

    @ManyToOne(fetch = FetchType.EAGER)
    @MapsId("metricaId")
    @JoinColumn(name = "metrica_id")
    private Metrica metrica;

    @Column(nullable = false)
    private Boolean aprobada = false;

    @Column(name = "aprobada_por")
    private String aprobadaPor;

    @Column(name = "aprobada_at")
    private Instant aprobadaAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
