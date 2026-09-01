// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto.ai;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO para representar un insight generado por IA.
 * Incluye evidencia estructurada, explicación y recomendaciones.
 */
public record AIInsightDto(
        UUID id,
        UUID proyectoId,
        UUID sprintId,
        String type,         // TREND | ANOMALY | RISK | COMPARISON
        String severity,     // LOW | MEDIUM | HIGH
        String title,
        String description,
        List<InsightEvidenceDto> evidence,
        String recommendation,
        String confidence,   // LOW | MEDIUM | HIGH
        Boolean dismissed,
        Instant createdAt
) {
}
