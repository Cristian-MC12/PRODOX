// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { SeccionReporte } from './reporte-word.model';
import { AISprintReport } from '../models/ai-reports.model';
import { LimpiarMarkdownIAPipe } from './limpiar-markdown-ia.pipe';

const limpiador = new LimpiarMarkdownIAPipe();

/**
 * Construye el CONTENIDO (texto plano, ver reporte-word.model.ts) del
 * reporte exportable del Reporte Ejecutivo de Sprint (AI Report) —
 * únicamente con los datos que AISprintReportDto realmente trae y que la
 * pantalla (ai-report.component.html) ya muestra: resumen ejecutivo,
 * métricas, highlights, concerns, insights relacionados y recomendaciones.
 * No agrega ningún dato nuevo ni hace ninguna llamada adicional a IA — el
 * reporte ya fue generado antes de exportar.
 *
 * Las secciones opcionales (métricas/highlights/concerns/insights) solo se
 * incluyen si tienen contenido, igual que en la pantalla (@if de
 * ai-report.component.html) — así el documento nunca inventa una sección
 * vacía como si hubiera datos.
 *
 * Se aplica la misma limpieza de Markdown (LimpiarMarkdownIAPipe) que ya usa
 * la pantalla, para que el texto generado por Gemini no llegue al .docx con
 * marcadores crudos (**, ---, etc.).
 */
export function construirSeccionesReporteEjecutivo(
  report: AISprintReport,
  proyectoNombre: string
): SeccionReporte[] {
  const secciones: SeccionReporte[] = [];

  secciones.push({
    titulo: 'Datos del sprint',
    nivel: 2,
    parrafos: [
      `Proyecto: ${proyectoNombre}`,
      `Sprint: ${report.sprintNumero}${report.sprintGoal ? ' — ' + report.sprintGoal : ''}`,
      report.fechaInicio && report.fechaFin ? `Periodo: ${report.fechaInicio} a ${report.fechaFin}` : 'Periodo: no registrado',
      `Fecha de exportación: ${new Date().toLocaleDateString('es-AR')}`
    ]
  });

  secciones.push({
    titulo: 'Resumen Ejecutivo',
    nivel: 2,
    parrafos: [limpiador.transform(report.resumenEjecutivo)]
  });

  const metricasEntries = Object.entries(report.metricas ?? {});
  if (metricasEntries.length > 0) {
    secciones.push({
      titulo: 'Métricas del Sprint',
      nivel: 2,
      bullets: metricasEntries.map(([categoria, valor]) => `${categoria}: ${valor}`)
    });
  }

  if (report.highlights && report.highlights.length > 0) {
    secciones.push({
      titulo: 'Aspectos Destacados',
      nivel: 2,
      bullets: report.highlights.map(h => limpiador.transform(h))
    });
  }

  if (report.concerns && report.concerns.length > 0) {
    secciones.push({
      titulo: 'Puntos de Atención',
      nivel: 2,
      bullets: report.concerns.map(c => limpiador.transform(c))
    });
  }

  if (report.insights && report.insights.length > 0) {
    secciones.push({
      titulo: 'Insights Relacionados',
      nivel: 2,
      bullets: report.insights.map(i =>
        `[${i.severity}] ${limpiador.transform(i.title)}: ${limpiador.transform(i.description)}`
      )
    });
  }

  secciones.push({
    titulo: 'Recomendaciones',
    nivel: 2,
    parrafos: [limpiador.transform(report.recomendaciones)]
  });

  return secciones;
}
