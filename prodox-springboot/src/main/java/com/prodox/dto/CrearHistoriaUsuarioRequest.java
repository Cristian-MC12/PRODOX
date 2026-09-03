// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearHistoriaUsuarioRequest(
    @NotBlank @Size(max = 200) String titulo,
    String descripcion,
    String criteriosAceptacion,
    /** Opcional: si se omite, el servicio la fija en "media". */
    @Pattern(regexp = "alta|media|baja") String prioridad
) {}
