// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto.ai.gemini;

import java.util.Map;

/**
 * Representa una llamada a función solicitada por Gemini.
 */
public record FunctionCall(
    String name,
    Map<String, Object> args
) {}
