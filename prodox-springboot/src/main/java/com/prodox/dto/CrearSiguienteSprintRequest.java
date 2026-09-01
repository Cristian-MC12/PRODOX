// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import jakarta.validation.constraints.NotBlank;

public record CrearSiguienteSprintRequest(
    @NotBlank String sprintGoal
) {}
