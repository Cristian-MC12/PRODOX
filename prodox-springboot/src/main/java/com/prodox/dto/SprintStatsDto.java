// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Estadísticas de una variable dentro de un sprint específico (para comparación entre sprints). */
public record SprintStatsDto(
    UUID       sprintId,
    Integer    sprintNumero,
    Integer    totalRegistros,
    BigDecimal promedio,
    BigDecimal minimo,
    BigDecimal maximo
) {}
