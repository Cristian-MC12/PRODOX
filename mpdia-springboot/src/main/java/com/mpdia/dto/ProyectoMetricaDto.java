// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import java.time.Instant;
import java.util.UUID;

public record ProyectoMetricaDto(
    UUID    metricaId,
    String  codigo,
    String  nombre,
    String  descripcion,
    String  categoria,
    String  factor,
    boolean seleccionada,
    boolean aprobada,
    String  aprobadaPor,
    Instant aprobadaAt,
    /** true si ya tiene variable generada */
    boolean tieneVariable
) {}
