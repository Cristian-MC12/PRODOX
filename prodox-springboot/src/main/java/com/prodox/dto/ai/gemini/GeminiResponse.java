// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto.ai.gemini;

/**
 * Respuesta procesada de Gemini.
 * Puede contener texto o una llamada a función.
 */
public record GeminiResponse(
    String text,
    FunctionCall functionCall,
    Boolean isTextResponse,
    Boolean isFunctionCall
) {
    public static GeminiResponse text(String text) {
        return new GeminiResponse(text, null, true, false);
    }
    
    public static GeminiResponse functionCall(FunctionCall call) {
        return new GeminiResponse(null, call, false, true);
    }
}
