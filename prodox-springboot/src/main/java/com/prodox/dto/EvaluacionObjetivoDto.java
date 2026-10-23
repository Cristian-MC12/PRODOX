// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO para la evaluación del objetivo de un sprint.
 * 
 * Representa la evaluación manual del Scrum Master sobre si el objetivo
 * del sprint se cumplió, proporcionando justificación y evidencia opcional.
 * 
 * FASE 6B: evaluación manual sin criterios cuantitativos automáticos.
 */
public record EvaluacionObjetivoDto(
        UUID id,
        UUID sprintId,
        /**
         * Estado de cumplimiento: cumplido | parcialmente_cumplido | no_cumplido | no_evaluable
         */
        String estado,
        /**
         * Justificación obligatoria del Scrum Master
         */
        String justificacion,
        /**
         * URLs opcionales de evidencia (demos, documentos, artefactos)
         */
        List<String> evidenciaUrls,
        /**
         * Usuario que creó la evaluación (Scrum Master)
         */
        String evaluadoPor,
        /**
         * Fecha y hora de creación
         */
        Instant evaluadoAt,
        /**
         * Usuario que modificó la evaluación (null si nunca se modificó)
         */
        String actualizadoPor,
        /**
         * Fecha y hora de última modificación (null si nunca se modificó)
         */
        Instant actualizadoAt
) {
}
