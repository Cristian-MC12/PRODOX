/**
 * Representa una métrica seleccionada para el sprint,
 * con su factor asociado y estado de parametrización.
 */
export interface MetricaSeleccionada {
  id: string;
  factorId: string;
  factorNombre: string;
  factorCategoria: string;
  metricaNombre: string;
  metricaDescripcion: string;
  proyectoId: string | null;
  // Parametrización (se completa con GenAI o manualmente)
  parametrizacion?: Parametrizacion;
  estadoParametrizacion: 'sin_parametrizar' | 'parcial' | 'completa';
  creadoEn: string;
}

export interface Parametrizacion {
  objetivo: string;
  procedimiento: string;       // fórmula o procedimiento de medición
  indicadorVariable: string;   // indicador y variables involucradas
  escala: string;              // escala de medición
  frecuenciaCaptura?: string;  // por_sprint | semanal | diaria | ilimitada
  // Campos académicos
  fuenteAcademica?: string;    // Fuente académica de referencia
  formulaAcademica?: string;   // Fórmula matemática formal
  tipoOperacion?: string;      // SUMA | PROMEDIO | PORCENTAJE | etc.
  unidadResultado?: string;    // Unidad del resultado
  nombreVariable?: string;     // Identificador técnico snake_case (Fase 16.10-E)
  // Propuesta elegida de las 3 que da GenAI
  propuestaElegida?: number;   // 0, 1 o 2
}

export interface PropuestaGenAI {
  titulo: string;
  objetivo: string;
  procedimiento: string;
  indicadorVariable: string;
  escala: string;
  frecuenciaCaptura?: string;  // Frecuencia recomendada por IA
  fuenteAcademica?: string;    // Fuente académica de referencia
  formulaAcademica?: string;   // Fórmula académica formal
  tipoOperacion?: string;      // Tipo de operación (SUMA, PROMEDIO, etc.)
  unidadResultado?: string;    // Unidad del resultado (problemas, puntos, etc.)
  nombreVariable?: string;     // Identificador técnico snake_case (Fase 16.10-E)
  justificacion: string;
}
