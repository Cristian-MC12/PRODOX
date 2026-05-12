package com.mpdia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CopilotConfigDto(
    UUID id,
    String userId,
    @NotBlank String tool,
    @NotBlank String url,
    @NotBlank @Size(min = 8) String apiKey,
    @NotBlank String frequency,
    @NotNull Boolean active,
    Instant lastSyncAt
) {}
