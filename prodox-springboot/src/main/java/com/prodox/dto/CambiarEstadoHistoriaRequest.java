// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CambiarEstadoHistoriaRequest(
    @NotBlank @Pattern(regexp = "pendiente|en_progreso|completada") String estado
) {}
