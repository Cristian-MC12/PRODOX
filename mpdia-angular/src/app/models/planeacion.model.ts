// Autor: Cristian Santiago Martinez Cordoba — MPDIA

export interface ProyectoMetricaDto {
  metricaId:     string;
  codigo:        string;
  nombre:        string;
  descripcion:   string | null;
  categoria:     string;
  seleccionada:  boolean;
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
