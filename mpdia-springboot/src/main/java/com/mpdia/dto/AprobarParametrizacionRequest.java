// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request para aprobar formalmente una parametrización.
 * 
 * La configuración aprobada se conserva en un snapshot JSON
 * para garantizar reproducibilidad de cálculos históricos.
 * FASE 16.9.4: Incluye campos académicos completos.
 */
public record AprobarParametrizacionRequest(
    @NotBlank String objetivo,
    @NotBlank String procedimiento,
    @NotBlank String indicadorVariable,
    @NotBlank String escala,
    String frecuenciaCaptura,
    
    /** Campos académicos */
    String fuenteAcademica,
    String formulaAcademica,
    String tipoOperacion,
    String unidadResultado,

    /** Identificador técnico snake_case de la variable principal (Fase 16.10-E) */
    String nombreVariable
) {}
