// Autor: Cristian Santiago Martinez Cordoba — PRODOX
// V41 — Timebox de la iteración/Sprint: HORAS | DIAS | SEMANAS.
export type TimeboxUnidad = 'HORAS' | 'DIAS' | 'SEMANAS';

interface ProyectoConTimebox {
  /** Campo legado (siempre presente, incluso en objetos cacheados antes de
   *  V41) — fallback cuando timeboxUnidad/timeboxDuracion todavía no llegan. */
  timeBoxSemanas: number;
  timeboxUnidad?: TimeboxUnidad;
  timeboxDuracion?: number;
}

const ABREVIATURA: Record<TimeboxUnidad, string> = { HORAS: 'h', DIAS: 'días', SEMANAS: 'sem' };
const PALABRA_COMPLETA: Record<TimeboxUnidad, string> = { HORAS: 'hora(s)', DIAS: 'día(s)', SEMANAS: 'semana(s)' };

/**
 * "8 h" | "3 días" | "2 sem". Si el proyecto todavía no trae
 * timeboxUnidad/timeboxDuracion (caché previa a V41, o un backend que aún
 * no fue reiniciado con este cambio), cae al formato histórico basado en
 * timeBoxSemanas — igual criterio que ya se usa para ProyectoDto.miRol.
 */
export function timeboxAbreviado(p: ProyectoConTimebox): string {
  if (p.timeboxUnidad && p.timeboxDuracion != null) {
    return `${p.timeboxDuracion} ${ABREVIATURA[p.timeboxUnidad]}`;
  }
  return `${p.timeBoxSemanas} sem`;
}

/** "8 hora(s)" | "3 día(s)" | "2 semana(s)". Mismo fallback que arriba. */
export function timeboxPalabraCompleta(p: ProyectoConTimebox): string {
  if (p.timeboxUnidad && p.timeboxDuracion != null) {
    return `${p.timeboxDuracion} ${PALABRA_COMPLETA[p.timeboxUnidad]}`;
  }
  return `${p.timeBoxSemanas} semana(s)`;
}
