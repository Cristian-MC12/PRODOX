// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import java.time.Instant;
import java.util.UUID;

/** Una entrada del top 3 de parametrizaciones más usadas de un factor */
public record TopParametrizacionDto(
    UUID    id,
    String  userEmail,
    String  objetivo,
    String  procedimiento,
    String  indicadorVariable,
    String  escala,
    int     usos,
    Instant createdAt
) {}
