// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpdia.dto.CalcularMetricaRequest;
import com.mpdia.dto.ResultadoMetricaDto;
import com.mpdia.entity.*;
import com.mpdia.formula.FormulaEvaluator;
import com.mpdia.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Servicio para cálculo determinista de métricas.
 * Fase 16.8: NO utiliza Gemini para cálculos.
 */
@Service
@RequiredArgsConstructor
public class CalculoMetricaService {
    
    private final MetricaRepository metricaRepo;
    private final ProyectoRepository proyectoRepo;
    private final SprintRepository sprintRepo;
    private final MetricParametrizacionRepository parametrizacionRepo;
    private final VariableRepository variableRepo;
    private final RegistroValorRepository registroRepo;
    private final ResultadoMetricaRepository resultadoRepo;
    private final ProjectMemberRepository projectMemberRepo;
    private final FormulaEvaluator evaluator;
    private final ObjectMapper objectMapper;
    
    @Transactional
    public ResultadoMetricaDto calcularMetrica(
            UUID metricaId, 
            CalcularMetricaRequest request, 
            String userId) {
        
        // 1. Validaciones básicas
        Metrica metrica = metricaRepo.findById(metricaId)
            .orElseThrow(() -> new IllegalArgumentException("Métrica no encontrada"));
        
        Proyecto proyecto = proyectoRepo.findById(request.proyectoId())
            .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado"));

        Sprint sprint = sprintRepo.findById(request.sprintId())
            .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado"));

        if (!sprint.getProyectoId().equals(request.proyectoId())) {
            throw new IllegalArgumentException("El sprint no pertenece al proyecto");
        }

        if (!projectMemberRepo.existsByProyectoIdAndUserId(request.proyectoId(), userId)) {
            throw new SecurityException("No tienes acceso a este proyecto");
        }
        
        // 2. Obtener parametrización aprobada
        MetricParametrizacion parametrizacion = parametrizacionRepo
            .findUltimaVersionAprobada(metricaId, request.proyectoId())
            .orElseThrow(() -> new IllegalArgumentException(
                "No existe parametrización aprobada para esta métrica"));
        
        // 3. Obtener variables de la parametrización
        List<Variable> variables = variableRepo
            .findByParametrizacionIdAndParametrizacionVersion(
                parametrizacion.getId(), 
                parametrizacion.getVersion()
            );
        
        if (variables.isEmpty()) {
            throw new IllegalArgumentException(
                "La parametrización no tiene variables definidas");
        }
        
        // 4. Obtener valores registrados
        Map<UUID, List<BigDecimal>> valoresPorVariable = new HashMap<>();
        for (Variable variable : variables) {
            List<RegistroValor> registros = registroRepo
                .findBySprintIdAndVariable_Id(request.sprintId(), variable.getId());
            
            List<BigDecimal> valores = registros.stream()
                .map(RegistroValor::getValorNum)
                .filter(Objects::nonNull)
                .toList();
            
            valoresPorVariable.put(variable.getId(), valores);
        }
        
        // 5. Determinar tipo de cálculo y calcular
        String configuracionJson = parametrizacion.getConfiguracionAprobadaJson();
        
        try {
            BigDecimal resultado;
            String tipoCalculo;
            String expresion = null;
            
            if (configuracionJson != null && !configuracionJson.isBlank()) {
                // Configuración estructurada (nuevo formato)
                Map<String, Object> config = objectMapper.readValue(
                    configuracionJson, new TypeReference<>() {});
                
                tipoCalculo = (String) config.get("tipo");
                
                resultado = switch (tipoCalculo) {
                    case "directo" -> calcularDirecto(config, valoresPorVariable);
                    case "suma" -> calcularSuma(config, valoresPorVariable);
                    case "promedio" -> calcularPromedio(config, valoresPorVariable);
                    case "formula" -> {
                        expresion = (String) config.get("expresion");
                        yield calcularFormula(expresion, config, valoresPorVariable);
                    }
                    default -> throw new IllegalArgumentException(
                        "Tipo de cálculo no soportado: " + tipoCalculo);
                };
            } else {
                // Fallback: legacy sin configuración estructurada
                // Por ahora usar valor directo de la primera variable
                tipoCalculo = "directo";
                Variable primeraVariable = variables.get(0);
                Map<UUID, BigDecimal> valores = extraerUltimoValor(valoresPorVariable);
                resultado = evaluator.evaluarDirecto(primeraVariable.getId(), valores);
            }
            
            // 6. Persistir resultado
            ResultadoMetrica resultadoEntity = new ResultadoMetrica();
            resultadoEntity.setProyectoId(request.proyectoId());
            resultadoEntity.setMetrica(metrica);
            resultadoEntity.setSprintId(request.sprintId());
            resultadoEntity.setParametrizacionId(parametrizacion.getId());
            resultadoEntity.setParametrizacionVersion(parametrizacion.getVersion());
            resultadoEntity.setTipoCalculo(tipoCalculo);
            resultadoEntity.setExpresionUtilizada(expresion);
            resultadoEntity.setValoresUtilizados(
                objectMapper.writeValueAsString(valoresPorVariable));
            resultadoEntity.setResultado(resultado);
            resultadoEntity.setEstado("calculado");
            resultadoEntity.setCalculadoPor(userId);
            resultadoEntity.setCalculadoAt(Instant.now());
            
            resultadoEntity = resultadoRepo.save(resultadoEntity);
            
            // 7. Retornar DTO
            return new ResultadoMetricaDto(
                resultadoEntity.getId(),
                metrica.getId(),
                metrica.getNombre(),
                request.proyectoId(),
                request.sprintId(),
                parametrizacion.getId(),
                parametrizacion.getVersion(),
                tipoCalculo,
                expresion,
                resultadoEntity.getValoresUtilizados(),
                resultado,
                resultadoEntity.getUnidad(),
                "calculado",
                null,
                resultadoEntity.getCalculadoAt()
            );
            
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (ArithmeticException e) {
            throw new ArithmeticException("Error de cálculo: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Error calculando métrica: " + e.getMessage(), e);
        }
    }
    
    private BigDecimal calcularDirecto(
            Map<String, Object> config,
            Map<UUID, List<BigDecimal>> valoresPorVariable) {
        
        String variableIdStr = (String) config.get("variable_id");
        UUID variableId = UUID.fromString(variableIdStr);
        
        Map<UUID, BigDecimal> valores = extraerUltimoValor(valoresPorVariable);
        return evaluator.evaluarDirecto(variableId, valores);
    }
    
    private BigDecimal calcularSuma(
            Map<String, Object> config,
            Map<UUID, List<BigDecimal>> valoresPorVariable) {
        
        String variableIdStr = (String) config.get("variable_id");
        UUID variableId = UUID.fromString(variableIdStr);
        
        List<BigDecimal> valores = valoresPorVariable.get(variableId);
        if (valores == null || valores.isEmpty()) {
            throw new IllegalArgumentException(
                "No hay valores para sumar de la variable: " + variableId);
        }
        
        return evaluator.evaluarSuma(variableId, valores);
    }
    
    private BigDecimal calcularPromedio(
            Map<String, Object> config,
            Map<UUID, List<BigDecimal>> valoresPorVariable) {
        
        String variableIdStr = (String) config.get("variable_id");
        UUID variableId = UUID.fromString(variableIdStr);
        
        List<BigDecimal> valores = valoresPorVariable.get(variableId);
        if (valores == null || valores.isEmpty()) {
            throw new IllegalArgumentException(
                "No hay valores para promediar de la variable: " + variableId);
        }
        
        return evaluator.evaluarPromedio(variableId, valores);
    }
    
    private BigDecimal calcularFormula(
            String expresion,
            Map<String, Object> config,
            Map<UUID, List<BigDecimal>> valoresPorVariable) {
        
        // Extraer último valor de cada variable
        Map<UUID, BigDecimal> valores = extraerUltimoValor(valoresPorVariable);
        
        return evaluator.evaluarFormula(expresion, valores);
    }
    
    /**
     * Extrae el último (más reciente) valor de cada variable.
     */
    private Map<UUID, BigDecimal> extraerUltimoValor(
            Map<UUID, List<BigDecimal>> valoresPorVariable) {
        
        Map<UUID, BigDecimal> resultado = new HashMap<>();
        
        for (Map.Entry<UUID, List<BigDecimal>> entry : valoresPorVariable.entrySet()) {
            List<BigDecimal> valores = entry.getValue();
            if (valores != null && !valores.isEmpty()) {
                resultado.put(entry.getKey(), valores.get(0)); // Primero es el más reciente
            }
        }
        
        return resultado;
    }
}
