// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import java.time.Instant;
import java.util.UUID;

public record MetricParametrizacionDto(
    UUID    id,
    UUID    factorId,
    String  factorNombre,
    String  factorCategoria,
    String  userEmail,
    String  objetivo,
    String  procedimiento,
    String  indicadorVariable,
    String  escala,
    UUID    metricaBaseId,
    String  status,           // pendiente | aprobada | rechazada
    String  revisadoPor,
    Instant revisadoAt,
    String  motivoRechazo,
    Instant createdAt
) {}
