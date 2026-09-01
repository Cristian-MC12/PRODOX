// Autor: Cristian Santiago Martinez Cordoba — PRODOX
// Fase 16.9.1: Request para ejecutar métrica académica
package com.prodox.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Request para ejecutar una métrica académica en un sprint específico.
 * Los valores son un mapa donde la clave es el nombre de la variable
 * y el valor es el dato capturado.
 */
public record EjecutarMetricaAcademicaRequest(
    UUID proyectoId,
    UUID sprintId,
    Map<String, Object> valores
) {
    public EjecutarMetricaAcademicaRequest {
        if (proyectoId == null) {
            throw new IllegalArgumentException("proyectoId es obligatorio");
        }
        if (sprintId == null) {
            throw new IllegalArgumentException("sprintId es obligatorio");
        }
        if (valores == null || valores.isEmpty()) {
            throw new IllegalArgumentException("valores son obligatorios");
        }
    }
}
