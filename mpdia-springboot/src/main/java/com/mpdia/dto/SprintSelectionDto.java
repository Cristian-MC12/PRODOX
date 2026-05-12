package com.mpdia.dto;

import java.util.UUID;

public record SprintSelectionDto(
    UUID id,
    UUID factorId,
    String sprintName
) {}
