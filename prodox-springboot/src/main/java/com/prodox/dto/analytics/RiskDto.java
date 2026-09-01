// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto.analytics;

import java.time.Instant;
import java.util.UUID;

/**
 * Representa un riesgo detectado automáticamente por el análisis de métricas.
 * NO incluye interpretación de IA, solo señales objetivas.
 */
public record RiskDto(
    UUID proyectoId,
    String tipo, // DECLINING_PRODUCTIVITY | INCREASING_DEFECTS | FAILING_GOALS | etc.
    String severidad, // LOW | MEDIUM | HIGH | CRITICAL
    String titulo,
    String evidencia, // Descripción objetiva del problema detectado
    String categoriaAfectada,
    Instant detectedAt
) {}
