// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { SeccionReporte } from './reporte-word.model';
import { ProyectoDto } from '../models/proyecto.model';
import { ProjectOverview } from '../models/analytics.model';
import { MetricaEvaluacionDetalleDto } from '../models/evaluacion-detalle.model';
import { AIInsight } from '../models/ai-insights.model';

export interface DatosReporteGeneral {
  proyecto: ProyectoDto;
  overview: ProjectOverview;
  metricas: MetricaEvaluacionDetalleDto[];
  insights: AIInsight[];
}

/**
 * Construye el contenido (texto plano, ver reporte-word.model.ts) del
 * reporte histórico general del proyecto.
 *
 * PRINCIPIO CENTRAL (mismo criterio ya aplicado en la corrección de
 * auditoría de Dashboard/Evaluación — ver AgileAnalyticsService,
 * EvaluacionService): las métricas de distintas categorías pueden
 * representar escalas o unidades heterogéneas (ej. Velocidad en Story
 * Points vs. Satisfacción en %) y NUNCA se combinan en un único score o
 * promedio general. Cada métrica se presenta con sus propios valores,
 * promedio, tendencia y variación, siempre por separado.
 *
 * Solo se incluye información que PRODOX realmente calcula y almacena —
 * no se inventan benchmarks, pesos, normalizaciones ni objetivos no
 * registrados. Cuando un dato no existe (ej. variabilidad sin suficientes
 * registros, retrospectivas — que PRODOX no persiste), se dice
 * explícitamente en vez de omitirlo en silencio o fabricarlo.
 */
export function construirSeccionesReporteGeneral(datos: DatosReporteGeneral): SeccionReporte[] {
  const { proyecto, overview, metricas, insights } = datos;
  const secciones: SeccionReporte[] = [];

  // 1. Identificación
  secciones.push({
    titulo: 'Identificación del proyecto',
    nivel: 1,
    parrafos: [
      `Proyecto: ${proyecto.nombre}`,
      `Metodología: ${proyecto.metodo === 'scrum' ? 'Scrum' : 'XP'}`,
      `Fecha de generación: ${new Date().toLocaleDateString('es-AR')}`
    ]
  });

  // 2. Resumen del proyecto
  const resumen = [
    `Total de sprints: ${overview.totalSprints}`,
    `Sprints finalizados: ${overview.sprintsFinalizados}`
  ];
  if (proyecto.timeBoxSemanas) resumen.push(`Time box: ${proyecto.timeBoxSemanas} semana(s)`);
  if (proyecto.productGoal) resumen.push(`Product Goal: ${proyecto.productGoal}`);
  secciones.push({ titulo: 'Resumen del proyecto', nivel: 1, parrafos: resumen });

  // 3. Evolución de métricas — cada una por separado, nunca combinadas.
  secciones.push({
    titulo: 'Evolución de métricas',
    nivel: 1,
    parrafos: [
      'Cada métrica se presenta de forma individual, en su propia escala. ' +
      'No se calcula ningún promedio ni puntaje que combine métricas de distinta categoría.'
    ]
  });
  if (metricas.length === 0) {
    secciones.push({
      titulo: 'Sin métricas registradas',
      nivel: 2,
      parrafos: ['No hay variables con registros para este proyecto.']
    });
  } else {
    for (const m of metricas) {
      const est = m.estadisticas;
      const parrafos = [
        `Categoría: ${m.categoria}`,
        `Alcance: ${m.tipoAlcance}`,
        `Frecuencia de captura: ${m.frecuenciaCaptura}`
      ];
      if (est) {
        parrafos.push(`Promedio histórico: ${est.promedio}`);
        parrafos.push(`Tendencia: ${est.tendencia ?? 'no disponible (registros insuficientes)'}`);
        parrafos.push(
          `Cambio (primero a último valor): ${est.cambio}` +
          (est.cambioPct !== null ? ` (${est.cambioPct}%)` : '')
        );
        parrafos.push(
          `Variabilidad: ${est.variabilidad ? `${est.variabilidad} (CV ${est.coeficienteVariacion}%)` : 'no disponible (registros insuficientes)'}`
        );
      }
      secciones.push({
        titulo: m.metricaNombre || m.variableNombre,
        nivel: 2,
        parrafos,
        bullets: m.porSprint.map(sp => `Sprint ${sp.sprintNumero}: ${sp.promedio}`)
      });
    }
  }

  // 4. Evaluación — resultados ya calculados por el equipo (ResultadoMetrica), cuando existen.
  secciones.push({
    titulo: 'Evaluación',
    nivel: 1,
    parrafos: ['Resultados ya calculados por el equipo, por sprint, para las variables que los tienen.']
  });
  const conResultados = metricas.filter(m => (m.resultadosCalculados?.length ?? 0) > 0);
  if (conResultados.length === 0) {
    secciones.push({
      titulo: 'Sin resultados calculados',
      nivel: 2,
      parrafos: ['Ninguna variable tiene resultados calculados vigentes todavía.']
    });
  } else {
    for (const m of conResultados) {
      secciones.push({
        titulo: m.metricaNombre || m.variableNombre,
        nivel: 2,
        bullets: (m.resultadosCalculados ?? []).map(r => `Sprint ${r.sprintNumero}: ${r.resultado}`)
      });
    }
  }

  // 5. AI Insights
  secciones.push({ titulo: 'AI Insights', nivel: 1 });
  if (insights.length === 0) {
    secciones.push({
      titulo: 'Sin insights activos',
      nivel: 2,
      parrafos: ['No hay insights activos para este proyecto.']
    });
  } else {
    for (const ins of insights) {
      const parrafos = [
        `Tipo: ${ins.type}`,
        `Severidad: ${ins.severity}`,
        `Observación: ${ins.description}`
      ];
      if (ins.evidence && ins.evidence.length > 0) {
        parrafos.push(`Categoría afectada: ${ins.evidence[0].categoria}`);
      }
      if (ins.recommendation) parrafos.push(`Recomendación: ${ins.recommendation}`);
      secciones.push({ titulo: ins.title, nivel: 2, parrafos });
    }
  }

  // 6. Retrospectivas — PRODOX no las persiste: no se inventa contenido histórico.
  secciones.push({
    titulo: 'Retrospectivas',
    nivel: 1,
    parrafos: [
      'PRODOX genera retrospectivas de sprint bajo demanda y no las almacena. ' +
      'No existe un historial de retrospectivas que incluir en este reporte.'
    ]
  });

  // 7. Conclusiones — solo hechos ya calculados arriba, sin benchmarks ni scores inventados.
  const conclusiones: string[] = [];
  conclusiones.push(`${overview.sprintsFinalizados} de ${overview.totalSprints} sprint(s) finalizado(s).`);
  if (insights.length > 0) {
    const relevantes = insights.filter(i => i.severity === 'CRITICAL' || i.severity === 'HIGH').length;
    conclusiones.push(
      `${insights.length} insight(s) activo(s) detectado(s)` +
      (relevantes > 0 ? `, ${relevantes} de severidad alta o crítica.` : '.')
    );
  } else {
    conclusiones.push('No hay insights activos detectados actualmente.');
  }
  for (const m of metricas) {
    if (m.estadisticas?.tendencia) {
      conclusiones.push(
        `${m.metricaNombre || m.variableNombre} (${m.categoria}): tendencia ${m.estadisticas.tendencia}` +
        (m.estadisticas.cambioPct !== null ? ` (${m.estadisticas.cambioPct}%).` : '.')
      );
    }
  }
  secciones.push({ titulo: 'Conclusiones', nivel: 1, bullets: conclusiones });

  return secciones;
}
