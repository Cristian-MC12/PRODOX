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

    public List<PropuestaParametrizacionDto> generarPropuestas(ParametrizacionRequest request) {
        String prompt = buildPrompt(request);
        String raw    = geminiService.generate(prompt);
        return parsePropuestas(raw, request);
    }

    private String buildPrompt(ParametrizacionRequest r) {
        return """
            Eres un experto en métricas de productividad para equipos Scrum y metodologías ágiles.
            
            El equipo Scrum necesita parametrizar la siguiente métrica de medición de productividad:
            
            Factor:      %s (Categoría: %s)
            Métrica:     %s
            Descripción: %s
            
            Genera EXACTAMENTE 3 propuestas diferentes de parametrización para esta métrica.
            Cada propuesta debe ser una forma distinta y válida de medir este atributo en un sprint.
            
            Responde ÚNICAMENTE con un array JSON válido con EXACTAMENTE este formato,
            sin texto adicional, sin markdown, sin explicaciones fuera del JSON:
            [
              {
                "titulo": "Nombre corto de la propuesta (máx 8 palabras)",
                "objetivo": "Qué se quiere lograr midiendo esta métrica en el sprint",
                "procedimiento": "Fórmula o procedimiento paso a paso para calcular el valor",
                "indicadorVariable": "Indicador principal y variables que intervienen en el cálculo",
                "escala": "Escala de medición: numérica, porcentual, ordinal, etc. con rango",
                "justificacion": "Por qué esta propuesta es adecuada para equipos Scrum"
              }
            ]
            
            IMPORTANTE: genera exactamente 3 objetos en el array. Todos los campos son obligatorios.
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
