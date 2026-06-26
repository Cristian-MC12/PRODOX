// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RegistroValorDto(
    UUID       id,
    UUID       variableId,
    String     variableNombre,
    UUID       sprintId,
    String     userId,
    BigDecimal valorNum,
    String     valorTexto,
    Boolean    valorBool,
    String     observacion,
    Instant    registradoAt
) {}
