// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

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
    String fuenteSugerida
) {}
