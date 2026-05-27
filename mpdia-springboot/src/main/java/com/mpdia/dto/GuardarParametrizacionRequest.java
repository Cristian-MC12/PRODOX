// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GuardarParametrizacionRequest(
    @NotNull  UUID   factorId,
    @NotBlank String objetivo,
    @NotBlank String procedimiento,
    @NotBlank String indicadorVariable,
    @NotBlank String escala,
    /** ID de la parametrización base que se usó como plantilla (null si es nueva) */
    UUID metricaBaseId
) {}
