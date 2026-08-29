package com.mpdia.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

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
    /** Texto libre legible, conservado para compatibilidad/visualización (ver campos estructurados abajo). */
    String escala,
    String frecuenciaCaptura,
    String fuenteAcademica,
    String formulaAcademica,
    String tipoOperacion,
    String unidadResultado,
    String justificacion,
    /** Identificador técnico snake_case de la variable principal (Fase 16.10-E) */
    String nombreVariable,
    /**
     * Escala estructurada — fuente de verdad funcional (la que usa Ejecución para
     * validar), en vez de depender de interpretar el texto libre `escala`.
     * escalaTipo: NUMERICA_ENTERA | NUMERICA_DECIMAL.
     */
    String escalaTipo,
    BigDecimal escalaMin,
    /** NULL cuando escalaSinLimite=true. */
    BigDecimal escalaMax,
    BigDecimal escalaPaso,
    Boolean escalaSinLimite,
    /** Significado de los valores (ej. "0 = Muy malo; 10 = Excelente"), independiente del texto libre `escala`. */
    String escalaDescripcion
) {}
