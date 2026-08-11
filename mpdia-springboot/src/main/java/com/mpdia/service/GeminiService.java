package com.mpdia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpdia.dto.ai.gemini.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio que llama a la API de Google Gemini.
 * 
 * VERSIÓN EXTENDIDA (Fase 3.1):
 * - Mantiene método generate(String) para compatibilidad con código existente
 * - Agrega soporte para function calling / tool use
 * - Soporta conversaciones multi-turn
 * - Soporta system instructions
 */
@Slf4j
@Service
public class GeminiService {

    @Value("${mpdia.gemini.api-key}")
    private String apiKey;

    @Value("${mpdia.gemini.api-url}")
    private String apiUrl;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Llama a Gemini y devuelve el texto generado para el prompt dado.
     * MÉTODO ORIGINAL - Mantiene compatibilidad con código existente.
     */
    public String generate(String prompt) {
        Map<String, Object> body = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(Map.of("text", prompt)))
            )
        );

        String url = apiUrl + "?key=" + apiKey;

        String response;
        try {
            response = restClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        byte[] bytes;
                        try { bytes = res.getBody().readAllBytes(); } catch (Exception ex) { bytes = new byte[0]; }
                        String errorBody = new String(bytes);
                        System.err.println("=== GEMINI HTTP " + res.getStatusCode() + " ===");
                        System.err.println(errorBody);
                        System.err.println("===========================================");
                        throw new RuntimeException("Gemini error " + res.getStatusCode() + ": " + errorBody);
                    })
                    .body(String.class);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("=== GEMINI ERROR ===");
            System.err.println(e.getMessage());
            System.err.println("===================");
            throw new RuntimeException("Error al llamar Gemini: " + e.getMessage());
        }

        try {
            JsonNode root = mapper.readTree(response);
            // Gemini 2.5 puede tener múltiples parts (thinking + response)
            // Buscamos el último part con texto
            JsonNode parts = root.path("candidates").get(0)
                    .path("content").path("parts");
            String text = "";
            for (JsonNode part : parts) {
                if (part.has("text")) {
                    text = part.path("text").asText();
                }
            }
            if (text.isBlank()) {
                throw new RuntimeException("Gemini devolvió texto vacío. Response: " + response);
            }
            return text;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error al procesar respuesta de Gemini: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // NUEVOS MÉTODOS (Fase 3.1) - Function Calling Support
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Llama a Gemini con soporte de function calling / tool use.
     * Soporta conversaciones multi-turn y system instructions.
     * 
     * @param messages Historial de mensajes de la conversación
     * @param tools Lista de herramientas disponibles (puede ser null)
     * @param systemInstruction Instrucción del sistema (puede ser null)
     * @return GeminiResponse con texto o function call
     */
    public GeminiResponse chatWithTools(
            List<Message> messages,
            List<Tool> tools,
            String systemInstruction
    ) {
        Map<String, Object> body = buildRequestBody(messages, tools, systemInstruction);
        String url = apiUrl + "?key=" + apiKey;

        log.debug("Llamando a Gemini con {} mensajes y {} tools", 
                  messages.size(), tools != null ? tools.size() : 0);

        String responseJson;
        try {
            responseJson = restClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        byte[] bytes;
                        try { bytes = res.getBody().readAllBytes(); } catch (Exception ex) { bytes = new byte[0]; }
                        String errorBody = new String(bytes);
                        log.error("Gemini HTTP {}: {}", res.getStatusCode(), errorBody);
                        throw new RuntimeException("Gemini error " + res.getStatusCode() + ": " + errorBody);
                    })
                    .body(String.class);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error al llamar Gemini: {}", e.getMessage());
            throw new RuntimeException("Error al llamar Gemini: " + e.getMessage());
        }

        return parseResponse(responseJson);
    }

    /**
     * Construye el body del request para Gemini API.
     */
    private Map<String, Object> buildRequestBody(
            List<Message> messages,
            List<Tool> tools,
            String systemInstruction
    ) {
        Map<String, Object> body = new HashMap<>();

        // Contents (mensajes de la conversación)
        List<Map<String, Object>> contents = new ArrayList<>();
        for (Message msg : messages) {
            Map<String, Object> content = new HashMap<>();
            content.put("role", msg.role());
            
            List<Map<String, Object>> parts = new ArrayList<>();
            for (Part part : msg.parts()) {
                Map<String, Object> partMap = new HashMap<>();
                if (part.text() != null) {
                    partMap.put("text", part.text());
                } else if (part.functionCall() != null) {
                    Map<String, Object> fcMap = new HashMap<>();
                    fcMap.put("name", part.functionCall().name());
                    fcMap.put("args", part.functionCall().args());
                    partMap.put("functionCall", fcMap);
                } else if (part.functionResponse() != null) {
                    Map<String, Object> frMap = new HashMap<>();
                    frMap.put("name", part.functionResponse().name());
                    frMap.put("response", part.functionResponse().response());
                    partMap.put("functionResponse", frMap);
                }
                parts.add(partMap);
            }
            content.put("parts", parts);
            contents.add(content);
        }
        body.put("contents", contents);

        // System Instruction (opcional)
        if (systemInstruction != null && !systemInstruction.isBlank()) {
            body.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", systemInstruction))
            ));
        }

        // Tools (opcional)
        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> toolsList = new ArrayList<>();
            for (Tool tool : tools) {
                Map<String, Object> toolMap = new HashMap<>();
                List<Map<String, Object>> functionDeclarations = new ArrayList<>();
                
                for (FunctionDeclaration func : tool.functionDeclarations()) {
                    Map<String, Object> funcMap = new HashMap<>();
                    funcMap.put("name", func.name());
                    funcMap.put("description", func.description());
                    funcMap.put("parameters", func.parameters());
                    functionDeclarations.add(funcMap);
                }
                
                toolMap.put("functionDeclarations", functionDeclarations);
                toolsList.add(toolMap);
            }
            body.put("tools", toolsList);
        }

        return body;
    }

    /**
     * Parsea la respuesta de Gemini y determina si es texto o function call.
     */
    private GeminiResponse parseResponse(String responseJson) {
        try {
            JsonNode root = mapper.readTree(responseJson);
            JsonNode candidates = root.path("candidates");
            
            if (candidates.isEmpty() || !candidates.isArray()) {
                throw new RuntimeException("Respuesta de Gemini sin candidates");
            }

            JsonNode firstCandidate = candidates.get(0);
            JsonNode content = firstCandidate.path("content");
            JsonNode parts = content.path("parts");

            if (parts.isEmpty() || !parts.isArray()) {
                throw new RuntimeException("Respuesta de Gemini sin parts");
            }

            // Buscar function call o texto
            for (JsonNode part : parts) {
                // Si contiene function call
                if (part.has("functionCall")) {
                    JsonNode fc = part.get("functionCall");
                    String functionName = fc.path("name").asText();
                    Map<String, Object> args = mapper.convertValue(
                        fc.path("args"),
                        mapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class)
                    );
                    
                    log.debug("Gemini solicitó function call: {}", functionName);
                    return GeminiResponse.functionCall(new FunctionCall(functionName, args));
                }
                
                // Si contiene texto
                if (part.has("text")) {
                    String text = part.path("text").asText();
                    if (!text.isBlank()) {
                        log.debug("Gemini retornó texto de {} caracteres", text.length());
                        return GeminiResponse.text(text);
                    }
                }
            }

            throw new RuntimeException("Respuesta de Gemini sin texto ni function call");
            
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error al parsear respuesta de Gemini: {}", e.getMessage());
            throw new RuntimeException("Error al procesar respuesta de Gemini: " + e.getMessage());
        }
    }
}
