// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record EvaluacionSprintDto(
    UUID       sprintId,
    Integer    sprintNumero,
    UUID       variableId,
    String     variableNombre,
    String     categoria,
    String     tipoAlcance,
    BigDecimal promedio,
    BigDecimal min,
    BigDecimal max,
    Integer    totalRegistros
) {}
