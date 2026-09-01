// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service.copilot;

import com.prodox.dto.ai.gemini.FunctionDeclaration;
import com.prodox.dto.ai.gemini.Tool;
import com.prodox.dto.analytics.*;
import com.prodox.dto.SprintDto;
import com.prodox.entity.Proyecto;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.repository.ProyectoRepository;
import com.prodox.repository.SprintRepository;
import com.prodox.service.AgileAnalyticsService;
import com.prodox.service.ProyectoService;
import com.prodox.service.SprintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CopilotToolsService {

    private final AgileAnalyticsService analyticsService;
    private final SprintService sprintService;
    private final ProyectoService proyectoService;
    private final ProjectMemberRepository projectMemberRepo;
    private final ProyectoRepository proyectoRepo;
    private final SprintRepository sprintRepo;

    public List<Tool> getAvailableTools() {
        List<FunctionDeclaration> functions = new ArrayList<>();

        // Tool 1: getProjectDetails
        // No requiere parámetros porque usa el contexto del proyecto actual
        functions.add(new FunctionDeclaration(
                "getProjectDetails",
                "Obtiene detalles completos del proyecto actual (el proyecto en el que está trabajando el usuario)",
                Map.of(
                        "type", "OBJECT",
                        "properties", Map.of() // Sin parámetros, usa contexto
                )
        ));

        // Tool 2: getActiveSprintMetrics
        // No requiere parámetros porque usa el contexto del proyecto actual
        functions.add(new FunctionDeclaration(
                "getActiveSprintMetrics",
                "Obtiene las métricas del sprint activo del proyecto actual (el proyecto en el que está trabajando el usuario)",
                Map.of(
                        "type", "OBJECT",
                        "properties", Map.of() // Sin parámetros, usa contexto
                )
        ));

        return List.of(new Tool(functions));
    }

    public Object executeTool(String toolName, Map<String, Object> args, String userId, UUID contextProyectoId) {
        log.info("Ejecutando tool: {} para userId: {}", toolName, userId);

        return switch (toolName) {
            case "getProjectDetails" -> executeGetProjectDetails(args, userId, contextProyectoId);
            case "getActiveSprintMetrics" -> executeGetActiveSprintMetrics(args, userId, contextProyectoId);
            default -> throw new IllegalArgumentException("Tool desconocida: " + toolName);
        };
    }

    private Object executeGetProjectDetails(Map<String, Object> args, String userId, UUID contextProyectoId) {
        // Usar el proyecto del contexto (no requiere parámetros)
        if (contextProyectoId == null) {
            throw new IllegalArgumentException("No hay proyecto en el contexto. El usuario debe seleccionar un proyecto primero.");
        }
        
        validateProjectAccess(userId, contextProyectoId);
        
        Proyecto proyecto = proyectoRepo.findById(contextProyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado"));
        
        return Map.of(
                "proyectoId", contextProyectoId.toString(),
                "nombre", proyecto.getNombre(),
                "descripcion", proyecto.getDescripcion() != null ? proyecto.getDescripcion() : "",
                "metodo", proyecto.getMetodo(),
                "timeBoxSemanas", proyecto.getTimeBoxSemanas(),
                "numeroSprints", proyecto.getNumeroSprints(),
                "estado", proyecto.getEstado(),
                "mensaje", "Proyecto actual: " + proyecto.getNombre()
        );
    }

    private Object executeGetActiveSprintMetrics(Map<String, Object> args, String userId, UUID contextProyectoId) {
        // Usar el proyecto del contexto (no requiere parámetros)
        if (contextProyectoId == null) {
            throw new IllegalArgumentException("No hay proyecto en el contexto. El usuario debe seleccionar un proyecto primero.");
        }
        
        validateProjectAccess(userId, contextProyectoId);

        SprintDto sprintActivo = sprintService.getSprintActivo(contextProyectoId);
        SprintMetricsSummaryDto metrics = analyticsService.getSprintMetricsSummary(sprintActivo.id());

        return metrics;
    }

    private void validateProjectAccess(String userId, UUID proyectoId) {
        if (!projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)) {
            log.warn("Usuario {} intentó acceder a proyecto {} sin autorización", userId, proyectoId);
            throw new SecurityException("No tienes acceso a este proyecto");
        }
    }

    private void validateSprintAccess(String userId, UUID sprintId, UUID contextProyectoId) {
        var sprint = sprintRepo.findById(sprintId)
                .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado"));

        validateProjectAccess(userId, sprint.getProyectoId());

        if (contextProyectoId != null && !sprint.getProyectoId().equals(contextProyectoId)) {
            log.warn("Sprint {} no pertenece al proyecto del contexto {}", sprintId, contextProyectoId);
            throw new SecurityException("El sprint no pertenece al proyecto del contexto actual");
        }
    }
}
