// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto.ai.gemini;

/**
 * Respuesta de la ejecución de una función para enviar de vuelta a Gemini.
 */
public record FunctionResponse(
    String name,
    Object response
) {}
