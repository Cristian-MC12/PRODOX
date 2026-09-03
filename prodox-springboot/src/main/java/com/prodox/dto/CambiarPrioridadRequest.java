// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CambiarPrioridadRequest(
    @NotBlank @Pattern(regexp = "alta|media|baja") String prioridad
) {}
