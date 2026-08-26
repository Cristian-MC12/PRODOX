// Autor: Cristian Santiago Martinez Cordoba — MPDIA
package com.mpdia.service;

import com.mpdia.dto.EvaluacionSprintDto;
import com.mpdia.dto.MetricaEvaluacionDetalleDto;
import com.mpdia.dto.RegistroPuntoDto;
import com.mpdia.dto.SprintStatsDto;
import com.mpdia.dto.VariableEstadisticasDto;
import com.mpdia.entity.RegistroValor;
import com.mpdia.entity.Sprint;
import com.mpdia.entity.Variable;
import com.mpdia.repository.RegistroValorRepository;
import com.mpdia.repository.SprintRepository;
import com.mpdia.repository.VariableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

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

    // ════════════════════════════════════════════════════════════════════
    // Evaluación detallada: usa TODOS los registros de cada variable
    // (registros ilimitados por sprint), para construir gráficas de
    // evolución, comparación entre sprints y análisis estadístico.
    // No reemplaza evaluar()/evaluarSprint() — esos siguen igual porque
    // los usa AgileAnalyticsService (Copiloto IA).
    // ════════════════════════════════════════════════════════════════════

    /**
     * Evaluación detallada de todas las variables activas de un proyecto: cada variable trae
     * TODOS sus registros (todos los sprints, ordenados por fecha), estadísticas globales y
     * desglose por sprint. Fuente única para las gráficas de evolución/comparación/estadísticas.
     */
    public List<MetricaEvaluacionDetalleDto> evaluarDetalle(UUID proyectoId) {
        List<Variable> variables = variableRepo.findByProyectoIdAndActivaTrue(proyectoId);
        Map<UUID, Sprint> sprintsPorId = sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)
                .stream().collect(Collectors.toMap(Sprint::getId, s -> s));

        List<MetricaEvaluacionDetalleDto> resultado = new ArrayList<>();
        for (Variable variable : variables) {
            List<RegistroValor> registrosVariable = registroRepo.findByVariable_IdOrderByRegistradoAtAsc(variable.getId())
                    .stream()
                    .filter(r -> sprintsPorId.containsKey(r.getSprintId()))
                    .toList();
            if (registrosVariable.isEmpty()) continue;

            List<RegistroPuntoDto> puntos = registrosVariable.stream()
                    .filter(r -> r.getValorNum() != null)
                    .map(r -> {
                        Sprint s = sprintsPorId.get(r.getSprintId());
                        return new RegistroPuntoDto(
                                r.getId(), r.getValorNum(), r.getRegistradoAt(),
                                r.getSprintId(), s != null ? s.getNumero() : null,
                                r.getUserId());
                    }).toList();
            if (puntos.isEmpty()) continue;

            List<BigDecimal> valores = puntos.stream().map(RegistroPuntoDto::valor).toList();

            resultado.add(new MetricaEvaluacionDetalleDto(
                    variable.getId(), variable.getNombre(), variable.getDescripcion(),
                    variable.getMetrica().getCategoria().getNombre(),
                    variable.getTipoAlcance(),
                    variable.getFrecuenciaCaptura(),
                    variable.getFormulaTexto(),
                    puntos,
                    calcularEstadisticas(valores),
                    calcularPorSprint(puntos)
            ));
        }
        return resultado;
    }

    /**
     * Calcula promedio, min, max, cambio primero→último, tendencia (regresión lineal) y
     * variabilidad (coeficiente de variación) sobre una serie de valores ya ordenada
     * cronológicamente (orden = orden real de registro, usado como eje X de la regresión).
     */
    private VariableEstadisticasDto calcularEstadisticas(List<BigDecimal> valores) {
        int n = valores.size();
        BigDecimal sum = valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal promedio = sum.divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
        BigDecimal min = valores.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal max = valores.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal primero = valores.get(0);
        BigDecimal ultimo  = valores.get(n - 1);
        BigDecimal cambio  = ultimo.subtract(primero);
        BigDecimal cambioPct = primero.compareTo(BigDecimal.ZERO) == 0
                ? null
                : cambio.divide(primero.abs(), MathContext.DECIMAL64).multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP);

        // Tendencia: pendiente de una regresión lineal simple (mínimos cuadrados) usando el
        // índice de orden de registro como eje X (0,1,2,...). Se necesita al menos 2 registros.
        // Se clasifica "estable" cuando el cambio total proyectado por la pendiente a lo largo
        // de toda la serie es pequeño frente al promedio (umbral 5%, o 0.5 en términos absolutos
        // si el promedio es 0), para evitar que oscilaciones mínimas se etiqueten como tendencia.
        String tendencia = null;
        BigDecimal pendiente = null;
        if (n >= 2) {
            pendiente = calcularPendiente(valores);
            BigDecimal cambioProyectado = pendiente.multiply(BigDecimal.valueOf(n - 1));
            BigDecimal umbral = promedio.abs().compareTo(BigDecimal.ZERO) > 0
                    ? promedio.abs().multiply(BigDecimal.valueOf(0.05))
                    : BigDecimal.valueOf(0.5);
            if (cambioProyectado.abs().compareTo(umbral) < 0) {
                tendencia = "estable";
            } else {
                tendencia = cambioProyectado.compareTo(BigDecimal.ZERO) > 0 ? "ascendente" : "descendente";
            }
        }

        // Variabilidad: desviación estándar muestral + coeficiente de variación (%).
        // Solo se clasifica con al menos 3 registros (con 2 la desviación es poco informativa).
        BigDecimal desviacion = null;
        BigDecimal coefVariacion = null;
        String variabilidad = null;
        if (n >= 2) {
            BigDecimal sumaCuadrados = valores.stream()
                    .map(v -> v.subtract(promedio).pow(2))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal varianza = sumaCuadrados.divide(BigDecimal.valueOf(n - 1), MathContext.DECIMAL64);
            desviacion = varianza.max(BigDecimal.ZERO).sqrt(MathContext.DECIMAL64).setScale(4, RoundingMode.HALF_UP);
        }
        if (n >= 3 && promedio.compareTo(BigDecimal.ZERO) != 0) {
            coefVariacion = desviacion.divide(promedio.abs(), MathContext.DECIMAL64)
                    .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
            double cv = coefVariacion.doubleValue();
            variabilidad = cv < 15 ? "baja" : (cv <= 35 ? "media" : "alta");
        }

        return new VariableEstadisticasDto(
                n, promedio.setScale(2, RoundingMode.HALF_UP), min, max, primero, ultimo,
                cambio.setScale(2, RoundingMode.HALF_UP), cambioPct,
                tendencia, pendiente != null ? pendiente.setScale(4, RoundingMode.HALF_UP) : null,
                desviacion, coefVariacion, variabilidad
        );
    }

    /** Pendiente de la recta de regresión lineal (mínimos cuadrados) con X = índice 0..n-1. */
    private BigDecimal calcularPendiente(List<BigDecimal> valores) {
        int n = valores.size();
        double xMean = (n - 1) / 2.0;
        double yMean = valores.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double num = 0, den = 0;
        for (int i = 0; i < n; i++) {
            double x = i - xMean;
            double y = valores.get(i).doubleValue() - yMean;
            num += x * y;
            den += x * x;
        }
        if (den == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(num / den);
    }

    /** Agrupa los puntos por sprint y calcula promedio/min/max/total por sprint (orden ascendente). */
    private List<SprintStatsDto> calcularPorSprint(List<RegistroPuntoDto> puntos) {
        Map<UUID, List<RegistroPuntoDto>> porSprint = puntos.stream()
                .collect(Collectors.groupingBy(RegistroPuntoDto::sprintId, LinkedHashMap::new, Collectors.toList()));

        return porSprint.values().stream().map(pts -> {
            List<BigDecimal> valores = pts.stream().map(RegistroPuntoDto::valor).toList();
            BigDecimal sum = valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = sum.divide(BigDecimal.valueOf(valores.size()), 2, RoundingMode.HALF_UP);
            BigDecimal min = valores.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            BigDecimal max = valores.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            RegistroPuntoDto first = pts.get(0);
            return new SprintStatsDto(first.sprintId(), first.sprintNumero(), valores.size(), avg, min, max);
        })
        .sorted(Comparator.comparing(SprintStatsDto::sprintNumero, Comparator.nullsLast(Integer::compareTo)))
        .toList();
    }
}
