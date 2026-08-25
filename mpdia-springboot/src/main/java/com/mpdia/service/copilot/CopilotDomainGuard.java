// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service.copilot;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * FASE 12.2 — Guardrail de dominio del AI Agile Copilot.
 *
 * Determina, de forma completamente local y determinística (sin llamar a ningún modelo,
 * ni siquiera uno "clasificador" barato), si un mensaje del usuario pertenece al dominio
 * MPDIA/Agile/Proyecto/Sprint/Métricas/Mejora continua. Se usa ANTES de construir historial,
 * prompt o invocar al LLM (ver AICopilotService.chat()), para que preguntas ajenas al dominio
 * ("cuánto es 2 + 2") nunca lleguen a Gemini.
 *
 * Reglas (en este orden, y el orden importa para la "regla de prioridad"):
 * 1. Si el mensaje normalizado contiene una señal clara de contenido ajeno (aritmética,
 *    geografía, entretenimiento, deportes/actualidad, tarea escolar general), se rechaza
 *    SIEMPRE, incluso si también menciona una palabra del dominio MPDIA (ej. "cuéntame un
 *    chiste sobre el sprint" sigue siendo fuera de dominio).
 * 2. Si contiene un término explícito del dominio MPDIA, se acepta.
 * 3. Si es corto (hasta 6 palabras) y no cayó en la regla 1, se acepta: el Copilot solo vive
 *    dentro del contexto de un proyecto/sprint activo, así que preguntas ambiguas y breves
 *    ("¿cómo vamos?", "¿qué pasó?") se asumen relativas a ese contexto.
 * 4. En cualquier otro caso, se rechaza.
 */
@Component
public class CopilotDomainGuard {

    public static final String RESPUESTA_FUERA_DE_DOMINIO =
        "Soy el AI Agile Copilot de MPDIA. Puedo ayudarte con el sprint, métricas, riesgos, " +
        "problemas, impedimentos, tendencias y retrospectivas del proyecto activo.";

    /** Máximo de palabras para que un mensaje ambiguo se considere dentro de dominio por contexto. */
    private static final int AMBIGUOUS_SHORT_MESSAGE_MAX_WORDS = 6;

    /**
     * Señales claras de contenido ajeno al dominio. Se evalúan primero y tienen prioridad
     * sobre cualquier término del dominio presente en el mismo mensaje.
     */
    private static final List<Pattern> OFF_DOMAIN_PATTERNS = List.of(
        // Aritmética / matemáticas generales (símbolo u operador escrito, entre dos números)
        Pattern.compile("\\b\\d+\\s*[+x*/-]\\s*\\d+\\b"),
        Pattern.compile("\\b\\d+\\s+(mas|menos|por|entre|dividido)\\s+\\d+\\b"),
        // FASE 12.9 — la misma aritmética pero con números escritos en palabras ("dos por
        // dos", "tres mas uno"): sin esto, un mensaje corto y sin términos de dominio caía en
        // la regla 3 (mensaje ambiguo corto → se asume dentro de dominio) y llegaba a Gemini.
        Pattern.compile("\\b(cero|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)\\s+" +
                "(mas|menos|por|entre|dividido)\\s+" +
                "(cero|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)\\b"),
        Pattern.compile("\\b(resuelve|resolver)\\s+(esta\\s+|este\\s+)?(ecuacion|ecuaciones|operacion|operaciones)\\b"),
        Pattern.compile("\\bexplicame\\s+(algebra|geometria|calculo|trigonometria|fisica|quimica|historia|biologia|matematicas)\\b"),
        Pattern.compile("\\btarea\\s+de\\s+matematicas\\b"),
        Pattern.compile("\\bayudame\\s+con\\s+mi\\s+tarea\\b"),
        // Geografía / conocimiento general
        Pattern.compile("\\bcapital\\s+de\\s+\\w+"),
        Pattern.compile("\\bdonde\\s+esta\\s+(japon|francia|espana|alemania|italia|mexico|argentina|brasil|china|rusia)\\b"),
        // Entretenimiento
        Pattern.compile("\\bchiste\\b"),
        Pattern.compile("\\bpoema\\b"),
        Pattern.compile("\\bcancion\\b"),
        // Deportes / actualidad general
        Pattern.compile("\\bpartido\\b"),
        Pattern.compile("\\bfutbol\\b"),
        Pattern.compile("\\bquien\\s+gano\\b")
    );

    /**
     * Términos explícitos del dominio MPDIA/Agile. Lista centralizada: agregar aquí cualquier
     * término nuevo relacionado con métricas/categorías/fases del producto.
     */
    private static final List<String> DOMAIN_TERMS = List.of(
        "proyecto", "sprint", "sprint activo", "metrica", "metricas",
        "defecto", "defectos", "deuda tecnica", "impedimento", "impedimentos",
        "problema", "problemas", "riesgo", "riesgos", "equipo",
        "retrospectiva", "retrospectivas", "mejora", "mejorar", "siguiente sprint",
        "tendencia", "tendencias", "rendimiento", "resultado", "resultados",
        "planificacion", "planeacion", "ejecucion", "evaluacion",
        "scrum", "scrum master", "backlog", "story points",
        "fat", "aprendizaje organizacional", "capacidad",
        "objetivo del sprint", "estado del sprint"
    );

    /**
     * @param message mensaje crudo del usuario (nunca se modifica el original; esta clase
     *                solo usa una copia normalizada internamente para clasificar).
     * @return true si el mensaje pertenece al dominio MPDIA (o es ambiguo/corto dentro de
     *         contexto) y por lo tanto puede continuar hacia el LLM.
     */
    public boolean isInDomain(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }

        String normalized = normalize(message);

        // Regla de prioridad: una señal clara de contenido ajeno bloquea siempre,
        // sin importar si el mensaje también contiene una palabra del dominio.
        for (Pattern pattern : OFF_DOMAIN_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                return false;
            }
        }

        for (String term : DOMAIN_TERMS) {
            if (normalized.contains(term)) {
                return true;
            }
        }

        int wordCount = normalized.split("\\s+").length;
        return wordCount > 0 && wordCount <= AMBIGUOUS_SHORT_MESSAGE_MAX_WORDS;
    }

    /** Respuesta local (sin LLM) para mensajes fuera de dominio. */
    public String respuestaFueraDeDominio() {
        return RESPUESTA_FUERA_DE_DOMINIO;
    }

    /**
     * Normaliza únicamente para clasificación: minúsculas, sin tildes, sin signos de
     * puntuación, espacios colapsados. El mensaje original que se envía al LLM nunca se
     * toca — esta normalización es de uso exclusivamente interno.
     */
    private String normalize(String message) {
        String lower = message.toLowerCase(Locale.forLanguageTag("es"));
        String withoutAccents = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String withoutPunctuation = withoutAccents.replaceAll("[¿?¡!.,;:\"']", " ");
        return withoutPunctuation.replaceAll("\\s+", " ").trim();
    }
}
