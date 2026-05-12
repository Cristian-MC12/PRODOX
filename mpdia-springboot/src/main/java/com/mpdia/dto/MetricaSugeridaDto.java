package com.mpdia.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Métrica sugerida por el Copiloto (Gemini) durante la fase de planeación.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MetricaSugeridaDto(
    @JsonAlias({"nombre", "name"})          String nombre,
    @JsonAlias({"descripcion", "description"}) String descripcion,
    @JsonAlias({"unidad", "unit"})          String unidad,
    @JsonAlias({"valorMeta", "valor_meta", "target", "meta", "value"}) double valorMeta,
    @JsonAlias({"fuente", "source"})        String fuente,
    @JsonAlias({"justificacion", "justification", "rationale"}) String justificacion
) {}
