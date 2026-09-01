// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import java.util.UUID;

/**
 * FASE 15 — resultado de confirmar una propuesta ("Usar esta propuesta"):
 * la Metrica ya fue creada en el catálogo global y seleccionada
 * (ProyectoMetrica) para el proyecto indicado, mediante el flujo existente
 * (PlaneacionService.seleccionar). NO implica aprobación: sigue pendiente de
 * parametrización → verificación → aprobación, exactamente igual que
 * cualquier otra métrica del catálogo.
 */
public record MetricaIACreadaDto(
    UUID metricaId,
    String codigo,
    String nombre,
    UUID proyectoId
) {}
