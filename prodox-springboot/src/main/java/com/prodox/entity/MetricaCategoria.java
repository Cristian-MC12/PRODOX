// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "metrica_categorias")
@Getter @Setter @NoArgsConstructor
public class MetricaCategoria {

    @Id
    private Short id;

    @Column(nullable = false, unique = true, length = 60)
    private String nombre;
}
