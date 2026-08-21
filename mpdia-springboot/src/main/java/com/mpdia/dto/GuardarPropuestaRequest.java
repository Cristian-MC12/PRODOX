// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request para guardar una propuesta de parametrización generada por IA.
 * La propuesta queda en estado "propuesta" hasta que el usuario la apruebe.
 * FASE 16.9.4: Incluye campos académicos completos.
 */
public record GuardarPropuestaRequest(
    @NotNull UUID metricaId,
    @NotNull UUID proyectoId,
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
    
    /** Propuesta original de Gemini (para auditoría) */
    String propuestaIAJson,

    /** Identificador técnico snake_case de la variable principal (Fase 16.10-E) */
    String nombreVariable
) {}
