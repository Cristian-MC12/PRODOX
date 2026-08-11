// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.dto.ai.gemini;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Mensaje en una conversación con Gemini.
 * Roles: "user", "model", "function"
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Message(
    String role,
    List<Part> parts
) {
    public static Message user(String text) {
        return new Message("user", List.of(Part.text(text)));
    }
    
    public static Message model(String text) {
        return new Message("model", List.of(Part.text(text)));
    }
    
    public static Message modelFunctionCall(FunctionCall call) {
        return new Message("model", List.of(Part.functionCall(call)));
    }
    
    public static Message functionResult(FunctionResponse response) {
        return new Message("function", List.of(Part.functionResponse(response)));
    }
}
