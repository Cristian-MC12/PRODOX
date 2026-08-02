// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VariableDto(
    UUID       id,
    UUID       proyectoId,
    UUID       metricaId,
    String     metricaNombre,
    String     metricaCategoria,
    String     nombre,
    String     descripcion,
    String     tipoIndicador,
    String     tipoAlcance,
    String     frecuencia,
    String     cardinalidad,
    String     tipoDato,
    BigDecimal escalaMin,
    BigDecimal escalaMax,
    Boolean    activa,
    Instant    createdAt,
    /** Texto legible de la fórmula, ej: "ISE = Crítico×5 + Mayor×1 + Medio×4 + Menor×6" */
    String     formulaTexto,
    /**
     * JSON estructurado de la fórmula.
     * Estructura: { "expresion": "...", "operandos": [...], "escalaResultado": "..." }
     * Cada operando: { "clave": "Critico", "etiqueta": "Errores Críticos", "tipo": "numerico", "pesoFactor": 5 }
     */
    String     formulaJson,
    /** Frecuencia de captura: por_sprint | semanal | diaria | ilimitada */
    String     frecuenciaCaptura,
    /** Objetivo de la métrica (de la parametrización) */
    String     objetivo,
    /** Procedimiento para medir (de la parametrización) */
    String     procedimiento,
    /** Definición de escala (de la parametrización) */
    String     escalaDefinicion
) {}
