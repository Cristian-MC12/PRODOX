// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.ai.AgentResponse;
import com.mpdia.dto.ai.gemini.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Servicio que orquesta la interacción con el AI Agent (Gemini).
 * Maneja el flujo de function calling / tool use.
 * 
 * FASE 3.1 - Implementación básica:
 * - Procesa un mensaje del usuario
 * - Gestiona tool calls
 * - Retorna respuesta final
 * 
 * NO implementado todavía (FASE 3.2+):
 * - Validación de autorización (se hace en CopilotToolsService)
 * - Gestión de historial completo
 * - Guardado en base de datos
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIAgentService {

    private final GeminiService geminiService;

    @org.springframework.beans.factory.annotation.Value("${mpdia.ai.max-tool-iterations:5}")
    private int maxToolIterations;

    /**
     * Procesa un mensaje del usuario utilizando tools disponibles.
     * 
     * @param userMessage Mensaje del usuario
     * @param availableTools Lista de tools disponibles para la IA
     * @param systemInstruction Instrucción del sistema
     * @param toolExecutor Función para ejecutar tools
     * @return AgentResponse con la respuesta final
     */
    public AgentResponse processMessage(
            String userMessage,
            List<Tool> availableTools,
            String systemInstruction,
            ToolExecutor toolExecutor
    ) {
        log.info("Procesando mensaje del usuario con {} tools disponibles", 
                 availableTools != null ? availableTools.size() : 0);

        List<Message> conversationHistory = new ArrayList<>();
        List<String> toolsUsed = new ArrayList<>();

        // 1. Agregar mensaje del usuario
        conversationHistory.add(Message.user(userMessage));

        // 2. Iniciar conversación con la IA
        int iteration = 0;

        while (iteration < maxToolIterations) {
            iteration++;
            log.debug("Iteración {} de conversación con IA", iteration);

            // Llamar a Gemini
            GeminiResponse response = geminiService.chatWithTools(
                conversationHistory,
                availableTools,
                systemInstruction
            );

            // 3. Procesar respuesta
            if (response.isTextResponse()) {
                // IA retornó texto final
                log.info("IA retornó respuesta final después de {} iteraciones", iteration);
                return new AgentResponse(
                    response.text(),
                    toolsUsed,
                    true
                );
            }

            if (response.isFunctionCall()) {
                // IA solicitó una tool
                FunctionCall functionCall = response.functionCall();
                String toolName = functionCall.name();
                
                log.info("IA solicitó tool: {}", toolName);
                toolsUsed.add(toolName);

                // 4. Agregar function call al historial
                conversationHistory.add(Message.modelFunctionCall(functionCall));

                // 5. Ejecutar tool
                Object toolResult;
                try {
                    toolResult = toolExecutor.execute(toolName, functionCall.args());
                    log.debug("Tool {} ejecutada exitosamente", toolName);
                } catch (Exception e) {
                    log.error("Error al ejecutar tool {}: {}", toolName, e.getMessage());
                    
                    // Devolver error a la IA para que pueda informar al usuario
                    toolResult = Map.of(
                        "error", true,
                        "message", e.getMessage()
                    );
                }

                // 6. Agregar resultado de tool al historial
                conversationHistory.add(Message.functionResult(
                    new FunctionResponse(toolName, toolResult)
                ));

                // 7. Continuar el loop para que la IA procese el resultado
                continue;
            }

            // Si llegamos aquí, algo salió mal
            throw new RuntimeException("Respuesta de IA sin texto ni function call");
        }

        // Si llegamos al límite de iteraciones
        log.warn("Se alcanzó el límite de iteraciones ({}) sin respuesta final", maxToolIterations);
        return new AgentResponse(
            "Lo siento, no pude procesar tu solicitud completamente.",
            toolsUsed,
            false
        );
    }

    /**
     * Versión simplificada sin tools (para pruebas o casos simples).
     */
    public AgentResponse processSimpleMessage(String userMessage, String systemInstruction) {
        log.info("Procesando mensaje simple sin tools");
        
        List<Message> messages = List.of(Message.user(userMessage));
        
        GeminiResponse response = geminiService.chatWithTools(
            messages,
            null, // sin tools
            systemInstruction
        );

        if (response.isTextResponse()) {
            return new AgentResponse(response.text(), List.of(), true);
        }

        throw new RuntimeException("Respuesta inesperada de IA");
    }
}
