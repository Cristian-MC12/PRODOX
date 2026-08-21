// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpdia.dto.GuardarValoresRequest;
import com.mpdia.dto.VariableConValorDto;
import com.mpdia.dto.VariablesMetricaResponse;
import com.mpdia.entity.*;
import com.mpdia.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Servicio para captura dinámica de variables desde parametrizaciones aprobadas.
 * Fase 16.7: Materialización on-demand de variables.
 */
@Service
@RequiredArgsConstructor
public class VariableDinamicaService {

    private final MetricParametrizacionRepository parametrizacionRepo;
    private final VariableRepository variableRepo;
    private final RegistroValorRepository registroRepo;
    private final MetricaRepository metricaRepo;
    private final SprintRepository sprintRepo;
    private final ProyectoRepository proyectoRepo;
    private final ObjectMapper objectMapper;
    private final EjecucionService ejecucionService;

    /**
     * Obtiene las variables de una métrica para captura en un sprint.
     * Materializa variables on-demand desde la parametrización aprobada.
     */
    @Transactional
    public VariablesMetricaResponse obtenerVariables(UUID metricaId, UUID proyectoId, UUID sprintId, String userId) {
        // 1. Validar proyecto
        Proyecto proyecto = proyectoRepo.findById(proyectoId)
            .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado"));
        
        // 2. Validar sprint
        Sprint sprint = sprintRepo.findById(sprintId)
            .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado"));
        
        if (!sprint.getProyectoId().equals(proyectoId)) {
            throw new IllegalArgumentException("El sprint no pertenece al proyecto");
        }
        
        // 3. Buscar parametrización aprobada
        MetricParametrizacion parametrizacion = parametrizacionRepo
            .findUltimaVersionAprobada(metricaId, proyectoId)
            .orElseThrow(() -> new IllegalArgumentException(
                "No existe parametrización aprobada para esta métrica. " +
                "Debe aprobar una parametrización antes de capturar valores."
            ));
        
        // 4. Buscar o crear variables
        List<Variable> variables = obtenerOCrearVariables(parametrizacion, proyecto);
        
        // 5. Obtener valores actuales del sprint
        List<VariableConValorDto> variablesConValor = variables.stream()
            .map(v -> construirVariableConValor(v, sprintId))
            .toList();
        
        return new VariablesMetricaResponse(
            parametrizacion.getId(),
            parametrizacion.getVersion(),
            parametrizacion.getStatus(),
            variablesConValor
        );
    }

    /**
     * Obtiene variables existentes o las crea on-demand desde la parametrización.
     */
    private List<Variable> obtenerOCrearVariables(MetricParametrizacion parametrizacion, Proyecto proyecto) {
        // Buscar variables existentes
        List<Variable> existentes = variableRepo.findByParametrizacionIdAndParametrizacionVersion(
            parametrizacion.getId(),
            parametrizacion.getVersion()
        );
        
        if (!existentes.isEmpty()) {
            return existentes;
        }
        
        // Si no existen, crearlas desde el JSON
        return crearVariablesDesdeParametrizacion(parametrizacion, proyecto);
    }

    /**
     * Crea variables automáticamente desde configuracionAprobadaJson.
     * Parser simple: por ahora crea UNA variable principal.
     */
    private List<Variable> crearVariablesDesdeParametrizacion(MetricParametrizacion parametrizacion, Proyecto proyecto) {
        try {
            // Parsear JSON
            JsonNode json = objectMapper.readTree(parametrizacion.getConfiguracionAprobadaJson());
            
            String indicadorVariable = json.get("indicadorVariable").asText();
            String procedimiento = json.get("procedimiento").asText();
            String escala = json.get("escala").asText();
            String frecuenciaCaptura = json.has("frecuenciaCaptura") 
                ? json.get("frecuenciaCaptura").asText() 
                : "por_sprint";
            
            // Buscar métrica
            Metrica metrica = metricaRepo.findById(parametrizacion.getMetricaId())
                .orElseThrow(() -> new IllegalArgumentException("Métrica no encontrada"));
            
            // Crear variable principal
            Variable variable = new Variable();
            variable.setProyectoId(proyecto.getId());
            variable.setMetrica(metrica);
            variable.setNombre(indicadorVariable);
            variable.setDescripcion(procedimiento);  // Usar procedimiento como descripción
            variable.setTipoAlcance("grupal");
            variable.setTipoDato("numerico");  // Por defecto numérico
            variable.setActiva(true);
            variable.setFrecuenciaCaptura(frecuenciaCaptura);
            variable.setParametrizacionId(parametrizacion.getId());
            variable.setParametrizacionVersion(parametrizacion.getVersion());
            
            // Intentar extraer escala si existe
            parseEscala(escala, variable);
            
            // Guardar
            Variable guardada = variableRepo.save(variable);
            
            List<Variable> resultado = new ArrayList<>();
            resultado.add(guardada);
            return resultado;
            
        } catch (Exception e) {
            throw new RuntimeException("Error parseando configuración aprobada: " + e.getMessage(), e);
        }
    }

    /**
     * Intenta extraer escalaMin/Max del texto de escala.
     * Ejemplo: "Numérica 0-100 puntos" → min=0, max=100
     */
    private void parseEscala(String escala, Variable variable) {
        if (escala == null) return;
        
        // Patrón simple: buscar "N-M" en el texto
        String[] tokens = escala.split("\\s+");
        for (String token : tokens) {
            if (token.matches("\\d+-\\d+")) {
                String[] partes = token.split("-");
                try {
                    variable.setEscalaMin(new java.math.BigDecimal(partes[0]));
                    variable.setEscalaMax(new java.math.BigDecimal(partes[1]));
                    return;
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    /**
     * Construye DTO con valor actual del sprint (si existe).
     */
    private VariableConValorDto construirVariableConValor(Variable variable, UUID sprintId) {
        // Buscar valor existente en este sprint
        List<RegistroValor> registros = registroRepo.findBySprintIdAndVariable_Id(sprintId, variable.getId());
        
        RegistroValor ultimoRegistro = registros.isEmpty() ? null : registros.get(0);
        
        return new VariableConValorDto(
            variable.getId(),
            variable.getNombre(),
            variable.getDescripcion(),
            variable.getTipoDato(),
            true,  // Por ahora todas obligatorias
            null,  // Unidad no se extrae todavía
            variable.getEscalaMin(),
            variable.getEscalaMax(),
            ultimoRegistro != null ? ultimoRegistro.getValorNum() : null,
            ultimoRegistro != null ? ultimoRegistro.getValorTexto() : null,
            ultimoRegistro != null ? ultimoRegistro.getValorBool() : null
        );
    }

    /**
     * Guarda valores de variables en un sprint.
     */
    @Transactional
    public void guardarValores(UUID metricaId, GuardarValoresRequest request, String userId) {
        // 1. Validar proyecto
        Proyecto proyecto = proyectoRepo.findById(request.proyectoId())
            .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado"));
        
        // 2. Validar sprint
        Sprint sprint = sprintRepo.findById(request.sprintId())
            .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado"));
        
        if (!sprint.getProyectoId().equals(request.proyectoId())) {
            throw new IllegalArgumentException("El sprint no pertenece al proyecto");
        }
        
        // 3. Validar parametrización aprobada
        MetricParametrizacion parametrizacion = parametrizacionRepo
            .findUltimaVersionAprobada(metricaId, request.proyectoId())
            .orElseThrow(() -> new IllegalArgumentException("No existe parametrización aprobada"));
        
        // 4. Guardar cada valor
        for (GuardarValoresRequest.ValorVariable valorRequest : request.valores()) {
            Variable variable = variableRepo.findById(valorRequest.variableId())
                .orElseThrow(() -> new IllegalArgumentException("Variable no encontrada"));
            
            // Validar que la variable pertenece a la parametrización correcta
            if (!variable.getParametrizacionId().equals(parametrizacion.getId())) {
                throw new IllegalArgumentException("Variable no pertenece a la parametrización aprobada");
            }
            
            // Validar tipo de dato
            validarTipoDato(variable, valorRequest);
            
            // Crear o actualizar registro
            guardarRegistroValor(variable, sprint, userId, valorRequest);
        }
    }

    /**
     * Valida que el valor coincida con el tipo de dato de la variable.
     */
    private void validarTipoDato(Variable variable, GuardarValoresRequest.ValorVariable valor) {
        switch (variable.getTipoDato()) {
            case "numerico":
                if (valor.valorNum() == null) {
                    throw new IllegalArgumentException(
                        "Variable '" + variable.getNombre() + "' requiere valor numérico");
                }
                break;
            case "texto":
                if (valor.valorTexto() == null || valor.valorTexto().isBlank()) {
                    throw new IllegalArgumentException(
                        "Variable '" + variable.getNombre() + "' requiere valor de texto");
                }
                break;
            case "booleano":
                if (valor.valorBool() == null) {
                    throw new IllegalArgumentException(
                        "Variable '" + variable.getNombre() + "' requiere valor booleano");
                }
                break;
        }
    }

    /**
     * Guarda o actualiza un registro de valor (FASE 16.11: único camino de
     * escritura, ver EjecucionService.guardarOActualizarValor()).
     */
    private void guardarRegistroValor(Variable variable, Sprint sprint, String userId,
                                      GuardarValoresRequest.ValorVariable valorRequest) {
        ejecucionService.guardarOActualizarValor(
            variable, sprint.getId(), userId,
            valorRequest.valorNum(), valorRequest.valorTexto(),
            valorRequest.valorBool(), valorRequest.observacion());
    }
}
