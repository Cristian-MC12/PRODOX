// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

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

    /**
     * Revisión de captura por parametrización: "EQUIPO" | "SCRUM_MASTER".
     * Opcional — si se omite, se conserva el valor ya guardado en la
     * propuesta (o "SCRUM_MASTER" si nunca se definió). Independiente de
     * tipoOperacion: decide QUIÉN captura, no CÓMO se calcula.
     */
    String responsableCaptura,

    /** Identificador técnico snake_case de la variable principal (Fase 16.10-E) */
    String nombreVariable,

    /** Escala estructurada — ver GuardarParametrizacionRequest/ParametrizacionService.validarEscalaEstructurada(). */
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
     * conserva el valor ya guardado en la propuesta, o "SCRUM_MASTER" si nunca
     * se definió — mismo comportamiento que antes de este campo).
     */
    public AprobarParametrizacionRequest(
        String objetivo, String procedimiento, String indicadorVariable, String escala,
        String frecuenciaCaptura, String fuenteAcademica, String formulaAcademica,
        String tipoOperacion, String unidadResultado, String nombreVariable,
        String escalaTipo, BigDecimal escalaMin, BigDecimal escalaMax, BigDecimal escalaPaso,
        Boolean escalaSinLimite, String escalaDescripcion
    ) {
        this(objetivo, procedimiento, indicadorVariable, escala, frecuenciaCaptura,
            fuenteAcademica, formulaAcademica, tipoOperacion, unidadResultado, null, nombreVariable,
            escalaTipo, escalaMin, escalaMax, escalaPaso, escalaSinLimite, escalaDescripcion);
    }
}
