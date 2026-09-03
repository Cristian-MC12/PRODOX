// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto.ai;

/**
 * DTO para actualizar campos editables de un insight.
 * Solo permite modificar title, description y recommendation.
 */
public record UpdateInsightDto(
    String title,
    String description,
    String recommendation
) {}
