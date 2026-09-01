// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import java.util.UUID;

public record MetricaDto(
    UUID   id,
    String codigo,
    String nombre,
    String descripcion,
    String categoria
) {}
