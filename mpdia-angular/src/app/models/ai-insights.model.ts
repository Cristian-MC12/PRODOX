// Autor: Cristian Santiago Martinez Cordoba — MPDIA

/**
 * Evidencia estructurada que respalda un insight de IA.
 * Contiene datos cuantitativos de las métricas analizadas.
 */
export interface InsightEvidence {
  categoria: string;
  valorActual: number | null;
  valorAnterior: number | null;
  promedioHistorico: number | null;
  desviacionEstandar: number | null;
  variacionPorcentual: number | null;
  tendencia: string | null;
  numeroSprints: number | null;
  metadata: Record<string, any>;
}

/**
 * Insight generado automáticamente por análisis de métricas Agile.
 * Combina análisis determinístico con interpretación de IA.
 */
export interface AIInsight {
  id: string;
  proyectoId: string;
  sprintId: string | null;
  type: string; // TREND | ANOMALY | RISK | COMPARISON
  severity: string; // LOW | MEDIUM | HIGH | CRITICAL
  title: string;
  description: string;
  evidence: InsightEvidence[];
  recommendation: string | null;
  confidence: string; // LOW | MEDIUM | HIGH
  dismissed: boolean;
  createdAt: string;
  dismissedAt: string | null;
}
