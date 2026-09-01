// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.prodox.dto.ProyectoMetricaDto;
import com.prodox.dto.ResultadoMetricaDto;
import com.prodox.dto.ai.AIRetrospectiveDto;
import com.prodox.dto.ai.AgentResponse;
import com.prodox.dto.ai.ChatRequest;
import com.prodox.dto.ai.ChatResponse;
import com.prodox.dto.ai.gemini.*;
import com.prodox.dto.analytics.RiskDto;
import com.prodox.dto.analytics.SprintComparisonDto;
import com.prodox.dto.analytics.SprintMetricsSummaryDto;
import com.prodox.dto.analytics.TrendAnalysisDto;
import com.prodox.entity.AIChatMessage;
import com.prodox.entity.Proyecto;
import com.prodox.entity.Sprint;
import com.prodox.repository.AIChatMessageRepository;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.repository.ProyectoRepository;
import com.prodox.repository.SprintRepository;
import com.prodox.service.copilot.CopilotDomainGuard;
import com.prodox.service.copilot.CopilotIntentClassifier;
import com.prodox.service.copilot.CopilotToolsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio principal del AI Copilot.
 * 
 * Responsabilidades:
 * - Validar autorización del usuario sobre el proyecto
 * - Gestionar historial de conversación
 * - Construir contexto del usuario/proyecto/sprint
 * - Invocar AIAgentService con tools apropiados
 * - Guardar mensajes en base de datos
 * - Retornar respuesta estructurada
 * 
 * SEGURIDAD:
 * - SIEMPRE valida que el usuario pertenezca al proyecto
 * - NUNCA confía en proyectoId sin validar
 * - Limita el historial enviado al modelo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AICopilotService {

    private final AIAgentService aiAgentService;
    private final CopilotToolsService toolsService;
    private final CopilotDomainGuard domainGuard;
    private final AIChatMessageRepository chatMessageRepo;
    private final ProjectMemberRepository projectMemberRepo;
    private final ProyectoRepository proyectoRepo;
    private final SprintRepository sprintRepo;
    private final PlaneacionService planeacionService;
    private final MetricaAcademicaService metricaAcademicaService;
    private final AgileAnalyticsService agileAnalyticsService;
    private final GeminiService geminiService;
    private final AIRetrospectiveService aiRetrospectiveService;

    // FASE 12.4 — clasificador local de intención: determinístico, sin acceso a BD ni a
    // Spring, por eso se instancia directamente en vez de inyectarse como bean.
    private final CopilotIntentClassifier intentClassifier = new CopilotIntentClassifier();

    @Value("${prodox.ai.max-history-messages:10}")
    private int maxHistoryMessages;

    /**
     * Procesa un mensaje del usuario en el contexto de un proyecto.
     * 
     * @param request Request con mensaje, proyectoId y sprintId opcional
     * @param userId ID del usuario autenticado (desde JWT)
     * @return ChatResponse con la respuesta de la IA
     * @throws SecurityException si el usuario no tiene acceso al proyecto
     * @throws IllegalArgumentException si proyecto/sprint no existe
     */
    @Transactional
    public ChatResponse chat(ChatRequest request, String userId) {
        log.info("Chat request de userId={} para proyectoId={}", userId, request.proyectoId());

        // 1. VALIDAR AUTORIZACIÓN
        if (!projectMemberRepo.existsByProyectoIdAndUserId(request.proyectoId(), userId)) {
            log.warn("Usuario {} intentó acceder a proyecto {} sin autorización", 
                     userId, request.proyectoId());
            throw new SecurityException("No tienes acceso a este proyecto");
        }

        // 2. OBTENER DATOS DEL PROYECTO
        Proyecto proyecto = proyectoRepo.findById(request.proyectoId())
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado"));

        // 3. OBTENER DATOS DEL SPRINT (si se especificó)
        Sprint sprint = null;
        if (request.sprintId() != null) {
            sprint = sprintRepo.findById(request.sprintId())
                    .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado"));
            
            // Validar que el sprint pertenece al proyecto
            if (!sprint.getProyectoId().equals(request.proyectoId())) {
                log.warn("Usuario {} intentó acceder a sprint {} que no pertenece al proyecto {}", 
                         userId, request.sprintId(), request.proyectoId());
                throw new SecurityException("El sprint no pertenece a este proyecto");
            }
        }

        // 4. GUARDRAIL DE DOMINIO (FASE 12.2) — antes de tocar historial, prompt o el LLM.
        // Si el mensaje no pertenece al dominio PRODOX/Agile, se responde localmente y se
        // corta el flujo aquí: no se recupera historial, no se construye system instruction,
        // no se obtienen tools y NUNCA se invoca a AIAgentService/GeminiService. Tampoco se
        // persiste el mensaje rechazado, para que no vuelva a inyectarse como "historial" en
        // una futura llamada real al modelo.
        if (!domainGuard.isInDomain(request.message())) {
            log.info("Mensaje fuera de dominio PRODOX rechazado localmente (sin llamar al LLM) " +
                      "para userId={} proyectoId={}", userId, request.proyectoId());
            return new ChatResponse(
                domainGuard.respuestaFueraDeDominio(),
                List.of(),
                Instant.now(),
                false
            );
        }

        // 5. CLASIFICACIÓN DE INTENCIÓN LOCAL Y RESOLUCIÓN DETERMINÍSTICA (FASE 12.4)
        // Corre después del guardrail de dominio y antes de tocar historial, prompt o el LLM.
        // Solo dos intenciones muy acotadas (RESULTADO_METRICA, RESULTADO_ULTIMO_SPRINT) se
        // intentan resolver localmente, y SOLO si se pueden resolver sin ambigüedad contra los
        // datos reales y aislados del proyecto. Si la intención es desconocida, o la métrica/
        // sprint no se puede identificar con certeza, se continúa exactamente igual que antes
        // (historial + prompt + tools + Gemini) — "es preferible mandar la pregunta a Gemini
        // innecesariamente antes que responder incorrectamente con datos determinísticos".
        CopilotIntentClassifier.ClassifiedIntent intent = intentClassifier.classify(request.message());
        Optional<ChatResponse> respuestaDeterministica =
                resolverIntentoDeterministico(intent, request.proyectoId(), sprint, request.message(), userId);
        if (respuestaDeterministica.isPresent()) {
            log.info("Intención {} resuelta sin AIAgentService ni tools (máx. 1 llamada a " +
                     "GeminiService.generate(), sin function-calling) para userId={} proyectoId={}",
                     intent.type(), userId, request.proyectoId());
            return respuestaDeterministica.get();
        }

        // 6. RECUPERAR HISTORIAL RECIENTE
        List<Message> historialMessages = recuperarHistorial(userId, request.proyectoId());

        // 7. AGREGAR MENSAJE DEL USUARIO AL HISTORIAL
        historialMessages.add(Message.user(request.message()));

        // 8. CONSTRUIR SYSTEM INSTRUCTION
        String systemInstruction = construirSystemInstruction(proyecto, sprint);

        // 9. OBTENER TOOLS DISPONIBLES
        List<Tool> tools = toolsService.getAvailableTools();

        // 10. CREAR TOOL EXECUTOR CON CONTEXTO
        ToolExecutor executor = (toolName, args) ->
            toolsService.executeTool(toolName, args, userId, request.proyectoId());

        // 11. INVOCAR AI AGENT
        AgentResponse agentResponse;
        try {
            agentResponse = aiAgentService.processMessage(
                request.message(),
                tools,
                systemInstruction,
                executor
            );
        } catch (Exception e) {
            log.error("Error en AI Agent: {}", e.getMessage(), e);

            // Guardar mensaje del usuario aunque falle la IA
            guardarMensaje(userId, request.proyectoId(), request.sprintId(),
                          "user", request.message());

            throw new RuntimeException("Error al procesar mensaje con IA: " + e.getMessage());
        }

        // 12. GUARDAR MENSAJES EN BASE DE DATOS
        guardarMensaje(userId, request.proyectoId(), request.sprintId(),
                      "user", request.message());
        guardarMensaje(userId, request.proyectoId(), request.sprintId(),
                      "assistant", agentResponse.message());

        // 13. CONSTRUIR Y RETORNAR RESPUESTA
        return new ChatResponse(
            agentResponse.message(),
            agentResponse.toolsUsed(),
            Instant.now(),
            agentResponse.hasData()
        );
    }

    /**
     * FASE 12.4 — Intenta resolver la intención clasificada localmente, sin BD-global ni LLM.
     * Devuelve Optional.empty() cuando la intención es UNKNOWN o cuando no se puede resolver
     * sin ambigüedad; en ese caso el llamador debe continuar con el flujo normal (Gemini).
     */
    private Optional<ChatResponse> resolverIntentoDeterministico(
            CopilotIntentClassifier.ClassifiedIntent intent, UUID proyectoId, Sprint sprint,
            String mensajeOriginal, String userId) {
        return switch (intent.type()) {
            case RESULTADO_METRICA -> resolverResultadoMetrica(intent.metricaCandidata(), proyectoId, sprint);
            case RESULTADO_ULTIMO_SPRINT -> resolverResultadoUltimoSprint(sprint);
            case COMPARACION_SPRINTS -> resolverComparacionSprints(proyectoId, sprint, mensajeOriginal);
            case TENDENCIAS -> resolverTendencias(proyectoId, mensajeOriginal);
            case RIESGOS -> resolverRiesgos(proyectoId, mensajeOriginal);
            case RECOMENDACIONES -> resolverRecomendaciones(proyectoId, sprint, mensajeOriginal);
            case RETROSPECTIVA -> resolverRetrospectiva(sprint, userId);
            case UNKNOWN -> Optional.empty();
        };
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FASE 12.5 — Tipo B: datos deterministas (AgileAnalyticsService) + máximo UNA
    // llamada a GeminiService.generate() para redactar la interpretación. Nunca se usa
    // AIAgentService, tools, function-calling ni una segunda llamada al LLM. No se recupera
    // historial: el prompt se construye solo con los datos ya resueltos.
    // ═══════════════════════════════════════════════════════════════════════

    private static final String INSTRUCCION_TIPO_B =
        "Eres el AI Agile Copilot de PRODOX. A continuación recibes datos YA CALCULADOS por el " +
        "backend de PRODOX: son la única fuente de verdad, no los recalcules ni los cuestiones.\n" +
        "REGLAS ESTRICTAS:\n" +
        "1. No inventes métricas, valores, sprints ni datos que no aparezcan a continuación.\n" +
        "2. No modifiques ningún número.\n" +
        "3. No agregues información que no esté presente en los datos.\n" +
        "4. PRODOX no define si un aumento o una disminución de una métrica es 'mejor' o 'peor'. " +
        "NUNCA concluyas que el equipo 'mejoró' o 'empeoró' en términos generales: describe " +
        "únicamente el cambio observado (aumentó/disminuyó/se mantuvo estable) por categoría.\n" +
        "5. Si los datos no permiten concluir algo con certeza, dilo explícitamente en vez de asumir.\n" +
        "6. Responde en español, de forma breve y clara.\n\n";

    /**
     * "¿Mejoramos respecto al sprint anterior?" — compara el sprint activo contra el sprint
     * finalizado inmediatamente anterior en el MISMO proyecto (nunca contra otro proyecto).
     * Si no hay sprint activo o no existe un sprint anterior finalizado, no se puede resolver
     * sin ambigüedad: se retorna vacío y continúa el flujo actual.
     */
    private Optional<ChatResponse> resolverComparacionSprints(
            UUID proyectoId, Sprint sprintActivo, String preguntaUsuario) {
        if (sprintActivo == null) {
            return Optional.empty();
        }

        Optional<Sprint> anterior = obtenerSprintAnteriorFinalizado(proyectoId, sprintActivo);

        if (anterior.isEmpty()) {
            return Optional.empty();
        }

        SprintComparisonDto comparacion =
                agileAnalyticsService.compareSprints(anterior.get().getId(), sprintActivo.getId());

        if (comparacion.datosDisponibles() == null || !comparacion.datosDisponibles()) {
            return Optional.empty();
        }

        String datos = construirDatosComparacion(comparacion);
        String prompt = construirPromptTipoB(
                "comparación entre el sprint actual y el sprint anterior", datos, preguntaUsuario);
        return Optional.of(llamarGeminiTipoB(prompt));
    }

    private String construirDatosComparacion(SprintComparisonDto c) {
        StringBuilder sb = new StringBuilder();
        sb.append("Sprint anterior: Sprint ").append(c.sprint1Numero()).append("\n");
        sb.append("Sprint actual: Sprint ").append(c.sprint2Numero()).append("\n");
        sb.append("Por categoría (sprint anterior -> sprint actual, variación %, dirección):\n");
        for (String categoria : c.tendencia().keySet()) {
            BigDecimal valorAnterior = c.sprint1Metricas().getOrDefault(categoria, BigDecimal.ZERO);
            BigDecimal valorActual = c.sprint2Metricas().getOrDefault(categoria, BigDecimal.ZERO);
            BigDecimal variacionPorc = c.variacionPorcentual().getOrDefault(categoria, BigDecimal.ZERO);
            String direccion = c.tendencia().get(categoria);
            sb.append("- ").append(categoria).append(": ").append(valorAnterior).append(" -> ")
              .append(valorActual).append(" (").append(variacionPorc).append("%, ")
              .append(direccion).append(")\n");
        }
        return sb.toString();
    }

    /**
     * "¿Qué métricas mejoraron/empeoraron?" / "¿Cómo vienen las métricas?" — reutiliza
     * AgileAnalyticsService.getSprintTrends (mismo N=3 que usan AIInsightsService/identifyRisks)
     * sobre los sprints FINALIZADOS del proyecto activo. Si no hay suficientes sprints
     * finalizados para calcular tendencias, se retorna vacío y continúa el flujo actual.
     */
    private Optional<ChatResponse> resolverTendencias(UUID proyectoId, String preguntaUsuario) {
        List<TrendAnalysisDto> tendencias = agileAnalyticsService.getSprintTrends(proyectoId, null, 3);

        boolean hayDatos = tendencias.stream().anyMatch(t -> Boolean.TRUE.equals(t.datosDisponibles()));
        if (tendencias.isEmpty() || !hayDatos) {
            return Optional.empty();
        }

        String datos = construirDatosTendencias(tendencias);
        String prompt = construirPromptTipoB(
                "tendencias de las métricas en los últimos sprints finalizados", datos, preguntaUsuario);
        return Optional.of(llamarGeminiTipoB(prompt));
    }

    private String construirDatosTendencias(List<TrendAnalysisDto> tendencias) {
        StringBuilder sb = new StringBuilder();
        for (TrendAnalysisDto t : tendencias) {
            if (!Boolean.TRUE.equals(t.datosDisponibles())) continue;
            sb.append("- ").append(t.categoria()).append(": tendencia ").append(t.tendenciaGeneral())
              .append(" (variación total ").append(t.variacionTotal()).append("% sobre ")
              .append(t.numeroSprints()).append(" sprints, promedio ").append(t.promedioGeneral())
              .append(")\n");
        }
        return sb.toString();
    }

    /**
     * "¿Qué riesgos detectas?" — reutiliza AgileAnalyticsService.identifyRisks(proyectoId), ya
     * aislado por proyecto. Si no hay riesgos, se responde localmente sin llamar a Gemini (no
     * hace falta interpretación para decir que no se detectó nada).
     */
    private Optional<ChatResponse> resolverRiesgos(UUID proyectoId, String preguntaUsuario) {
        List<RiskDto> riesgos = agileAnalyticsService.identifyRisks(proyectoId);

        if (riesgos.isEmpty()) {
            return Optional.of(new ChatResponse(
                    "No se detectaron riesgos según los datos disponibles del proyecto.",
                    List.of(), Instant.now(), true));
        }

        String datos = construirDatosRiesgos(riesgos);
        String prompt = construirPromptTipoB("riesgos detectados en el proyecto", datos, preguntaUsuario);
        return Optional.of(llamarGeminiTipoB(prompt));
    }

    private String construirDatosRiesgos(List<RiskDto> riesgos) {
        StringBuilder sb = new StringBuilder();
        for (RiskDto r : riesgos) {
            sb.append("- [").append(r.severidad()).append("] ").append(r.titulo())
              .append(": ").append(r.evidencia()).append(" (categoría: ")
              .append(r.categoriaAfectada()).append(")\n");
        }
        return sb.toString();
    }

    private String construirPromptTipoB(String tipoAnalisis, String datosEstructurados, String preguntaUsuario) {
        return INSTRUCCION_TIPO_B +
                "TIPO DE ANÁLISIS: " + tipoAnalisis + "\n" +
                "DATOS (fuente de verdad, no los alteres):\n" + datosEstructurados + "\n" +
                "PREGUNTA DEL USUARIO: " + preguntaUsuario + "\n\n" +
                "Redacta una respuesta breve y natural basada EXCLUSIVAMENTE en los datos anteriores.";
    }

    /** Única llamada a Gemini permitida para Tipo B: sin tools, sin function-calling, sin
     *  iteraciones. Si Gemini falla, se retorna un mensaje de error controlado en vez de
     *  reintentar (una sola llamada como máximo, siempre). */
    private ChatResponse llamarGeminiTipoB(String prompt) {
        String respuesta;
        try {
            respuesta = geminiService.generate(prompt);
        } catch (Exception e) {
            log.error("Error generando respuesta Tipo B con Gemini: {}", e.getMessage(), e);
            respuesta = "No fue posible generar la interpretación en este momento. Intenta de nuevo más tarde.";
        }
        return new ChatResponse(respuesta, List.of(), Instant.now(), true);
    }

    /** Sprint finalizado inmediatamente anterior al activo, en el MISMO proyecto (nunca de
     *  otro proyecto). Compartido entre COMPARACION_SPRINTS y RECOMENDACIONES para no duplicar
     *  la misma consulta dos veces. */
    private Optional<Sprint> obtenerSprintAnteriorFinalizado(UUID proyectoId, Sprint sprintActivo) {
        return sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId).stream()
                .filter(s -> s.getNumero() < sprintActivo.getNumero())
                .filter(s -> "finalizado".equalsIgnoreCase(s.getEstado()))
                .findFirst();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FASE 12.6 — Tipo C: RECOMENDACIONES y RETROSPECTIVA. Mismo principio que Tipo B (datos
    // deterministas primero, máximo 1 GeminiService.generate(), 0 AIAgentService, 0 tools),
    // pero para preguntas más abiertas. Nunca se mezclan entre sí.
    // ═══════════════════════════════════════════════════════════════════════

    private static final String INSTRUCCION_RECOMENDACIONES =
        "Eres el AI Agile Copilot de PRODOX. A continuación recibes datos YA CALCULADOS por el " +
        "backend de PRODOX (sprint actual, comparación con el sprint anterior si existe, " +
        "tendencias y riesgos): son la única fuente de verdad, no los recalcules ni los cuestiones.\n" +
        "REGLAS ESTRICTAS:\n" +
        "1. No inventes métricas, valores, sprints, riesgos ni tendencias que no aparezcan a continuación.\n" +
        "2. No modifiques ningún número.\n" +
        "3. PRODOX no define si un aumento o una disminución de una métrica es 'mejor' o 'peor'. " +
        "NUNCA concluyas que el equipo 'mejoró' o 'empeoró' salvo que el dato ya venga clasificado " +
        "así: describe los cambios como 'subió de X a Y', 'bajó de X a Y' o 'se mantuvo en X'.\n" +
        "4. Distingue con claridad HECHOS (datos observados) de INTERPRETACIÓN (tu explicación) y " +
        "de RECOMENDACIONES (acciones sugeridas). Nunca presentes una recomendación como si fuera un hecho.\n" +
        "5. Si los datos no permiten concluir algo, dilo explícitamente: \"Con los datos disponibles " +
        "no es posible determinar...\". No rellenes huecos con suposiciones.\n" +
        "6. Responde en español, en este formato exacto:\n" +
        "Resumen\n- ...\nHallazgos\n- ...\nRiesgos\n- ...\nQué revisar\n- ...\n" +
        "Sugerencias para el siguiente sprint\n- ...\n\n";

    /**
     * "¿Qué deberíamos mejorar?" / "¿Qué recomendamos?" — junta en un solo contexto todo lo que
     * ya calcula AgileAnalyticsService (métricas del sprint actual, comparación con el sprint
     * anterior si existe, tendencias, riesgos) y pide a Gemini UNA sola redacción de
     * recomendaciones. Requiere al menos métricas del sprint activo con datos disponibles; el
     * resto (comparación/tendencias/riesgos) es contexto opcional que se marca explícitamente
     * como no disponible si falta, para que Gemini nunca lo invente.
     */
    private Optional<ChatResponse> resolverRecomendaciones(UUID proyectoId, Sprint sprintActivo, String preguntaUsuario) {
        if (sprintActivo == null) {
            return Optional.empty();
        }

        SprintMetricsSummaryDto metricasActuales = agileAnalyticsService.getSprintMetricsSummary(sprintActivo.getId());
        if (metricasActuales.datosDisponibles() == null || !metricasActuales.datosDisponibles()) {
            return Optional.empty();
        }

        Optional<Sprint> anterior = obtenerSprintAnteriorFinalizado(proyectoId, sprintActivo);
        SprintComparisonDto comparacion = anterior
                .map(a -> agileAnalyticsService.compareSprints(a.getId(), sprintActivo.getId()))
                .filter(c -> Boolean.TRUE.equals(c.datosDisponibles()))
                .orElse(null);

        List<TrendAnalysisDto> tendencias = agileAnalyticsService.getSprintTrends(proyectoId, null, 3).stream()
                .filter(t -> Boolean.TRUE.equals(t.datosDisponibles()))
                .toList();

        List<RiskDto> riesgos = agileAnalyticsService.identifyRisks(proyectoId);

        String datos = construirDatosRecomendaciones(
                sprintActivo, anterior.orElse(null), metricasActuales, comparacion, tendencias, riesgos);
        String prompt = INSTRUCCION_RECOMENDACIONES +
                "DATOS (fuente de verdad, no los alteres):\n" + datos + "\n" +
                "PREGUNTA DEL USUARIO: " + preguntaUsuario + "\n\n" +
                "Redacta la respuesta siguiendo EXACTAMENTE el formato indicado arriba, " +
                "basándote EXCLUSIVAMENTE en los datos anteriores.";

        return Optional.of(llamarGeminiTipoB(prompt));
    }

    private String construirDatosRecomendaciones(
            Sprint actual, Sprint anterior, SprintMetricsSummaryDto metricasActuales,
            SprintComparisonDto comparacion, List<TrendAnalysisDto> tendencias, List<RiskDto> riesgos) {
        StringBuilder sb = new StringBuilder();

        sb.append("SPRINT ACTUAL: Sprint ").append(actual.getNumero())
          .append(" (estado: ").append(actual.getEstado()).append(")\n");
        sb.append("MÉTRICAS DEL SPRINT ACTUAL:\n");
        metricasActuales.promediosPorCategoria().forEach((categoria, valor) ->
                sb.append("- ").append(categoria).append(": ").append(valor).append("\n"));

        if (anterior == null) {
            sb.append("\nSPRINT ANTERIOR: no existe un sprint finalizado anterior disponible.\n");
        } else if (comparacion == null) {
            sb.append("\nSPRINT ANTERIOR: Sprint ").append(anterior.getNumero())
              .append(" (no hay datos suficientes para compararlo con el actual).\n");
        } else {
            sb.append("\nCOMPARACIÓN CON SPRINT ANTERIOR (Sprint ").append(anterior.getNumero()).append("):\n");
            for (String categoria : comparacion.tendencia().keySet()) {
                BigDecimal valorAnterior = comparacion.sprint1Metricas().getOrDefault(categoria, BigDecimal.ZERO);
                BigDecimal valorActual = comparacion.sprint2Metricas().getOrDefault(categoria, BigDecimal.ZERO);
                BigDecimal variacionPorc = comparacion.variacionPorcentual().getOrDefault(categoria, BigDecimal.ZERO);
                sb.append("- ").append(categoria).append(": ").append(valorAnterior).append(" -> ")
                  .append(valorActual).append(" (").append(variacionPorc).append("%, ")
                  .append(comparacion.tendencia().get(categoria)).append(")\n");
            }
        }

        sb.append("\nTENDENCIAS (últimos sprints finalizados):\n");
        if (tendencias.isEmpty()) {
            sb.append("- No hay suficientes sprints finalizados para calcular tendencias.\n");
        } else {
            for (TrendAnalysisDto t : tendencias) {
                sb.append("- ").append(t.categoria()).append(": ").append(t.tendenciaGeneral())
                  .append(" (variación total ").append(t.variacionTotal()).append("%)\n");
            }
        }

        sb.append("\nRIESGOS DETECTADOS:\n");
        if (riesgos.isEmpty()) {
            sb.append("- No se detectaron riesgos según los datos disponibles.\n");
        } else {
            for (RiskDto r : riesgos) {
                sb.append("- [").append(r.severidad()).append("] ").append(r.titulo())
                  .append(": ").append(r.evidencia()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * "¿Qué revisar en retrospectiva?" — reutiliza ÍNTEGRAMENTE AIRetrospectiveService, que ya
     * implementa el flujo Tipo C completo (datos deterministas + UNA llamada a
     * GeminiService.generate(), sin AIAgentService ni tools) para la pantalla de retrospectiva
     * existente. No se reimplementa ningún prompt ni cálculo aquí: solo se formatea el mismo
     * DTO que ya usa esa pantalla. Requiere un sprint de contexto activo (validado más arriba
     * contra el proyecto autenticado) para saber sin ambigüedad de qué sprint se habla.
     */
    private Optional<ChatResponse> resolverRetrospectiva(Sprint sprintActivo, String userId) {
        if (sprintActivo == null) {
            return Optional.empty();
        }

        try {
            AIRetrospectiveDto retro = aiRetrospectiveService.generateRetrospective(sprintActivo.getId(), userId);
            return Optional.of(new ChatResponse(formatearRetrospectiva(retro), List.of(), Instant.now(), true));
        } catch (Exception e) {
            log.warn("No se pudo generar retrospectiva Tipo C para sprint {}: {}",
                     sprintActivo.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private String formatearRetrospectiva(AIRetrospectiveDto retro) {
        StringBuilder sb = new StringBuilder();
        sb.append("Retrospectiva del Sprint ").append(retro.sprintNumero()).append("\n\n");

        sb.append("Qué funcionó bien:\n");
        retro.whatWentWell().forEach(item -> sb.append("- ").append(item).append("\n"));

        sb.append("\nQué podría mejorar:\n");
        retro.whatCouldImprove().forEach(item -> sb.append("- ").append(item).append("\n"));

        if (!retro.risks().isEmpty()) {
            sb.append("\nRiesgos:\n");
            retro.risks().forEach(item -> sb.append("- ").append(item).append("\n"));
        }

        sb.append("\nRecomendaciones:\n");
        retro.recommendations().forEach(item -> sb.append("- ").append(item).append("\n"));

        sb.append("\nPreguntas para el equipo:\n");
        retro.questionsForTeam().forEach(item -> sb.append("- ").append(item).append("\n"));

        return sb.toString();
    }

    /**
     * Resuelve "¿cuánto dio/fue el resultado de <métrica>?" contra las métricas APROBADAS
     * del proyecto activo (nunca contra un proyectoId del texto del usuario) y el histórico
     * de resultados aislado por proyecto. Si el nombre candidato no matchea con exactamente
     * una métrica aprobada, o no hay un resultado calculado válido, retorna vacío (no se
     * inventa una métrica ni un resultado).
     */
    private Optional<ChatResponse> resolverResultadoMetrica(
            String nombreCandidato, UUID proyectoId, Sprint sprintActivo) {
        if (nombreCandidato == null || nombreCandidato.isBlank()) {
            return Optional.empty();
        }

        String candidataNormalizada = normalizarNombre(nombreCandidato);

        List<ProyectoMetricaDto> coincidencias = planeacionService.listarMetricasConEstado(proyectoId)
                .stream()
                .filter(ProyectoMetricaDto::aprobada)
                .filter(m -> {
                    String nombreNormalizado = normalizarNombre(m.nombre());
                    String codigoNormalizado = normalizarNombre(m.codigo());
                    return nombreNormalizado.equals(candidataNormalizada)
                            || nombreNormalizado.contains(candidataNormalizada)
                            || candidataNormalizada.contains(nombreNormalizado)
                            || codigoNormalizado.equals(candidataNormalizada);
                })
                .toList();

        if (coincidencias.size() != 1) {
            // Sin coincidencia clara, o ambigua (más de una métrica aprobada calza): no
            // se responde localmente, se prefiere continuar por Gemini.
            return Optional.empty();
        }

        ProyectoMetricaDto metrica = coincidencias.get(0);

        // Fuente EXCLUSIVA y aislada por proyecto (FASE 12.3): jamás
        // ResultadoMetricaRepository.findByMetrica_IdOrderByCalculadoAtDesc, que no filtra
        // por proyectoId y filtraría resultados de otros proyectos.
        List<ResultadoMetricaDto> historico =
                metricaAcademicaService.obtenerHistorico(metrica.metricaId(), proyectoId);

        Optional<ResultadoMetricaDto> ultimoValido = historico.stream()
                .filter(r -> "calculado".equalsIgnoreCase(r.estado()) && r.resultado() != null)
                .findFirst(); // ya viene ordenado desc por calculadoAt

        if (ultimoValido.isEmpty()) {
            return Optional.empty();
        }

        ResultadoMetricaDto resultado = ultimoValido.get();
        Integer sprintNumero = resolverNumeroSprint(resultado.sprintId(), sprintActivo);
        if (sprintNumero == null) {
            return Optional.empty();
        }

        String unidad = (resultado.unidad() == null || resultado.unidad().isBlank())
                ? "" : " " + resultado.unidad();
        String mensaje = String.format("%s: %s%s en Sprint %d.",
                metrica.nombre(), resultado.resultado().toPlainString(), unidad, sprintNumero);

        return Optional.of(new ChatResponse(mensaje, List.of(), Instant.now(), true));
    }

    /**
     * Resuelve "¿cuál fue el resultado del último sprint?" reutilizando el mismo cálculo
     * determinístico ya usado por AIInsightsService/AIReportService (nunca se reimplementa
     * el resumen). Solo se responde si el mensaje llegó con un sprint de contexto activo
     * (validado más arriba contra el proyecto): sin eso no hay forma de saber sin ambigüedad
     * cuál es "el último sprint", así que se prefiere continuar por Gemini.
     */
    private Optional<ChatResponse> resolverResultadoUltimoSprint(Sprint sprint) {
        if (sprint == null) {
            return Optional.empty();
        }

        SprintMetricsSummaryDto resumen = agileAnalyticsService.getSprintMetricsSummary(sprint.getId());
        if (resumen.datosDisponibles() == null || !resumen.datosDisponibles()
                || resumen.promediosPorCategoria() == null || resumen.promediosPorCategoria().isEmpty()) {
            return Optional.empty();
        }

        String detalle = resumen.promediosPorCategoria().entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue().toPlainString())
                .collect(Collectors.joining(", "));

        String mensaje = String.format("Sprint %d (%s): %s.",
                resumen.sprintNumero(), resumen.estado(), detalle);

        return Optional.of(new ChatResponse(mensaje, List.of(), Instant.now(), true));
    }

    private Integer resolverNumeroSprint(UUID sprintId, Sprint sprintActivo) {
        if (sprintId == null) {
            return null;
        }
        if (sprintActivo != null && sprintId.equals(sprintActivo.getId())) {
            return sprintActivo.getNumero();
        }
        return sprintRepo.findById(sprintId).map(Sprint::getNumero).orElse(null);
    }

    /** Misma estrategia de normalización que CopilotDomainGuard/CopilotIntentClassifier:
     *  minúsculas, sin tildes, sin puntuación, espacios colapsados — solo para comparar
     *  nombres de métricas, nunca para alterar lo que eventualmente llega al LLM. */
    private String normalizarNombre(String texto) {
        String lower = texto.toLowerCase(Locale.forLanguageTag("es"));
        String sinAcentos = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.replaceAll("[¿?¡!.,;:\"']", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Recupera el historial reciente de conversación.
     * Limita el número de mensajes para no exceder el contexto del modelo.
     */
    private List<Message> recuperarHistorial(String userId, UUID proyectoId) {
        List<AIChatMessage> historial = chatMessageRepo
                .findByUserIdAndProyectoIdOrderByCreatedAtAsc(userId, proyectoId);

        // Limitar historial
        int startIndex = Math.max(0, historial.size() - maxHistoryMessages);
        List<AIChatMessage> historialLimitado = historial.subList(startIndex, historial.size());

        log.debug("Recuperados {} mensajes de historial (de {} totales)", 
                 historialLimitado.size(), historial.size());

        // Convertir a formato de Gemini y retornar lista MUTABLE
        List<Message> messages = historialLimitado.stream()
                .map(msg -> {
                    if ("user".equals(msg.getRole())) {
                        return Message.user(msg.getContent());
                    } else if ("assistant".equals(msg.getRole())) {
                        return Message.model(msg.getContent());
                    } else {
                        // No incluir mensajes de function en historial recuperado
                        return null;
                    }
                })
                .filter(msg -> msg != null)
                .collect(Collectors.toList()); // Lista MUTABLE (no .toList() que es inmutable)
        
        return messages;
    }

    /**
     * Construye el system instruction especializado para PRODOX.
     */
    private String construirSystemInstruction(Proyecto proyecto, Sprint sprint) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("Eres el AI Agile Copilot de PRODOX, un sistema especializado en medición ");
        sb.append("de productividad de equipos Agile.\n\n");
        
        sb.append("CONTEXTO PRODOX:\n");
        sb.append("- PRODOX permite a equipos Agile definir y medir métricas personalizadas\n");
        sb.append("- Las métricas se agrupan en categorías: Calidad, Productividad, Cumplimiento, ");
        sb.append("Flexibilidad, Sociohumano\n");
        sb.append("- Cada equipo configura sus propias variables de medición\n");
        sb.append("- Los valores se registran durante los sprints\n\n");
        
        sb.append("IMPORTANTE: PRODOX NO ES SCRUM TRADICIONAL\n");
        sb.append("- NO tiene 'story points' nativos (depende de configuración del equipo)\n");
        sb.append("- NO rastrea items individuales con estados\n");
        sb.append("- NO tiene Cycle Time tradicional\n");
        sb.append("- NO tiene WIP tradicional\n");
        sb.append("- Las métricas son adaptadas a cada proyecto\n\n");
        
        sb.append("REGLAS ESTRICTAS:\n");
        sb.append("1. NUNCA inventes datos, métricas, usuarios, proyectos o sprints\n");
        sb.append("2. SIEMPRE usa tools para consultar información real\n");
        sb.append("3. Diferencia HECHOS de INFERENCIAS claramente\n");
        sb.append("4. Si una métrica retorna datosDisponibles=false, dilo explícitamente\n");
        sb.append("5. NO presentes hipótesis como causas confirmadas\n");
        sb.append("6. NO reveles información de proyectos sin autorización\n");
        sb.append("7. Responde en ESPAÑOL\n");
        sb.append("8. Usa lenguaje claro orientado a equipos Agile\n");
        sb.append("9. NO afirmes causalidad sin evidencia\n");
        sb.append("10. Si Velocity o Throughput de PRODOX son adaptaciones, acláralos como tal\n\n");
        
        sb.append("ESTRUCTURA DE RESPUESTA:\n");
        sb.append("- Resumen\n");
        sb.append("- Datos relevantes (cita las métricas utilizadas)\n");
        sb.append("- Hallazgos\n");
        sb.append("- Posibles causas (márcalas como hipótesis)\n");
        sb.append("- Riesgos detectados\n");
        sb.append("- Recomendaciones\n\n");
        
        // Contexto del usuario
        sb.append("=== CONTEXTO ACTUAL ===\n");
        sb.append("Proyecto: ").append(proyecto.getNombre()).append("\n");
        sb.append("Método: ").append(proyecto.getMetodo().toUpperCase()).append("\n");
        sb.append("Time Box: ").append(proyecto.getTimeBoxSemanas()).append(" semana(s)\n");
        
        if (sprint != null) {
            sb.append("Sprint actual: Sprint ").append(sprint.getNumero())
              .append(" - ").append(sprint.getSprintGoal()).append("\n");
            sb.append("Estado: ").append(sprint.getEstado()).append("\n");
        }
        
        return sb.toString();
    }

    /**
     * Guarda un mensaje en el historial.
     */
    private void guardarMensaje(String userId, UUID proyectoId, UUID sprintId, 
                                String role, String content) {
        AIChatMessage msg = new AIChatMessage();
        msg.setUserId(userId);
        msg.setProyectoId(proyectoId);
        msg.setSprintId(sprintId);
        msg.setRole(role);
        msg.setContent(content);
        chatMessageRepo.save(msg);
        
        log.debug("Guardado mensaje {} para usuario {} en proyecto {}", 
                 role, userId, proyectoId);
    }

    /**
     * Limpia el historial de un usuario en un proyecto.
     */
    @Transactional
    public void clearHistory(String userId, UUID proyectoId) {
        // Validar autorización
        if (!projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)) {
            throw new SecurityException("No tienes acceso a este proyecto");
        }
        
        chatMessageRepo.deleteByUserIdAndProyectoId(userId, proyectoId);
        log.info("Historial limpiado para userId={} en proyectoId={}", userId, proyectoId);
    }
}
