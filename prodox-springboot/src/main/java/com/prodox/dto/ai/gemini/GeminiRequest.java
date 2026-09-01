// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto.ai.gemini;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Request completo a Gemini API con soporte para function calling.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeminiRequest(
    List<Message> contents,
    Map<String, Object> systemInstruction,
    List<Tool> tools
) {}
