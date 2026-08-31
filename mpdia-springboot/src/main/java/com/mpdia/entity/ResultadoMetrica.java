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

/**
 * Resultado del cálculo de una métrica en un sprint específico.
 * Fase 16.8: Motor determinista de cálculo.
 * 
 * Mantiene trazabilidad completa para reproducibilidad:
 * - Parametrización y versión utilizadas
 * - Expresión/configuración empleada
 * - Valores utilizados en el cálculo
 * - Usuario y fecha del cálculo
 * 
 * INMUTABLE: Una vez creado, no se modifica. Si se recalcula,
 * se crea un nuevo registro.
 */
@Entity
@Table(name = "resultados_metricas")
@Getter @Setter @NoArgsConstructor
public class ResultadoMetrica {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "proyecto_id", nullable = false)
    private UUID proyectoId;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "metrica_id", nullable = false)
    private Metrica metrica;
    
    @Column(name = "sprint_id", nullable = false)
    private UUID sprintId;
    
    /** Parametrización utilizada para el cálculo */
    @Column(name = "parametrizacion_id", nullable = false)
    private UUID parametrizacionId;
    
    /** Versión de la parametrización */
    @Column(name = "parametrizacion_version", nullable = false)
    private Integer parametrizacionVersion;
    
    /** 
     * Tipo de cálculo: directo | suma | promedio | formula
     */
    @Column(name = "tipo_calculo", nullable = false, length = 20)
    private String tipoCalculo;
    
    /**
     * Expresión utilizada (para tipo 'formula').
     * Null para otros tipos.
     */
    @Column(name = "expresion_utilizada", columnDefinition = "TEXT")
    private String expresionUtilizada;
    
    /**
     * Valores utilizados en el cálculo (snapshot para reproducibilidad).
     * Formato JSON: { "variable_id": valor, ... }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valores_utilizados", columnDefinition = "jsonb", nullable = false)
    private String valoresUtilizados;
    
    /**
     * Resultado del cálculo con precisión de 4 decimales.
     */
    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal resultado;
    
    /**
     * Unidad del resultado (%, puntos, etc.)
     */
    @Column(length = 50)
    private String unidad;
    
    /**
     * Estado del cálculo: calculado | error | incompleto
     */
    @Column(nullable = false, length = 20)
    private String estado = "calculado";
    
    /**
     * Mensaje de error si estado = error
     */
    @Column(name = "mensaje_error", columnDefinition = "TEXT")
    private String mensajeError;
    
    /**
     * Usuario que ejecutó el cálculo
     */
    @Column(name = "calculado_por", nullable = false)
    private String calculadoPor;
    
    /**
     * Fecha y hora del cálculo
     */
    @Column(name = "calculado_at", nullable = false)
    private Instant calculadoAt = Instant.now();

    /**
     * true = este es el resultado vigente para (proyecto, métrica, sprint,
     * parametrizacion_version); false = histórico, reemplazado por un
     * recálculo posterior. Nunca se borra una fila al recalcular — la
     * anterior pasa a vigente=false y se inserta una nueva vigente=true
     * (ver V37__resultado_metrica_vigente.sql y
     * CalculoMetricaService.marcarResultadoAnteriorComoHistorico).
     */
    @Column(nullable = false)
    private Boolean vigente = true;
}
