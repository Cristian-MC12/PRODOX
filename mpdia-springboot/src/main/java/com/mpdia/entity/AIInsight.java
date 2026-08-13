// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

/**
 * Insight generado automáticamente por el AI Copilot.
 * Identifica riesgos, problemas o recomendaciones basadas en análisis de métricas.
 * 
 * FASE 13: Extendido para incluir evidencia estructurada y metadatos adicionales.
 */
@Entity
@Table(name = "ai_insights")
@Getter @Setter @NoArgsConstructor
public class AIInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Proyecto al que pertenece el insight */
    @Column(name = "proyecto_id", nullable = false)
    private UUID proyectoId;

    /** Sprint específico analizado (opcional) */
    @Column(name = "sprint_id")
    private UUID sprintId;

    /** Tipo de insight: 'TREND' | 'ANOMALY' | 'RISK' | 'COMPARISON' */
    @Column(nullable = false, length = 30)
    private String tipo;

    /** Título breve del insight */
    @Column(nullable = false, length = 200)
    private String titulo;

    /** Descripción detallada generada por IA */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    /** Nivel de severidad: 'LOW' | 'MEDIUM' | 'HIGH' */
    @Column(nullable = false, length = 20)
    private String severidad = "MEDIUM";

    /** Nivel de confianza: 'LOW' | 'MEDIUM' | 'HIGH' */
    @Column(nullable = false, length = 20)
    private String confianza = "MEDIUM";

    /**
     * Evidencia estructurada en formato JSON.
     * Contiene los datos cuantitativos que respaldan el insight.
     * 
     * Ejemplo:
     * {
     *   "metricName": "Calidad",
     *   "currentValue": 7.2,
     *   "previousValue": 8.5,
     *   "changePercentage": -15.29,
     *   "historicalAverage": 8.1
     * }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_json", columnDefinition = "jsonb")
    private String evidenceJson;

    /** Recomendación sugerida por IA */
    @Column(columnDefinition = "TEXT")
    private String recomendacion;

    /** Categoría de métrica afectada (Calidad, Productividad, etc.) */
    @Column(name = "categoria_afectada", length = 50)
    private String categoriaAfectada;

    /** Indica si el usuario descartó el insight */
    @Column(nullable = false)
    private Boolean dismissed = false;

    /** Fecha de creación del insight */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Fecha en que fue descartado (si aplica) */
    @Column(name = "dismissed_at")
    private Instant dismissedAt;
}
