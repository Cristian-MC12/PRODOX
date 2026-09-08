package com.prodox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request para generar una propuesta de parametrización vía Gemini
 * (ParametrizacionController.generarPropuestas). Los 4 campos se usan
 * directamente para construir el prompt y el texto de fallback (ver
 * ParametrizacionService, ej. "Medir " + metricaNombre + " ..."): un valor
 * null/blank hoy produce texto roto ("Medir null de forma directa...").
 *
 * Límites: factorNombre/factorCategoria no tienen columna con longitud
 * explícita en Factor (@Column sin "length" — default JPA/Hibernate para
 * String es 255). metricaNombre coincide con Metrica.nombre
 * (@Column(length = 120)). metricaDescripcion no tiene columna con límite
 * fijo (Metrica.descripcion es TEXT) pero es texto de prompt puro — mismo
 * criterio que ChatRequest.message (@Size(max = 4000)).
 */
public record ParametrizacionRequest(
    @NotBlank @Size(max = 255) String factorNombre,
    @NotBlank @Size(max = 255) String factorCategoria,
    @NotBlank @Size(max = 120) String metricaNombre,
    @NotBlank @Size(max = 4000) String metricaDescripcion
) {}
