// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "metricas")
@Getter @Setter @NoArgsConstructor
public class Metrica {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private MetricaCategoria categoria;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(length = 120)
    private String factor;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    /*
     * Metrica es EXCLUSIVAMENTE el catálogo global de definiciones de
     * métrica — nunca tiene un proyecto propietario. La asociación entre un
     * proyecto y una Metrica vive en ProyectoMetrica, nunca acá (revisión
     * post-implementación de la Fase PRODOX AI: V30 había agregado una
     * columna proyecto_id que contradecía este modelo — revertida en V31).
     */
}
