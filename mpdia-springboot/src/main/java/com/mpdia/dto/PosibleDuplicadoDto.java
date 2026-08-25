// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import java.util.List;

/**
 * FASE 23 — candidato a duplicado CONCEPTUAL (no exacto) detectado por
 * MetricaSimilitudService al confirmar "Crear métrica con IA". A diferencia
 * de MetricaDuplicadaEnCatalogoException (nombre normalizado idéntico, que
 * sigue bloqueando la creación sin excepción), esto es una posible
 * coincidencia de SIGNIFICADO que el Scrum Master debe decidir: score y
 * razones existen para que la decisión sea explicable, nunca una caja negra.
 */
public record PosibleDuplicadoDto(
    MetricaDto metrica,
    int score,
    List<String> razones
) {}
