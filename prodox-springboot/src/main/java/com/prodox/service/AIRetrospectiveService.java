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
    private final MetricaRepository metricaRepository;
    private final AgileAnalyticsService analyticsService;
    private final AIInsightsService insightsService;
    private final GeminiService geminiService;
    
    /**
     * Contenedor interno para métricas agregadas y detalladas.
     * V50 Fase 5D: Permite pasar ambos datos sin cambiar firmas públicas.
     */
    private record MetricasConDetalle(
            Map<String, BigDecimal> promediosPorCategoria,
            List<MetricaDetalleDto> detalleMetricas
    ) {}
    
    /**
     * Genera una retrospectiva inteligente para el sprint especificado.
     */
    public AIRetrospectiveDto generateRetrospective(UUID sprintId, String userId) {
        log.info("Generando retrospectiva para sprint={} usuario={}", sprintId, userId);
        
        // 1. Validar sprint
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado: " + sprintId));
        
        // 2. Validar autorización (generar retrospectiva es una acción restringida al Scrum Master del proyecto)
        validateScrumMasterAccess(userId, sprint.getProyectoId());
        
        // 3. Recopilar datos
        MetricasConDetalle currentMetrics = getCurrentMetricsConDetalle(sprintId);
        Optional<Sprint> previousSprint = getPreviousSprint(sprint);
        MetricasConDetalle previousMetrics = getPreviousMetricsConDetalle(previousSprint.orElse(null));
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
        String context = buildRetrospectiveContext(sprint, currentMetrics, previousSprint.orElse(null), 
                previousMetrics, comparison, insights, risks);
        
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
            // Bloque de seguridad Logging/Exposición (H2, Bloque 8A): nunca
            // registrar e.getMessage() ni el throwable completo acá — mismo
            // principio ya aplicado en MetricaIAService/MetricaAcademicaService.
            log.error("Gemini falló generando retrospectiva para sprint {} (tipo de error: {})",
                    sprintId, e.getClass().getSimpleName());
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
    
    /**
     * Valida que el usuario sea miembro del proyecto Y su Scrum Master (rol de liderazgo
     * a nivel de proyecto). Usado solo para la acción restringida de generar la retrospectiva.
     */
    private void validateScrumMasterAccess(String userId, UUID proyectoId) {
        ProjectMember member = projectMemberRepository.findByProyectoIdAndUserId(proyectoId, userId)
                .orElseThrow(() -> {
                    log.warn("Usuario {} intentó acceder a sprint del proyecto {} sin autorización", userId, proyectoId);
                    return new SecurityException("No tienes acceso a este proyecto");
                });
        if (!"scrum_master".equals(member.getRol())) {
            log.warn("Usuario {} intentó generar una retrospectiva del proyecto {} sin ser Scrum Master", userId, proyectoId);
            throw new SecurityException("Solo el Scrum Master del proyecto puede generar retrospectivas");
        }
    }
    
    /**
     * V50 Fase 5D: Ahora retorna promedios Y detalles para evitar consultas N+1.
     */
    private MetricasConDetalle getCurrentMetricsConDetalle(UUID sprintId) {
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
     * Mantiene compatibilidad con código que solo necesita el DTO.
     * @deprecated Usar getCurrentMetricsConDetalle para aprovechar detalleMetricas
     */
    @Deprecated
    private SprintMetricsSummaryDto getCurrentMetrics(UUID sprintId) {
        try {
            return analyticsService.getSprintMetricsSummary(sprintId);
        } catch (Exception e) {
            log.warn("Error obteniendo métricas del sprint {}: {}", sprintId, e.getMessage());
            return null;
        }
    }
    
    /**
     * V50 Fase 5D: Obtiene métricas del sprint anterior con detalles.
     */
    private MetricasConDetalle getPreviousMetricsConDetalle(Sprint previousSprint) {
        if (previousSprint == null) {
            return new MetricasConDetalle(Map.of(), List.of());
        }
        try {
            SprintMetricsSummaryDto summary = analyticsService.getSprintMetricsSummary(previousSprint.getId());
            if (summary != null && summary.datosDisponibles()) {
                return new MetricasConDetalle(
                        summary.promediosPorCategoria(),
                        summary.detalleMetricas() != null ? summary.detalleMetricas() : List.of()
                );
            }
            return new MetricasConDetalle(Map.of(), List.of());
        } catch (Exception e) {
            log.warn("Error obteniendo métricas del sprint anterior {}: {}", previousSprint.getId(), e.getMessage());
            return new MetricasConDetalle(Map.of(), List.of());
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
    
    private String buildRetrospectiveContext(Sprint sprint, MetricasConDetalle currentMetrics,
                                           Sprint previousSprint, MetricasConDetalle previousMetrics,
                                           SprintComparisonDto comparison,
                                           List<AIInsightDto> insights, List<RiskDto> risks) {
        StringBuilder context = new StringBuilder();
        
        Integer duracionDias = null;
        if (sprint.getFechaInicio() != null && sprint.getFechaFin() != null) {
            duracionDias = (int) java.time.temporal.ChronoUnit.DAYS.between(sprint.getFechaInicio(), sprint.getFechaFin());
        }
        
        // Datos del sprint actual
        // Bloque 10B-3: mismo caso que AIReportService — sprintGoal libre,
        // fijado y leído solo por el Scrum Master (impacto auto-acotado),
        // delimitado igual por consistencia/defensa en profundidad.
        context.append("SPRINT ACTUAL: ").append(sprint.getNumero()).append("\n");
        context.append("GOAL: ").append(PromptDataDelimiter.delimitar("SPRINT_GOAL",
                sprint.getSprintGoal() != null ? sprint.getSprintGoal() : "No definido")).append("\n");
        context.append("DURACIÓN: ").append(duracionDias != null ? duracionDias + " días" : "No definida").append("\n");
        
        // V50 Fase 5D: Usar detalleMetricas en lugar de consultas N+1
        String contextoDireccion = buildContextoMetricasDetalladas("SPRINT ACTUAL", currentMetrics);
        if (!contextoDireccion.isEmpty()) {
            context.append(contextoDireccion);
        }
        
        context.append("\nMÉTRICAS ACTUALES:\n");
        if (currentMetrics.promediosPorCategoria().isEmpty()) {
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
            // V50 Fase 5D: Usar detalleMetricas del sprint anterior
            String contextoDireccionPrev = buildContextoMetricasDetalladas("SPRINT ANTERIOR", previousMetrics);
            if (!contextoDireccionPrev.isEmpty()) {
                context.append(contextoDireccionPrev);
            }
            
            context.append("- Sprint anterior: ").append(previousSprint.getNumero()).append("\n");
            comparison.tendencia().forEach((categoria, direccion) -> {
                BigDecimal varPorc = comparison.variacionPorcentual().getOrDefault(categoria, BigDecimal.ZERO);
                context.append("- ").append(categoria).append(": ")
                        .append(direccion)
                        .append(" (").append(varPorc).append("%)\n");
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
    
    /**
     * Construye contexto de métricas usando detalleMetricas.
     * 
     * V50 Fase 5D: Reemplaza buildContextoDireccionDeseable eliminando consultas N+1.
     * 
     * @param label "SPRINT ACTUAL" o "SPRINT ANTERIOR" para identificar el origen
     * @param metricas Métricas con promedios y detalles individuales
     * @return Contexto formateado con dirección deseable por métrica
     */
    private String buildContextoMetricasDetalladas(String label, MetricasConDetalle metricas) {
        if (metricas.detalleMetricas().isEmpty()) {
            // Fallback: sin detalles, usar el método anterior (mantiene compatibilidad)
            return buildContextoDireccionDeseableLegacy(metricas.promediosPorCategoria());
        }
        
        StringBuilder contexto = new StringBuilder("\nCONTEXTO DE MÉTRICAS (").append(label).append("):\n");
        
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
     * V50 Fase 5D: Solo se usa como fallback cuando detalleMetricas no está disponible.
     * 
     * @deprecated Consultas N+1 - solo para compatibilidad temporal
     */
    @Deprecated
    private String buildContextoDireccionDeseableLegacy(Map<String, BigDecimal> promediosPorCategoria) {
        return buildContextoDireccionDeseable(promediosPorCategoria);
    }

    /**
     * Construye contexto de dirección deseable para cada categoría con datos.
     * Adaptado de AIReportService.buildContextoDireccionDeseable() para uso en retrospectivas.
     * 
     * V50 — Fase 2B: Calidad analítica de módulos de IA (Prioridad 1)
     */
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
    
    private String buildRetrospectivePrompt(String context) {
        return PromptDataDelimiter.NOTA_DATOS_EXTERNOS + """
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
                
                INTERPRETACIÓN DE MÉTRICAS:
                - Respeta el CONTEXTO DE MÉTRICAS proporcionado arriba
                - MAYOR_MEJOR: un valor mayor puede ser favorable, pero evalúa contexto y magnitud (aumentos excesivos pueden indicar anomalías)
                - MENOR_MEJOR: un valor menor puede ser favorable, pero evalúa contexto y magnitud (reducciones excesivas pueden indicar falta de actividad)
                - OBJETIVO: evalúa cercanía al objetivo; NO asumas que subir o bajar es siempre positivo
                - Dirección NO configurada: describe cambios de manera NEUTRAL sin clasificar como bueno o malo
                - Direcciones DIFERENTES en una categoría: NO clasifiques el promedio como mejora o deterioro general
                - NO inventes direcciones basándote en nombres de métricas o categorías
                - NO inventes valores objetivo si no están disponibles
                - Utiliza datos y cálculos deterministas del backend; interpreta, pero NO recalcules
                
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
