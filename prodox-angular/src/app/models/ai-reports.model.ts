// Autor: Cristian Santiago Martinez Cordoba — PRODOX

import { AIInsight } from './ai-insights.model';

/**
 * Reporte ejecutivo de sprint generado por IA.
 * Combina datos objetivos (métricas) con interpretación de Gemini.
 */
export interface AISprintReport {
  sprintId: string;
  sprintNumero: number;
  sprintGoal: string;
  fechaInicio: string; // ISO date
  fechaFin: string; // ISO date
  resumenEjecutivo: string; // Generado por Gemini
  metricas: Record<string, number>;
  highlights: string[]; // Generado por Gemini
  concerns: string[]; // Generado por Gemini
  insights: AIInsight[];
  recomendaciones: string; // Generado por Gemini
  generatedAt: string; // ISO datetime
}

/**
 * Retrospectiva inteligente de sprint generada por IA.
 * Combina datos del sprint actual con comparación histórica.
 */
export interface AIRetrospective {
  sprintId: string;
  sprintNumero: number;
  sprintGoal: string;
  fechaInicio: string; // ISO date
  fechaFin: string; // ISO date
  whatWentWell: string[]; // Generado por Gemini basado en datos
  whatCouldImprove: string[]; // Generado por Gemini basado en datos
  risks: string[]; // Del sistema + Gemini
  recommendations: string[]; // Gemini
  questionsForTeam: string[]; // Gemini
  generatedAt: string; // ISO datetime
}
