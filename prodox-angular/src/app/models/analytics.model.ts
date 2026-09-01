// Autor: Cristian Santiago Martinez Cordoba — PRODOX

/**
 * Resumen general de un proyecto con todas sus métricas agregadas.
 */
export interface ProjectOverview {
  proyectoId: string;
  proyectoNombre: string;
  totalSprints: number;
  sprintsFinalizados: number;
  sprintActualNumero: number | null;
  promedioHistorico: Record<string, number>;
  mejorSprint: SprintPerformance | null;
  peorSprint: SprintPerformance | null;
  datosDisponibles: boolean;
}

export interface SprintPerformance {
  numero: number;
  scoreGeneral: number;
  razon: string;
}

/**
 * Riesgo detectado automáticamente por análisis de métricas.
 */
export interface Risk {
  proyectoId: string;
  tipo: string; // DECLINING_METRIC | HIGH_VARIABILITY
  severidad: string; // LOW | MEDIUM | HIGH | CRITICAL
  titulo: string;
  evidencia: string;
  categoriaAfectada: string;
  detectedAt: string;
}

/**
 * Tendencia de métricas a lo largo de los sprints.
 */
export interface TrendAnalysis {
  proyectoId: string;
  categoria: string;
  numeroSprints: number;
  dataPoints: SprintDataPoint[];
  promedioGeneral: number;
  desviacionEstandar: number;
  tendenciaGeneral: string; // UP | DOWN | STABLE
  variacionTotal: number;
  datosDisponibles: boolean;
}

export interface SprintDataPoint {
  sprintNumero: number;
  valor: number;
  fecha: string | null;
}
