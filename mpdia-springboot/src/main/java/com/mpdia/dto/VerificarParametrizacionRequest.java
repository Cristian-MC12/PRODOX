// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record VerificarParametrizacionRequest(
    @NotNull UUID   parametrizacionId,
    @NotBlank String accion,        // "aprobar" | "rechazar"
    String motivoRechazo            // requerido si accion = "rechazar"
) {}
