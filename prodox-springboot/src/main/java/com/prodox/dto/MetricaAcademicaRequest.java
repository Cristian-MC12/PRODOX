// Autor: Cristian Santiago Martinez Cordoba — PRODOX
// Fase 16.9.1: Request para generar propuesta de métrica académica
package com.prodox.dto;

import java.util.UUID;

/**
 * Request para generar propuesta de parametrización de métrica académica.
 * Contiene la información de la fuente académica y la definición formal.
 */
public record MetricaAcademicaRequest(
    UUID proyectoId,
    UUID metricaId,
    String codigoMetrica,
    String nombreMetrica,
    String definicion,
    String fuenteAcademica,
    String formulaAcademica,
    String tipoOperacion,
    String unidadResultado,
    String frecuencia
) {
    public MetricaAcademicaRequest {
        if (proyectoId == null) {
            throw new IllegalArgumentException("proyectoId es obligatorio");
        }
        if (metricaId == null) {
            throw new IllegalArgumentException("metricaId es obligatorio");
        }
        if (formulaAcademica == null || formulaAcademica.isBlank()) {
            throw new IllegalArgumentException("formulaAcademica es obligatoria");
        }
        if (tipoOperacion == null || tipoOperacion.isBlank()) {
            throw new IllegalArgumentException("tipoOperacion es obligatorio");
        }
    }
}
