// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request para enviar un mensaje al AI Copilot.
 */
public record ChatRequest(
    @NotBlank(message = "El mensaje no puede estar vacío")
    @Size(max = 4000, message = "El mensaje no puede exceder 4000 caracteres")
    String message,
    
    @NotNull(message = "El proyectoId es requerido")
    UUID proyectoId,
    
    UUID sprintId // opcional
) {}
