// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto.analytics;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Comparación entre dos sprints.
 * Muestra métricas lado a lado con variación absoluta y porcentual.
 */
public record SprintComparisonDto(
    UUID sprint1Id,
    Integer sprint1Numero,
    UUID sprint2Id,
    Integer sprint2Numero,
    /** Métricas del sprint 1 por categoría */
    Map<String, BigDecimal> sprint1Metricas,
    /** Métricas del sprint 2 por categoría */
    Map<String, BigDecimal> sprint2Metricas,
    /** Variación absoluta por categoría (sprint2 - sprint1) */
    Map<String, BigDecimal> variacionAbsoluta,
    /** Variación porcentual por categoría ((sprint2 - sprint1) / sprint1 * 100) */
    Map<String, BigDecimal> variacionPorcentual,
    /** Dirección de tendencia por categoría: UP | DOWN | STABLE */
    Map<String, String> tendencia,
    Boolean datosDisponibles
) {}
