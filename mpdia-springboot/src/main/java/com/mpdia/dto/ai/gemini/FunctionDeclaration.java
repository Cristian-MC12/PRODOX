// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto.ai.gemini;

import java.util.Map;

/**
 * Declaración de una función/tool disponible para Gemini.
 */
public record FunctionDeclaration(
    String name,
    String description,
    Map<String, Object> parameters
) {}
