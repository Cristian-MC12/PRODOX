// Autor: Cristian Santiago Martinez Cordoba — MPDIA
// Fase 16.9.2: Modelos para métricas académicas

export interface MetricaAcademicaRequest {
  proyectoId: string;
  metricaId: string;
  codigoMetrica?: string;
  nombreMetrica?: string;
  definicion?: string;
  fuenteAcademica: string;
  formulaAcademica: string;
  tipoOperacion: string;
  unidadResultado: string;
  frecuencia: string;
}

export interface PropuestaParametrizacionAcademica {
  titulo: string;
  objetivo: string;
  procedimiento: string;
  indicadorVariable: string;
  escala: string;
  justificacion: string;
}

export interface GuardarPropuestaAcademicaRequest {
  proyectoId: string;
  metricaId: string;
  fuenteAcademica: string;
  formulaAcademica: string;
  tipoOperacion: string;
  unidadResultado: string;
  objetivo: string;
  procedimiento: string;
  indicadorVariable: string;
  escala: string;
  frecuenciaCaptura: string;
}

export interface ParametrizacionAcademicaDto {
  id: string;
  status: 'propuesta' | 'aprobada';
  version: number;
  metricaId: string;
  proyectoId: string;
  fuenteAcademica: string;
  formulaAcademica: string;
  tipoOperacion: string;
  unidadResultado: string;
  objetivo: string;
  procedimiento: string;
  indicadorVariable: string;
  escala: string;
  frecuenciaCaptura: string;
  createdAt: string;
  approvedAt?: string;
}

export interface EjecutarMetricaAcademicaRequest {
  proyectoId: string;
  sprintId: string;
  valores: { [key: string]: number | string | boolean };
}

export interface ResultadoMetricaDto {
  resultadoId: string;
  metricaId: string;
  metricaNombre: string;
  proyectoId: string;
  sprintId: string;
  parametrizacionId: string;
  parametrizacionVersion: number;
  tipoCalculo: string;
  expresion: string;
  valoresUtilizados: string;
  resultado: number;
  unidad: string;
  estado: 'calculado' | 'error';
  mensajeError?: string;
  calculadoAt: string;
}

export interface InterpretacionIADto {
  resultadoId: string;
  metricaNombre: string;
  resultado: number;
  unidad: string;
  interpretacion: string;
  generadoAt: string;
}

export interface VariableAcademica {
  nombre: string;        // técnico (snake_case) — clave real hacia el backend, NO usar como texto principal
  nombreHumano: string;  // texto legible para el usuario (derivado de nombre, o indicadorVariable cuando aplica)
  etiqueta: string;      // descripción de qué debe capturarse
  tipo: 'INTEGER' | 'DECIMAL' | 'TEXT' | 'BOOLEAN';
  unidad: string;
  requerida: boolean;
}
