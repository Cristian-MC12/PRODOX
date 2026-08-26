// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpdia.dto.ai.AIInsightDto;
import com.mpdia.dto.ai.GenerateInsightsResultDto;
import com.mpdia.dto.ai.InsightEvidenceDto;
import com.mpdia.dto.analytics.*;
import com.mpdia.entity.AIInsight;
import com.mpdia.entity.Proyecto;
import com.mpdia.entity.Sprint;
import com.mpdia.repository.AIInsightRepository;
import com.mpdia.repository.ProjectMemberRepository;
import com.mpdia.repository.ProyectoRepository;
import com.mpdia.repository.SprintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Servicio principal para generación de AI Insights en MPDIA.
 * 
 * ARQUITECTURA:
 * 1. Obtener datos de PostgreSQL (via SprintService, AgileAnalyticsService)
 * 2. Ejecutar analytics determinísticos
 * 3. Identificar señales (tendencias, anomalías, riesgos)
 * 4. Construir contexto estructurado con evidencia
 * 5. Enviar SOLO datos necesarios a Gemini
 * 6. Gemini genera explicación y recomendaciones
 * 7. Persistir insight con evidencia estructurada
 * 
 * IMPORTANTE:
 * - Los cálculos numéricos son determinísticos (NO usa IA para calcular)
 * - Gemini solo interpreta y explica los datos
 * - Maneja explícitamente casos de datos insuficientes
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIInsightsService {

    private final AgileAnalyticsService analyticsService;
    private final GeminiService geminiService;
    private final AIInsightRepository insightRepo;
    private final ProyectoRepository proyectoRepo;
    private final SprintRepository sprintRepo;
    private final ProjectMemberRepository projectMemberRepo;
    private final ObjectMapper objectMapper;

    // ═══════════════════════════════════════════════════════════════════════
    // GENERACIÓN DE INSIGHTS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Resultado interno de un detector individual (trend/anomaly/risk/comparison).
     * FASE 23: separa "señal detectada" de "insight nuevo persistido" de
     * "señal ya cubierta por un insight existente (duplicado evitado)" de
     * "fallo real" — antes todo se colapsaba en un try/catch mudo por
     * generador que abortaba el resto del propio bucle ante el primer fallo
     * y nunca informaba nada al llamador.
     */
    private record GeneratorOutcome(
            List<AIInsight> nuevos,
            int detectadas,
            int omitidosPorDuplicado,
            List<String> errores
    ) {}

    /**
     * Genera todos los insights disponibles para un proyecto.
     * Analiza tendencias, anomalías y riesgos basándose en datos históricos.
     *
     * @param proyectoId ID del proyecto
     * @param userId ID del usuario (para validación de acceso)
     * @return Resultado con los insights nuevos y el estado real de la corrida
     *         (COMPLETE/PARTIAL/FAILED/SIN_SENALES/SIN_DATOS — ver GenerateInsightsResultDto)
     */
    @Transactional
    public GenerateInsightsResultDto generateInsights(UUID proyectoId, String userId) {
        log.info("Generando insights para proyecto={} usuario={}", proyectoId, userId);

        // 1. Validar acceso
        validateProjectAccess(userId, proyectoId);

        // 2. Verificar datos disponibles
        Proyecto proyecto = proyectoRepo.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado"));

        List<Sprint> sprintsFinalizados = sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)
                .stream()
                .filter(s -> "finalizado".equals(s.getEstado()))
                .toList();

        if (sprintsFinalizados.isEmpty()) {
            log.info("Proyecto {} no tiene sprints finalizados, no se pueden generar insights", proyectoId);
            return new GenerateInsightsResultDto(List.of(), "SIN_DATOS", 0, 0, 0, List.of());
        }

        if (sprintsFinalizados.size() < 2) {
            log.info("Proyecto {} tiene menos de 2 sprints finalizados, insights limitados", proyectoId);
            // Aún podemos generar insights básicos del sprint actual
        }

        List<AIInsight> todosNuevos = new ArrayList<>();
        List<String> todosErrores = new ArrayList<>();
        int totalDetectadas = 0;
        int totalOmitidas = 0;

        // 3. Generar diferentes tipos de insights
        GeneratorOutcome trendOutcome = generateTrendInsights(proyectoId);
        todosNuevos.addAll(trendOutcome.nuevos());
        todosErrores.addAll(trendOutcome.errores());
        totalDetectadas += trendOutcome.detectadas();
        totalOmitidas += trendOutcome.omitidosPorDuplicado();

        if (sprintsFinalizados.size() >= 3) {
            GeneratorOutcome anomalyOutcome = generateAnomalyInsights(proyectoId);
            todosNuevos.addAll(anomalyOutcome.nuevos());
            todosErrores.addAll(anomalyOutcome.errores());
            totalDetectadas += anomalyOutcome.detectadas();
            totalOmitidas += anomalyOutcome.omitidosPorDuplicado();

            GeneratorOutcome riskOutcome = generateRiskInsights(proyectoId);
            todosNuevos.addAll(riskOutcome.nuevos());
            todosErrores.addAll(riskOutcome.errores());
            totalDetectadas += riskOutcome.detectadas();
            totalOmitidas += riskOutcome.omitidosPorDuplicado();
        }

        if (sprintsFinalizados.size() >= 2) {
            GeneratorOutcome comparisonOutcome = generateComparisonInsights(proyectoId);
            todosNuevos.addAll(comparisonOutcome.nuevos());
            todosErrores.addAll(comparisonOutcome.errores());
            totalDetectadas += comparisonOutcome.detectadas();
            totalOmitidas += comparisonOutcome.omitidosPorDuplicado();
        }

        String status;
        if (totalDetectadas == 0) {
            status = "SIN_SENALES";
        } else if (todosErrores.isEmpty()) {
            status = "COMPLETE";
        } else if (todosNuevos.isEmpty() && totalOmitidas == 0) {
            status = "FAILED";
        } else {
            status = "PARTIAL";
        }

        log.info("Generación de insights para proyecto {}: status={} detectadas={} nuevas={} omitidas={} errores={}",
                proyectoId, status, totalDetectadas, todosNuevos.size(), totalOmitidas, todosErrores.size());

        List<AIInsightDto> dtos = todosNuevos.stream().map(this::toDto).toList();
        return new GenerateInsightsResultDto(dtos, status, totalDetectadas, todosNuevos.size(), totalOmitidas, todosErrores);
    }

    /**
     * FASE 23 — deduplicación: determina si ya existe un insight activo (no
     * descartado) para la misma señal exacta (mismo proyecto, tipo, categoría,
     * sprint y evidencia determinística). La evidencia usada como huella
     * (evidenceJson para TREND/ANOMALY/COMPARISON, título+descripción — que
     * para RISK se llenan directo desde AgileAnalyticsService, no desde
     * Gemini — para RISK) es siempre calculada ANTES de llamar a Gemini, así
     * que nunca se compara texto libre generado por IA: si los números no
     * cambiaron, es la misma señal y no se repite: si cambiaron (nuevo
     * sprint, nueva variación), la huella difiere y sí se genera un insight
     * nuevo y legítimo.
     */
    private boolean esDuplicadoDeSenialExistente(UUID proyectoId, String tipo, String categoria,
                                                  UUID sprintId, String huella) {
        List<AIInsight> existentes = insightRepo
                .findByProyectoIdAndTipoAndCategoriaAfectadaAndDismissedFalse(proyectoId, tipo, categoria);
        return existentes.stream().anyMatch(e ->
                Objects.equals(e.getSprintId(), sprintId) && huella.equals(huellaDe(e)));
    }

    private String huellaDe(AIInsight insight) {
        if ("RISK".equals(insight.getTipo())) {
            return huellaRisk(insight.getTitulo(), insight.getDescripcion());
        }
        return insight.getEvidenceJson() != null ? insight.getEvidenceJson() : "";
    }

    private String huellaRisk(String titulo, String descripcion) {
        return (titulo != null ? titulo : "") + " " + (descripcion != null ? descripcion : "");
    }

    /**
     * Genera insights basados en tendencias históricas.
     * Detecta métricas que están mejorando o empeorando consistentemente.
     */
    private GeneratorOutcome generateTrendInsights(UUID proyectoId) {
        List<AIInsight> nuevos = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        int detectadas = 0;
        int omitidas = 0;

        List<TrendAnalysisDto> trends;
        try {
            // Obtener tendencias de últimos 3 sprints
            trends = analyticsService.getSprintTrends(proyectoId, null, 3);
        } catch (Exception e) {
            log.error("Error obteniendo tendencias para proyecto {}", proyectoId, e);
            errores.add("TREND: " + e.getMessage());
            return new GeneratorOutcome(nuevos, detectadas, omitidas, errores);
        }

        for (TrendAnalysisDto trend : trends) {
            if (!trend.datosDisponibles()) continue;

            // Solo generar insight si hay cambio significativo
            if ("STABLE".equals(trend.tendenciaGeneral())) continue;

            detectadas++;
            try {
                InsightEvidenceDto evidence = new InsightEvidenceDto(
                        trend.categoria(),
                        trend.dataPoints().get(trend.dataPoints().size() - 1).valor(),
                        trend.dataPoints().get(0).valor(),
                        trend.promedioGeneral(),
                        trend.desviacionEstandar(),
                        trend.variacionTotal(),
                        trend.tendenciaGeneral(),
                        trend.numeroSprints(),
                        Map.of()
                );
                String evidenceJson = serializeEvidence(List.of(evidence));

                if (esDuplicadoDeSenialExistente(proyectoId, "TREND", trend.categoria(), null, evidenceJson)) {
                    log.debug("Señal de tendencia para {} ya existe sin cambios, se omite duplicado", trend.categoria());
                    omitidas++;
                    continue;
                }

                AIInsight insight = new AIInsight();
                insight.setProyectoId(proyectoId);
                insight.setTipo("TREND");
                insight.setCategoriaAfectada(trend.categoria());
                insight.setEvidenceJson(evidenceJson);
                insight.setSeveridad(determineTrendSeverity(trend.variacionTotal()));

                // Generar título y descripción con Gemini
                String prompt = buildTrendPrompt(trend, evidence);
                String geminiResponse = geminiService.generate(prompt);

                // Parsear respuesta (título|descripción|recomendación)
                parseTrendResponse(geminiResponse, insight, trend.categoria(), "TREND");

                insight.setConfianza(determineConfidence(trend.numeroSprints()));

                nuevos.add(insightRepo.save(insight));
                log.debug("Generado insight de tendencia para {}: {}", trend.categoria(), insight.getTitulo());
            } catch (Exception e) {
                log.error("Error generando insight de tendencia para {} en proyecto {}: {}",
                        trend.categoria(), proyectoId, e.getMessage(), e);
                errores.add("TREND (" + trend.categoria() + "): " + e.getMessage());
            }
        }

        return new GeneratorOutcome(nuevos, detectadas, omitidas, errores);
    }

    /**
     * Genera insights basados en anomalías detectadas.
     * Detecta valores significativamente diferentes del histórico.
     */
    private GeneratorOutcome generateAnomalyInsights(UUID proyectoId) {
        List<AIInsight> nuevos = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        int detectadas = 0;
        int omitidas = 0;

        List<AnomalyDto> anomalies;
        try {
            // Obtener sprint activo
            Sprint sprintActivo = sprintRepo.findByProyectoIdAndEstado(proyectoId, "en_ejecucion")
                    .orElse(null);

            if (sprintActivo == null) {
                // Usar último sprint finalizado
                List<Sprint> finalizados = sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)
                        .stream()
                        .filter(s -> "finalizado".equals(s.getEstado()))
                        .toList();

                if (finalizados.isEmpty()) return new GeneratorOutcome(nuevos, detectadas, omitidas, errores);
                sprintActivo = finalizados.get(0);
            }

            anomalies = analyticsService.detectAnomalies(sprintActivo.getId());
        } catch (Exception e) {
            log.error("Error detectando anomalías para proyecto {}", proyectoId, e);
            errores.add("ANOMALY: " + e.getMessage());
            return new GeneratorOutcome(nuevos, detectadas, omitidas, errores);
        }

        for (AnomalyDto anomaly : anomalies) {
            detectadas++;
            try {
                InsightEvidenceDto evidence = new InsightEvidenceDto(
                        anomaly.categoria(),
                        anomaly.valorActual(),
                        null,
                        anomaly.promedioHistorico(),
                        anomaly.desviacionEstandar(),
                        null,
                        anomaly.direccion(),
                        null,
                        Map.of(
                                "numberOfDeviations", anomaly.numDesviaciones()
                        )
                );
                String evidenceJson = serializeEvidence(List.of(evidence));

                if (esDuplicadoDeSenialExistente(proyectoId, "ANOMALY", anomaly.categoria(), anomaly.sprintId(), evidenceJson)) {
                    log.debug("Señal de anomalía para {} en sprint {} ya existe sin cambios, se omite duplicado",
                            anomaly.categoria(), anomaly.sprintId());
                    omitidas++;
                    continue;
                }

                AIInsight insight = new AIInsight();
                insight.setProyectoId(proyectoId);
                insight.setSprintId(anomaly.sprintId());
                insight.setTipo("ANOMALY");
                insight.setCategoriaAfectada(anomaly.categoria());
                insight.setEvidenceJson(evidenceJson);
                insight.setSeveridad(anomaly.severidad());

                // Generar descripción con Gemini
                String prompt = buildAnomalyPrompt(anomaly, evidence);
                String geminiResponse = geminiService.generate(prompt);

                parseAnomalyResponse(geminiResponse, insight, anomaly.categoria());

                insight.setConfianza("HIGH"); // Anomalías estadísticas tienen alta confianza

                nuevos.add(insightRepo.save(insight));
                log.debug("Generado insight de anomalía para {}: {}", anomaly.categoria(), insight.getTitulo());
            } catch (Exception e) {
                log.error("Error generando insight de anomalía para {} en proyecto {}: {}",
                        anomaly.categoria(), proyectoId, e.getMessage(), e);
                errores.add("ANOMALY (" + anomaly.categoria() + "): " + e.getMessage());
            }
        }

        return new GeneratorOutcome(nuevos, detectadas, omitidas, errores);
    }

    /**
     * Genera insights basados en riesgos identificados.
     * Detecta señales que pueden indicar problemas futuros.
     */
    private GeneratorOutcome generateRiskInsights(UUID proyectoId) {
        List<AIInsight> nuevos = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        int detectadas = 0;
        int omitidas = 0;

        List<RiskDto> risks;
        try {
            risks = analyticsService.identifyRisks(proyectoId);
        } catch (Exception e) {
            log.error("Error identificando riesgos para proyecto {}", proyectoId, e);
            errores.add("RISK: " + e.getMessage());
            return new GeneratorOutcome(nuevos, detectadas, omitidas, errores);
        }

        for (RiskDto risk : risks) {
            detectadas++;
            try {
                String huella = huellaRisk(risk.titulo(), risk.evidencia());

                if (esDuplicadoDeSenialExistente(proyectoId, "RISK", risk.categoriaAfectada(), null, huella)) {
                    log.debug("Riesgo '{}' ya existe sin cambios, se omite duplicado", risk.titulo());
                    omitidas++;
                    continue;
                }

                AIInsight insight = new AIInsight();
                insight.setProyectoId(proyectoId);
                insight.setTipo("RISK");
                insight.setCategoriaAfectada(risk.categoriaAfectada());
                insight.setSeveridad(risk.severidad());

                // La evidencia ya está en la descripción del riesgo
                insight.setTitulo(risk.titulo());
                insight.setDescripcion(risk.evidencia());

                // Generar recomendación con Gemini
                String prompt = buildRiskPrompt(risk);
                insight.setRecomendacion(limpiarMarkdown(geminiService.generate(prompt)));

                insight.setConfianza("MEDIUM"); // Riesgos son hipótesis, confianza media

                nuevos.add(insightRepo.save(insight));
                log.debug("Generado insight de riesgo: {}", insight.getTitulo());
            } catch (Exception e) {
                log.error("Error generando insight de riesgo '{}' en proyecto {}: {}",
                        risk.titulo(), proyectoId, e.getMessage(), e);
                errores.add("RISK (" + risk.titulo() + "): " + e.getMessage());
            }
        }

        return new GeneratorOutcome(nuevos, detectadas, omitidas, errores);
    }

    /**
     * Genera insights comparando los dos últimos sprints.
     */
    private GeneratorOutcome generateComparisonInsights(UUID proyectoId) {
        List<AIInsight> nuevos = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        int detectadas = 0;
        int omitidas = 0;

        Sprint ultimo;
        Sprint penultimo;
        SprintComparisonDto comparison;
        try {
            List<Sprint> finalizados = sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)
                    .stream()
                    .filter(s -> "finalizado".equals(s.getEstado()))
                    .limit(2)
                    .toList();

            if (finalizados.size() < 2) return new GeneratorOutcome(nuevos, detectadas, omitidas, errores);

            ultimo = finalizados.get(0);
            penultimo = finalizados.get(1);

            comparison = analyticsService.compareSprints(ultimo.getId(), penultimo.getId());

            if (!comparison.datosDisponibles()) return new GeneratorOutcome(nuevos, detectadas, omitidas, errores);
        } catch (Exception e) {
            log.error("Error comparando sprints para proyecto {}", proyectoId, e);
            errores.add("COMPARISON: " + e.getMessage());
            return new GeneratorOutcome(nuevos, detectadas, omitidas, errores);
        }

        // Generar un insight por categoría con cambio significativo
        for (String categoria : comparison.tendencia().keySet()) {
            String tendencia = comparison.tendencia().get(categoria);
            if ("STABLE".equals(tendencia)) continue;

            BigDecimal cambio = comparison.variacionPorcentual().get(categoria);
            if (cambio.abs().compareTo(new BigDecimal("10.0")) < 0) continue; // Ignorar cambios < 10%

            detectadas++;
            try {
                InsightEvidenceDto evidence = new InsightEvidenceDto(
                        categoria,
                        comparison.sprint2Metricas().get(categoria),
                        comparison.sprint1Metricas().get(categoria),
                        null,
                        null,
                        comparison.variacionPorcentual().get(categoria),
                        tendencia,
                        2,
                        Map.of()
                );
                String evidenceJson = serializeEvidence(List.of(evidence));

                if (esDuplicadoDeSenialExistente(proyectoId, "COMPARISON", categoria, ultimo.getId(), evidenceJson)) {
                    log.debug("Señal de comparación para {} (sprint {}) ya existe sin cambios, se omite duplicado",
                            categoria, ultimo.getNumero());
                    omitidas++;
                    continue;
                }

                AIInsight insight = new AIInsight();
                insight.setProyectoId(proyectoId);
                insight.setSprintId(ultimo.getId());
                insight.setTipo("COMPARISON");
                insight.setCategoriaAfectada(categoria);
                insight.setEvidenceJson(evidenceJson);
                insight.setSeveridad(determineTrendSeverity(cambio));

                // Generar descripción con Gemini
                String prompt = buildComparisonPrompt(categoria, comparison, ultimo.getNumero(), penultimo.getNumero());
                String geminiResponse = geminiService.generate(prompt);

                parseComparisonResponse(geminiResponse, insight, categoria);

                insight.setConfianza("HIGH"); // Comparación directa tiene alta confianza

                nuevos.add(insightRepo.save(insight));
                log.debug("Generado insight de comparación para {}: {}", categoria, insight.getTitulo());
            } catch (Exception e) {
                log.error("Error generando insight de comparación para {} en proyecto {}: {}",
                        categoria, proyectoId, e.getMessage(), e);
                errores.add("COMPARISON (" + categoria + "): " + e.getMessage());
            }
        }

        return new GeneratorOutcome(nuevos, detectadas, omitidas, errores);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPERS - PROMPTS PARA GEMINI
    // ═══════════════════════════════════════════════════════════════════════

    private String buildTrendPrompt(TrendAnalysisDto trend, InsightEvidenceDto evidence) {
        return String.format(
                """
                Eres un experto en métricas Agile. Analiza la siguiente tendencia y genera un insight conciso.
                
                DATOS OBJETIVOS:
                - Métrica: %s
                - Tendencia: %s
                - Variación: %.1f%%
                - Promedio histórico: %.2f
                - Desviación estándar: %.2f
                - Sprints analizados: %d
                
                INSTRUCCIONES:
                1. NO inventes ni calcules números. Usa solo los datos provistos.
                2. Explica qué significa esta tendencia en el contexto de metodologías ágiles.
                3. Indica si es una señal positiva o negativa.
                4. Sugiere posibles causas (usa "podría" para hipótesis).
                5. Provee una recomendación accionable.
                
                FORMATO DE RESPUESTA:
                TÍTULO: [Título breve, máximo 100 caracteres]
                DESCRIPCIÓN: [Explicación clara de 2-3 oraciones]
                RECOMENDACIÓN: [Acción sugerida de 1-2 oraciones]
                """,
                trend.categoria(),
                trend.tendenciaGeneral().equals("UP") ? "Ascendente" : "Descendente",
                trend.variacionTotal(),
                trend.promedioGeneral(),
                trend.desviacionEstandar(),
                trend.numeroSprints()
        );
    }

    private String buildAnomalyPrompt(AnomalyDto anomaly, InsightEvidenceDto evidence) {
        return String.format(
                """
                Detectamos una anomalía estadística en las métricas del equipo. Explícala de forma clara y no alarmista.
                
                DATOS:
                - Métrica: %s
                - Valor actual: %.2f
                - Promedio histórico: %.2f
                - Desviación: %.1f desviaciones estándar %s del promedio
                - Sprint: %d
                
                INSTRUCCIONES:
                1. Explica que este valor es inusual comparado con el histórico.
                2. NO asumas que es un problema grave (puede ser una mejora).
                3. Sugiere revisar el contexto del sprint.
                4. Provee preguntas que el equipo debería hacerse.
                
                FORMATO:
                TÍTULO: [Título breve]
                DESCRIPCIÓN: [Explicación contextual]
                RECOMENDACIÓN: [Qué revisar o investigar]
                """,
                anomaly.categoria(),
                anomaly.valorActual(),
                anomaly.promedioHistorico(),
                anomaly.numDesviaciones(),
                anomaly.direccion().equals("ABOVE") ? "por encima" : "por debajo",
                anomaly.sprintNumero()
        );
    }

    private String buildRiskPrompt(RiskDto risk) {
        return String.format(
                """
                Genera una recomendación práctica para el siguiente riesgo identificado:
                
                RIESGO: %s
                DESCRIPCIÓN: %s
                MÉTRICA AFECTADA: %s
                SEVERIDAD: %s
                
                Provee una recomendación accionable de 2-3 oraciones sobre qué puede hacer el equipo para mitigar este riesgo.
                Usa un tono constructivo y enfocado en soluciones.
                """,
                risk.titulo(),
                risk.evidencia(),
                risk.categoriaAfectada(),
                risk.severidad()
        );
    }

    private String buildComparisonPrompt(String categoria, SprintComparisonDto comparison, 
                                          int sprint1Num, int sprint2Num) {
        BigDecimal val1 = comparison.sprint1Metricas().get(categoria);
        BigDecimal val2 = comparison.sprint2Metricas().get(categoria);
        BigDecimal cambio = comparison.variacionPorcentual().get(categoria);
        
        return String.format(
                """
                Compara el desempeño entre dos sprints y genera un insight.
                
                COMPARACIÓN:
                - Métrica: %s
                - Sprint %d: %.2f
                - Sprint %d: %.2f
                - Cambio: %.1f%%
                
                INSTRUCCIONES:
                1. Describe el cambio observado.
                2. Contextualiza si es significativo.
                3. Sugiere si requiere atención o es esperado.
                
                FORMATO:
                TÍTULO: [Título comparativo]
                DESCRIPCIÓN: [Explicación del cambio]
                RECOMENDACIÓN: [Qué hacer con esta información]
                """,
                categoria,
                sprint2Num, val1,
                sprint1Num, val2,
                cambio
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPERS - PARSING Y UTILIDADES
    // ═══════════════════════════════════════════════════════════════════════

    // FASE 23: TÍTULO:/DESCRIPCIÓN:/RECOMENDACIÓN: se localizan por marcador
    // (no por posición). Antes, un split()+parts[0] asumía que la respuesta
    // empezaba exactamente en "TÍTULO:" — si Gemini anteponía cualquier
    // preámbulo ("Aquí tienes la comparación...") antes del formato pedido,
    // ese preámbulo completo (con Markdown crudo) quedaba como "título"
    // (causa raíz confirmada en FASE 22). Ahora cada sección se busca de
    // forma independiente en cualquier posición del texto; si TÍTULO: no
    // aparece en absoluto, se usa un título corto derivado de datos
    // determinísticos (categoría + tipo de señal) en vez de mostrar texto
    // de IA sin acotar.
    private static final java.util.regex.Pattern TITULO_PATTERN = java.util.regex.Pattern.compile(
            "T[ÍI]TULO:\\s*(.+?)(?=\\n\\s*(?:DESCRIPCI[ÓO]N:|RECOMENDACI[ÓO]N:)|$)",
            java.util.regex.Pattern.DOTALL | java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern DESCRIPCION_PATTERN = java.util.regex.Pattern.compile(
            "DESCRIPCI[ÓO]N:\\s*(.+?)(?=\\n\\s*RECOMENDACI[ÓO]N:|$)",
            java.util.regex.Pattern.DOTALL | java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern RECOMENDACION_PATTERN = java.util.regex.Pattern.compile(
            "RECOMENDACI[ÓO]N:\\s*(.+)",
            java.util.regex.Pattern.DOTALL | java.util.regex.Pattern.CASE_INSENSITIVE);

    private void parseTrendResponse(String response, AIInsight insight, String categoriaFallback, String tipoFallback) {
        java.util.regex.Matcher tituloM = TITULO_PATTERN.matcher(response);
        java.util.regex.Matcher descM = DESCRIPCION_PATTERN.matcher(response);
        java.util.regex.Matcher recM = RECOMENDACION_PATTERN.matcher(response);

        String titulo = tituloM.find() ? limpiarMarkdown(tituloM.group(1)) : null;
        if (titulo == null || titulo.isBlank()) {
            // Respuesta sin marcador TÍTULO: reconocible — nunca se usa el
            // bloque completo de Gemini como título, se deriva uno corto y
            // legible a partir de datos ya conocidos por el backend.
            titulo = tituloFallback(categoriaFallback, tipoFallback);
        }
        insight.setTitulo(titulo.substring(0, Math.min(200, titulo.length())));

        if (descM.find()) {
            insight.setDescripcion(limpiarMarkdown(descM.group(1)));
        } else {
            insight.setDescripcion(limpiarMarkdown(response).substring(0, Math.min(500, response.length())));
        }

        if (recM.find()) {
            insight.setRecomendacion(limpiarMarkdown(recM.group(1)));
        }
    }

    private void parseAnomalyResponse(String response, AIInsight insight, String categoria) {
        parseTrendResponse(response, insight, categoria, "ANOMALY"); // Mismo formato
    }

    private void parseComparisonResponse(String response, AIInsight insight, String categoria) {
        parseTrendResponse(response, insight, categoria, "COMPARISON"); // Mismo formato
    }

    private String tituloFallback(String categoria, String tipo) {
        String tipoLabel = switch (tipo) {
            case "TREND" -> "Tendencia detectada";
            case "ANOMALY" -> "Anomalía detectada";
            case "COMPARISON" -> "Comparación de sprints";
            default -> "Insight detectado";
        };
        return tipoLabel + " en " + categoria;
    }

    /** Quita ruido de Markdown (encabezados, énfasis, separadores) y recorta espacios. */
    private String limpiarMarkdown(String texto) {
        if (texto == null) return "";
        return texto
                .replaceAll("^[\\s*#>-]+", "")
                .replaceAll("[\\s*]+$", "")
                .replace("**", "")
                .trim();
    }

    private String determineTrendSeverity(BigDecimal variacion) {
        BigDecimal abs = variacion.abs();
        if (abs.compareTo(new BigDecimal("20.0")) > 0) return "HIGH";
        if (abs.compareTo(new BigDecimal("10.0")) > 0) return "MEDIUM";
        return "LOW";
    }

    private String determineConfidence(int numberOfSprints) {
        if (numberOfSprints >= 5) return "HIGH";
        if (numberOfSprints >= 3) return "MEDIUM";
        return "LOW";
    }

    private String serializeEvidence(List<InsightEvidenceDto> evidence) {
        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (JsonProcessingException e) {
            log.error("Error serializando evidencia", e);
            return "[]";
        }
    }

    private AIInsightDto toDto(AIInsight insight) {
        List<InsightEvidenceDto> evidence = new ArrayList<>();
        if (insight.getEvidenceJson() != null) {
            try {
                evidence = objectMapper.readValue(
                        insight.getEvidenceJson(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, InsightEvidenceDto.class)
                );
            } catch (JsonProcessingException e) {
                log.error("Error deserializando evidencia", e);
            }
        }
        
        return new AIInsightDto(
                insight.getId(),
                insight.getProyectoId(),
                insight.getSprintId(),
                insight.getTipo(),
                insight.getSeveridad(),
                insight.getTitulo(),
                insight.getDescripcion(),
                evidence,
                insight.getRecomendacion(),
                insight.getConfianza(),
                insight.getDismissed(),
                insight.getCreatedAt()
        );
    }

    private void validateProjectAccess(String userId, UUID proyectoId) {
        if (!projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)) {
            log.warn("Usuario {} intentó acceder a insights del proyecto {} sin autorización", userId, proyectoId);
            throw new SecurityException("No tienes acceso a este proyecto");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CONSULTAS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Obtiene todos los insights activos de un proyecto.
     */
    public List<AIInsightDto> getProjectInsights(UUID proyectoId, String userId) {
        validateProjectAccess(userId, proyectoId);
        
        return insightRepo.findByProyectoIdAndDismissedFalseOrderByCreatedAtDesc(proyectoId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Marca un insight como descartado.
     */
    @Transactional
    public void dismissInsight(UUID insightId, String userId) {
        AIInsight insight = insightRepo.findById(insightId)
                .orElseThrow(() -> new IllegalArgumentException("Insight no encontrado"));
        
        validateProjectAccess(userId, insight.getProyectoId());
        
        insight.setDismissed(true);
        insight.setDismissedAt(Instant.now());
        insightRepo.save(insight);
        
        log.info("Insight {} descartado por usuario {}", insightId, userId);
    }
}
