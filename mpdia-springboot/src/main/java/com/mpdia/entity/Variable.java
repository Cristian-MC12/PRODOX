// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "variables")
@Getter @Setter @NoArgsConstructor
public class Variable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "proyecto_id", nullable = false)
    private UUID proyectoId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "metrica_id", nullable = false)
    private Metrica metrica;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    /** grupal | individual */
    @Column(name = "tipo_alcance", nullable = false, length = 20)
    private String tipoAlcance = "grupal";

    /** calidad | productividad | cumplimiento | flexibilidad | sociohumano */
    @Column(name = "tipo_indicador", nullable = false, length = 20)
    private String tipoIndicador = "calidad";

    /** diaria | semanal | por_sprint */
    @Column(nullable = false, length = 20)
    private String frecuencia = "por_sprint";

    /** unico | multiple */
    @Column(nullable = false, length = 20)
    private String cardinalidad = "unico";

    /** numerico | texto | booleano | escala */
    @Column(name = "tipo_dato", nullable = false, length = 20)
    private String tipoDato = "numerico";

    @Column(name = "escala_min", precision = 10, scale = 2)
    private BigDecimal escalaMin;

    @Column(name = "escala_max", precision = 10, scale = 2)
    private BigDecimal escalaMax;

    @Column(nullable = false)
    private Boolean activa = true;

    /** Texto libre de la fórmula, ej: "ISE = Crítico×5 + Mayor×1 + Medio×4 + Menor×6" */
    @Column(name = "formula_texto", columnDefinition = "TEXT")
    private String formulaTexto;

    /**
     * Definición estructurada de la fórmula en JSON:
     * { "expresion": "...", "operandos": [...], "escalaResultado": "..." }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "formula_json", columnDefinition = "jsonb")
    private String formulaJson;

    /**
     * Con qué frecuencia se capturan los datos: por_sprint | semanal | diaria | ilimitada
     */
    @Column(name = "frecuencia_captura", nullable = false, length = 20)
    private String frecuenciaCaptura = "por_sprint";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
