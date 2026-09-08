// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto;

import jakarta.validation.constraints.Size;

/**
 * Request para actualizar la fórmula y frecuencia de captura de una variable.
 * Enviado desde la pantalla de Ejecución cuando el usuario configura cómo se mide.
 */
public record ActualizarFormulaRequest(
    /**
     * Texto legible de la fórmula, ej: "ISE = Crítico×5 + Mayor×1 + Medio×4 + Menor×6".
     * Variable.formulaTexto es columnDefinition="TEXT": sin límite en BD, sin @Size.
     */
    String formulaTexto,
    /**
     * JSON estructurado con expresión y operandos.
     * Ej: {"expresion":"ISE","operandos":[{"clave":"Critico","etiqueta":"Errores Críticos","tipo":"numerico","pesoFactor":5}],"escalaResultado":">=0"}
     * Variable.formulaJson es columnDefinition="jsonb": sin límite en BD, sin @Size.
     */
    String formulaJson,
    /** por_sprint | semanal | diaria | ilimitada — coincide con Variable.frecuenciaCaptura (@Column(length = 20)). */
    @Size(max = 20) String frecuenciaCaptura
) {}
