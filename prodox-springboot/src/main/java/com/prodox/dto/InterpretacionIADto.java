// Autor: Cristian Santiago Martinez Cordoba — PRODOX
// Fase 16.9.1: DTO para interpretación IA de resultados
package com.prodox.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO que contiene la interpretación generada por IA para un resultado.
 * La IA NO calcula, solo interpreta resultados ya calculados.
 */
public record InterpretacionIADto(
    UUID resultadoId,
    String metricaNombre,
    BigDecimal resultado,
    String unidad,
    String interpretacion,
    Instant generadoAt
) {}
