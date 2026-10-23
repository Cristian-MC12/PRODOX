// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resumen de métricas de un sprint.
 * Incluye promedios por categoría (legacy) y detalle por métrica (V50).
 */
public record SprintMetricsSummaryDto(
    UUID sprintId,
    Integer sprintNumero,
    String sprintGoal,
    String estado,
    LocalDate fechaInicio,
    LocalDate fechaFin,
    Integer duracionDias,
    
    /** 
     * Promedio de métricas por categoría: {"Calidad": 8.5, "Productividad": 7.2, ...}
     * 
     * LIMITACIÓN CONOCIDA: Cuando una categoría contiene métricas con direcciones
     * deseables diferentes (MAYOR_MEJOR vs MENOR_MEJOR) o unidades incompatibles
     * (% vs días), el promedio pierde significado. Usar detalleMetricas para
     * interpretación correcta.
     * 
     * Mantenido por compatibilidad hacia atrás. Se recomienda migrar a detalleMetricas.
     */
    Map<String, BigDecimal> promediosPorCategoria,
    
    /**
     * Detalle de cada variable/métrica con resultados en el sprint.
     * 
     * Incluye dirección deseable, valor objetivo (si aplica), y valores individuales.
     * Permite interpretación correcta de tendencias sin promediar métricas incompatibles.
     * 
     * Lista vacía si no hay evaluaciones disponibles (compatibilidad con sprints antiguos
     * o sprints sin datos).
     * 
     * V50 — Fase 4: Calidad analítica de módulos de IA (Prioridad 1)
     */
    List<MetricaDetalleDto> detalleMetricas,
    
    /** Total de registros de valores en el sprint */
    Integer totalRegistros,
    
    /** Indica si hay datos suficientes para análisis */
    Boolean datosDisponibles
) {}
