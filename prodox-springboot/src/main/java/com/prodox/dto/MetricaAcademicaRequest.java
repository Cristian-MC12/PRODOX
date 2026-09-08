// Autor: Cristian Santiago Martinez Cordoba — PRODOX
// Fase 16.9.1: Request para generar propuesta de métrica académica
package com.prodox.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request para generar propuesta de parametrización de métrica académica.
 * Contiene la información de la fuente académica y la definición formal.
 *
 * Límites @Size elegidos para coincidir con las columnas que estos valores
 * terminan poblando: Metrica.codigo length=20, Metrica.nombre length=120,
 * MetricParametrizacion.formulaAcademica length=500,
 * tipoOperacion/frecuenciaCaptura length=20, unidadResultado length=50.
 * definicion/fuenteAcademica no tienen columna con límite fijo (persisten
 * como TEXT), pero SÍ viajan como contexto directo al prompt de Gemini en
 * generarPropuestaAcademica() — se acotan con el mismo criterio ya usado en
 * ChatRequest.message (@Size(max = 4000)) para evitar abuso de costo/latencia.
 */
public record MetricaAcademicaRequest(
    UUID proyectoId,
    UUID metricaId,
    @Size(max = 20) String codigoMetrica,
    @Size(max = 120) String nombreMetrica,
    @Size(max = 4000) String definicion,
    @Size(max = 4000) String fuenteAcademica,
    @Size(max = 500) String formulaAcademica,
    @Size(max = 20) String tipoOperacion,
    @Size(max = 50) String unidadResultado,
    @Size(max = 20) String frecuencia
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
