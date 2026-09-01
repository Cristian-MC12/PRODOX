// Autor: Cristian Santiago Martinez Cordoba — PRODOX

export interface ProyectoMetricaDto {
  metricaId:     string;
  codigo:        string;
  nombre:        string;
  descripcion:   string | null;
  categoria:     string;
  factor:        string | null;
  seleccionada:  boolean;
  seleccionadaAt: string | null;
  aprobada:      boolean;
  aprobadaPor:   string | null;
  aprobadaAt:    string | null;
  tieneVariable: boolean;
}

export interface EvaluacionSprintDto {
  sprintId:       string;
  sprintNumero:   number;
  variableId:     string;
  variableNombre: string;
  categoria:      string;
  tipoAlcance:    string;
  promedio:       number;
  min:            number;
  max:            number;
  totalRegistros: number;
}
