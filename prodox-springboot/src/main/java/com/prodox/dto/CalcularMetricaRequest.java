// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import java.util.UUID;

/**
 * Request para calcular una métrica.
 * Fase 16.8: Motor de cálculo.
 */
public record CalcularMetricaRequest(
    UUID proyectoId,
    UUID sprintId
) {}
