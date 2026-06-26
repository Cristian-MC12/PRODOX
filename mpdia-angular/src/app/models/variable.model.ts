// Autor: Cristian Santiago Martinez Cordoba — MPDIA

export type TipoIndicador = 'calidad' | 'productividad' | 'cumplimiento' | 'flexibilidad' | 'sociohumano';
export type TipoAlcance   = 'grupal' | 'individual';
export type Frecuencia    = 'diaria' | 'semanal' | 'por_sprint';
export type Cardinalidad  = 'unico' | 'multiple';
export type TipoDato      = 'numerico' | 'texto' | 'booleano' | 'escala';

export interface VariableDto {
  id:               string;
  proyectoId:       string;
  metricaId:        string;
  metricaNombre:    string;
  metricaCategoria: string;
  nombre:           string;
  descripcion:      string | null;
  tipoIndicador:    TipoIndicador;
  tipoAlcance:      TipoAlcance;
  frecuencia:       Frecuencia;
  cardinalidad:     Cardinalidad;
  tipoDato:         TipoDato;
  escalaMin:        number | null;
  escalaMax:        number | null;
  activa:           boolean;
  createdAt:        string;
}

export interface CrearVariableRequest {
  metricaId:      string;
  nombre:         string;
  descripcion?:   string;
  tipoIndicador:  TipoIndicador;
  tipoAlcance:    TipoAlcance;
  frecuencia:     Frecuencia;
  cardinalidad:   Cardinalidad;
  tipoDato:       TipoDato;
  escalaMin?:     number;
  escalaMax?:     number;
}
