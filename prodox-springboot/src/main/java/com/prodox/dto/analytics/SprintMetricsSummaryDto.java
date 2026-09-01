// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Resumen de métricas de un sprint.
 * Incluye promedios por categoría y metadata del sprint.
 */
public record SprintMetricsSummaryDto(
    UUID sprintId,
    Integer sprintNumero,
    String sprintGoal,
    String estado,
    LocalDate fechaInicio,
    LocalDate fechaFin,
    Integer duracionDias,
    /** Promedio de métricas por categoría: {"Calidad": 8.5, "Productividad": 7.2, ...} */
    Map<String, BigDecimal> promediosPorCategoria,
    /** Total de registros de valores en el sprint */
    Integer totalRegistros,
    /** Indica si hay datos suficientes para análisis */
    Boolean datosDisponibles
) {}
