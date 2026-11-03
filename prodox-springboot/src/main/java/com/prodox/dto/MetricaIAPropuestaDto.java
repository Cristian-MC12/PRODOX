// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * FASE 15 — "Crear métrica con IA": propuesta de métrica generada por Gemini
 * a partir de una necesidad en texto libre.
 *
 * Es SOLO una propuesta: no se persiste nada al generarla (ver
 * MetricaIAService.generarPropuesta). Si Gemini no puede determinar
 * razonablemente un campo con la información dada, el valor es exactamente
 * "No determinado" — nunca se inventa un dato para rellenar el campo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MetricaIAPropuestaDto(
    String nombre,
    String descripcion,
    String objetivo,
    String queMide,
    String variablesSugeridas,
    String tipoOperacionSugerido,
    String formulaSugerida,
    String unidadResultado,
    String fuenteSugerida,
    String escalaTipo,
    Double escalaMin,
    Double escalaMax,
    Double escalaPaso,
    Boolean escalaSinLimite,
    String escalaDescripcion
) {
    /** Constructor de compatibilidad para código existente con 9 parámetros. */
    public MetricaIAPropuestaDto(
            String nombre, String descripcion, String objetivo, String queMide,
            String variablesSugeridas, String tipoOperacionSugerido, String formulaSugerida,
            String unidadResultado, String fuenteSugerida) {
        this(nombre, descripcion, objetivo, queMide, variablesSugeridas,
             tipoOperacionSugerido, formulaSugerida, unidadResultado, fuenteSugerida,
             null, null, null, null, null, null);
    }
}
