// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { SeccionReporte } from './reporte-word.model';
import { AIRetrospective } from '../models/ai-reports.model';

/**
 * Construye el CONTENIDO (texto plano, ver reporte-word.model.ts) del
 * reporte exportable de una retrospectiva de sprint — únicamente con los
 * datos que AIRetrospectiveDto realmente trae (whatWentWell/whatCouldImprove/
 * risks/recommendations/questionsForTeam). No agrega ningún dato que PRODOX
 * no almacene (PRODOX no persiste retrospectivas — se generan bajo demanda,
 * ver AIRetrospectiveService).
 */
export function construirSeccionesRetrospectiva(
  r: AIRetrospective,
  proyectoNombre: string
): SeccionReporte[] {
  const datosSprint = [`Proyecto: ${proyectoNombre}`, `Sprint: ${r.sprintNumero}${r.sprintGoal ? ' — ' + r.sprintGoal : ''}`];
  datosSprint.push(r.fechaInicio && r.fechaFin ? `Periodo: ${r.fechaInicio} a ${r.fechaFin}` : 'Periodo: no registrado');
  datosSprint.push(`Fecha de exportación: ${new Date().toLocaleDateString('es-AR')}`);

  return [
    { titulo: 'Datos del sprint', nivel: 2, parrafos: datosSprint },
    { titulo: 'Qué funcionó bien', nivel: 2, bullets: r.whatWentWell ?? [] },
    { titulo: 'Qué se puede mejorar', nivel: 2, bullets: r.whatCouldImprove ?? [] },
    { titulo: 'Riesgos identificados', nivel: 2, bullets: r.risks ?? [] },
    { titulo: 'Recomendaciones', nivel: 2, bullets: r.recommendations ?? [] },
    { titulo: 'Preguntas para el equipo', nivel: 2, bullets: r.questionsForTeam ?? [] }
  ];
}
