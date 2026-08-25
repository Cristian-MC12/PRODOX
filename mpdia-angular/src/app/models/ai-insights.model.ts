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

/**
 * Resultado de una generación de AI Insights (FASE 23).
 *
 * Antes el endpoint devolvía solo AIInsight[], sin forma de distinguir "no
 * había nada que reportar" de "algunos detectores fallaron" de "generación
 * completa" — ver GenerateInsightsResultDto (backend) para el detalle de
 * cada valor de `status`.
 */
export interface GenerateInsightsResult {
  insights: AIInsight[];
  status: 'COMPLETE' | 'PARTIAL' | 'FAILED' | 'SIN_SENALES' | 'SIN_DATOS';
  senalesDetectadas: number;
  senalesNuevas: number;
  senalesOmitidasPorDuplicado: number;
  errores: string[];
}
