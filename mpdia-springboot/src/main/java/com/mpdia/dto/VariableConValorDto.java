// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Variable con su valor actual del sprint (si existe).
 * Fase 16.7: Captura dinámica de variables.
 */
public record VariableConValorDto(
    UUID id,
    String nombre,
    String descripcion,
    String tipoDato,
    Boolean obligatorio,
    String unidad,
    BigDecimal escalaMin,
    BigDecimal escalaMax,
    // Valor actual del sprint (si ya fue registrado)
    BigDecimal valorNum,
    String valorTexto,
    Boolean valorBool,
    /** FASE 16 — frecuencia de captura de la variable (diaria | semanal | por_sprint | ilimitada). */
    String frecuenciaCaptura,
    /** Escala estructurada — NUMERICA_ENTERA | NUMERICA_DECIMAL, NULL = sin estructura. */
    String escalaTipo,
    BigDecimal escalaPaso,
    Boolean escalaSinLimite,
    /** Revisión de captura individual: grupal | individual (Variable.tipoAlcance).
     *  El frontend usa este campo para decidir si un scrum_member (no solo el
     *  Scrum Master) puede ver/usar el formulario de captura de esta variable. */
    String tipoAlcance
) {}
