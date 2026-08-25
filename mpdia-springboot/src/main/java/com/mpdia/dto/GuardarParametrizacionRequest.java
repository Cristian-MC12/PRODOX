// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
    String frecuenciaCaptura
) {}
