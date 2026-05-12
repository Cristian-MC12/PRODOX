package com.mpdia.dto;

import jakarta.validation.constraints.NotBlank;

public record ParametrizacionRequest(
    @NotBlank String factorNombre,
    @NotBlank String factorCategoria,
    @NotBlank String metricaNombre,
    @NotBlank String metricaDescripcion
) {}
