// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

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
