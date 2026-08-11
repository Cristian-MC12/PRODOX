// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto.analytics;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Análisis de tendencias de una métrica a lo largo de varios sprints.
 */
public record TrendAnalysisDto(
    UUID proyectoId,
    String categoria,
    Integer numeroSprints,
    List<SprintDataPoint> dataPoints,
    BigDecimal promedioGeneral,
    BigDecimal desviacionEstandar,
    String tendenciaGeneral, // UP | DOWN | STABLE
    BigDecimal variacionTotal, // % cambio entre primer y último sprint
    Boolean datosDisponibles
) {
    public record SprintDataPoint(
        Integer sprintNumero,
        BigDecimal valor,
        String fecha
    ) {}
}
