// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto.ai;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Evidencia cuantitativa que respalda un insight.
 * Todos los valores numéricos provienen de cálculos determinísticos del backend.
 *
 * IMPORTANTE: Gemini NO debe calcular ni modificar estos valores.
 *
 * FASE 23: los nombres de campo se alinearon a los que ya esperaba
 * ai-insights.component.html / InsightEvidence (modelo Angular) —
 * antes este record usaba nombres en inglés (metricName, currentValue, ...)
 * mientras el resto de los DTOs de analytics (TrendAnalysisDto, RiskDto, etc.)
 * y el frontend usan español (categoria, valorActual, ...), lo que hacía que
 * Jackson serializara claves que el template nunca leía y la tabla de
 * "Evidencia" apareciera vacía en pantalla (causa raíz confirmada en FASE 22).
 */
public record InsightEvidenceDto(
        String categoria,
        BigDecimal valorActual,
        BigDecimal valorAnterior,
        BigDecimal promedioHistorico,
        BigDecimal desviacionEstandar,
        BigDecimal variacionPorcentual,
        String tendencia,  // UP | DOWN | STABLE
        Integer numeroSprints,
        Map<String, BigDecimal> metadata
) {
    public InsightEvidenceDto {
        if (categoria == null || categoria.isBlank()) {
            throw new IllegalArgumentException("categoria no puede ser nula o vacía");
        }
    }
}
