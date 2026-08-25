// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.ai.*;
import com.mpdia.dto.analytics.*;
import com.mpdia.entity.Sprint;
import com.mpdia.repository.ProjectMemberRepository;
import com.mpdia.repository.SprintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Servicio para generar retrospectivas inteligentes de sprint usando IA.
 * 
 * Combina datos del sprint actual con comparación histórica y genera
 * observaciones y preguntas para la retrospectiva del equipo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AIRetrospectiveService {
    
    private final SprintRepository sprintRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final AgileAnalyticsService analyticsService;
    private final AIInsightsService insightsService;
    private final GeminiService geminiService;
    
    /**
     * Genera una retrospectiva inteligente para el sprint especificado.
     */
    public AIRetrospectiveDto generateRetrospective(UUID sprintId, String userId) {
        log.info("Generando retrospectiva para sprint={} usuario={}", sprintId, userId);
        
        // 1. Validar sprint
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado: " + sprintId));
        
        // 2. Validar autorización
        validateUserAccess(userId, sprint.getProyectoId());
        
        // 3. Recopilar datos
        SprintMetricsSummaryDto currentMetrics = getCurrentMetrics(sprintId);
        Optional<Sprint> previousSprint = getPreviousSprint(sprint);
        SprintComparisonDto comparison = null;
        
        if (previousSprint.isPresent()) {
            try {
                // FASE 12.8: el orden debe ser (anterior, actual) para que variación/dirección
                // representen "sprint anterior → sprint actual", coherente con el texto narrativo
                // "Sprint anterior: X" que construye buildRetrospectiveContext.
                comparison = analyticsService.compareSprints(previousSprint.get().getId(), sprintId);
            } catch (Exception e) {
                log.warn("Error comparando sprints: {}", e.getMessage());
            }
        }
        
        List<AIInsightDto> insights = getSprintInsights(sprint.getProyectoId(), userId);
        List<RiskDto> risks = getRisks(sprint.getProyectoId());
        
        // 4. Construir contexto
        String context = buildRetrospectiveContext(sprint, currentMetrics, previousSprint.orElse(null), comparison, insights, risks);
        
        // 5. Generar con Gemini
        // FASE 24: a diferencia de getCurrentMetrics/getSprintInsights/getRisks
        // (que sí degradan con valores por defecto), esta llamada NO tenía
        // try/catch — cualquier fallo de Gemini (cuota agotada, HTTP error,
        // red, texto vacío — ver GeminiService.generate()) se propagaba sin
        // capturar y terminaba como un HTTP 500 opaco (mismo defecto que
        // AIReportService tenía antes de FASE 23, confirmado en vivo con un
        // 429 RESOURCE_EXHAUSTED real de Gemini).
        String geminiResponse;
        try {
            geminiResponse = geminiService.generate(buildRetrospectivePrompt(context));
        } catch (Exception e) {
            log.error("Gemini falló generando retrospectiva para sprint {}: {}", sprintId, e.getMessage(), e);
            throw new RetrospectivaIANoDisponibleException(
                    "No se pudo generar la retrospectiva: el servicio de IA no respondió correctamente. Intenta nuevamente en unos segundos.",
                    e);
        }

        // 6. Parsear respuesta
        RetrospectiveContent content = parseRetrospectiveResponse(geminiResponse);
        
        // 7. Construir DTO
        return new AIRetrospectiveDto(
                sprintId,
                sprint.getNumero(),
                sprint.getSprintGoal(),
                sprint.getFechaInicio(),
                sprint.getFechaFin(),
                content.whatWentWell(),
                content.whatCouldImprove(),
                content.risks(),
                content.recommendations(),
                content.questionsForTeam(),
                Instant.now()
        );
    }
    
    private void validateUserAccess(String userId, UUID proyectoId) {
        boolean hasAccess = projectMemberRepository.existsByProyectoIdAndUserId(proyectoId, userId);
        if (!hasAccess) {
            log.warn("Usuario {} intentó acceder a sprint del proyecto {} sin autorización", userId, proyectoId);
            throw new SecurityException("No tienes acceso a este proyecto");
        }
    }
    
    private SprintMetricsSummaryDto getCurrentMetrics(UUID sprintId) {
        try {
            return analyticsService.getSprintMetricsSummary(sprintId);
        } catch (Exception e) {
            log.warn("Error obteniendo métricas del sprint {}: {}", sprintId, e.getMessage());
            return null;
        }
    }
    
    private Optional<Sprint> getPreviousSprint(Sprint currentSprint) {
        List<Sprint> sprints = sprintRepository.findByProyectoIdOrderByNumeroDesc(currentSprint.getProyectoId());
        
        return sprints.stream()
                .filter(s -> s.getNumero() < currentSprint.getNumero())
                .filter(s -> "finalizado".equalsIgnoreCase(s.getEstado()))
                .findFirst();
    }
    
    private List<AIInsightDto> getSprintInsights(UUID proyectoId, String userId) {
        try {
            return insightsService.getProjectInsights(proyectoId, userId);
        } catch (Exception e) {
            log.warn("Error obteniendo insights del proyecto {}: {}", proyectoId, e.getMessage());
            return List.of();
        }
    }
    
    private List<RiskDto> getRisks(UUID proyectoId) {
        try {
            return analyticsService.identifyRisks(proyectoId);
        } catch (Exception e) {
            log.warn("Error obteniendo riesgos del proyecto {}: {}", proyectoId, e.getMessage());
            return List.of();
        }
    }
    
    private String buildRetrospectiveContext(Sprint sprint, SprintMetricsSummaryDto currentMetrics,
                                           Sprint previousSprint, SprintComparisonDto comparison,
                                           List<AIInsightDto> insights, List<RiskDto> risks) {
        StringBuilder context = new StringBuilder();
        
        Integer duracionDias = null;
        if (sprint.getFechaInicio() != null && sprint.getFechaFin() != null) {
            duracionDias = (int) java.time.temporal.ChronoUnit.DAYS.between(sprint.getFechaInicio(), sprint.getFechaFin());
        }
        
        // Datos del sprint actual
        context.append("SPRINT ACTUAL: ").append(sprint.getNumero()).append("\n");
        context.append("GOAL: ").append(sprint.getSprintGoal() != null ? sprint.getSprintGoal() : "No definido").append("\n");
        context.append("DURACIÓN: ").append(duracionDias != null ? duracionDias + " días" : "No definida").append("\n");
        
        context.append("\nMÉTRICAS ACTUALES:\n");
        if (currentMetrics == null || !currentMetrics.datosDisponibles()) {
            context.append("- No hay métricas disponibles\n");
        } else {
            currentMetrics.promediosPorCategoria().forEach((categoria, valor) -> 
                context.append("- ").append(categoria).append(": ").append(valor).append("\n"));
        }
        
        // Comparación con sprint anterior
        context.append("\nCOMPARACIÓN CON SPRINT ANTERIOR:\n");
        if (previousSprint == null) {
            context.append("- Este es el primer sprint del proyecto\n");
        } else if (comparison == null || !comparison.datosDisponibles()) {
            context.append("- No se pudo realizar comparación con sprint ").append(previousSprint.getNumero()).append("\n");
        } else {
            context.append("- Sprint anterior: ").append(previousSprint.getNumero()).append("\n");
            comparison.tendencia().forEach((categoria, direccion) -> {
                BigDecimal varPorc = comparison.variacionPorcentual().getOrDefault(categoria, BigDecimal.ZERO);
                context.append("- ").append(categoria).append(": ")
                        .append(direccion)
                        .append(" (").append(varPorc).append("%)\\n");
            });
        }
        
        // Insights
        context.append("\nINSIGHTS DETECTADOS:\n");
        if (insights.isEmpty()) {
            context.append("- No hay insights disponibles\n");
        } else {
            insights.forEach(insight -> 
                context.append("- ").append(insight.type()).append(": ").append(insight.title()).append("\n"));
        }
        
        // Riesgos
        context.append("\nRIESGOS IDENTIFICADOS:\n");
        if (risks.isEmpty()) {
            context.append("- No se detectaron riesgos\n");
        } else {
            risks.forEach(risk -> 
                context.append("- ").append(risk.titulo()).append(": ").append(risk.evidencia()).append("\n"));
        }
        
        return context.toString();
    }
    
    private String buildRetrospectivePrompt(String context) {
        return """
                Eres un facilitador experto de retrospectivas Agile. Genera observaciones y preguntas para la retrospectiva.
                
                DATOS DEL SPRINT:
                %s
                
                INSTRUCCIONES:
                1. Identifica qué funcionó bien según los datos objetivos
                2. Identifica oportunidades de mejora basándote en los datos
                3. Resalta riesgos observados
                4. Genera preguntas abiertas para discusión del equipo
                5. NO asumas causas. Pregunta sobre ellas.
                6. Si no hay datos suficientes, enfócate en preguntas sobre el proceso
                
                FORMATO:
                WHAT WENT WELL:
                - [observación basada en datos]
                
                WHAT COULD IMPROVE:
                - [observación basada en datos]
                
                RISKS:
                - [riesgo]
                
                RECOMMENDATIONS:
                - [recomendación]
                
                QUESTIONS FOR TEAM:
                - [pregunta abierta]
                """.formatted(context);
    }
    
    private static final List<String> MARCADORES_RETROSPECTIVA = List.of(
            "WHAT WENT WELL:", "WHAT COULD IMPROVE:", "RISKS:", "RECOMMENDATIONS:", "QUESTIONS FOR TEAM:");

    private RetrospectiveContent parseRetrospectiveResponse(String response) {
        // FASE 24: si NINGUNO de los 5 marcadores esperados aparece en la
        // respuesta, Gemini no siguió el formato solicitado — no es el caso
        // legítimo de "datos insuficientes" (donde Gemini sí escribe los
        // marcadores pero deja la sección vacía, ver test
        // generateRetrospective_datosInsuficientes_generaRetrospectiva), sino
        // una respuesta que no se puede interpretar en absoluto. Antes esto
        // se disfrazaba de retrospectiva válida con texto de relleno; ahora
        // se trata igual que un fallo de Gemini: este servicio nunca
        // persiste nada, así que no queda ningún dato falso guardado, y no
        // se le muestra al usuario una retrospectiva ficticia.
        boolean ningunMarcadorPresente = MARCADORES_RETROSPECTIVA.stream().noneMatch(response::contains);
        if (ningunMarcadorPresente) {
            throw new RetrospectivaIANoDisponibleException(
                    "La IA devolvió una respuesta que no se pudo interpretar. Intenta generar la retrospectiva nuevamente.",
                    null);
        }

        List<String> whatWentWell = extractList(response, "WHAT WENT WELL:", "WHAT COULD IMPROVE:");
        List<String> whatCouldImprove = extractList(response, "WHAT COULD IMPROVE:", "RISKS:");
        List<String> risks = extractList(response, "RISKS:", "RECOMMENDATIONS:");
        List<String> recommendations = extractList(response, "RECOMMENDATIONS:", "QUESTIONS FOR TEAM:");
        List<String> questionsForTeam = extractList(response, "QUESTIONS FOR TEAM:", null);

        return new RetrospectiveContent(
                whatWentWell.isEmpty() ? List.of("Datos insuficientes para identificar aspectos positivos") : whatWentWell,
                whatCouldImprove.isEmpty() ? List.of("Considerar registrar más métricas para futuros sprints") : whatCouldImprove,
                risks,
                recommendations.isEmpty() ? List.of("Continuar con las prácticas actuales del equipo") : recommendations,
                questionsForTeam.isEmpty() ? List.of("¿Qué podemos hacer para mejorar nuestro proceso?") : questionsForTeam
        );
    }
    
    private List<String> extractList(String response, String startMarker, String endMarker) {
        int startIndex = response.indexOf(startMarker);
        if (startIndex == -1) return List.of();
        
        startIndex += startMarker.length();
        int endIndex = endMarker != null ? response.indexOf(endMarker, startIndex) : response.length();
        if (endIndex == -1) endIndex = response.length();
        
        String section = response.substring(startIndex, endIndex).trim();
        if (section.isEmpty()) return List.of();
        
        return Arrays.stream(section.split("\n"))
                .map(String::trim)
                .filter(line -> line.startsWith("-"))
                .map(line -> line.substring(1).trim())
                .filter(line -> !line.isEmpty())
                .toList();
    }
    
    /**
     * Record interno para contenido de retrospectiva parseado de Gemini.
     */
    private record RetrospectiveContent(
            List<String> whatWentWell,
            List<String> whatCouldImprove,
            List<String> risks,
            List<String> recommendations,
            List<String> questionsForTeam
    ) {}
}
