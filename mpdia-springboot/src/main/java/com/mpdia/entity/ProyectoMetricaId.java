// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter @Setter @NoArgsConstructor @EqualsAndHashCode
public class ProyectoMetricaId implements Serializable {

    @Column(name = "proyecto_id")
    private UUID proyectoId;

    @Column(name = "metrica_id")
    private UUID metricaId;
}
