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
 * Servicio para generar reportes ejecutivos de sprint usando IA.
 * 
 * Combina datos objetivos del AgileAnalyticsService con interpretación
 * y recomendaciones generadas por Gemini.
 * 
 * IMPORTANTE:
 * - Gemini NO accede directamente a PostgreSQL
 * - Solo interpreta datos ya calculados por otros servicios
 * - NO inventa métricas ni fechas
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AIReportService {
    
    private final SprintRepository sprintRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final AgileAnalyticsService analyticsService;
    private final AIInsightsService insightsService;
    private final GeminiService geminiService;
    
    /**
     * Genera un reporte ejecutivo para el sprint especificado.
     * 
     * @param sprintId ID del sprint
     * @param userId ID del usuario (para autorización)
     * @return Reporte ejecutivo generado
     * @throws IllegalArgumentException si el sprint no existe
     * @throws SecurityException si el usuario no tiene acceso al proyecto
     */
    public AISprintReportDto generateReport(UUID sprintId, String userId) {
        log.info("Generando reporte para sprint={} usuario={}", sprintId, userId);
        
        // 1. Validar sprint
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado: " + sprintId));
        
        // 2. Validar autorización
        validateUserAccess(userId, sprint.getProyectoId());
        
        // 3. Recopilar datos objetivos
        Map<String, BigDecimal> metricas = getSprintMetrics(sprintId);
        List<AIInsightDto> insights = getSprintInsights(sprint.getProyectoId(), userId);
        
        // 4. Construir contexto para Gemini
        String context = buildReportContext(sprint, metricas, insights);

        // 5. Generar interpretación con Gemini
        // FASE 23: a diferencia de getSprintMetrics/getSprintInsights (que sí
        // degradan con valores por defecto), esta llamada NO tenía try/catch —
        // cualquier fallo de Gemini (cuota agotada, HTTP error, red, texto
        // vacío — ver GeminiService.generate()) se propagaba sin capturar y
        // terminaba como un HTTP 500 opaco (causa raíz confirmada en FASE 22
        // con un 429 RESOURCE_EXHAUSTED real de la API de Gemini).
        String geminiResponse;
        try {
            geminiResponse = geminiService.generate(buildReportPrompt(context));
        } catch (Exception e) {
            log.error("Gemini falló generando reporte para sprint {}: {}", sprintId, e.getMessage(), e);
            throw new ReporteIANoDisponibleException(
                    "No se pudo generar el reporte: el servicio de IA no respondió correctamente. Intenta nuevamente en unos segundos.",
                    e);
        }

        // 6. Parsear respuesta
        ReportContent content = parseReportResponse(geminiResponse);
        
        // 7. Construir DTO
        return new AISprintReportDto(
                sprintId,
                sprint.getNumero(),
                sprint.getSprintGoal(),
                sprint.getFechaInicio(),
                sprint.getFechaFin(),
                content.resumen(),
                metricas,
                content.highlights(),
                content.concerns(),
                insights,
                content.recomendaciones(),
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
    
    private Map<String, BigDecimal> getSprintMetrics(UUID sprintId) {
        try {
            SprintMetricsSummaryDto summary = analyticsService.getSprintMetricsSummary(sprintId);
            return summary != null && summary.datosDisponibles() ? summary.promediosPorCategoria() : Map.of();
        } catch (Exception e) {
            log.warn("Error obteniendo métricas del sprint {}: {}", sprintId, e.getMessage());
            return Map.of();
        }
    }
    
    private List<AIInsightDto> getSprintInsights(UUID proyectoId, String userId) {
        try {
            return insightsService.getProjectInsights(proyectoId, userId);
        } catch (Exception e) {
            log.warn("Error obteniendo insights del proyecto {}: {}", proyectoId, e.getMessage());
            return List.of();
        }
    }
    
    private String buildReportContext(Sprint sprint, Map<String, BigDecimal> metricas, List<AIInsightDto> insights) {
        StringBuilder context = new StringBuilder();
        
        Integer duracionDias = null;
        if (sprint.getFechaInicio() != null && sprint.getFechaFin() != null) {
            duracionDias = (int) java.time.temporal.ChronoUnit.DAYS.between(sprint.getFechaInicio(), sprint.getFechaFin());
        }
        
        context.append("SPRINT: ").append(sprint.getNumero()).append("\n");
        context.append("GOAL: ").append(sprint.getSprintGoal() != null ? sprint.getSprintGoal() : "No definido").append("\n");
        context.append("DURACIÓN: ").append(duracionDias != null ? duracionDias + " días" : "No definida").append("\n");
        context.append("FECHAS: ").append(sprint.getFechaInicio()).append(" a ").append(sprint.getFechaFin()).append("\n\n");
        
        context.append("MÉTRICAS:\n");
        if (metricas.isEmpty()) {
            context.append("- No hay métricas disponibles\n");
        } else {
            metricas.forEach((categoria, valor) -> 
                context.append("- ").append(categoria).append(": ").append(valor).append("\n"));
        }
        
        context.append("\nINSIGHTS DETECTADOS:\n");
        if (insights.isEmpty()) {
            context.append("- No hay insights disponibles\n");
        } else {
            insights.forEach(insight -> {
                context.append("- ").append(insight.type()).append(": ").append(insight.title())
                        .append(" (Severidad: ").append(insight.severity()).append(")\n");
                context.append("  ").append(insight.description()).append("\n");
            });
        }
        
        return context.toString();
    }
    
    private String buildReportPrompt(String context) {
        return """
                Eres un experto en metodologías Agile. Genera un reporte ejecutivo del sprint.
                
                DATOS OBJETIVOS:
                %s
                
                INSTRUCCIONES:
                1. NO inventes métricas. Usa solo los datos provistos.
                2. Si no hay datos suficientes, menciona qué información falta.
                3. Genera:
                   - Resumen ejecutivo (2-3 oraciones)
                   - 3-5 highlights principales (si hay datos)
                   - 2-3 concerns (solo si los datos los sugieren)
                   - Recomendaciones accionables
                
                FORMATO DE RESPUESTA:
                RESUMEN: [texto]
                HIGHLIGHTS:
                - [punto 1]
                - [punto 2]
                CONCERNS:
                - [concern 1]
                RECOMENDACIONES: [texto]
                """.formatted(context);
    }
    
    private ReportContent parseReportResponse(String response) {
        String resumen = extractSection(response, "RESUMEN:", "HIGHLIGHTS:");
        List<String> highlights = extractList(response, "HIGHLIGHTS:", "CONCERNS:");
        List<String> concerns = extractList(response, "CONCERNS:", "RECOMENDACIONES:");
        String recomendaciones = extractSection(response, "RECOMENDACIONES:", null);

        // FASE 23: si NINGUNA de las 4 secciones esperadas aparece en la
        // respuesta, Gemini no siguió el formato solicitado — no es el caso
        // legítimo de "datos insuficientes" (que Gemini sí reporta dentro de
        // RESUMEN: siguiendo el formato pedido), sino una respuesta que no se
        // puede interpretar. Antes esto se disfrazaba de reporte válido con
        // texto de relleno ("Error procesando el reporte"); ahora se trata
        // igual que un fallo de Gemini: no se persiste nada (este servicio
        // nunca persiste) y no se le muestra al usuario un reporte falso.
        if (resumen == null && highlights.isEmpty() && concerns.isEmpty() && recomendaciones == null) {
            throw new ReporteIANoDisponibleException(
                    "La IA devolvió una respuesta que no se pudo interpretar. Intenta generar el reporte nuevamente.",
                    null);
        }

        return new ReportContent(
                resumen != null ? resumen : "Datos insuficientes para generar resumen",
                highlights,
                concerns,
                recomendaciones != null ? recomendaciones : "No se pudieron generar recomendaciones"
        );
    }
    
    private String extractSection(String response, String startMarker, String endMarker) {
        int startIndex = response.indexOf(startMarker);
        if (startIndex == -1) return null;
        
        startIndex += startMarker.length();
        int endIndex = endMarker != null ? response.indexOf(endMarker, startIndex) : response.length();
        if (endIndex == -1) endIndex = response.length();
        
        return response.substring(startIndex, endIndex).trim();
    }
    
    private List<String> extractList(String response, String startMarker, String endMarker) {
        String section = extractSection(response, startMarker, endMarker);
        if (section == null || section.isEmpty()) return List.of();
        
        return Arrays.stream(section.split("\n"))
                .map(String::trim)
                .filter(line -> line.startsWith("-"))
                .map(line -> line.substring(1).trim())
                .filter(line -> !line.isEmpty())
                .toList();
    }
    
    /**
     * Record interno para contenido del reporte parseado de Gemini.
     */
    private record ReportContent(
            String resumen,
            List<String> highlights,
            List<String> concerns,
            String recomendaciones
    ) {}
}
