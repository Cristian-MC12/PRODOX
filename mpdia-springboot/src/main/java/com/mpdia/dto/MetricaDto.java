// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import java.util.UUID;

public record MetricaDto(
    UUID   id,
    String codigo,
    String nombre,
    String descripcion,
    String categoria
) {}
