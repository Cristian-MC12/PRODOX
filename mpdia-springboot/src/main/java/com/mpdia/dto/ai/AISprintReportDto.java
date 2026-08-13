// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO para reportes ejecutivos de sprint generados por IA.
 * 
 * Combina datos objetivos del sprint (métricas, fechas) con interpretación
 * y análisis generado por Gemini basado en datos reales.
 */
public record AISprintReportDto(
        UUID sprintId,
        Integer sprintNumero,
        String sprintGoal,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        String resumenEjecutivo,        // Generado por Gemini
        Map<String, BigDecimal> metricas,
        List<String> highlights,         // Generado por Gemini
        List<String> concerns,           // Generado por Gemini
        List<AIInsightDto> insights,
        String recomendaciones,          // Generado por Gemini
        Instant generatedAt
) {
}
