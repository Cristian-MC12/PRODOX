package com.mpdia.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateIndicatorRequest(
    @NotNull UUID factorId,
    @NotNull Double value,
    String unit
) {}
