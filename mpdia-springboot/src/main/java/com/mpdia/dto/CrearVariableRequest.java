// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record CrearVariableRequest(
    @NotNull  UUID       metricaId,
    @NotBlank String     nombre,
    String               descripcion,
    @Pattern(regexp = "calidad|productividad|cumplimiento|flexibilidad|sociohumano") String tipoIndicador,
    @Pattern(regexp = "grupal|individual")  String tipoAlcance,
    @Pattern(regexp = "diaria|semanal|por_sprint") String frecuencia,
    @Pattern(regexp = "unico|multiple")    String cardinalidad,
    @Pattern(regexp = "numerico|texto|booleano|escala") String tipoDato,
    BigDecimal           escalaMin,
    BigDecimal           escalaMax
) {}
