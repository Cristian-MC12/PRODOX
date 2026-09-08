// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request para calcular una métrica.
 * Fase 16.8: Motor de cálculo.
 *
 * proyectoId/sprintId nulos hacían fallar CalculoMetricaService con un 500
 * (findById(null) de Spring Data JPA, nunca capturado como IllegalArgumentException)
 * en vez de un 400 claro — @NotNull corrige eso sin cambiar ningún caso válido.
 */
public record CalcularMetricaRequest(
    @NotNull UUID proyectoId,
    @NotNull UUID sprintId
) {}
