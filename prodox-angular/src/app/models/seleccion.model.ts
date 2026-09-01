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

/**
 * Escala estructurada — corrección del manejo de escalas (antes solo existía
 * `escala` como texto libre, ej. "Numérica, entera (0 o más)", que Ejecución
 * no podía usar para validar de verdad). Estos campos son la fuente de verdad
 * funcional; `escala` se conserva como resumen legible auto-generado.
 */
export type EscalaTipo = 'NUMERICA_ENTERA' | 'NUMERICA_DECIMAL';

export interface EscalaEstructurada {
  escalaTipo?: EscalaTipo | null;
  escalaMin?: number | null;
  /** Ignorado (debe quedar null/undefined) cuando escalaSinLimite es true. */
  escalaMax?: number | null;
  escalaPaso?: number | null;
  escalaSinLimite?: boolean | null;
  /** Significado de los valores, ej: "0 = Muy malo; 10 = Excelente". */
  escalaDescripcion?: string | null;
}

export interface Parametrizacion extends EscalaEstructurada {
  objetivo: string;
  procedimiento: string;       // fórmula o procedimiento de medición
  indicadorVariable: string;   // indicador y variables involucradas
  escala: string;              // resumen legible de la escala (auto-generado desde los campos estructurados)
  frecuenciaCaptura?: string;  // por_sprint | semanal | diaria | ilimitada
  /**
   * Revisión de captura por parametrización: alcance/responsable de captura,
   * elegido explícitamente por el Scrum Master — independiente de
   * tipoOperacion (uno decide QUIÉN captura, el otro CÓMO se calcula).
   * 'EQUIPO' = cada integrante registra su propio valor; 'SCRUM_MASTER' =
   * solo el Scrum Master registra. El backend es la autoridad real; este
   * campo solo viaja hasta la parametrización aprobada.
   */
  responsableCaptura?: 'EQUIPO' | 'SCRUM_MASTER';
  // Campos académicos
  fuenteAcademica?: string;    // Fuente académica de referencia
  formulaAcademica?: string;   // Fórmula matemática formal
  tipoOperacion?: string;      // SUMA | PROMEDIO | PORCENTAJE | etc.
  unidadResultado?: string;    // Unidad del resultado
  nombreVariable?: string;     // Identificador técnico snake_case (Fase 16.10-E)
  // Propuesta elegida de las 3 que da GenAI
  propuestaElegida?: number;   // 0, 1 o 2
}

export interface PropuestaGenAI extends EscalaEstructurada {
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
