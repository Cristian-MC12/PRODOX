package com.prodox.dto;

import java.util.UUID;

public record SprintSelectionDto(
    UUID id,
    UUID factorId,
    String sprintName
) {}
