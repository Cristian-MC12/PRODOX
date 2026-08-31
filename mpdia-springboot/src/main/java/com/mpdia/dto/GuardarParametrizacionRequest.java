// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record GuardarParametrizacionRequest(
    UUID   factorId,
    @NotBlank String objetivo,
    @NotBlank String procedimiento,
    @NotBlank String indicadorVariable,
    @NotBlank String escala,
    UUID metricaBaseId,
    UUID proyectoId,
    UUID metricaId,
    // FASE 11: campos académicos opcionales — sin ellos, MetricaAcademicaService no puede
    // calcular la métrica aprobada por este flujo (ver diagnóstico FASE 10, bloque E2E).
    String tipoOperacion,
    String formulaAcademica,
    String unidadResultado,
    String fuenteAcademica,
    /**
     * Revisión de frecuencia de captura: faltaba en este DTO, por lo que
     * MetricRankingService.guardarPorMetrica() nunca podía propagarla —
     * la entidad quedaba siempre en su default ("por_sprint") sin importar
     * lo que el usuario eligiera en Planeación. Opcional por compatibilidad:
     * null se interpreta como "por_sprint", igual que el resto del sistema.
     */
    String frecuenciaCaptura,
    /**
     * Revisión de captura por parametrización: "EQUIPO" | "SCRUM_MASTER".
     * Opcional — si se omite, MetricRankingService la trata como
     * "SCRUM_MASTER" (comportamiento previo). Independiente de tipoOperacion.
     */
    String responsableCaptura,
    /**
     * Escala estructurada (corrección del manejo de escalas): opcional para
     * compatibilidad, pero si escalaTipo viene informado se valida
     * completa — ver ParametrizacionService.validarEscalaEstructurada().
     */
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
     * el constructor canónico con responsableCaptura=null (MetricRankingService
     * lo resuelve a "SCRUM_MASTER" — mismo comportamiento que antes de este campo).
     */
    public GuardarParametrizacionRequest(
        UUID factorId, String objetivo, String procedimiento, String indicadorVariable, String escala,
        UUID metricaBaseId, UUID proyectoId, UUID metricaId,
        String tipoOperacion, String formulaAcademica, String unidadResultado, String fuenteAcademica,
        String frecuenciaCaptura,
        String escalaTipo, BigDecimal escalaMin, BigDecimal escalaMax, BigDecimal escalaPaso,
        Boolean escalaSinLimite, String escalaDescripcion
    ) {
        this(factorId, objetivo, procedimiento, indicadorVariable, escala, metricaBaseId, proyectoId, metricaId,
            tipoOperacion, formulaAcademica, unidadResultado, fuenteAcademica, frecuenciaCaptura, null,
            escalaTipo, escalaMin, escalaMax, escalaPaso, escalaSinLimite, escalaDescripcion);
    }
}
