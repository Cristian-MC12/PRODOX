// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto.analytics;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Representa una anomalía detectada en las métricas.
 * Una anomalía es un valor significativamente alejado del promedio histórico.
 */
public record AnomalyDto(
    UUID sprintId,
    Integer sprintNumero,
    String categoria,
    String variableNombre,
    BigDecimal valorActual,
    BigDecimal promedioHistorico,
    BigDecimal desviacionEstandar,
    BigDecimal numDesviaciones, // |valor - promedio| / stdDev
    String severidad, // LOW | MEDIUM | HIGH | CRITICAL
    String direccion, // ABOVE | BELOW
    String mensaje
) {}
