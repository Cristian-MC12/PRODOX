// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.ai.AgentResponse;
import com.mpdia.dto.ai.ChatRequest;
import com.mpdia.dto.ai.ChatResponse;
import com.mpdia.dto.ai.gemini.*;
import com.mpdia.entity.AIChatMessage;
import com.mpdia.entity.Proyecto;
import com.mpdia.entity.Sprint;
import com.mpdia.repository.AIChatMessageRepository;
import com.mpdia.repository.ProjectMemberRepository;
import com.mpdia.repository.ProyectoRepository;
import com.mpdia.repository.SprintRepository;
import com.mpdia.service.copilot.CopilotToolsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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
    private final AIChatMessageRepository chatMessageRepo;
    private final ProjectMemberRepository projectMemberRepo;
    private final ProyectoRepository proyectoRepo;
    private final SprintRepository sprintRepo;

    @Value("${mpdia.ai.max-history-messages:10}")
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

        // 4. RECUPERAR HISTORIAL RECIENTE
        List<Message> historialMessages = recuperarHistorial(userId, request.proyectoId());

        // 5. AGREGAR MENSAJE DEL USUARIO AL HISTORIAL
        historialMessages.add(Message.user(request.message()));

        // 6. CONSTRUIR SYSTEM INSTRUCTION
        String systemInstruction = construirSystemInstruction(proyecto, sprint);

        // 7. OBTENER TOOLS DISPONIBLES
        List<Tool> tools = toolsService.getAvailableTools();

        // 8. CREAR TOOL EXECUTOR CON CONTEXTO
        ToolExecutor executor = (toolName, args) -> 
            toolsService.executeTool(toolName, args, userId, request.proyectoId());

        // 9. INVOCAR AI AGENT
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

        // 10. GUARDAR MENSAJES EN BASE DE DATOS
        guardarMensaje(userId, request.proyectoId(), request.sprintId(), 
                      "user", request.message());
        guardarMensaje(userId, request.proyectoId(), request.sprintId(), 
                      "assistant", agentResponse.message());

        // 11. CONSTRUIR Y RETORNAR RESPUESTA
        return new ChatResponse(
            agentResponse.message(),
            agentResponse.toolsUsed(),
            Instant.now(),
            agentResponse.hasData()
        );
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
     * Construye el system instruction especializado para MPDIA.
     */
    private String construirSystemInstruction(Proyecto proyecto, Sprint sprint) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("Eres el AI Agile Copilot de MPDIA, un sistema especializado en medición ");
        sb.append("de productividad de equipos Agile.\n\n");
        
        sb.append("CONTEXTO MPDIA:\n");
        sb.append("- MPDIA permite a equipos Agile definir y medir métricas personalizadas\n");
        sb.append("- Las métricas se agrupan en categorías: Calidad, Productividad, Cumplimiento, ");
        sb.append("Flexibilidad, Sociohumano\n");
        sb.append("- Cada equipo configura sus propias variables de medición\n");
        sb.append("- Los valores se registran durante los sprints\n\n");
        
        sb.append("IMPORTANTE: MPDIA NO ES SCRUM TRADICIONAL\n");
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
        sb.append("10. Si Velocity o Throughput de MPDIA son adaptaciones, acláralos como tal\n\n");
        
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
