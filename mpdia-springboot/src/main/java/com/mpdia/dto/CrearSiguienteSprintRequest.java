// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import jakarta.validation.constraints.NotBlank;

public record CrearSiguienteSprintRequest(
    @NotBlank String sprintGoal
) {}
