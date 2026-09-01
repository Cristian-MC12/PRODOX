// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

/**
 * Resumen persistente (consultado en BD, no en memoria de sesión) de cuántas
 * parametrizaciones de un proyecto están pendientes/aprobadas/rechazadas.
 * FASE 10: usado por VerificacionComponent para que sus contadores reflejen
 * el estado real al entrar o recargar la pantalla (ver diagnóstico FASE 9, bloque 4).
 */
public record ResumenVerificacionDto(
    long pendientes,
    long aprobadas,
    long rechazadas
) {}
