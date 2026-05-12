package com.mpdia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Servicio que llama a la API de Google Gemini para generar
 * métricas de productividad ágil basadas en un factor dado.
 */
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
     */
    public String generate(String prompt) {
        Map<String, Object> body = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(Map.of("text", prompt)))
            )
        );

        String url = apiUrl + "?key=" + apiKey;

        String response = restClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(String.class);

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
            return text;
        } catch (Exception e) {
            throw new RuntimeException("Error al procesar respuesta de Gemini: " + e.getMessage());
        }
    }
}
