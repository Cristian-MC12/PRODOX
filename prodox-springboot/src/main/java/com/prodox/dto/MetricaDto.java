// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO para métricas del catálogo global.
 * 
 * V50: Añadidos direccionDeseable y valorObjetivo para soporte de 
 * interpretación de tendencias en módulos de IA.
 */
public record MetricaDto(
    UUID   id,
    String codigo,
    String nombre,
    String descripcion,
    String categoria,
    String direccionDeseable,  // MAYOR_MEJOR | MENOR_MEJOR | OBJETIVO | null
    BigDecimal valorObjetivo   // Solo válido cuando direccionDeseable=OBJETIVO
) {}
