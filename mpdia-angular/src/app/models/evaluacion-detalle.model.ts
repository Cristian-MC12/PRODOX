// Autor: Cristian Santiago Martinez Cordoba — MPDIA

export interface RegistroPuntoDto {
  id:           string;
  valor:        number;
  registradoAt: string;
  sprintId:     string;
  sprintNumero: number | null;
  userId:       string;
}

/**
 * Revisión de Evaluación: un punto de la serie de resultados YA CALCULADOS del
 * equipo (ResultadoMetrica vigente) por sprint — no un registro individual
 * crudo. Solo se puebla para frecuenciaCaptura='por_sprint' (ver
 * MetricaEvaluacionDetalleDto.resultadosCalculados / EvaluacionService).
 */
export interface ResultadoCalculadoPuntoDto {
  resultadoId:  string;
  resultado:    number;
  sprintId:     string;
  sprintNumero: number | null;
  calculadoAt:  string;
}

export interface SprintStatsDto {
  sprintId:       string;
  sprintNumero:   number;
  totalRegistros: number;
  promedio:       number;
  minimo:         number;
  maximo:         number;
}

export type Tendencia = 'ascendente' | 'descendente' | 'estable' | null;
export type Variabilidad = 'baja' | 'media' | 'alta' | null;

export interface VariableEstadisticasDto {
  totalRegistros:        number;
  promedio:               number;
  minimo:                 number;
  maximo:                 number;
  primerValor:            number;
  ultimoValor:            number;
  cambio:                 number;
  cambioPct:              number | null;
  tendencia:               Tendencia;
  pendiente:              number | null;
  desviacionEstandar:     number | null;
  coeficienteVariacion:   number | null;
  variabilidad:           Variabilidad;
}

export interface MetricaEvaluacionDetalleDto {
  variableId:        string;
  variableNombre:    string;
  /** Variable.descripcion tal cual la persiste el backend (puede venir null/ausente en datos
   *  antiguos). El fallback a variableNombre cuando no hay descripción amigable se resuelve
   *  en la presentación (componente), nunca mutando este dato. */
  variableDescripcion?: string | null;
  categoria:         string;
  tipoAlcance:       string;
  frecuenciaCaptura: string;
  formulaTexto:      string | null;
  registros:         RegistroPuntoDto[];
  estadisticas:      VariableEstadisticasDto;
  porSprint:         SprintStatsDto[];
  /** Resultado calculado del equipo por sprint (preferido sobre 'registros' cuando
   *  no está vacío — ver evaluacion.component.ts:registrosParaVista). Opcional:
   *  el backend siempre lo envía, pero se mantiene opcional en el modelo para no
   *  romper fixtures de tests existentes construidos antes de este campo. */
  resultadosCalculados?: ResultadoCalculadoPuntoDto[];
}
