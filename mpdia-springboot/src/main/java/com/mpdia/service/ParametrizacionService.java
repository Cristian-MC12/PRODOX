package com.mpdia.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpdia.dto.ParametrizacionRequest;
import com.mpdia.dto.PropuestaParametrizacionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Usa Gemini para generar 3 propuestas de parametrización
 * para una métrica seleccionada por el equipo Scrum.
 * Cada propuesta incluye: objetivo, procedimiento/fórmula,
 * indicador y variables, escala de medición.
 */
@Service
@RequiredArgsConstructor
public class ParametrizacionService {

    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    /**
     * Genera UNA propuesta de parametrización usando Gemini.
     * 
     * FASE 16.5: Evolucionado de "3 propuestas" a "asistente que propone 1 parametrización".
     * La IA actúa como asistente experto, no como generador de opciones múltiples.
     * 
     * Retorna List con 1 elemento para mantener compatibilidad con frontend.
     * En caso de error con Gemini, retorna propuesta genérica (nunca falla).
     */
    public List<PropuestaParametrizacionDto> generarPropuestas(ParametrizacionRequest request) {
        String prompt = buildPrompt(request);
        try {
            String raw = geminiService.generate(prompt);
            return parsePropuestas(raw, request);
        } catch (Exception e) {
            System.err.println("=== ERROR GEMINI ===");
            System.err.println(e.getMessage());
            System.err.println("===================");
            return fallbackPropuestas(request);
        }
    }

    private List<PropuestaParametrizacionDto> fallbackPropuestas(ParametrizacionRequest r) {
        return List.of(
            new PropuestaParametrizacionDto(
                "Medición directa por sprint",
                "Medir " + r.metricaNombre() + " de forma directa y práctica durante el sprint.",
                "Al cierre del sprint, el Scrum Master o equipo recopila el valor observado según criterios definidos por el equipo.",
                r.metricaNombre() + " (valor registrado según ocurrencia durante el sprint)",
                "Escala numérica (definir rango según contexto del equipo)",
                "PROPUESTA de IA generada automáticamente. Requiere validación y ajuste del equipo según su contexto específico. Esta es una parametrización genérica que debe adaptarse."
            )
        );
    }

    private String buildPrompt(ParametrizacionRequest r) {
        return """
            Eres un asistente experto en métricas de productividad para equipos Scrum y metodologías ágiles.
            
            Tu objetivo es AYUDAR al equipo a parametrizar la siguiente métrica:
            
            Factor:      %s (Categoría: %s)
            Métrica:     %s
            Descripción: %s
            
            Genera UNA propuesta estructurada de parametrización basándote en:
            - La definición de la métrica
            - Buenas prácticas de Scrum/Agile
            - Simplicidad y practicidad para el equipo
            
            REGLAS IMPORTANTES:
            1. NO inventes datos, resultados históricos o benchmarks
            2. NO afirmes que una escala es "oficial" sin fundamento
            3. Si algo no está definido claramente, propón algo razonable pero indica que es una PROPUESTA
            4. Prioriza la SIMPLICIDAD sobre la complejidad
            5. La parametrización debe ser PRÁCTICA y APLICABLE
            6. Diferencia claramente entre información existente y propuesta
            
            Responde ÚNICAMENTE con un array JSON con UN objeto (para compatibilidad), 
            sin texto adicional, sin markdown, sin explicaciones fuera del JSON:
            [
              {
                "titulo": "Nombre descriptivo de la parametrización (máx 8 palabras)",
                "objetivo": "Qué se quiere lograr midiendo esta métrica en el sprint",
                "procedimiento": "Fórmula o procedimiento paso a paso claro y específico para medir",
                "indicadorVariable": "Indicador principal y variables necesarias para el cálculo",
                "escala": "Escala de medición: numérica, porcentual, ordinal, etc. con rango específico",
                "justificacion": "Por qué esta propuesta es adecuada para equipos Scrum (menciona que es una PROPUESTA que requiere validación del equipo)"
              }
            ]
            
            IMPORTANTE: Genera EXACTAMENTE 1 objeto en el array. Todos los campos son obligatorios.
            La justificación debe dejar claro que es una propuesta de IA que requiere validación humana.
            """.formatted(
                r.factorNombre(), r.factorCategoria(),
                r.metricaNombre(), r.metricaDescripcion()
            );
    }

    private List<PropuestaParametrizacionDto> parsePropuestas(String raw, ParametrizacionRequest r) {
        try {
            String cleaned = raw
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();
            int start = cleaned.indexOf('[');
            int end   = cleaned.lastIndexOf(']') + 1;
            if (start >= 0 && end > start) {
                cleaned = cleaned.substring(start, end);
            }
            return objectMapper.readValue(cleaned,
                    new TypeReference<List<PropuestaParametrizacionDto>>() {});
        } catch (Exception e) {
            // Fallback con una propuesta genérica
            return List.of(new PropuestaParametrizacionDto(
                "Medición directa de " + r.metricaNombre(),
                "Medir " + r.metricaNombre() + " durante el sprint.",
                "Recopilar el valor al cierre del sprint y comparar con el objetivo.",
                r.metricaNombre() + " (variable principal)",
                "Escala numérica de 0 a 100",
                "Propuesta generada automáticamente."
            ));
        }
    }
}
