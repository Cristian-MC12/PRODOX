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
  // Propuesta elegida de las 3 que da GenAI
  propuestaElegida?: number;   // 0, 1 o 2
}

export interface PropuestaGenAI {
  titulo: string;
  objetivo: string;
  procedimiento: string;
  indicadorVariable: string;
  escala: string;
  justificacion: string;
}
