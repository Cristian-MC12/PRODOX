package com.mpdia.dto;

import java.time.Instant;
import java.util.UUID;

public record IndicatorDto(
    UUID id,
    UUID factorId,
    String factorName,
    String factorCategory,
    Double value,
    String unit,
    Instant measuredAt,
    String status,
    String approvedBy,
    Instant approvedAt,
    String rejectedBy,
    Instant rejectedAt,
    String rejectionReason
) {}
