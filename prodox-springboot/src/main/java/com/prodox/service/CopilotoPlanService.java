package com.prodox.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodox.dto.MetricaSugeridaDto;
import com.prodox.entity.Factor;
import com.prodox.repository.FactorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Servicio del Copiloto para la fase de Planeación.
 * Usa Gemini para generar métricas de productividad ágil
 * basadas en el factor seleccionado por el equipo Scrum.
 */
@Slf4j
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
        List<MetricaSugeridaDto> metricas = parseMetricas(rawResponse, factor);
        log.debug("Métricas sugeridas generadas para factorId={}: {} sugerencia(s)", factorId, metricas.size());
        return metricas;
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

            return objectMapper.readValue(cleaned,
                    new TypeReference<List<MetricaSugeridaDto>>() {});

        } catch (Exception e) {
            // Bloque de seguridad Logging/Exposición (H1, Bloque 8A): nunca
            // registrar rawResponse/cleaned (contenido generado por Gemini a
            // partir de datos del Factor) ni e.getMessage() (puede incluir
            // fragmentos del JSON no parseable) — solo el tipo de excepción,
            // mismo patrón que MetricaIAService/MetricaAcademicaService.
            log.warn("No se pudo parsear la respuesta de Gemini para factorId={} (tipo de error: {})",
                    factor.getId(), e.getClass().getSimpleName());
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
