// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record VerificarParametrizacionRequest(
    @NotNull UUID   parametrizacionId,
    @NotBlank String accion,        // "aprobar" | "rechazar"
    String motivoRechazo            // requerido si accion = "rechazar"
) {}
