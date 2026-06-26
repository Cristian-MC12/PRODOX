// Autor: Cristian Santiago Martinez Cordoba — MPDIA
export type EstadoSprint = 'pendiente' | 'en_ejecucion' | 'finalizado' | 'reabierto';

export interface SprintDto {
  id:             string;
  proyectoId:     string;
  proyectoNombre: string;
  metodo:         string;
  timeBoxSemanas: number;
  numero:         number;
  sprintGoal:     string;
  estado:         EstadoSprint;
  fechaInicio:    string;
  fechaFin:       string | null;
  cerradoPor:     string | null;
  cerradoAt:      string | null;
  createdAt:      string;
}
