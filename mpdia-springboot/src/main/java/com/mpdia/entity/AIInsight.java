// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/**
 * Insight generado automáticamente por el AI Copilot.
 * Identifica riesgos, problemas o recomendaciones basadas en análisis de métricas.
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

    /** Tipo de insight: 'RIESGO' | 'MEJORA' | 'ALERTA' | 'RECOMENDACION' */
    @Column(nullable = false, length = 30)
    private String tipo;

    /** Título breve del insight */
    @Column(nullable = false, length = 200)
    private String titulo;

    /** Descripción detallada */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    /** Nivel de impacto: 'BAJO' | 'MEDIO' | 'ALTO' | 'CRITICO' */
    @Column(nullable = false, length = 20)
    private String impacto;

    /** Nivel de confianza: 'BAJA' | 'MEDIA' | 'ALTA' */
    @Column(nullable = false, length = 20)
    private String confianza;

    /** Recomendación sugerida */
    @Column(columnDefinition = "TEXT")
    private String recomendacion;

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
