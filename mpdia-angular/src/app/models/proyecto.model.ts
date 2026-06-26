// Autor: Cristian Santiago Martinez Cordoba — MPDIA

export interface ProyectoDto {
  id:               string;
  nombre:           string;
  descripcion:      string | null;
  metodo:           'scrum' | 'xp';
  timeBoxSemanas:   number;
  numeroSprints:    number;
  fechaInicio:      string | null;
  productGoal:      string;
  sprintGoal:       string;
  estado:           'activo' | 'finalizado';
  scrumMasterEmail: string;
  totalMiembros:    number;
  createdAt:        string;
}

export interface CrearProyectoRequest {
  nombre:         string;
  descripcion:    string;
  metodo:         'scrum' | 'xp';
  timeBoxSemanas: number;
  numeroSprints:  number;
  fechaInicio:    string; // ISO date YYYY-MM-DD
  productGoal:    string;
}
