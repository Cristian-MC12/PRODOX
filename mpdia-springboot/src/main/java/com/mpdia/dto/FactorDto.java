package com.mpdia.dto;

import java.util.UUID;

public record FactorDto(
    UUID id,
    String name,
    String description,
    String category
) {}
