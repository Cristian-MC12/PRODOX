// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request para crear o actualizar una evaluación del objetivo de un sprint.
 * 
 * FASE 6B: evaluación manual del cumplimiento del objetivo del sprint.
 * Solo el Scrum Master del proyecto puede crear/modificar evaluaciones.
 */
public record CrearEvaluacionObjetivoRequest(
        /**
         * Estado de cumplimiento del objetivo.
         * Valores permitidos: cumplido | parcialmente_cumplido | no_cumplido | no_evaluable
         */
        @NotBlank(message = "El estado es obligatorio")
        @Pattern(regexp = "^(cumplido|parcialmente_cumplido|no_cumplido|no_evaluable)$",
                message = "Estado inválido. Debe ser: cumplido, parcialmente_cumplido, no_cumplido o no_evaluable")
        String estado,

        /**
         * Justificación obligatoria del Scrum Master.
         * Mínimo 10 caracteres, máximo 2000.
         */
        @NotBlank(message = "La justificación es obligatoria")
        @Size(min = 10, max = 2000, message = "La justificación debe tener entre 10 y 2000 caracteres")
        String justificacion,

        /**
         * URLs opcionales de evidencia (demos, documentos, artefactos).
         * Cada URL debe comenzar con http:// o https://
         */
        List<@Pattern(regexp = "^https?://.*", message = "URL inválida, debe comenzar con http:// o https://") String> evidenciaUrls
) {
}
