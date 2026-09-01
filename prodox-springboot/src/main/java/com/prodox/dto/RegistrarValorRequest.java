// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record RegistrarValorRequest(
    @NotNull UUID       variableId,
    @NotNull UUID       sprintId,
    BigDecimal          valorNum,
    String              valorTexto,
    Boolean             valorBool,
    String              observacion
) {}
