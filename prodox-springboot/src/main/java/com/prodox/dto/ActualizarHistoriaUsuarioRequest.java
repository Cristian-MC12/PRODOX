// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActualizarHistoriaUsuarioRequest(
    @NotBlank @Size(max = 200) String titulo,
    String descripcion,
    String criteriosAceptacion
) {}
