// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;

public record CrearProyectoRequest(
    @NotBlank String nombre,
    String descripcion,
    @NotBlank @Pattern(regexp = "scrum|xp") String metodo,
    /** V41 — timebox de la iteración/Sprint. */
    @NotBlank @Pattern(regexp = "HORAS|DIAS|SEMANAS") String timeboxUnidad,
    @NotNull @Min(1) Integer timeboxDuracion,
    /** Requerida solo cuando timeboxUnidad="HORAS" — no se anota @NotNull acá
     *  porque su obligatoriedad depende del valor de otro campo; se valida
     *  en ProyectoService.validarTimebox(). */
    LocalTime horaInicio,
    @NotNull @Min(1) @Max(20) Integer numeroSprints,
    @NotNull LocalDate fechaInicio,
    @NotBlank String productGoal
) {}
