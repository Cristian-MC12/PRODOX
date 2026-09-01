// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectMemberDto(
    UUID    proyectoId,
    String  userId,
    String  userEmail,
    String  rol,
    Instant joinedAt
) {}
