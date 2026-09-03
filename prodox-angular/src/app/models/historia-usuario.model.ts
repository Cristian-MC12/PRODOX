// Autor: Cristian Santiago Martinez Cordoba — PRODOX
export type PrioridadHistoria = 'alta' | 'media' | 'baja';
export type EstadoHistoria = 'pendiente' | 'en_progreso' | 'completada';

export interface HistoriaUsuarioDto {
  id:                  string;
  proyectoId:          string;
  sprintId:            string | null;
  titulo:              string;
  descripcion:         string | null;
  criteriosAceptacion: string | null;
  prioridad:           PrioridadHistoria;
  estado:              EstadoHistoria;
  creadoPor:           string;
  createdAt:           string;
  updatedAt:           string;
}

export interface CrearHistoriaUsuarioRequest {
  titulo:              string;
  descripcion?:        string;
  criteriosAceptacion?: string;
  prioridad?:          PrioridadHistoria;
}

export interface ActualizarHistoriaUsuarioRequest {
  titulo:              string;
  descripcion?:        string;
  criteriosAceptacion?: string;
}
