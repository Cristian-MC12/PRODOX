// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO para respuesta de cálculo de métrica.
 * Fase 16.8: Motor de cálculo.
 */
public record ResultadoMetricaDto(
    UUID resultadoId,
    UUID metricaId,
    String metricaNombre,
    UUID proyectoId,
    UUID sprintId,
    UUID parametrizacionId,
    Integer parametrizacionVersion,
    String tipoCalculo,
    String expresion,
    String valoresUtilizados,
    BigDecimal resultado,
    String unidad,
    String estado,
    String mensajeError,
    Instant calculadoAt
) {}
