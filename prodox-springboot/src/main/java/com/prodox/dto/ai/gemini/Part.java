// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.dto.ai.gemini;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Parte de un mensaje en Gemini.
 * Puede contener texto, function call o function response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Part(
    String text,
    FunctionCall functionCall,
    FunctionResponse functionResponse
) {
    public static Part text(String text) {
        return new Part(text, null, null);
    }
    
    public static Part functionCall(FunctionCall call) {
        return new Part(null, call, null);
    }
    
    public static Part functionResponse(FunctionResponse response) {
        return new Part(null, null, response);
    }
}
