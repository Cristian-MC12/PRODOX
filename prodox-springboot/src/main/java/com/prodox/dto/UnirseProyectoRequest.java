// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import jakarta.validation.constraints.NotBlank;

public record UnirseProyectoRequest(
    @NotBlank String codigo
) {}
