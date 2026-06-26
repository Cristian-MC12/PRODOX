// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "registro_valores")
@Getter @Setter @NoArgsConstructor
public class RegistroValor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "variable_id", nullable = false)
    private Variable variable;

    @Column(name = "sprint_id", nullable = false)
    private UUID sprintId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "valor_num", precision = 12, scale = 4)
    private BigDecimal valorNum;

    @Column(name = "valor_texto", columnDefinition = "TEXT")
    private String valorTexto;

    @Column(name = "valor_bool")
    private Boolean valorBool;

    @Column(columnDefinition = "TEXT")
    private String observacion;

    @Column(name = "registrado_at", nullable = false)
    private Instant registradoAt = Instant.now();
}
