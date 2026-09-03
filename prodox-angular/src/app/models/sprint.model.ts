// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { TimeboxUnidad } from './timebox.model';

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
  /** V41 — timebox real del proyecto dueño de este sprint (copiado de
   *  ProyectoDto, mismo patrón que timeBoxSemanas). Opcionales: un
   *  SprintDto cacheado antes de V41 no los trae. */
  timeboxUnidad?:    TimeboxUnidad;
  timeboxDuracion?:  number;
  /** Representación temporal real (fecha+hora) del inicio/fin de este
   *  sprint — solo no-null cuando el timebox del proyecto está en HORAS. */
  fechaHoraInicio?:  string | null;
  fechaHoraFin?:     string | null;
}
