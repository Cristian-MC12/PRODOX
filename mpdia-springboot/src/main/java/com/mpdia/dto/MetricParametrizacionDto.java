// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import java.time.Instant;
import java.util.UUID;

public record MetricParametrizacionDto(
    UUID    id,
    Integer version,
    UUID    factorId,
    String  factorNombre,
    String  factorCategoria,
    String  userEmail,
    String  objetivo,
    String  procedimiento,
    String  indicadorVariable,
    String  escala,
    String  frecuenciaCaptura,
    UUID    metricaBaseId,
    String  status,
    String  revisadoPor,
    Instant revisadoAt,
    String  motivoRechazo,
    UUID    proyectoId,
    Instant createdAt,
    String  propuestaIAJson,
    String  configuracionAprobadaJson
) {}
