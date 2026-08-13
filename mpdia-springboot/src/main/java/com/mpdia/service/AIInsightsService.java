// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpdia.dto.ai.AIInsightDto;
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
     * Genera todos los insights disponibles para un proyecto.
     * Analiza tendencias, anomalías y riesgos basándose en datos históricos.
     * 
     * @param proyectoId ID del proyecto
     * @param userId ID del usuario (para validación de acceso)
     * @return Lista de insights generados
     */
    @Transactional
    public List<AIInsightDto> generateInsights(UUID proyectoId, String userId) {
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
            return List.of();
        }
        
        if (sprintsFinalizados.size() < 2) {
            log.info("Proyecto {} tiene menos de 2 sprints finalizados, insights limitados", proyectoId);
            // Aún podemos generar insights básicos del sprint actual
        }
        
        List<AIInsight> insightsGenerados = new ArrayList<>();
        
        // 3. Generar diferentes tipos de insights
        insightsGenerados.addAll(generateTrendInsights(proyectoId));
        
        if (sprintsFinalizados.size() >= 3) {
            insightsGenerados.addAll(generateAnomalyInsights(proyectoId));
            insightsGenerados.addAll(generateRiskInsights(proyectoId));
        }
        
        if (sprintsFinalizados.size() >= 2) {
            insightsGenerados.addAll(generateComparisonInsights(proyectoId));
        }
        
        log.info("Generados {} insights para proyecto {}", insightsGenerados.size(), proyectoId);
        
        return insightsGenerados.stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Genera insights basados en tendencias históricas.
     * Detecta métricas que están mejorando o empeorando consistentemente.
     */
    private List<AIInsight> generateTrendInsights(UUID proyectoId) {
        List<AIInsight> insights = new ArrayList<>();
        
        try {
            // Obtener tendencias de últimos 3 sprints
            List<TrendAnalysisDto> trends = analyticsService.getSprintTrends(proyectoId, null, 3);
            
            for (TrendAnalysisDto trend : trends) {
                if (!trend.datosDisponibles()) continue;
                
                // Solo generar insight si hay cambio significativo
                if ("STABLE".equals(trend.tendenciaGeneral())) continue;
                
                AIInsight insight = new AIInsight();
                insight.setProyectoId(proyectoId);
                insight.setTipo("TREND");
                insight.setCategoriaAfectada(trend.categoria());
                
                // Construir evidencia estructurada
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
                
                insight.setEvidenceJson(serializeEvidence(List.of(evidence)));
                
                // Determinar severidad basada en magnitud del cambio
                insight.setSeveridad(determineTrendSeverity(trend.variacionTotal()));
                
                // Generar título y descripción con Gemini
                String prompt = buildTrendPrompt(trend, evidence);
                String geminiResponse = geminiService.generate(prompt);
                
                // Parsear respuesta (título|descripción|recomendación)
                parseTrendResponse(geminiResponse, insight);
                
                insight.setConfianza(determineConfidence(trend.numeroSprints()));
                
                insights.add(insightRepo.save(insight));
                log.debug("Generado insight de tendencia para {}: {}", trend.categoria(), insight.getTitulo());
            }
        } catch (Exception e) {
            log.error("Error generando insights de tendencia para proyecto {}", proyectoId, e);
        }
        
        return insights;
    }

    /**
     * Genera insights basados en anomalías detectadas.
     * Detecta valores significativamente diferentes del histórico.
     */
    private List<AIInsight> generateAnomalyInsights(UUID proyectoId) {
        List<AIInsight> insights = new ArrayList<>();
        
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
                
                if (finalizados.isEmpty()) return insights;
                sprintActivo = finalizados.get(0);
            }
            
            List<AnomalyDto> anomalies = analyticsService.detectAnomalies(sprintActivo.getId());
            
            for (AnomalyDto anomaly : anomalies) {
                AIInsight insight = new AIInsight();
                insight.setProyectoId(proyectoId);
                insight.setSprintId(anomaly.sprintId());
                insight.setTipo("ANOMALY");
                insight.setCategoriaAfectada(anomaly.categoria());
                
                // Construir evidencia
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
                
                insight.setEvidenceJson(serializeEvidence(List.of(evidence)));
                insight.setSeveridad(anomaly.severidad());
                
                // Generar descripción con Gemini
                String prompt = buildAnomalyPrompt(anomaly, evidence);
                String geminiResponse = geminiService.generate(prompt);
                
                parseAnomalyResponse(geminiResponse, insight);
                
                insight.setConfianza("HIGH"); // Anomalías estadísticas tienen alta confianza
                
                insights.add(insightRepo.save(insight));
                log.debug("Generado insight de anomalía para {}: {}", anomaly.categoria(), insight.getTitulo());
            }
        } catch (Exception e) {
            log.error("Error generando insights de anomalías para proyecto {}", proyectoId, e);
        }
        
        return insights;
    }

    /**
     * Genera insights basados en riesgos identificados.
     * Detecta señales que pueden indicar problemas futuros.
     */
    private List<AIInsight> generateRiskInsights(UUID proyectoId) {
        List<AIInsight> insights = new ArrayList<>();
        
        try {
            List<RiskDto> risks = analyticsService.identifyRisks(proyectoId);
            
            for (RiskDto risk : risks) {
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
                insight.setRecomendacion(geminiService.generate(prompt));
                
                insight.setConfianza("MEDIUM"); // Riesgos son hipótesis, confianza media
                
                insights.add(insightRepo.save(insight));
                log.debug("Generado insight de riesgo: {}", insight.getTitulo());
            }
        } catch (Exception e) {
            log.error("Error generando insights de riesgo para proyecto {}", proyectoId, e);
        }
        
        return insights;
    }

    /**
     * Genera insights comparando los dos últimos sprints.
     */
    private List<AIInsight> generateComparisonInsights(UUID proyectoId) {
        List<AIInsight> insights = new ArrayList<>();
        
        try {
            List<Sprint> finalizados = sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)
                    .stream()
                    .filter(s -> "finalizado".equals(s.getEstado()))
                    .limit(2)
                    .toList();
            
            if (finalizados.size() < 2) return insights;
            
            Sprint ultimo = finalizados.get(0);
            Sprint penultimo = finalizados.get(1);
            
            SprintComparisonDto comparison = analyticsService.compareSprints(ultimo.getId(), penultimo.getId());
            
            if (!comparison.datosDisponibles()) return insights;
            
            // Generar un insight por categoría con cambio significativo
            for (String categoria : comparison.tendencia().keySet()) {
                String tendencia = comparison.tendencia().get(categoria);
                if ("STABLE".equals(tendencia)) continue;
                
                BigDecimal cambio = comparison.variacionPorcentual().get(categoria);
                if (cambio.abs().compareTo(new BigDecimal("10.0")) < 0) continue; // Ignorar cambios < 10%
                
                AIInsight insight = new AIInsight();
                insight.setProyectoId(proyectoId);
                insight.setSprintId(ultimo.getId());
                insight.setTipo("COMPARISON");
                insight.setCategoriaAfectada(categoria);
                
                // Construir evidencia
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
                
                insight.setEvidenceJson(serializeEvidence(List.of(evidence)));
                insight.setSeveridad(determineTrendSeverity(cambio));
                
                // Generar descripción con Gemini
                String prompt = buildComparisonPrompt(categoria, comparison, ultimo.getNumero(), penultimo.getNumero());
                String geminiResponse = geminiService.generate(prompt);
                
                parseComparisonResponse(geminiResponse, insight);
                
                insight.setConfianza("HIGH"); // Comparación directa tiene alta confianza
                
                insights.add(insightRepo.save(insight));
                log.debug("Generado insight de comparación para {}: {}", categoria, insight.getTitulo());
            }
        } catch (Exception e) {
            log.error("Error generando insights de comparación para proyecto {}", proyectoId, e);
        }
        
        return insights;
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

    private void parseTrendResponse(String response, AIInsight insight) {
        String[] parts = response.split("DESCRIPCIÓN:|RECOMENDACIÓN:");
        
        if (parts.length >= 1) {
            String titulo = parts[0].replace("TÍTULO:", "").trim();
            insight.setTitulo(titulo.substring(0, Math.min(200, titulo.length())));
        }
        if (parts.length >= 2) {
            insight.setDescripcion(parts[1].trim());
        }
        if (parts.length >= 3) {
            insight.setRecomendacion(parts[2].trim());
        }
    }

    private void parseAnomalyResponse(String response, AIInsight insight) {
        parseTrendResponse(response, insight); // Mismo formato
    }

    private void parseComparisonResponse(String response, AIInsight insight) {
        parseTrendResponse(response, insight); // Mismo formato
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
