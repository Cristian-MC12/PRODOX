// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import java.util.List;
import java.util.UUID;

/**
 * Respuesta con variables de una métrica y su parametrización.
 * Fase 16.7: Captura dinámica de variables.
 */
public record VariablesMetricaResponse(
    UUID parametrizacionId,
    Integer version,
    String status,
    List<VariableConValorDto> variables
) {}
