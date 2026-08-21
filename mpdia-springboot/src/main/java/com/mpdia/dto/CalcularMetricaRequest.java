// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import java.util.UUID;

/**
 * Request para calcular una métrica.
 * Fase 16.8: Motor de cálculo.
 */
public record CalcularMetricaRequest(
    UUID proyectoId,
    UUID sprintId
) {}
