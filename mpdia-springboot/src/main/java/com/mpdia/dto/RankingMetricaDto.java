// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import java.util.UUID;

public record RankingMetricaDto(
    UUID   factorId,
    String factorNombre,
    String factorCategoria,
    int    usos,
    UUID   parametrizacionId
) {}
