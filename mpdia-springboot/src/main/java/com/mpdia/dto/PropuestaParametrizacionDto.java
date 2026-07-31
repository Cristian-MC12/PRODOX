package com.mpdia.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PropuestaParametrizacionDto(
    String titulo,
    String objetivo,
    String procedimiento,
    String indicadorVariable,
    String escala,
    String justificacion
) {}
