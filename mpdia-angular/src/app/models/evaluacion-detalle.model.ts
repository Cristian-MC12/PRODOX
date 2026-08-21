// Autor: Cristian Santiago Martinez Cordoba — MPDIA

export interface RegistroPuntoDto {
  id:           string;
  valor:        number;
  registradoAt: string;
  sprintId:     string;
  sprintNumero: number | null;
  userId:       string;
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
  categoria:         string;
  tipoAlcance:       string;
  frecuenciaCaptura: string;
  formulaTexto:      string | null;
  registros:         RegistroPuntoDto[];
  estadisticas:      VariableEstadisticasDto;
  porSprint:         SprintStatsDto[];
}
