// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record CrearProyectoRequest(
    @NotBlank String nombre,
    String descripcion,
    @NotBlank @Pattern(regexp = "scrum|xp") String metodo,
    @NotNull @Min(1) @Max(4) Integer timeBoxSemanas,
    @NotNull @Min(1) @Max(20) Integer numeroSprints,
    @NotNull LocalDate fechaInicio,
    @NotBlank String productGoal
) {}
