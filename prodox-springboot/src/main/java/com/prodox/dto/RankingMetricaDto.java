// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import java.util.UUID;

public record RankingMetricaDto(
    UUID   factorId,
    String factorNombre,
    String factorCategoria,
    int    usos,
    UUID   parametrizacionId
) {}
