// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { construirSeccionesReporteGeneral, DatosReporteGeneral } from './reporte-general.builder';
import { ProyectoDto } from '../models/proyecto.model';
import { ProjectOverview } from '../models/analytics.model';
import { MetricaEvaluacionDetalleDto } from '../models/evaluacion-detalle.model';
import { AIInsight } from '../models/ai-insights.model';

describe('construirSeccionesReporteGeneral (Fase reportes: Reporte General del proyecto)', () => {
  const proyecto: ProyectoDto = {
    id: 'proj-1', nombre: 'Proyecto Xabi', descripcion: null, metodo: 'scrum',
    timeBoxSemanas: 2, numeroSprints: 3, fechaInicio: '2026-07-01', productGoal: 'Meta del producto',
    sprintGoal: '', estado: 'activo', scrumMasterEmail: 'sm@test.com', totalMiembros: 3,
    createdAt: '2026-07-01T00:00:00Z'
  };

  const overview: ProjectOverview = {
    proyectoId: 'proj-1', proyectoNombre: 'Proyecto Xabi',
    totalSprints: 3, sprintsFinalizados: 3, sprintActualNumero: null,
    promedioHistorico: {}, mejorSprint: null, peorSprint: null, datosDisponibles: true
  };

  function metrica(overrides: Partial<MetricaEvaluacionDetalleDto>): MetricaEvaluacionDetalleDto {
    return {
      variableId: 'v1', variableNombre: 'variable_x', metricaNombre: 'Métrica X',
      categoria: 'Categoria', tipoAlcance: 'grupal', frecuenciaCaptura: 'por_sprint',
      formulaTexto: null, registros: [], porSprint: [],
      estadisticas: {
        totalRegistros: 3, promedio: 0, minimo: 0, maximo: 0, primerValor: 0, ultimoValor: 0,
        cambio: 0, cambioPct: null, tendencia: null, pendiente: null,
        desviacionEstandar: null, coeficienteVariacion: null, variabilidad: null
      },
      ...overrides
    };
  }

  // ══════════════════════════════════════════════════════════════════════
  // Principio central: NO mezclar métricas de unidades/escalas heterogéneas
  // en un único score. Caso real de la auditoría: Velocidad en Story Points
  // (20) y Satisfacción en % (85) — un promedio ingenuo daría 52.5, un
  // número sin ningún respaldo matemático (ver AgileAnalyticsService /
  // EvaluacionService, corrección de auditoría Dashboard/Evaluación).
  // ══════════════════════════════════════════════════════════════════════
  it('con métricas de unidades diferentes (Story Points y %), NO genera ningún score global combinado', () => {
    const velocidad = metrica({
      metricaNombre: 'Velocidad del equipo', categoria: 'Productividad',
      porSprint: [{ sprintId: 's1', sprintNumero: 1, totalRegistros: 1, promedio: 20, minimo: 20, maximo: 20 }],
      estadisticas: {
        totalRegistros: 3, promedio: 20, minimo: 18, maximo: 22, primerValor: 18, ultimoValor: 22,
        cambio: 4, cambioPct: 22.2, tendencia: 'ascendente', pendiente: 2,
        desviacionEstandar: 1.5, coeficienteVariacion: 7.5, variabilidad: 'baja'
      }
    });
    const satisfaccion = metrica({
      metricaNombre: 'Satisfacción del cliente', categoria: 'Significado',
      porSprint: [{ sprintId: 's1', sprintNumero: 1, totalRegistros: 1, promedio: 85, minimo: 85, maximo: 85 }],
      estadisticas: {
        totalRegistros: 3, promedio: 85, minimo: 80, maximo: 90, primerValor: 80, ultimoValor: 90,
        cambio: 10, cambioPct: 12.5, tendencia: 'ascendente', pendiente: 5,
        desviacionEstandar: 4, coeficienteVariacion: 4.7, variabilidad: 'baja'
      }
    });

    const datos: DatosReporteGeneral = { proyecto, overview, metricas: [velocidad, satisfaccion], insights: [] };
    const secciones = construirSeccionesReporteGeneral(datos);

    // El "promedio ingenuo" (20+85)/2 = 52.5 NUNCA debe aparecer en ningún
    // texto del reporte, sin importar en qué sección se busque.
    const todoElTexto = secciones.flatMap(s => [s.titulo, ...(s.parrafos ?? []), ...(s.bullets ?? [])]).join(' | ');
    expect(todoElTexto).not.toContain('52.5');
    expect(todoElTexto).not.toMatch(/score general|promedio general|promedio combinado/i);

    // Cada métrica aparece en su propia sección, con sus propios valores,
    // sin combinarse con la otra.
    const seccionVelocidad = secciones.find(s => s.titulo === 'Velocidad del equipo');
    const seccionSatisfaccion = secciones.find(s => s.titulo === 'Satisfacción del cliente');
    expect(seccionVelocidad).toBeTruthy();
    expect(seccionSatisfaccion).toBeTruthy();
    expect(seccionVelocidad!.parrafos!.join(' ')).toContain('Promedio histórico: 20');
    expect(seccionSatisfaccion!.parrafos!.join(' ')).toContain('Promedio histórico: 85');
  });

  it('proyecto con varios sprints y varias métricas de la MISMA categoría: cada una se presenta por separado igualmente', () => {
    const m1 = metrica({ metricaNombre: 'Defectos', categoria: 'Calidad' });
    const m2 = metrica({ metricaNombre: 'Retrabajo', categoria: 'Calidad' });
    const datos: DatosReporteGeneral = {
      proyecto,
      overview: { ...overview, totalSprints: 5, sprintsFinalizados: 4 },
      metricas: [m1, m2],
      insights: []
    };

    const secciones = construirSeccionesReporteGeneral(datos);

    expect(secciones.some(s => s.titulo === 'Defectos')).toBeTrue();
    expect(secciones.some(s => s.titulo === 'Retrabajo')).toBeTrue();
    const resumen = secciones.find(s => s.titulo === 'Resumen del proyecto');
    expect(resumen!.parrafos!.join(' ')).toContain('Total de sprints: 5');
    expect(resumen!.parrafos!.join(' ')).toContain('Sprints finalizados: 4');
  });

  it('datos faltantes (sin métricas, sin insights): no lanza excepción y lo indica explícitamente en vez de inventar contenido', () => {
    const datos: DatosReporteGeneral = { proyecto, overview, metricas: [], insights: [] };

    expect(() => construirSeccionesReporteGeneral(datos)).not.toThrow();
    const secciones = construirSeccionesReporteGeneral(datos);

    expect(secciones.some(s => s.titulo === 'Sin métricas registradas')).toBeTrue();
    expect(secciones.some(s => s.titulo === 'Sin insights activos')).toBeTrue();
  });

  it('retrospectivas: indica explícitamente que PRODOX no las almacena, sin inventar un historial', () => {
    const datos: DatosReporteGeneral = { proyecto, overview, metricas: [], insights: [] };
    const secciones = construirSeccionesReporteGeneral(datos);

    const seccionRetro = secciones.find(s => s.titulo === 'Retrospectivas');
    expect(seccionRetro).toBeTruthy();
    expect(seccionRetro!.parrafos!.join(' ')).toContain('no las almacena');
  });

  it('variabilidad sin fundamento (registros insuficientes, variabilidad=null): lo dice explícitamente en vez de fabricar una clasificación', () => {
    const m = metrica({
      metricaNombre: 'Métrica nueva',
      estadisticas: {
        totalRegistros: 2, promedio: 10, minimo: 8, maximo: 12, primerValor: 8, ultimoValor: 12,
        cambio: 4, cambioPct: 50, tendencia: 'ascendente', pendiente: 2,
        desviacionEstandar: null, coeficienteVariacion: null, variabilidad: null
      }
    });
    const datos: DatosReporteGeneral = { proyecto, overview, metricas: [m], insights: [] };
    const secciones = construirSeccionesReporteGeneral(datos);

    const seccionMetrica = secciones.find(s => s.titulo === 'Métrica nueva');
    expect(seccionMetrica!.parrafos!.join(' ')).toContain('no disponible (registros insuficientes)');
  });

  it('AI Insights: incluye tipo, severidad, evidencia (categoría) y recomendación tal cual los trae el insight', () => {
    const insight: AIInsight = {
      id: 'i1', proyectoId: 'proj-1', sprintId: null, type: 'TREND', severity: 'HIGH',
      title: 'Calidad en descenso', description: 'La calidad bajó 20% en 3 sprints',
      evidence: [{
        categoria: 'Calidad', valorActual: 60, valorAnterior: 75, promedioHistorico: 70,
        desviacionEstandar: 5, variacionPorcentual: -20, tendencia: 'DOWN', numeroSprints: 3, metadata: {}
      }],
      recommendation: 'Revisar el proceso de QA', confidence: 'HIGH',
      dismissed: false, createdAt: '2026-08-01T00:00:00Z', dismissedAt: null
    };
    const datos: DatosReporteGeneral = { proyecto, overview, metricas: [], insights: [insight] };
    const secciones = construirSeccionesReporteGeneral(datos);

    const seccionInsight = secciones.find(s => s.titulo === 'Calidad en descenso');
    expect(seccionInsight).toBeTruthy();
    const texto = seccionInsight!.parrafos!.join(' ');
    expect(texto).toContain('Tipo: TREND');
    expect(texto).toContain('Severidad: HIGH');
    expect(texto).toContain('La calidad bajó 20% en 3 sprints');
    expect(texto).toContain('Revisar el proceso de QA');
  });
});
