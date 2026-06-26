// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VariableDto(
    UUID       id,
    UUID       proyectoId,
    UUID       metricaId,
    String     metricaNombre,
    String     metricaCategoria,
    String     nombre,
    String     descripcion,
    String     tipoIndicador,
    String     tipoAlcance,
    String     frecuencia,
    String     cardinalidad,
    String     tipoDato,
    BigDecimal escalaMin,
    BigDecimal escalaMax,
    Boolean    activa,
    Instant    createdAt
) {}
