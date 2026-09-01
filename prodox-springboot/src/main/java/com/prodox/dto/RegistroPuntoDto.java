// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Un punto individual de la evolución de una variable: un registro real, sin agregar. */
public record RegistroPuntoDto(
    UUID       id,
    BigDecimal valor,
    Instant    registradoAt,
    UUID       sprintId,
    Integer    sprintNumero,
    String     userId
) {}
