package com.mpdia.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpdia.dto.MetricaSugeridaDto;
import com.mpdia.entity.Factor;
import com.mpdia.repository.FactorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Servicio del Copiloto para la fase de Planeación.
 * Usa Gemini para generar métricas de productividad ágil
 * basadas en el factor seleccionado por el equipo Scrum.
 */
@Service
@RequiredArgsConstructor
public class CopilotoPlanService {

    private final GeminiService geminiService;
    private final FactorRepository factorRepository;
    private final ObjectMapper objectMapper;

    /**
     * Genera métricas de planeación para un factor usando Gemini.
     * Devuelve una lista de métricas con nombre, descripción, unidad,
     * valor meta recomendado, fuente de datos y justificación.
     */
    public List<MetricaSugeridaDto> generarMetricas(UUID factorId) {
        Factor factor = factorRepository.findById(factorId)
                .orElseThrow(() -> new IllegalArgumentException("Factor no encontrado."));

        String prompt = buildPrompt(factor);
        String rawResponse = geminiService.generate(prompt);
        return parseMetricas(rawResponse, factor);
    }

    private String buildPrompt(Factor factor) {
        return """
            Eres un experto en medición de productividad para equipos Scrum y metodologías ágiles.
            
            El equipo está en la fase de PLANEACIÓN del sprint y necesita definir cómo va a medir
            el siguiente factor de productividad:
            
            Factor: %s
            Categoría: %s
            Descripción: %s
            
            Genera exactamente 2 métricas de productividad ágil para medir este factor durante el sprint.
            Cada métrica debe ser concreta, medible y recopilable desde herramientas como Jira o GitHub.
            
            Responde ÚNICAMENTE con un array JSON válido con EXACTAMENTE este formato, sin texto adicional,
            sin markdown, sin explicaciones fuera del JSON:
            [
              {
                "nombre": "Nombre corto de la métrica",
                "descripcion": "Descripción de cómo se calcula o recopila el dato durante el sprint",
                "unidad": "unidad de medida como porcentaje, puntos, días, número, etc.",
                "valorMeta": 80,
                "fuente": "Jira",
                "justificacion": "Por qué esta métrica es relevante para medir este factor en Scrum"
              }
            ]
            
            IMPORTANTE: valorMeta debe ser un número sin comillas. fuente debe ser exactamente "Jira", "GitHub" o "Manual".
            """.formatted(factor.getName(), factor.getCategory(), factor.getDescription());
    }

    private List<MetricaSugeridaDto> parseMetricas(String rawResponse, Factor factor) {
        try {
            // Log para debug
            System.out.println("=== GEMINI RAW RESPONSE ===");
            System.out.println(rawResponse);
            System.out.println("===========================");

            // Limpiar el response por si Gemini agrega markdown
            String cleaned = rawResponse
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            // Encontrar el array JSON
            int start = cleaned.indexOf('[');
            int end   = cleaned.lastIndexOf(']') + 1;
            if (start >= 0 && end > start) {
                cleaned = cleaned.substring(start, end);
            }

            System.out.println("=== CLEANED JSON ===");
            System.out.println(cleaned);
            System.out.println("====================");

            return objectMapper.readValue(cleaned,
                    new TypeReference<List<MetricaSugeridaDto>>() {});

        } catch (Exception e) {
            System.out.println("=== PARSE ERROR: " + e.getMessage() + " ===");
            return List.of(new MetricaSugeridaDto(
                "Métrica de " + factor.getName(),
                rawResponse.length() > 300 ? rawResponse.substring(0, 300) + "..." : rawResponse,
                "%",
                80.0,
                "Manual",
                "Generado por Copiloto"
            ));
        }
    }
}
