// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto;

/**
 * Request para actualizar la fórmula y frecuencia de captura de una variable.
 * Enviado desde la pantalla de Ejecución cuando el usuario configura cómo se mide.
 */
public record ActualizarFormulaRequest(
    /** Texto legible de la fórmula, ej: "ISE = Crítico×5 + Mayor×1 + Medio×4 + Menor×6" */
    String formulaTexto,
    /**
     * JSON estructurado con expresión y operandos.
     * Ej: {"expresion":"ISE","operandos":[{"clave":"Critico","etiqueta":"Errores Críticos","tipo":"numerico","pesoFactor":5}],"escalaResultado":">=0"}
     */
    String formulaJson,
    /** por_sprint | semanal | diaria | ilimitada */
    String frecuenciaCaptura
) {}
