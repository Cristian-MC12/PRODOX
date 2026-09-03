// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import java.time.Instant;
import java.util.UUID;

public record HistoriaUsuarioDto(
    UUID    id,
    UUID    proyectoId,
    UUID    sprintId,
    String  titulo,
    String  descripcion,
    String  criteriosAceptacion,
    /** alta | media | baja */
    String  prioridad,
    /** pendiente | en_progreso | completada */
    String  estado,
    String  creadoPor,
    Instant createdAt,
    Instant updatedAt
) {}
