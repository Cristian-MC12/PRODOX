// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.EvaluacionSprintDto;
import com.mpdia.entity.RegistroValor;
import com.mpdia.entity.Sprint;
import com.mpdia.entity.Variable;
import com.mpdia.repository.RegistroValorRepository;
import com.mpdia.repository.SprintRepository;
import com.mpdia.repository.VariableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EvaluacionService {

    private final SprintRepository        sprintRepo;
    private final VariableRepository      variableRepo;
    private final RegistroValorRepository registroRepo;

    /**
     * Evaluación completa de todos los sprints de un proyecto.
     * Devuelve promedio/min/max/total por variable por sprint.
     * Incluye la fórmula configurada para dar contexto al evaluador.
     */
    public List<EvaluacionSprintDto> evaluar(UUID proyectoId) {
        List<Sprint>   sprints   = sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId);
        List<Variable> variables = variableRepo.findByProyectoIdAndActivaTrue(proyectoId);

        List<EvaluacionSprintDto> resultado = new ArrayList<>();

        for (Sprint sprint : sprints) {
            List<RegistroValor> registros = registroRepo.findBySprintId(sprint.getId());
            for (Variable variable : variables) {
                EvaluacionSprintDto dto = calcularMetricaSprint(sprint, variable, registros);
                if (dto != null) resultado.add(dto);
            }
        }
        return resultado;
    }

    /** Evaluación por sprint específico */
    public List<EvaluacionSprintDto> evaluarSprint(UUID sprintId) {
        Sprint sprint = sprintRepo.findById(sprintId)
                .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado."));

        List<RegistroValor> registros = registroRepo.findBySprintId(sprintId);
        List<Variable> variables = variableRepo.findByProyectoIdAndActivaTrue(sprint.getProyectoId());

        List<EvaluacionSprintDto> resultado = new ArrayList<>();
        for (Variable variable : variables) {
            EvaluacionSprintDto dto = calcularMetricaSprint(sprint, variable, registros);
            if (dto != null) resultado.add(dto);
        }
        return resultado;
    }

    /**
     * Calcula las estadísticas (promedio/min/max/total) de una variable dentro de un sprint.
     * Retorna null si no hay registros numéricos.
     */
    private EvaluacionSprintDto calcularMetricaSprint(Sprint sprint, Variable variable, List<RegistroValor> registros) {
        List<BigDecimal> valores = registros.stream()
                .filter(r -> r.getVariable().getId().equals(variable.getId()))
                .map(RegistroValor::getValorNum)
                .filter(Objects::nonNull)
                .toList();

        if (valores.isEmpty()) return null;

        BigDecimal sum = valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(valores.size()), 2, RoundingMode.HALF_UP);
        BigDecimal min = valores.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal max = valores.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);

        return new EvaluacionSprintDto(
                sprint.getId(), sprint.getNumero(),
                variable.getId(), variable.getNombre(),
                variable.getMetrica().getCategoria().getNombre(),
                variable.getTipoAlcance(),
                avg, min, max, valores.size(),
                variable.getFormulaTexto(),
                variable.getFrecuenciaCaptura()
        );
    }
}
