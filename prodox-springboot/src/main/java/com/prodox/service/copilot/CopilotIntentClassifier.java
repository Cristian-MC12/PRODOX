// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service.copilot;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FASE 12.4/12.5 — Clasificador local de intención del AI Agile Copilot.
 *
 * Corre DESPUÉS de CopilotDomainGuard (que ya garantizó que el mensaje pertenece al dominio
 * PRODOX) y ANTES de cualquier acceso a BD o al LLM. Es determinístico, sin llamadas externas,
 * sin acceso a base de datos y sin dependencias de Spring — solo clasifica texto.
 *
 * Intenciones Tipo A (FASE 12.4, ver diagnóstico FASE 12.3) — se resuelven 100% localmente,
 * 0 llamadas a Gemini:
 * - RESULTADO_METRICA: pregunta por el resultado de una métrica concreta ("¿cuánto dio
 *   Defectos?"). Devuelve el nombre candidato tal como aparece en el mensaje — la resolución
 *   contra las métricas reales del proyecto (y el rechazo si es ambiguo) ocurre en
 *   AICopilotService, que sí tiene acceso a BD.
 * - RESULTADO_ULTIMO_SPRINT: pregunta por el resumen del último sprint.
 *
 * Intenciones Tipo B (FASE 12.5) — los datos se calculan de forma determinística en
 * AgileAnalyticsService y solo se envían a Gemini para redactar la interpretación en
 * lenguaje natural (máximo 1 llamada a GeminiService.generate(), sin tools, sin AIAgentService):
 * - COMPARACION_SPRINTS: pregunta si se mejoró/empeoró respecto al sprint anterior, o pide
 *   comparar el sprint actual con el anterior.
 * - TENDENCIAS: pregunta qué métricas mejoraron/empeoraron o cómo vienen las métricas.
 * - RIESGOS: pregunta por riesgos detectados en el proyecto.
 *
 * Intenciones Tipo C (FASE 12.6) — mismo principio (datos deterministas primero, máximo 1
 * GeminiService.generate(), sin AIAgentService, sin tools), pero para preguntas más abiertas
 * de recomendación/retrospectiva. Deliberadamente separadas, nunca mezcladas:
 * - RECOMENDACIONES: pide sugerencias/acciones para mejorar o para el próximo sprint.
 * - RETROSPECTIVA: pide qué revisar/discutir en la retrospectiva del equipo.
 *
 * Cualquier mensaje que no calce con un patrón reconocido devuelve UNKNOWN y debe continuar
 * por el flujo actual (historial + prompt + tools + Gemini) sin cambios — "es preferible
 * mandar una pregunta a Gemini innecesariamente antes que responder incorrectamente con datos
 * determinísticos" (regla fundamental de FASE 12.4, vigente también en FASE 12.5/12.6).
 */
public class CopilotIntentClassifier {

    public enum IntentType {
        RESULTADO_METRICA, RESULTADO_ULTIMO_SPRINT, COMPARACION_SPRINTS, TENDENCIAS, RIESGOS,
        RECOMENDACIONES, RETROSPECTIVA, UNKNOWN
    }

    public record ClassifiedIntent(IntentType type, String metricaCandidata) {
        public static ClassifiedIntent unknown() {
            return new ClassifiedIntent(IntentType.UNKNOWN, null);
        }

        public static ClassifiedIntent resultadoMetrica(String candidata) {
            return new ClassifiedIntent(IntentType.RESULTADO_METRICA, candidata);
        }

        public static ClassifiedIntent resultadoUltimoSprint() {
            return new ClassifiedIntent(IntentType.RESULTADO_ULTIMO_SPRINT, null);
        }

        public static ClassifiedIntent comparacionSprints() {
            return new ClassifiedIntent(IntentType.COMPARACION_SPRINTS, null);
        }

        public static ClassifiedIntent tendencias() {
            return new ClassifiedIntent(IntentType.TENDENCIAS, null);
        }

        public static ClassifiedIntent riesgos() {
            return new ClassifiedIntent(IntentType.RIESGOS, null);
        }

        public static ClassifiedIntent recomendaciones() {
            return new ClassifiedIntent(IntentType.RECOMENDACIONES, null);
        }

        public static ClassifiedIntent retrospectiva() {
            return new ClassifiedIntent(IntentType.RETROSPECTIVA, null);
        }
    }

    /** Se evalúan ANTES que los patrones de métrica, para que "resultado del último sprint"
     *  nunca se confunda con "resultado de <métrica>". */
    private static final List<Pattern> ULTIMO_SPRINT_PATTERNS = List.of(
        Pattern.compile(".*resultado del ultimo sprint.*"),
        Pattern.compile(".*(como salieron|como estuvieron) las metricas del ultimo sprint.*"),
        Pattern.compile(".*metricas del ultimo sprint.*")
    );

    /** FASE 12.5 — se evalúan ANTES que RESULTADO_METRICA (no capturan candidata alguna). */
    private static final List<Pattern> COMPARACION_SPRINTS_PATTERNS = List.of(
        Pattern.compile(".*(mejoramos|empeoramos).*sprint anterior.*"),
        Pattern.compile(".*como nos fue.*sprint anterior.*"),
        Pattern.compile(".*compara.*sprint.*anterior.*")
    );

    private static final List<Pattern> TENDENCIAS_PATTERNS = List.of(
        Pattern.compile(".*que metricas.*(mejorar|mejoraron|han mejorado).*"),
        Pattern.compile(".*que metricas.*(empeorar|empeoraron|han empeorado).*"),
        Pattern.compile(".*como vienen las metricas.*")
    );

    private static final List<Pattern> RIESGOS_PATTERNS = List.of(
        Pattern.compile(".*que riesgos (detectas|tenemos|hay|existen).*"),
        Pattern.compile(".*hay riesgos.*")
    );

    /** FASE 12.6 — deliberadamente separado de RETROSPECTIVA: nunca deben mezclarse. */
    private static final List<Pattern> RECOMENDACIONES_PATTERNS = List.of(
        Pattern.compile(".*que deberiamos mejorar.*"),
        Pattern.compile(".*que deberiamos hacer.*(proximo|siguiente) sprint.*"),
        Pattern.compile(".*que (recomiendas|recomendamos).*"),
        Pattern.compile(".*que acciones deberiamos considerar.*"),
        Pattern.compile(".*en que deberiamos enfocarnos.*")
    );

    /** FASE 14 — patrones robustos por VERBO + "retrospectiva", en vez de depender de una
     *  coincidencia exacta de texto. Antes solo se reconocía "que revisar en retrospectiva"
     *  literal, lo que dejaba fuera "¿Qué deberíamos revisar en la retrospectiva?" (la MISMA
     *  frase que sugiere el menú del Copilot), haciendo que esa sugerencia cayera
     *  incorrectamente en UNKNOWN en vez de resolverse por el flujo determinístico. */
    private static final List<Pattern> RETROSPECTIVA_PATTERNS = List.of(
        Pattern.compile(".*revisar.*(en|para)\\s+(la\\s+)?retrospectiva.*"),
        Pattern.compile(".*discutir.*(en|para)\\s+(la\\s+)?retrospectiva.*"),
        Pattern.compile(".*que temas (llevar a|para) (la )?retrospectiva.*")
    );

    /** Cada patrón captura, en el último grupo, el nombre candidato de la métrica.
     *  El orden importa: los más específicos van primero; el genérico "cual fue el X" va
     *  último para no interceptar frases que ya deberían resolver a otra cosa. */
    private static final List<Pattern> RESULTADO_METRICA_PATTERNS = List.of(
        Pattern.compile("^cuanto\\s+(?:dio|saco|sacamos|salio)\\s+(?:en\\s+)?(.+)$"),
        Pattern.compile("^cual\\s+fue\\s+el\\s+resultado\\s+de\\s+(.+)$"),
        Pattern.compile("^cuanta\\s+(.+?)\\s+gestionamos$"),
        Pattern.compile("^cuantos?\\s+(.+?)\\s+tuvimos$"),
        Pattern.compile("^cuantos?\\s+(.+?)\\s+reporto\\s+el\\s+cliente$"),
        Pattern.compile("^cual\\s+fue\\s+el\\s+(.+)$")
    );

    public ClassifiedIntent classify(String message) {
        if (message == null || message.isBlank()) {
            return ClassifiedIntent.unknown();
        }

        String normalized = normalize(message);

        for (Pattern pattern : ULTIMO_SPRINT_PATTERNS) {
            if (pattern.matcher(normalized).matches()) {
                return ClassifiedIntent.resultadoUltimoSprint();
            }
        }

        for (Pattern pattern : COMPARACION_SPRINTS_PATTERNS) {
            if (pattern.matcher(normalized).matches()) {
                return ClassifiedIntent.comparacionSprints();
            }
        }

        for (Pattern pattern : TENDENCIAS_PATTERNS) {
            if (pattern.matcher(normalized).matches()) {
                return ClassifiedIntent.tendencias();
            }
        }

        for (Pattern pattern : RIESGOS_PATTERNS) {
            if (pattern.matcher(normalized).matches()) {
                return ClassifiedIntent.riesgos();
            }
        }

        for (Pattern pattern : RECOMENDACIONES_PATTERNS) {
            if (pattern.matcher(normalized).matches()) {
                return ClassifiedIntent.recomendaciones();
            }
        }

        for (Pattern pattern : RETROSPECTIVA_PATTERNS) {
            if (pattern.matcher(normalized).matches()) {
                return ClassifiedIntent.retrospectiva();
            }
        }

        for (Pattern pattern : RESULTADO_METRICA_PATTERNS) {
            Matcher matcher = pattern.matcher(normalized);
            if (matcher.matches()) {
                String candidata = matcher.group(1).trim();
                if (!candidata.isBlank()) {
                    return ClassifiedIntent.resultadoMetrica(candidata);
                }
            }
        }

        return ClassifiedIntent.unknown();
    }

    /** Misma normalización que CopilotDomainGuard: solo para clasificar, nunca se usa
     *  para modificar el mensaje que eventualmente pueda llegar al LLM. */
    private String normalize(String message) {
        String lower = message.toLowerCase(Locale.forLanguageTag("es"));
        String withoutAccents = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String withoutPunctuation = withoutAccents.replaceAll("[¿?¡!.,;:\"']", " ");
        return withoutPunctuation.replaceAll("\\s+", " ").trim();
    }
}
