// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { TimeboxUnidad } from './timebox.model';

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
  /** Rol POR PROYECTO del usuario autenticado que pidió este objeto (V39):
   *  'scrum_master' | 'product_owner' | 'scrum_member'. Nunca el rol global
   *  de la cuenta — es la fuente confiable para decidir qué mostrar en la UI
   *  sin comparar por email ni asumir que "si no es Scrum Master, es Scrum
   *  Member" (ya existe un tercer rol). */
  miRol?:           string;
  /** V41 — timebox real de la iteración; timeBoxSemanas se conserva sin
   *  cambios como campo legado (ver timebox.model.ts). Opcionales: un
   *  ProyectoDto cacheado antes de V41 no los trae. */
  timeboxUnidad?:   TimeboxUnidad;
  timeboxDuracion?: number;
  /** Solo presente cuando timeboxUnidad === 'HORAS'. Formato 'HH:mm'. */
  horaInicio?:      string | null;
}

export interface CrearProyectoRequest {
  nombre:          string;
  descripcion:     string;
  metodo:          'scrum' | 'xp';
  /** V41 — timebox de la iteración. */
  timeboxUnidad:   TimeboxUnidad;
  timeboxDuracion: number;
  /** Requerida solo cuando timeboxUnidad === 'HORAS'. Formato 'HH:mm'. */
  horaInicio?:     string | null;
  numeroSprints:   number;
  fechaInicio:     string; // ISO date YYYY-MM-DD
  productGoal:     string;
}
