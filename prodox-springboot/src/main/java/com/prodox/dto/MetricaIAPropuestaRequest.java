// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * FASE 15 — "Crear métrica con IA": Paso 1, necesidad en texto libre del
 * Scrum Master (ej. "Quiero medir el estado de ánimo del equipo").
 *
 * No incluye proyectoId: este paso solo genera una propuesta en memoria a
 * partir de texto libre, no crea ni asocia nada a ningún proyecto todavía.
 */
public record MetricaIAPropuestaRequest(
    /** Va directo al prompt de Gemini, nunca se persiste — ampliado a 10000 para soportar descripciones largas. */
    @NotBlank @Size(max = 10000) String necesidad
) {}
