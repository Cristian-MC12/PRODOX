// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
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

    /**
     * Revisión de captura por parametrización: "EQUIPO" | "SCRUM_MASTER".
     * Opcional — si se omite, ParametrizacionService la trata como
     * "SCRUM_MASTER" (comportamiento previo, sin cambios para quien no elige
     * explícitamente). Independiente de tipoOperacion.
     */
    String responsableCaptura,

    /** Propuesta original de Gemini (para auditoría) */
    String propuestaIAJson,

    /** Identificador técnico snake_case de la variable principal (Fase 16.10-E) */
    String nombreVariable,

    /** Escala estructurada — ver ParametrizacionService.validarEscalaEstructurada(). */
    String escalaTipo,
    BigDecimal escalaMin,
    BigDecimal escalaMax,
    BigDecimal escalaPaso,
    Boolean escalaSinLimite,
    String escalaDescripcion
) {
    /**
     * Constructor de compatibilidad: firma previa a la incorporación de
     * responsableCaptura (Revisión de captura por parametrización). Delega en
     * el constructor canónico con responsableCaptura=null (ParametrizacionService
     * lo resuelve a "SCRUM_MASTER" — mismo comportamiento que antes de este campo).
     */
    public GuardarPropuestaRequest(
        UUID metricaId, UUID proyectoId, String objetivo, String procedimiento,
        String indicadorVariable, String escala, String frecuenciaCaptura,
        String fuenteAcademica, String formulaAcademica, String tipoOperacion, String unidadResultado,
        String propuestaIAJson, String nombreVariable,
        String escalaTipo, BigDecimal escalaMin, BigDecimal escalaMax, BigDecimal escalaPaso,
        Boolean escalaSinLimite, String escalaDescripcion
    ) {
        this(metricaId, proyectoId, objetivo, procedimiento, indicadorVariable, escala, frecuenciaCaptura,
            fuenteAcademica, formulaAcademica, tipoOperacion, unidadResultado, null,
            propuestaIAJson, nombreVariable, escalaTipo, escalaMin, escalaMax, escalaPaso,
            escalaSinLimite, escalaDescripcion);
    }
}
