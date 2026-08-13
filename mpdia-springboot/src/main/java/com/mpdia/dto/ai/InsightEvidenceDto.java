// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto.ai;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Evidencia cuantitativa que respalda un insight.
 * Todos los valores numéricos provienen de cálculos determinísticos del backend.
 * 
 * IMPORTANTE: Gemini NO debe calcular ni modificar estos valores.
 */
public record InsightEvidenceDto(
        String metricName,
        BigDecimal currentValue,
        BigDecimal previousValue,
        BigDecimal historicalAverage,
        BigDecimal standardDeviation,
        BigDecimal changePercentage,
        String changeDirection,  // UP | DOWN | STABLE
        Integer numberOfSprints,
        Map<String, BigDecimal> additionalMetrics
) {
    public InsightEvidenceDto {
        if (metricName == null || metricName.isBlank()) {
            throw new IllegalArgumentException("metricName no puede ser nulo o vacío");
        }
    }
}
