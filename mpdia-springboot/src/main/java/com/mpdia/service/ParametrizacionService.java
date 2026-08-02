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
                "Conteo directo por sprint",
                "Medir la cantidad total de " + r.metricaNombre() + " registrados durante el sprint.",
                "Al cierre del sprint, el Scrum Master recopila y suma el valor de todos los miembros.",
                r.metricaNombre() + " = suma de ocurrencias registradas en el sprint",
                "Numérica entera ≥ 0",
                "Propuesta estándar de medición directa."
            ),
            new PropuestaParametrizacionDto(
                "Promedio por iteración",
                "Calcular el promedio de " + r.metricaNombre() + " a lo largo de los sprints.",
                "Suma de valores individuales dividido por el número de participantes.",
                r.metricaNombre() + " promedio = Σ valores / n participantes",
                "Decimal con 2 cifras, rango 0-100",
                "Permite comparar tendencias entre sprints."
            ),
            new PropuestaParametrizacionDto(
                "Escala de valoración",
                "Evaluar " + r.metricaNombre() + " en una escala ordinal acordada por el equipo.",
                "Cada miembro asigna un valor al finalizar el sprint según criterios definidos.",
                r.metricaNombre() + " = valoración subjetiva del equipo",
                "Escala ordinal 1-5 (1=muy bajo, 5=muy alto)",
                "Útil para métricas cualitativas o de percepción."
            )
        );
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
