// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto.ai;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO para retrospectivas inteligentes de sprint generadas por IA.
 * 
 * Contiene observaciones y preguntas generadas por Gemini basándose en
 * datos reales del sprint y comparación con sprints anteriores.
 */
public record AIRetrospectiveDto(
        UUID sprintId,
        Integer sprintNumero,
        String sprintGoal,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        List<String> whatWentWell,      // Generado por Gemini basado en datos
        List<String> whatCouldImprove,  // Generado por Gemini basado en datos
        List<String> risks,              // Del sistema + Gemini
        List<String> recommendations,    // Gemini
        List<String> questionsForTeam,  // Gemini
        Instant generatedAt
) {
}
