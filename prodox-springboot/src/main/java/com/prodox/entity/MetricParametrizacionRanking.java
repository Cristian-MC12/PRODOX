// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Ranking GLOBAL (sin distinción de proyecto) de popularidad de
 * configuraciones de parametrización equivalentes, por métrica.
 *
 * Distinta de {@link MetricUsoRanking} (flujo legacy por factor, sin
 * cambios) — ver V43__metric_parametrizacion_ranking.sql para el
 * razonamiento completo.
 *
 * {@code usos} representa EXCLUSIVAMENTE la cantidad de selecciones
 * "Usar" guardadas exitosamente desde que esta tabla existe — nunca un
 * conteo derivado de filas históricas de {@link MetricParametrizacion}
 * (una fila ahí no equivale a un uso real: puede venir de datos de
 * prueba, ediciones manuales, reenvíos, etc.).
 *
 * Sin columnas de email/usuario: el autor se obtiene, si se necesita,
 * vía {@code parametrizacionCanonicaId} → {@link MetricParametrizacion}.
 */
@Entity
@Table(name = "metric_parametrizacion_ranking")
@Data
@NoArgsConstructor
public class MetricParametrizacionRanking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "metrica_id", nullable = false)
    private UUID metricaId;

    /** SHA-256 hex (64 caracteres) — ver FingerprintUtil.calcularFingerprint(). */
    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    /**
     * Referencia a la metric_parametrizaciones que disparó la creación de
     * esta entrada de ranking. Nunca se reasigna tras crearse — preserva
     * el autor original aunque otros usuarios reutilicen la configuración.
     */
    @Column(name = "parametrizacion_canonica_id", nullable = false)
    private UUID parametrizacionCanonicaId;

    @Column(nullable = false)
    private Integer usos = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
