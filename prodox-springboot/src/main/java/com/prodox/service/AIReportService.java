// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.prodox.dto.ai.*;
import com.prodox.dto.analytics.*;
import com.prodox.entity.DireccionDeseableMetrica;
import com.prodox.entity.Metrica;
import com.prodox.entity.ProjectMember;
import com.prodox.entity.Sprint;
import com.prodox.repository.MetricaRepository;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.repository.SprintRepository;
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
    private final MetricaRepository metricaRepository;
    private final AgileAnalyticsService analyticsService;
    private final AIInsightsService insightsService;
    private final GeminiService geminiService;
    
    /**
     * Contenedor interno para métricas agregadas y detalladas.
     * V50 Fase 5C: Permite pasar ambos datos sin cambiar firmas públicas.
     */
    private record MetricasConDetalle(
            Map<String, BigDecimal> promediosPorCategoria,
            List<MetricaDetalleDto> detalleMetricas
    ) {}
    
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
        
        // 2. Validar autorización (generar reportes es una acción restringida al Scrum Master del proyecto)
        validateScrumMasterAccess(userId, sprint.getProyectoId());
        
        // 3. Recopilar datos objetivos
        MetricasConDetalle metricas = getSprintMetricsConDetalle(sprintId);
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
            // Bloque de seguridad Logging/Exposición (H2, Bloque 8A): nunca
            // registrar e.getMessage() ni el throwable completo acá — aunque
            // GeminiService ya sanitiza su propio mensaje, esta capa no debe
            // depender de eso para ser segura por sí misma (mismo principio
            // ya aplicado en MetricaIAService/MetricaAcademicaService).
            log.error("Gemini falló generando reporte para sprint {} (tipo de error: {})",
                    sprintId, e.getClass().getSimpleName());
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
                metricas.promediosPorCategoria(), // Mantiene compatibilidad con consumidores
                content.highlights(),
                content.concerns(),
                insights,
                content.recomendaciones(),
                Instant.now()
        );
    }
    
    /**
     * Valida que el usuario sea miembro del proyecto Y su Scrum Master (rol de liderazgo
     * a nivel de proyecto). Usado solo para la acción restringida de generar el reporte.
     */
    private void validateScrumMasterAccess(String userId, UUID proyectoId) {
        ProjectMember member = projectMemberRepository.findByProyectoIdAndUserId(proyectoId, userId)
                .orElseThrow(() -> {
                    log.warn("Usuario {} intentó acceder a sprint del proyecto {} sin autorización", userId, proyectoId);
                    return new SecurityException("No tienes acceso a este proyecto");
                });
        if (!"scrum_master".equals(member.getRol())) {
            log.warn("Usuario {} intentó generar un reporte del proyecto {} sin ser Scrum Master", userId, proyectoId);
            throw new SecurityException("Solo el Scrum Master del proyecto puede generar reportes");
        }
    }
    
    /**
     * V50 Fase 5C: Ahora retorna promedios Y detalles para evitar consultas N+1.
     */
    private MetricasConDetalle getSprintMetricsConDetalle(UUID sprintId) {
        try {
            SprintMetricsSummaryDto summary = analyticsService.getSprintMetricsSummary(sprintId);
            if (summary != null && summary.datosDisponibles()) {
                return new MetricasConDetalle(
                        summary.promediosPorCategoria(),
                        summary.detalleMetricas() != null ? summary.detalleMetricas() : List.of()
                );
            }
            return new MetricasConDetalle(Map.of(), List.of());
        } catch (Exception e) {
            log.warn("Error obteniendo métricas del sprint {}: {}", sprintId, e.getMessage());
            return new MetricasConDetalle(Map.of(), List.of());
        }
    }
    
    /**
     * Mantiene compatibilidad con código que solo necesita promedios.
     * @deprecated Usar getSprintMetricsConDetalle para aprovechar detalleMetricas
     */
    @Deprecated
    private Map<String, BigDecimal> getSprintMetrics(UUID sprintId) {
        return getSprintMetricsConDetalle(sprintId).promediosPorCategoria();
    }
    
    private List<AIInsightDto> getSprintInsights(UUID proyectoId, String userId) {
        try {
            return insightsService.getProjectInsights(proyectoId, userId);
        } catch (Exception e) {
            log.warn("Error obteniendo insights del proyecto {}: {}", proyectoId, e.getMessage());
            return List.of();
        }
    }
    
    private String buildReportContext(Sprint sprint, MetricasConDetalle metricas, List<AIInsightDto> insights) {
        StringBuilder context = new StringBuilder();
        
        Integer duracionDias = null;
        if (sprint.getFechaInicio() != null && sprint.getFechaFin() != null) {
            duracionDias = (int) java.time.temporal.ChronoUnit.DAYS.between(sprint.getFechaInicio(), sprint.getFechaFin());
        }
        
        // Bloque 10B-3: sprintGoal es texto libre (solo el Scrum Master puede
        // fijarlo — SprintController.siguiente — y solo el Scrum Master
        // puede generar este reporte, así que el impacto real de una
        // inyección aquí está auto-acotado al mismo usuario; se delimita
        // igual, por consistencia y como defensa en profundidad).
        context.append("SPRINT: ").append(sprint.getNumero()).append("\n");
        context.append("GOAL: ").append(PromptDataDelimiter.delimitar("SPRINT_GOAL",
                sprint.getSprintGoal() != null ? sprint.getSprintGoal() : "No definido")).append("\n");
        context.append("DURACIÓN: ").append(duracionDias != null ? duracionDias + " días" : "No definida").append("\n");
        context.append("FECHAS: ").append(sprint.getFechaInicio()).append(" a ").append(sprint.getFechaFin()).append("\n");
        
        // V50 Fase 5C: Usar detalleMetricas en lugar de consultas N+1
        String contextoDireccion = buildContextoMetricasDetalladas(metricas);
        if (!contextoDireccion.isEmpty()) {
            context.append(contextoDireccion);
        }
        
        context.append("\nMÉTRICAS:\n");
        if (metricas.promediosPorCategoria().isEmpty()) {
            context.append("- No hay métricas disponibles\n");
        } else {
            metricas.promediosPorCategoria().forEach((categoria, valor) -> 
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
        return PromptDataDelimiter.NOTA_DATOS_EXTERNOS + """
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
    
    /**
     * Construye contexto de dirección deseable para cada categoría con datos.
     * Reutiliza conceptualmente la lógica de AIInsightsService, pero adaptada
     * para reportes que agregan múltiples categorías.
     * 
     * V50 — Fase: Calidad analítica de módulos de IA (Prioridad 1)
     */
    /**
     * Construye contexto de métricas usando detalleMetricas.
     * 
     * V50 Fase 5C: Reemplaza buildContextoDireccionDeseable eliminando consultas N+1.
     * 
     * @param metricas Métricas con promedios y detalles individuales
     * @return Contexto formateado con dirección deseable por métrica
     */
    private String buildContextoMetricasDetalladas(MetricasConDetalle metricas) {
        if (metricas.detalleMetricas().isEmpty()) {
            // Fallback: sin detalles, usar el método anterior (mantiene compatibilidad)
            return buildContextoDireccionDeseableLegacy(metricas.promediosPorCategoria());
        }
        
        StringBuilder contexto = new StringBuilder("\nCONTEXTO DE MÉTRICAS:\n");
        
        // Agrupar detalles por categoría
        Map<String, List<MetricaDetalleDto>> porCategoria = new java.util.LinkedHashMap<>();
        for (MetricaDetalleDto detalle : metricas.detalleMetricas()) {
            porCategoria.computeIfAbsent(detalle.categoria(), k -> new ArrayList<>()).add(detalle);
        }
        
        for (Map.Entry<String, List<MetricaDetalleDto>> entry : porCategoria.entrySet()) {
            String categoria = entry.getKey();
            List<MetricaDetalleDto> metricasCategoria = entry.getValue();
            
            // Analizar direcciones en la categoría
            Set<String> direcciones = metricasCategoria.stream()
                    .map(MetricaDetalleDto::direccionDeseable)
                    .collect(java.util.stream.Collectors.toSet());
            
            contexto.append("- ").append(categoria).append(":\n");
            
            // Si todas tienen la misma dirección (o todas null), usar contexto agregado
            if (direcciones.size() == 1) {
                String direccionComun = direcciones.iterator().next();
                
                if (direccionComun == null) {
                    contexto.append("  Dirección NO configurada - describir cambios de manera NEUTRAL\n");
                } else {
                    switch (direccionComun) {
                        case "MAYOR_MEJOR" -> contexto.append(
                                "  Valores MAYORES pueden ser favorables, pero evalúa el contexto y la magnitud.\n");
                        case "MENOR_MEJOR" -> contexto.append(
                                "  Valores MENORES pueden ser favorables, pero evalúa el contexto y la magnitud.\n");
                        case "OBJETIVO" -> contexto.append(
                                "  Busca alcanzar valores OBJETIVO específicos (ver métricas individuales).\n");
                    }
                }
            } else {
                // Direcciones mixtas: listar métricas individuales
                contexto.append("  Esta categoría contiene métricas con direcciones DIFERENTES:\n");
            }
            
            // Listar métricas individuales con sus detalles
            for (MetricaDetalleDto detalle : metricasCategoria) {
                contexto.append("    • ").append(detalle.metricaNombre())
                        .append(": promedio ").append(String.format("%.2f", detalle.promedio()));
                
                if (detalle.direccionDeseable() != null) {
                    switch (detalle.direccionDeseable()) {
                        case "MAYOR_MEJOR" -> contexto.append(" [mayor es mejor]");
                        case "MENOR_MEJOR" -> contexto.append(" [menor es mejor]");
                        case "OBJETIVO" -> {
                            if (detalle.valorObjetivo() != null) {
                                BigDecimal cumplimiento = detalle.porcentajeCumplimientoObjetivo();
                                contexto.append(String.format(" [objetivo: %.2f, cumplimiento: %.1f%%]",
                                        detalle.valorObjetivo(), cumplimiento != null ? cumplimiento : BigDecimal.ZERO));
                            } else {
                                contexto.append(" [objetivo: no configurado]");
                            }
                        }
                    }
                } else {
                    contexto.append(" [dirección no configurada]");
                }
                
                contexto.append(String.format(" (rango: %.2f-%.2f, %d registros)\n",
                        detalle.min(), detalle.max(), detalle.totalRegistros()));
            }
        }
        
        contexto.append("\n");
        return contexto.toString();
    }
    
    /**
     * Método legacy que consulta el repositorio.
     * V50 Fase 5C: Solo se usa como fallback cuando detalleMetricas no está disponible.
     * 
     * @deprecated Consultas N+1 - solo para compatibilidad temporal
     */
    @Deprecated
    private String buildContextoDireccionDeseableLegacy(Map<String, BigDecimal> promediosPorCategoria) {
        return buildContextoDireccionDeseable(promediosPorCategoria);
    }

    private String buildContextoDireccionDeseable(Map<String, BigDecimal> promediosPorCategoria) {
        if (promediosPorCategoria == null || promediosPorCategoria.isEmpty()) {
            return "";
        }
        
        StringBuilder contexto = new StringBuilder("\nCONTEXTO DE MÉTRICAS:\n");
        
        for (String categoria : promediosPorCategoria.keySet()) {
            List<Metrica> metricas = metricaRepository.findByCategoriaNombre(categoria);
            
            if (metricas.isEmpty()) {
                contexto.append("- ").append(categoria)
                        .append(": dirección NO configurada - describir cambios de manera NEUTRAL\n");
                continue;
            }
            
            // Analizar direcciones en la categoría
            Set<String> direcciones = new HashSet<>();
            for (Metrica m : metricas) {
                direcciones.add(m.getDireccionDeseable());
            }
            
            // Caso 1: Todas las métricas tienen la misma dirección (o todas NULL)
            if (direcciones.size() == 1) {
                String direccionComun = direcciones.iterator().next();
                DireccionDeseableMetrica direccion = DireccionDeseableMetrica.fromString(direccionComun);
                
                if (direccion == null) {
                    contexto.append("- ").append(categoria)
                            .append(": dirección NO configurada - describir cambios de manera NEUTRAL\n");
                } else {
                    contexto.append("- ").append(categoria).append(": ");
                    
                    switch (direccion) {
                        case MAYOR_MEJOR -> contexto.append(
                                "valores MAYORES pueden ser favorables, pero evalúa el contexto y la magnitud. "
                                + "Un aumento excesivo puede indicar anomalías.\n");
                        case MENOR_MEJOR -> contexto.append(
                                "valores MENORES pueden ser favorables, pero evalúa el contexto y la magnitud. "
                                + "Una reducción excesiva puede indicar falta de actividad.\n");
                        case OBJETIVO -> {
                            // Buscar valor objetivo común
                            BigDecimal valorObjetivo = null;
                            Set<BigDecimal> objetivos = new HashSet<>();
                            for (Metrica m : metricas) {
                                if (m.getValorObjetivo() != null) {
                                    objetivos.add(m.getValorObjetivo());
                                }
                            }
                            if (objetivos.size() == 1) {
                                valorObjetivo = objetivos.iterator().next();
                            }
                            
                            if (valorObjetivo != null) {
                                contexto.append(String.format(
                                        "busca alcanzar un valor OBJETIVO de %.2f. "
                                        + "Evalúa qué tan cerca está el valor del objetivo, "
                                        + "NO solo si sube o baja.\n", valorObjetivo));
                            } else {
                                contexto.append(
                                        "busca alcanzar un valor OBJETIVO, pero el valor específico no está disponible. "
                                        + "Evalúa el cambio de manera neutral.\n");
                            }
                        }
                    }
                }
            } else {
                // Caso 2: Direcciones mixtas en la categoría
                contexto.append("- ").append(categoria)
                        .append(": esta categoría contiene métricas con direcciones DIFERENTES. "
                        + "NO clasifiques el promedio como mejora o deterioro general. Describir de manera NEUTRAL.\n");
            }
        }
        
        contexto.append("\n");
        return contexto.toString();
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
