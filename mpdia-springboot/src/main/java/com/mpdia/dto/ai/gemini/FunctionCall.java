// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto.ai.gemini;

import java.util.Map;

/**
 * Representa una llamada a función solicitada por Gemini.
 */
public record FunctionCall(
    String name,
    Map<String, Object> args
) {}
