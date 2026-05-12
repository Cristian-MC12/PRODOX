package com.mpdia.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GenerateIndicatorsRequest(
    @NotNull UUID factorId
) {}
