// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto.analytics;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Resumen general de un proyecto con todas sus métricas agregadas.
 */
public record ProjectOverviewDto(
    UUID proyectoId,
    String proyectoNombre,
    Integer totalSprints,
    Integer sprintsFinalizados,
    Integer sprintActualNumero,
    /** Promedio de métricas por categoría a lo largo de todos los sprints */
    Map<String, BigDecimal> promedioHistorico,
    /** Sprint con mejor desempeño general */
    SprintPerformance mejorSprint,
    /** Sprint con peor desempeño general */
    SprintPerformance peorSprint,
    Boolean datosDisponibles
) {
    public record SprintPerformance(
        Integer numero,
        BigDecimal scoreGeneral,
        String razon
    ) {}
}
