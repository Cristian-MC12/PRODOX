// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.prodox.dto.EvaluacionSprintDto;
import com.prodox.dto.analytics.*;
import com.prodox.entity.Proyecto;
import com.prodox.entity.Sprint;
import com.prodox.repository.ProyectoRepository;
import com.prodox.repository.SprintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio que calcula métricas Agile determinísticas basadas en datos reales de PRODOX.
 * 
 * IMPORTANTE: Este servicio NO utiliza IA. Todos los cálculos son determinísticos.
 * La interpretación y recomendaciones serán agregadas por el AI Copilot en fases posteriores.
 * 
 * LIMITACIONES:
 * - No calcula Cycle Time tradicional (PRODOX no rastrea items individuales con timestamps)
 * - No calcula WIP tradicional (PRODOX no tiene items con estados)
 * - Velocity depende de si el equipo configuró una variable de velocidad
 * - Las métricas dependen de las variables que cada equipo haya configurado
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgileAnalyticsService {

    private final SprintRepository   sprintRepo;
    private final ProyectoRepository proyectoRepo;
    private final EvaluacionService  evaluacionService;

    // Umbrales para detección de tendencias y anomalías
    private static final BigDecimal THRESHOLD_STABLE = new BigDecimal("5.0"); // ±5%
    private static final BigDecimal THRESHOLD_ANOMALY = new BigDecimal("2.0"); // 2 desviaciones estándar

    // ═══════════════════════════════════════════════════════════════════════
    // MÉTRICAS POR SPRINT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Obtiene un resumen de métricas de un sprint.
     * Calcula promedios por categoría y metadata del sprint.
     */
    public SprintMetricsSummaryDto getSprintMetricsSummary(UUID sprintId) {
        Sprint sprint = sprintRepo.findById(sprintId)
                .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado"));

        // Obtener evaluaciones del sprint (reutiliza EvaluacionService)
        List<EvaluacionSprintDto> evaluaciones = evaluacionService.evaluarSprint(sprintId);

        if (evaluaciones.isEmpty()) {
            return buildEmptySummary(sprint);
        }

        // Agrupar por categoría y calcular promedio
        Map<String, BigDecimal> promediosPorCategoria = evaluaciones.stream()
                .collect(Collectors.groupingBy(
                        EvaluacionSprintDto::categoria,
                        Collectors.averagingDouble(e -> e.promedio().doubleValue())
                ))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> BigDecimal.valueOf(e.getValue()).setScale(2, RoundingMode.HALF_UP)
                ));

        int totalRegistros = evaluaciones.stream()
                .mapToInt(EvaluacionSprintDto::totalRegistros)
                .sum();

        Integer duracionDias = null;
        if (sprint.getFechaInicio() != null && sprint.getFechaFin() != null) {
            duracionDias = (int) ChronoUnit.DAYS.between(sprint.getFechaInicio(), sprint.getFechaFin());
        }

        return new SprintMetricsSummaryDto(
                sprint.getId(),
                sprint.getNumero(),
                sprint.getSprintGoal(),
                sprint.getEstado(),
                sprint.getFechaInicio(),
                sprint.getFechaFin(),
                duracionDias,
                promediosPorCategoria,
                totalRegistros,
                true
        );
    }

    private SprintMetricsSummaryDto buildEmptySummary(Sprint sprint) {
        Integer duracionDias = null;
        if (sprint.getFechaInicio() != null && sprint.getFechaFin() != null) {
            duracionDias = (int) ChronoUnit.DAYS.between(sprint.getFechaInicio(), sprint.getFechaFin());
        }

        return new SprintMetricsSummaryDto(
                sprint.getId(),
                sprint.getNumero(),
                sprint.getSprintGoal(),
                sprint.getEstado(),
                sprint.getFechaInicio(),
                sprint.getFechaFin(),
                duracionDias,
                Map.of(),
                0,
                false
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    // COMPARACIÓN ENTRE SPRINTS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Compara dos sprints lado a lado.
     * Calcula variación absoluta, porcentual y dirección de tendencia.
     */
    public SprintComparisonDto compareSprints(UUID sprintId1, UUID sprintId2) {
        Sprint sprint1 = sprintRepo.findById(sprintId1)
                .orElseThrow(() -> new IllegalArgumentException("Sprint 1 no encontrado"));
        Sprint sprint2 = sprintRepo.findById(sprintId2)
                .orElseThrow(() -> new IllegalArgumentException("Sprint 2 no encontrado"));

        // Validar que pertenezcan al mismo proyecto
        if (!sprint1.getProyectoId().equals(sprint2.getProyectoId())) {
            throw new IllegalArgumentException("Los sprints deben pertenecer al mismo proyecto");
        }

        List<EvaluacionSprintDto> eval1 = evaluacionService.evaluarSprint(sprintId1);
        List<EvaluacionSprintDto> eval2 = evaluacionService.evaluarSprint(sprintId2);

        if (eval1.isEmpty() || eval2.isEmpty()) {
            return buildEmptyComparison(sprint1, sprint2);
        }

        // Promedios por categoría para cada sprint
        Map<String, BigDecimal> metricas1 = calcularPromediosPorCategoria(eval1);
        Map<String, BigDecimal> metricas2 = calcularPromediosPorCategoria(eval2);

        // Obtener todas las categorías presentes
        Set<String> categorias = new HashSet<>();
        categorias.addAll(metricas1.keySet());
        categorias.addAll(metricas2.keySet());

        Map<String, BigDecimal> variacionAbsoluta = new HashMap<>();
        Map<String, BigDecimal> variacionPorcentual = new HashMap<>();
        Map<String, String> tendencia = new HashMap<>();

        for (String categoria : categorias) {
            BigDecimal val1 = metricas1.getOrDefault(categoria, BigDecimal.ZERO);
            BigDecimal val2 = metricas2.getOrDefault(categoria, BigDecimal.ZERO);

            // Variación absoluta: val2 - val1
            BigDecimal varAbs = val2.subtract(val1).setScale(2, RoundingMode.HALF_UP);
            variacionAbsoluta.put(categoria, varAbs);

            // Variación porcentual: ((val2 - val1) / val1) * 100
            if (val1.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal varPorc = varAbs.divide(val1, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, RoundingMode.HALF_UP);
                variacionPorcentual.put(categoria, varPorc);

                // Tendencia
                if (varPorc.abs().compareTo(THRESHOLD_STABLE) < 0) {
                    tendencia.put(categoria, "STABLE");
                } else if (varPorc.compareTo(BigDecimal.ZERO) > 0) {
                    tendencia.put(categoria, "UP");
                } else {
                    tendencia.put(categoria, "DOWN");
                }
            } else {
                variacionPorcentual.put(categoria, BigDecimal.ZERO);
                tendencia.put(categoria, "STABLE");
            }
        }

        return new SprintComparisonDto(
                sprint1.getId(),
                sprint1.getNumero(),
                sprint2.getId(),
                sprint2.getNumero(),
                metricas1,
                metricas2,
                variacionAbsoluta,
                variacionPorcentual,
                tendencia,
                true
        );
    }

    private SprintComparisonDto buildEmptyComparison(Sprint sprint1, Sprint sprint2) {
        return new SprintComparisonDto(
                sprint1.getId(),
                sprint1.getNumero(),
                sprint2.getId(),
                sprint2.getNumero(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                false
        );
    }

    private Map<String, BigDecimal> calcularPromediosPorCategoria(List<EvaluacionSprintDto> evaluaciones) {
        return evaluaciones.stream()
                .collect(Collectors.groupingBy(
                        EvaluacionSprintDto::categoria,
                        Collectors.averagingDouble(e -> e.promedio().doubleValue())
                ))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> BigDecimal.valueOf(e.getValue()).setScale(2, RoundingMode.HALF_UP)
                ));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TENDENCIAS HISTÓRICAS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Analiza la tendencia de métricas a lo largo de los últimos N sprints.
     * 
     * @param proyectoId ID del proyecto
     * @param categoria Categoría a analizar (Calidad, Productividad, etc.) o null para todas
     * @param numberOfSprints Número de sprints a incluir en el análisis
     */
    public List<TrendAnalysisDto> getSprintTrends(UUID proyectoId, String categoria, Integer numberOfSprints) {
        // Obtener últimos N sprints finalizados
        List<Sprint> sprints = sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)
                .stream()
                .filter(s -> "finalizado".equals(s.getEstado()))
                .limit(numberOfSprints != null ? numberOfSprints : 5)
                .sorted(Comparator.comparing(Sprint::getNumero))
                .toList();

        if (sprints.size() < 2) {
            log.warn("No hay suficientes sprints finalizados para análisis de tendencias");
            return List.of();
        }

        // Evaluar todos los sprints
        Map<UUID, List<EvaluacionSprintDto>> evaluacionesPorSprint = new HashMap<>();
        for (Sprint sprint : sprints) {
            evaluacionesPorSprint.put(sprint.getId(), evaluacionService.evaluarSprint(sprint.getId()));
        }

        // Obtener todas las categorías si no se especificó una
        Set<String> categorias = new HashSet<>();
        if (categoria != null) {
            categorias.add(categoria);
        } else {
            evaluacionesPorSprint.values().stream()
                    .flatMap(List::stream)
                    .map(EvaluacionSprintDto::categoria)
                    .forEach(categorias::add);
        }

        List<TrendAnalysisDto> trends = new ArrayList<>();

        for (String cat : categorias) {
            List<TrendAnalysisDto.SprintDataPoint> dataPoints = new ArrayList<>();
            List<BigDecimal> valores = new ArrayList<>();

            for (Sprint sprint : sprints) {
                List<EvaluacionSprintDto> evals = evaluacionesPorSprint.get(sprint.getId());
                BigDecimal promedio = evals.stream()
                        .filter(e -> cat.equals(e.categoria()))
                        .map(EvaluacionSprintDto::promedio)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(
                                BigDecimal.valueOf(evals.stream().filter(e -> cat.equals(e.categoria())).count()),
                                2,
                                RoundingMode.HALF_UP
                        );

                dataPoints.add(new TrendAnalysisDto.SprintDataPoint(
                        sprint.getNumero(),
                        promedio,
                        sprint.getFechaInicio() != null ? sprint.getFechaInicio().toString() : null
                ));
                valores.add(promedio);
            }

            if (!valores.isEmpty()) {
                BigDecimal promedioGeneral = valores.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(valores.size()), 2, RoundingMode.HALF_UP);

                BigDecimal desviacion = calcularDesviacionEstandar(valores, promedioGeneral);

                // Calcular variación total (último - primero) / primero * 100
                BigDecimal variacionTotal = BigDecimal.ZERO;
                String tendenciaGeneral = "STABLE";
                if (valores.size() >= 2) {
                    BigDecimal primero = valores.get(0);
                    BigDecimal ultimo = valores.get(valores.size() - 1);
                    if (primero.compareTo(BigDecimal.ZERO) != 0) {
                        variacionTotal = ultimo.subtract(primero)
                                .divide(primero, 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"))
                                .setScale(2, RoundingMode.HALF_UP);

                        if (variacionTotal.abs().compareTo(THRESHOLD_STABLE) >= 0) {
                            tendenciaGeneral = variacionTotal.compareTo(BigDecimal.ZERO) > 0 ? "UP" : "DOWN";
                        }
                    }
                }

                trends.add(new TrendAnalysisDto(
                        proyectoId,
                        cat,
                        sprints.size(),
                        dataPoints,
                        promedioGeneral,
                        desviacion,
                        tendenciaGeneral,
                        variacionTotal,
                        true
                ));
            }
        }

        return trends;
    }

    private BigDecimal calcularDesviacionEstandar(List<BigDecimal> valores, BigDecimal promedio) {
        if (valores.size() < 2) return BigDecimal.ZERO;

        BigDecimal sumaCuadradosDiferencias = valores.stream()
                .map(v -> v.subtract(promedio).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal varianza = sumaCuadradosDiferencias.divide(
                BigDecimal.valueOf(valores.size()),
                4,
                RoundingMode.HALF_UP
        );

        return BigDecimal.valueOf(Math.sqrt(varianza.doubleValue())).setScale(2, RoundingMode.HALF_UP);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DETECCIÓN DE ANOMALÍAS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Detecta anomalías en un sprint comparado con el historial.
     * Una anomalía es un valor que se aleja significativamente del promedio histórico.
     * 
     * Criterio: |valor - promedio| > 2 * desviación_estándar
     */
    public List<AnomalyDto> detectAnomalies(UUID sprintId) {
        Sprint sprint = sprintRepo.findById(sprintId)
                .orElseThrow(() -> new IllegalArgumentException("Sprint no encontrado"));

        // Obtener sprints históricos del mismo proyecto (excluir el actual)
        List<Sprint> sprintsHistoricos = sprintRepo.findByProyectoIdOrderByNumeroDesc(sprint.getProyectoId())
                .stream()
                .filter(s -> "finalizado".equals(s.getEstado()))
                .filter(s -> !s.getId().equals(sprintId))
                .limit(10)
                .toList();

        if (sprintsHistoricos.size() < 3) {
            log.info("No hay suficientes sprints históricos para detectar anomalías");
            return List.of();
        }

        List<EvaluacionSprintDto> evaluacionActual = evaluacionService.evaluarSprint(sprintId);
        Map<String, BigDecimal> promediosActuales = calcularPromediosPorCategoria(evaluacionActual);

        // Calcular promedio y desviación histórica por categoría
        Map<String, List<BigDecimal>> valoresHistoricos = new HashMap<>();
        for (Sprint s : sprintsHistoricos) {
            List<EvaluacionSprintDto> eval = evaluacionService.evaluarSprint(s.getId());
            Map<String, BigDecimal> promedios = calcularPromediosPorCategoria(eval);
            promedios.forEach((cat, val) ->
                    valoresHistoricos.computeIfAbsent(cat, k -> new ArrayList<>()).add(val)
            );
        }

        List<AnomalyDto> anomalies = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : promediosActuales.entrySet()) {
            String categoria = entry.getKey();
            BigDecimal valorActual = entry.getValue();

            List<BigDecimal> historico = valoresHistoricos.get(categoria);
            if (historico == null || historico.size() < 3) continue;

            BigDecimal promedioHistorico = historico.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(historico.size()), 2, RoundingMode.HALF_UP);

            BigDecimal desviacion = calcularDesviacionEstandar(historico, promedioHistorico);

            if (desviacion.compareTo(BigDecimal.ZERO) == 0) continue;

            // Calcular número de desviaciones
            BigDecimal diferencia = valorActual.subtract(promedioHistorico).abs();
            BigDecimal numDesviaciones = diferencia.divide(desviacion, 2, RoundingMode.HALF_UP);

            // Detectar anomalía si supera el umbral
            if (numDesviaciones.compareTo(THRESHOLD_ANOMALY) > 0) {
                String direccion = valorActual.compareTo(promedioHistorico) > 0 ? "ABOVE" : "BELOW";
                String severidad = determineSeverity(numDesviaciones);
                String mensaje = String.format(
                        "%s: valor actual %.2f vs promedio histórico %.2f (%.1f desviaciones)",
                        categoria, valorActual, promedioHistorico, numDesviaciones
                );

                anomalies.add(new AnomalyDto(
                        sprint.getId(),
                        sprint.getNumero(),
                        categoria,
                        null,
                        valorActual,
                        promedioHistorico,
                        desviacion,
                        numDesviaciones,
                        severidad,
                        direccion,
                        mensaje
                ));
            }
        }

        return anomalies;
    }

    private String determineSeverity(BigDecimal numDesviaciones) {
        if (numDesviaciones.compareTo(new BigDecimal("3.0")) > 0) return "CRITICAL";
        if (numDesviaciones.compareTo(new BigDecimal("2.5")) > 0) return "HIGH";
        if (numDesviaciones.compareTo(new BigDecimal("2.0")) > 0) return "MEDIUM";
        return "LOW";
    }

    // ═══════════════════════════════════════════════════════════════════════
    // IDENTIFICACIÓN DE RIESGOS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Identifica riesgos en el proyecto basándose en señales objetivas.
     * NO incluye interpretación de IA, solo hechos detectables.
     */
    public List<RiskDto> identifyRisks(UUID proyectoId) {
        List<RiskDto> risks = new ArrayList<>();

        // Analizar tendencias de últimos 3 sprints
        List<TrendAnalysisDto> trends = getSprintTrends(proyectoId, null, 3);

        for (TrendAnalysisDto trend : trends) {
            // Riesgo: Tendencia negativa sostenida
            if ("DOWN".equals(trend.tendenciaGeneral()) &&
                    trend.variacionTotal().compareTo(new BigDecimal("-10.0")) < 0) {

                risks.add(new RiskDto(
                        proyectoId,
                        "DECLINING_METRIC",
                        determineTrendSeverity(trend.variacionTotal()),
                        trend.categoria() + " en descenso sostenido",
                        String.format("%s disminuyó %.1f%% en los últimos %d sprints",
                                trend.categoria(), trend.variacionTotal().abs(), trend.numeroSprints()),
                        trend.categoria(),
                        java.time.Instant.now()
                ));
            }

            // Riesgo: Alta variabilidad
            if (trend.desviacionEstandar().compareTo(new BigDecimal("3.0")) > 0) {
                risks.add(new RiskDto(
                        proyectoId,
                        "HIGH_VARIABILITY",
                        "MEDIUM",
                        "Alta variabilidad en " + trend.categoria(),
                        String.format("Desviación estándar de %.2f indica resultados inconsistentes",
                                trend.desviacionEstandar()),
                        trend.categoria(),
                        java.time.Instant.now()
                ));
            }
        }

        return risks;
    }

    private String determineTrendSeverity(BigDecimal variacion) {
        BigDecimal abs = variacion.abs();
        if (abs.compareTo(new BigDecimal("30.0")) > 0) return "CRITICAL";
        if (abs.compareTo(new BigDecimal("20.0")) > 0) return "HIGH";
        if (abs.compareTo(new BigDecimal("10.0")) > 0) return "MEDIUM";
        return "LOW";
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RESUMEN DEL PROYECTO
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Genera un resumen general del proyecto con todas sus métricas agregadas.
     */
    public ProjectOverviewDto getProjectOverview(UUID proyectoId) {
        Proyecto proyecto = proyectoRepo.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado"));

        List<Sprint> todosLosSprints = sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId);
        List<Sprint> sprintsFinalizados = todosLosSprints.stream()
                .filter(s -> "finalizado".equals(s.getEstado()))
                .toList();

        Sprint sprintActual = todosLosSprints.stream()
                .filter(s -> "en_ejecucion".equals(s.getEstado()))
                .findFirst()
                .orElse(null);

        if (sprintsFinalizados.isEmpty()) {
            return new ProjectOverviewDto(
                    proyectoId,
                    proyecto.getNombre(),
                    todosLosSprints.size(),
                    0,
                    sprintActual != null ? sprintActual.getNumero() : null,
                    Map.of(),
                    null,
                    null,
                    false
            );
        }

        // Calcular promedio histórico por categoría
        Map<String, List<BigDecimal>> valoresPorCategoria = new HashMap<>();
        Map<Sprint, BigDecimal> scoresPorSprint = new HashMap<>();

        for (Sprint sprint : sprintsFinalizados) {
            List<EvaluacionSprintDto> eval = evaluacionService.evaluarSprint(sprint.getId());
            Map<String, BigDecimal> promedios = calcularPromediosPorCategoria(eval);

            promedios.forEach((cat, val) ->
                    valoresPorCategoria.computeIfAbsent(cat, k -> new ArrayList<>()).add(val)
            );

            // Score general del sprint (promedio de todas las categorías)
            if (!promedios.isEmpty()) {
                BigDecimal scoreGeneral = promedios.values().stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(promedios.size()), 2, RoundingMode.HALF_UP);
                scoresPorSprint.put(sprint, scoreGeneral);
            }
        }

        Map<String, BigDecimal> promedioHistorico = valoresPorCategoria.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .divide(BigDecimal.valueOf(e.getValue().size()), 2, RoundingMode.HALF_UP)
                ));

        // Identificar mejor y peor sprint
        ProjectOverviewDto.SprintPerformance mejorSprint = null;
        ProjectOverviewDto.SprintPerformance peorSprint = null;

        if (!scoresPorSprint.isEmpty()) {
            Map.Entry<Sprint, BigDecimal> mejor = scoresPorSprint.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);

            Map.Entry<Sprint, BigDecimal> peor = scoresPorSprint.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .orElse(null);

            if (mejor != null) {
                mejorSprint = new ProjectOverviewDto.SprintPerformance(
                        mejor.getKey().getNumero(),
                        mejor.getValue(),
                        "Score general más alto"
                );
            }

            if (peor != null) {
                peorSprint = new ProjectOverviewDto.SprintPerformance(
                        peor.getKey().getNumero(),
                        peor.getValue(),
                        "Score general más bajo"
                );
            }
        }

        return new ProjectOverviewDto(
                proyectoId,
                proyecto.getNombre(),
                todosLosSprints.size(),
                sprintsFinalizados.size(),
                sprintActual != null ? sprintActual.getNumero() : null,
                promedioHistorico,
                mejorSprint,
                peorSprint,
                true
        );
    }
}
