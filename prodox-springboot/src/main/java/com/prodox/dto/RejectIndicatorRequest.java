package com.prodox.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectIndicatorRequest(
    @NotBlank(message = "El motivo de rechazo es obligatorio.") String reason
) {}
