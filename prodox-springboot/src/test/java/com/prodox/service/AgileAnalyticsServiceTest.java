// Autor: Cristian Santiago Martinez Cordoba — PRODOX
package com.prodox.service;

import com.prodox.dto.EvaluacionSprintDto;
import com.prodox.dto.analytics.ProjectOverviewDto;
import com.prodox.dto.analytics.RiskDto;
import com.prodox.dto.analytics.TrendAnalysisDto;
import com.prodox.entity.Proyecto;
import com.prodox.entity.Sprint;
import com.prodox.repository.ProyectoRepository;
import com.prodox.repository.SprintRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

/**
 * Corrección de auditoría (Dashboard/Evaluación): AgileAnalyticsService no
 * tenía cobertura de tests unitarios. Estas pruebas cubren específicamente
 * los dos defectos confirmados en la auditoría del flujo captura -> cálculo
 * -> resultado -> evaluación -> Dashboard -> análisis:
 *
 * 1. getProjectOverview() promediaba SIEMPRE todas las categorías del sprint
 *    entre sí para producir un "score general" (mejorSprint/peorSprint) —
 *    matemáticamente inválido cuando esas categorías representan escalas o
 *    unidades distintas (ej. Velocidad en Story Points vs. Satisfacción en
 *    %, caso real "Creación de un avatar Xabi"). Ahora solo se calcula un
 *    score cuando el sprint tiene evaluaciones de UNA sola categoría — con
 *    2+ categorías el sprint no participa, en vez de fabricar un número.
 *
 * 2. getSprintTrends() dividía siempre entre la cantidad de evaluaciones de
 *    una categoría en un sprint, sin comprobar si esa cantidad era cero —
 *    si una categoría no tenía NINGUNA evaluación en un sprint puntual de
 *    la serie (mientras sí las tenía en otros), lanzaba ArithmeticException
 *    y tumbaba la respuesta completa de tendencias (todas las categorías).
 *    Ahora ese punto se omite, sin inventar un valor ni lanzar excepción.
 *
 * Nota de alcance: AgileAnalyticsService NUNCA lee resultados_metricas
 * (estado="calculado"/"error"/"incompleto") — toda esta capa se alimenta de
 * EvaluacionService.evaluarSprint(), que promedia registro_valores crudo.
 * El filtrado por estado ya está cubierto donde sí aplica: EvaluacionService.
 * resultadosCalculadosDeLaMetrica() (EvaluacionServiceTest), CalculoMetricaService
 * (CalculoMetricaServiceTest) y MetricaAcademicaService (MetricaAcademicaServiceTest).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AgileAnalyticsService — Dashboard/Evaluación (corrección de auditoría)")
class AgileAnalyticsServiceTest {

    @Mock private SprintRepository sprintRepo;
    @Mock private ProyectoRepository proyectoRepo;
    @Mock private EvaluacionService evaluacionService;

    private AgileAnalyticsService service;
    private UUID proyectoId;

    @BeforeEach
    void setUp() {
        service = new AgileAnalyticsService(sprintRepo, proyectoRepo, evaluacionService);
        proyectoId = UUID.randomUUID();
    }

    private Sprint sprint(int numero, String estado) {
        Sprint s = new Sprint();
        s.setId(UUID.randomUUID());
        s.setProyectoId(proyectoId);
        s.setNumero(numero);
        s.setEstado(estado);
        s.setFechaInicio(LocalDate.now().minusWeeks(10 - numero));
        return s;
    }

    private EvaluacionSprintDto evalDto(UUID sprintId, int sprintNumero, String categoria, BigDecimal promedio) {
        return new EvaluacionSprintDto(
                sprintId, sprintNumero, UUID.randomUUID(), "variable-" + categoria,
                categoria, "grupal", promedio, promedio, promedio, 1,
                null, "por_sprint");
    }

    // ════════════════════════════════════════════════════════════════════
    // 1-2) No promediar directamente categorías con escalas/unidades
    // heterogéneas (caso real: "Creación de un avatar Xabi" — Velocidad en
    // Story Points junto con Satisfacción en %).
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getProjectOverview: con 2 categorías heterogéneas por sprint (Story Points y %), NO se fabrica un score general mezclándolas — mejorSprint/peorSprint quedan null")
    void getProjectOverview_dosCategoriasHeterogeneas_noFabricaScoreGeneral() {
        Sprint s1 = sprint(1, "finalizado");
        Sprint s2 = sprint(2, "finalizado");

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(new Proyecto()));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(s2, s1));

        // Velocidad ~20 Story Points, Satisfacción ~85-90% — magnitudes que,
        // si se promediaran directamente, darían un número "creíble" pero
        // sin ningún respaldo matemático (esa es exactamente la regresión
        // que este test evita).
        when(evaluacionService.evaluarSprint(s1.getId())).thenReturn(List.of(
                evalDto(s1.getId(), 1, "Velocidad", new BigDecimal("20")),
                evalDto(s1.getId(), 1, "Satisfaccion", new BigDecimal("85"))
        ));
        when(evaluacionService.evaluarSprint(s2.getId())).thenReturn(List.of(
                evalDto(s2.getId(), 2, "Velocidad", new BigDecimal("22")),
                evalDto(s2.getId(), 2, "Satisfaccion", new BigDecimal("90"))
        ));

        ProjectOverviewDto overview = service.getProjectOverview(proyectoId);

        assertThat(overview.mejorSprint()).isNull();
        assertThat(overview.peorSprint()).isNull();
        // Los promedios POR categoría (cada uno en su propia escala, sin
        // mezclar con la otra) siguen calculándose con normalidad.
        assertThat(overview.promedioHistorico().get("Velocidad")).isEqualByComparingTo("21.00");
        assertThat(overview.promedioHistorico().get("Satisfaccion")).isEqualByComparingTo("87.50");
        assertThat(overview.datosDisponibles()).isTrue();
    }

    @Test
    @DisplayName("getProjectOverview: con UNA sola categoría por sprint, mejorSprint/peorSprint SÍ se calculan (no hay mezcla de escalas que evitar)")
    void getProjectOverview_unaSolaCategoria_calculaMejorYPeorSprint() {
        Sprint s1 = sprint(1, "finalizado");
        Sprint s2 = sprint(2, "finalizado");

        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(new Proyecto()));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(s2, s1));

        when(evaluacionService.evaluarSprint(s1.getId())).thenReturn(List.of(
                evalDto(s1.getId(), 1, "Significado", new BigDecimal("60"))
        ));
        when(evaluacionService.evaluarSprint(s2.getId())).thenReturn(List.of(
                evalDto(s2.getId(), 2, "Significado", new BigDecimal("80"))
        ));

        ProjectOverviewDto overview = service.getProjectOverview(proyectoId);

        assertThat(overview.mejorSprint()).isNotNull();
        assertThat(overview.mejorSprint().numero()).isEqualTo(2);
        assertThat(overview.mejorSprint().scoreGeneral()).isEqualByComparingTo("80");
        assertThat(overview.peorSprint()).isNotNull();
        assertThat(overview.peorSprint().numero()).isEqualTo(1);
        assertThat(overview.peorSprint().scoreGeneral()).isEqualByComparingTo("60");
    }

    // ════════════════════════════════════════════════════════════════════
    // 3-4) Categoría sin resultados en un sprint puntual: nunca división por
    // cero, nunca ArithmeticException/NaN/Infinity — el Dashboard sigue
    // funcionando, sin inventar datos para rellenar el hueco.
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getSprintTrends: una categoría sin evaluaciones en un sprint puntual NO lanza ArithmeticException ni tumba las demás categorías")
    void getSprintTrends_categoriaSinDatosEnUnSprint_noLanzaExcepcion() {
        Sprint s1 = sprint(1, "finalizado");
        Sprint s2 = sprint(2, "finalizado");
        Sprint s3 = sprint(3, "finalizado");

        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(s3, s2, s1));

        // "Calidad" tiene datos en s1 y s3, pero NO en s2 (conteo=0 para esa
        // categoría en ese sprint). "Velocidad" sí tiene datos en los 3.
        when(evaluacionService.evaluarSprint(s1.getId())).thenReturn(List.of(
                evalDto(s1.getId(), 1, "Calidad", new BigDecimal("70")),
                evalDto(s1.getId(), 1, "Velocidad", new BigDecimal("10"))
        ));
        when(evaluacionService.evaluarSprint(s2.getId())).thenReturn(List.of(
                evalDto(s2.getId(), 2, "Velocidad", new BigDecimal("12"))
        ));
        when(evaluacionService.evaluarSprint(s3.getId())).thenReturn(List.of(
                evalDto(s3.getId(), 3, "Calidad", new BigDecimal("75")),
                evalDto(s3.getId(), 3, "Velocidad", new BigDecimal("15"))
        ));

        assertThatCode(() -> service.getSprintTrends(proyectoId, null, 5))
                .doesNotThrowAnyException();

        List<TrendAnalysisDto> resultado = service.getSprintTrends(proyectoId, null, 5);

        TrendAnalysisDto calidad = resultado.stream()
                .filter(t -> "Calidad".equals(t.categoria())).findFirst().orElseThrow();
        // Solo 2 puntos (s1 y s3) — el hueco de s2 se omite, nunca se rellena con 0.
        assertThat(calidad.dataPoints()).hasSize(2);
        assertThat(calidad.dataPoints())
                .extracting(TrendAnalysisDto.SprintDataPoint::sprintNumero)
                .containsExactly(1, 3);

        TrendAnalysisDto velocidad = resultado.stream()
                .filter(t -> "Velocidad".equals(t.categoria())).findFirst().orElseThrow();
        assertThat(velocidad.dataPoints()).hasSize(3);
    }

    @Test
    @DisplayName("getSprintTrends: categoría sin NINGUNA evaluación en toda la serie de sprints -> no aparece en el resultado, no lanza excepción")
    void getSprintTrends_categoriaSinDatosEnNingunSprint_noApareceEnResultado() {
        Sprint s1 = sprint(1, "finalizado");
        Sprint s2 = sprint(2, "finalizado");

        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(s2, s1));
        when(evaluacionService.evaluarSprint(s1.getId())).thenReturn(List.of(
                evalDto(s1.getId(), 1, "Velocidad", new BigDecimal("10"))
        ));
        when(evaluacionService.evaluarSprint(s2.getId())).thenReturn(List.of(
                evalDto(s2.getId(), 2, "Velocidad", new BigDecimal("12"))
        ));

        List<TrendAnalysisDto> resultado = service.getSprintTrends(proyectoId, "Satisfaccion", 5);

        assertThat(resultado).isEmpty();
    }

    // ════════════════════════════════════════════════════════════════════
    // Corrección de auditoría (variabilidad): identifyRisks() clasificaba
    // "Alta variabilidad" (HIGH_VARIABILITY) comparando la desviación
    // estándar ABSOLUTA de la categoría contra un umbral fijo de 3.0, sin
    // relativizar a la escala/promedio (caso real: sprints 80/90/95,
    // sd≈6.24, CV≈7.06% -> "baja" según el criterio que el propio proyecto
    // ya usa para variables individuales, pero "alta" con el umbral viejo).
    // Ahora reutiliza EvaluacionService.clasificarVariabilidadPorCV()
    // (CV<15%->baja, 15-35%->media, >35%->alta) — mismo criterio en todo
    // el proyecto, sin umbral nuevo.
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("identifyRisks: sprints 80/90/95 (sd≈6.24, CV≈7.06% -> baja) NO genera HIGH_VARIABILITY")
    void identifyRisks_cvBajo_noGeneraHighVariability() {
        Sprint s1 = sprint(1, "finalizado");
        Sprint s2 = sprint(2, "finalizado");
        Sprint s3 = sprint(3, "finalizado");

        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(s3, s2, s1));
        when(evaluacionService.evaluarSprint(s1.getId())).thenReturn(List.of(
                evalDto(s1.getId(), 1, "Significado", new BigDecimal("80"))));
        when(evaluacionService.evaluarSprint(s2.getId())).thenReturn(List.of(
                evalDto(s2.getId(), 2, "Significado", new BigDecimal("90"))));
        when(evaluacionService.evaluarSprint(s3.getId())).thenReturn(List.of(
                evalDto(s3.getId(), 3, "Significado", new BigDecimal("95"))));

        List<RiskDto> risks = service.identifyRisks(proyectoId);

        assertThat(risks).noneMatch(r -> "HIGH_VARIABILITY".equals(r.tipo()));
    }

    @Test
    @DisplayName("identifyRisks: CV medio (15-35%, valores 40/60/80) NO genera HIGH_VARIABILITY (solo 'alta' dispara el riesgo)")
    void identifyRisks_cvMedio_noGeneraHighVariability() {
        Sprint s1 = sprint(1, "finalizado");
        Sprint s2 = sprint(2, "finalizado");
        Sprint s3 = sprint(3, "finalizado");

        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(s3, s2, s1));
        when(evaluacionService.evaluarSprint(s1.getId())).thenReturn(List.of(
                evalDto(s1.getId(), 1, "Significado", new BigDecimal("40"))));
        when(evaluacionService.evaluarSprint(s2.getId())).thenReturn(List.of(
                evalDto(s2.getId(), 2, "Significado", new BigDecimal("60"))));
        when(evaluacionService.evaluarSprint(s3.getId())).thenReturn(List.of(
                evalDto(s3.getId(), 3, "Significado", new BigDecimal("80"))));

        List<RiskDto> risks = service.identifyRisks(proyectoId);

        assertThat(risks).noneMatch(r -> "HIGH_VARIABILITY".equals(r.tipo()));
    }

    @Test
    @DisplayName("identifyRisks: CV alto (>35%, valores 20/50/80) SÍ genera HIGH_VARIABILITY")
    void identifyRisks_cvAlto_generaHighVariability() {
        Sprint s1 = sprint(1, "finalizado");
        Sprint s2 = sprint(2, "finalizado");
        Sprint s3 = sprint(3, "finalizado");

        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(s3, s2, s1));
        when(evaluacionService.evaluarSprint(s1.getId())).thenReturn(List.of(
                evalDto(s1.getId(), 1, "Significado", new BigDecimal("20"))));
        when(evaluacionService.evaluarSprint(s2.getId())).thenReturn(List.of(
                evalDto(s2.getId(), 2, "Significado", new BigDecimal("50"))));
        when(evaluacionService.evaluarSprint(s3.getId())).thenReturn(List.of(
                evalDto(s3.getId(), 3, "Significado", new BigDecimal("80"))));

        List<RiskDto> risks = service.identifyRisks(proyectoId);

        assertThat(risks).anyMatch(r -> "HIGH_VARIABILITY".equals(r.tipo())
                && "Significado".equals(r.categoriaAfectada()));
    }

    @Test
    @DisplayName("identifyRisks: promedio general en 0 no divide por cero ni genera HIGH_VARIABILITY")
    void identifyRisks_promedioCero_noDivisionPorCeroNiRiesgo() {
        Sprint s1 = sprint(1, "finalizado");
        Sprint s2 = sprint(2, "finalizado");
        Sprint s3 = sprint(3, "finalizado");

        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(s3, s2, s1));
        when(evaluacionService.evaluarSprint(s1.getId())).thenReturn(List.of(
                evalDto(s1.getId(), 1, "Significado", BigDecimal.ZERO)));
        when(evaluacionService.evaluarSprint(s2.getId())).thenReturn(List.of(
                evalDto(s2.getId(), 2, "Significado", BigDecimal.ZERO)));
        when(evaluacionService.evaluarSprint(s3.getId())).thenReturn(List.of(
                evalDto(s3.getId(), 3, "Significado", BigDecimal.ZERO)));

        assertThatCode(() -> service.identifyRisks(proyectoId)).doesNotThrowAnyException();
        assertThat(service.identifyRisks(proyectoId)).noneMatch(r -> "HIGH_VARIABILITY".equals(r.tipo()));
    }

    // ════════════════════════════════════════════════════════════════════
    // 7-8) Resultado calculado válido / caso normal existente (regresión):
    // el flujo básico de overview y trends sigue funcionando exactamente
    // igual cuando los datos son homogéneos y completos.
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Regresión: getProjectOverview sin sprints finalizados devuelve el DTO vacío existente, sin cambios")
    void getProjectOverview_sinSprintsFinalizados_devuelveDtoVacio() {
        when(proyectoRepo.findById(proyectoId)).thenReturn(Optional.of(new Proyecto()));
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of());

        ProjectOverviewDto overview = service.getProjectOverview(proyectoId);

        assertThat(overview.datosDisponibles()).isFalse();
        assertThat(overview.sprintsFinalizados()).isZero();
        assertThat(overview.mejorSprint()).isNull();
        assertThat(overview.peorSprint()).isNull();
        assertThat(overview.promedioHistorico()).isEmpty();
    }

    @Test
    @DisplayName("Regresión: getSprintTrends con menos de 2 sprints finalizados devuelve lista vacía (comportamiento existente, sin cambios)")
    void getSprintTrends_menosDeDosSprints_devuelveListaVacia() {
        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId))
                .thenReturn(List.of(sprint(1, "finalizado")));

        assertThat(service.getSprintTrends(proyectoId, null, 5)).isEmpty();
    }

    @Test
    @DisplayName("Regresión: getSprintTrends con datos completos y homogéneos calcula promedio, desviación y tendencia normalmente")
    void getSprintTrends_datosCompletosYHomogeneos_calculaCorrectamente() {
        Sprint s1 = sprint(1, "finalizado");
        Sprint s2 = sprint(2, "finalizado");
        Sprint s3 = sprint(3, "finalizado");

        when(sprintRepo.findByProyectoIdOrderByNumeroDesc(proyectoId)).thenReturn(List.of(s3, s2, s1));
        when(evaluacionService.evaluarSprint(s1.getId())).thenReturn(List.of(
                evalDto(s1.getId(), 1, "Calidad", new BigDecimal("60"))));
        when(evaluacionService.evaluarSprint(s2.getId())).thenReturn(List.of(
                evalDto(s2.getId(), 2, "Calidad", new BigDecimal("70"))));
        when(evaluacionService.evaluarSprint(s3.getId())).thenReturn(List.of(
                evalDto(s3.getId(), 3, "Calidad", new BigDecimal("80"))));

        List<TrendAnalysisDto> resultado = service.getSprintTrends(proyectoId, "Calidad", 5);

        assertThat(resultado).hasSize(1);
        TrendAnalysisDto calidad = resultado.get(0);
        assertThat(calidad.dataPoints()).hasSize(3);
        assertThat(calidad.promedioGeneral()).isEqualByComparingTo("70.00");
        assertThat(calidad.tendenciaGeneral()).isEqualTo("UP");
        assertThat(calidad.datosDisponibles()).isTrue();
    }
}
