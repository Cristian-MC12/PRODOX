package com.mpdia.dto;

public record ParametrizacionRequest(
    String factorNombre,
    String factorCategoria,
    String metricaNombre,
    String metricaDescripcion
) {}
