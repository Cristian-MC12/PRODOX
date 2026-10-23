// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodox.dto.CrearEvaluacionObjetivoRequest;
import com.prodox.dto.EvaluacionObjetivoDto;
import com.prodox.entity.EvaluacionObjetivo;
import com.prodox.entity.ProjectMember;
import com.prodox.entity.Sprint;
import com.prodox.repository.EvaluacionObjetivoRepository;
import com.prodox.repository.ProjectMemberRepository;
import com.prodox.repository.SprintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Servicio para gestionar evaluaciones manuales del cumplimiento del objetivo del sprint.
 * 
 * FASE 6B: Implementación de evaluación manual sin criterios cuantitativos automáticos.
 * El Scrum Master del proyecto evalúa si el objetivo del sprint se cumplió,
 * proporcionando justificación y evidencia opcional.
 * 
 * Reglas de negocio:
 * - Solo se puede evaluar un sprint finalizado o reabierto
 * - Solo el Scrum Master del proyecto puede crear/modificar evaluaciones
 * - Un sprint tiene como máximo una evaluación (unicidad)
 * - La evaluación es independiente de métricas automáticas
 * - Modificaciones conservan creador original y registran modificador
 * 
 * Autorización:
 * - El usuario debe ser miembro del proyecto
 * - El usuario debe tener rol scrum_master en el proyecto
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluacionObjetivoService {

    private final EvaluacionObjetivoRepository evaluacionRepo;
    private final SprintRepository sprintRepo;
    private final ProjectMemberRepository projectMemberRepo;
    private final ObjectMapper objectMapper;

    /**
     * Crea una evaluación para un sprint.
     * 
     * @param sprintId ID del sprint a evaluar
     * @param request datos de la evaluación
     * @param userId usuario autenticado (Scrum Master)
     * @return evaluación creada
     * @throws IllegalArgumentException si sprint no existe o no está finalizado
     * @throws SecurityException si usuario no es Scrum Master del proyecto
     * @throws IllegalStateException si ya existe evaluación para el sprint
     */
    @Transactional
    public EvaluacionObjetivoDto crear(UUID sprintId, CrearEvaluacionObjetivoRequest request, String userId) {
        log.info("Creando evaluación de objetivo para sprint={} por usuario={}", sprintId, userId);

        // 1. Validar que el sprint existe
        Sprint sprint = sprintRepo.findById(sprintId)
                .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado: " + sprintId));

        // 2. Validar que el sprint está finalizado o reabierto
        if (!"finalizado".equals(sprint.getEstado()) && !"reabierto".equals(sprint.getEstado())) {
            throw new IllegalArgumentException(
                    "Solo se pueden evaluar sprints finalizados o reabiertos. Estado actual: " + sprint.getEstado());
        }

        // 3. Validar que el usuario es Scrum Master del proyecto
        validarScrumMaster(sprint.getProyectoId(), userId);

        // 4. Validar que no existe evaluación previa para este sprint
        if (evaluacionRepo.existsBySprintId(sprintId)) {
            throw new IllegalStateException("Ya existe una evaluación para este sprint. Use actualizar en su lugar.");
        }

        // 5. Crear evaluación
        EvaluacionObjetivo evaluacion = new EvaluacionObjetivo();
        evaluacion.setSprintId(sprintId);
        evaluacion.setEstado(request.estado());
        evaluacion.setJustificacion(request.justificacion().trim());
        evaluacion.setEvidenciaUrls(serializarEvidencias(request.evidenciaUrls()));
        evaluacion.setEvaluadoPor(userId);
        evaluacion.setEvaluadoAt(Instant.now());

        evaluacion = evaluacionRepo.save(evaluacion);
        log.info("Evaluación creada con id={} para sprint={} por usuario={}", 
                evaluacion.getId(), sprintId, userId);

        return toDto(evaluacion);
    }

    /**
     * Actualiza una evaluación existente.
     * 
     * @param sprintId ID del sprint cuya evaluación se actualizará
     * @param request nuevos datos de la evaluación
     * @param userId usuario autenticado (Scrum Master)
     * @return evaluación actualizada
     * @throws IllegalArgumentException si sprint o evaluación no existen
     * @throws SecurityException si usuario no es Scrum Master del proyecto
     */
    @Transactional
    public EvaluacionObjetivoDto actualizar(UUID sprintId, CrearEvaluacionObjetivoRequest request, String userId) {
        log.info("Actualizando evaluación de objetivo para sprint={} por usuario={}", sprintId, userId);

        // 1. Validar que el sprint existe
        Sprint sprint = sprintRepo.findById(sprintId)
                .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado: " + sprintId));

        // 2. Validar que el usuario es Scrum Master del proyecto
        validarScrumMaster(sprint.getProyectoId(), userId);

        // 3. Obtener evaluación existente
        EvaluacionObjetivo evaluacion = evaluacionRepo.findBySprintId(sprintId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe evaluación para este sprint. Use crear en su lugar."));

        // 4. Actualizar campos (conservando creador original)
        evaluacion.setEstado(request.estado());
        evaluacion.setJustificacion(request.justificacion().trim());
        evaluacion.setEvidenciaUrls(serializarEvidencias(request.evidenciaUrls()));
        evaluacion.setActualizadoPor(userId);
        evaluacion.setActualizadoAt(Instant.now());

        evaluacion = evaluacionRepo.save(evaluacion);
        log.info("Evaluación actualizada id={} para sprint={} por usuario={}", 
                evaluacion.getId(), sprintId, userId);

        return toDto(evaluacion);
    }

    /**
     * Obtiene la evaluación de un sprint.
     * 
     * @param sprintId ID del sprint
     * @param userId usuario autenticado
     * @return evaluación del sprint
     * @throws IllegalArgumentException si sprint no existe o no tiene evaluación
     * @throws SecurityException si usuario no tiene acceso al proyecto
     */
    public EvaluacionObjetivoDto obtener(UUID sprintId, String userId) {
        // 1. Validar que el sprint existe
        Sprint sprint = sprintRepo.findById(sprintId)
                .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado: " + sprintId));

        // 2. Validar que el usuario tiene acceso al proyecto
        validarAccesoProyecto(sprint.getProyectoId(), userId);

        // 3. Obtener evaluación
        EvaluacionObjetivo evaluacion = evaluacionRepo.findBySprintId(sprintId)
                .orElseThrow(() -> new IllegalArgumentException("No existe evaluación para este sprint"));

        return toDto(evaluacion);
    }

    /**
     * Valida que el usuario es Scrum Master del proyecto.
     * 
     * @param proyectoId ID del proyecto
     * @param userId usuario a validar
     * @throws SecurityException si usuario no es miembro o no es Scrum Master
     */
    private void validarScrumMaster(UUID proyectoId, String userId) {
        ProjectMember member = projectMemberRepo.findByProyectoIdAndUserId(proyectoId, userId)
                .orElseThrow(() -> new SecurityException("No tienes acceso a este proyecto"));

        if (!ProjectMember.ROL_SCRUM_MASTER.equals(member.getRol())) {
            throw new SecurityException("Solo el Scrum Master del proyecto puede evaluar el objetivo del sprint");
        }
    }

    /**
     * Valida que el usuario tiene acceso al proyecto.
     * 
     * @param proyectoId ID del proyecto
     * @param userId usuario a validar
     * @throws SecurityException si usuario no es miembro
     */
    private void validarAccesoProyecto(UUID proyectoId, String userId) {
        if (!projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)) {
            throw new SecurityException("No tienes acceso a este proyecto");
        }
    }

    /**
     * Serializa lista de URLs de evidencia a JSON.
     * 
     * @param urls lista de URLs (puede ser null o vacía)
     * @return JSON array de strings, o null si lista está vacía
     */
    private String serializarEvidencias(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(urls);
        } catch (JsonProcessingException e) {
            // No debería ocurrir con lista de strings simples
            log.error("Error serializando evidencias: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Deserializa JSON de evidencias a lista de URLs.
     * 
     * @param json JSON array de strings (puede ser null)
     * @return lista de URLs, o lista vacía si json es null
     */
    private List<String> deserializarEvidencias(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            log.error("Error deserializando evidencias: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Convierte entidad a DTO.
     */
    private EvaluacionObjetivoDto toDto(EvaluacionObjetivo evaluacion) {
        return new EvaluacionObjetivoDto(
                evaluacion.getId(),
                evaluacion.getSprintId(),
                evaluacion.getEstado(),
                evaluacion.getJustificacion(),
                deserializarEvidencias(evaluacion.getEvidenciaUrls()),
                evaluacion.getEvaluadoPor(),
                evaluacion.getEvaluadoAt(),
                evaluacion.getActualizadoPor(),
                evaluacion.getActualizadoAt()
        );
    }
}
