// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto.ai;

import java.util.List;

/**
 * Resultado de una generación de AI Insights (FASE 23).
 *
 * Antes POST /api/ai/insights/generate/{proyectoId} devolvía solo
 * List&lt;AIInsightDto&gt;, sin forma de distinguir "no había nada que
 * reportar" de "algunos detectores fallaron" de "generación completa" — un
 * fallo parcial de Gemini (try/catch silencioso por generador) se veía
 * exactamente igual que una corrida exitosa con pocos resultados. Este DTO
 * hace explícito el resultado real de la corrida.
 *
 * status:
 *  - COMPLETE: se detectaron señales y todas se generaron (o ya existían,
 *    ver senalesOmitidasPorDuplicado) sin errores.
 *  - PARTIAL: se detectaron señales, al menos una falló, pero al menos una
 *    se generó o ya existía de una corrida anterior.
 *  - FAILED: se detectaron señales pero ninguna pudo generarse (todas
 *    fallaron) — Gemini no respondió para ningún detector.
 *  - SIN_SENALES: no se detectó ninguna señal significativa (no es un
 *    fallo: los datos existen pero no hay tendencias/riesgos/anomalías
 *    relevantes que reportar).
 *  - SIN_DATOS: el proyecto no tiene sprints finalizados todavía.
 */
public record GenerateInsightsResultDto(
        List<AIInsightDto> insights,
        String status,
        int senalesDetectadas,
        int senalesNuevas,
        int senalesOmitidasPorDuplicado,
        List<String> errores
) {}
