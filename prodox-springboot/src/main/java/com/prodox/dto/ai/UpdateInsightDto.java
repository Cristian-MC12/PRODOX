// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto.ai;

import jakarta.validation.constraints.Size;

/**
 * DTO para actualizar campos editables de un insight.
 * Solo permite modificar title, description y recommendation.
 */
public record UpdateInsightDto(
    /** Coincide con AIInsight.titulo (@Column(length = 200)) — evita un 409 por violación de columna. */
    @Size(max = 200) String title,
    /** AIInsight.descripcion es TEXT sin límite en BD: sin @Size. */
    String description,
    /** AIInsight.recomendacion es TEXT sin límite en BD: sin @Size. */
    String recommendation
) {}
