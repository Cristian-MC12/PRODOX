package com.mpdia.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO para propuestas de parametrización generadas por Gemini.
 * FASE 16.9.4: Incluye frecuenciaCaptura y campos académicos.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PropuestaParametrizacionDto(
    String titulo,
    String objetivo,
    String procedimiento,
    String indicadorVariable,
    String escala,
    String frecuenciaCaptura,
    String fuenteAcademica,
    String formulaAcademica,
    String tipoOperacion,
    String unidadResultado,
    String justificacion,
    /** Identificador técnico snake_case de la variable principal (Fase 16.10-E) */
    String nombreVariable
) {}
