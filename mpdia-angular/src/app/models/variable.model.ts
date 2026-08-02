// Autor: Cristian Santiago Martinez Cordoba — MPDIA

export type TipoIndicador = 'calidad' | 'productividad' | 'cumplimiento' | 'flexibilidad' | 'sociohumano';
export type TipoAlcance   = 'grupal' | 'individual';
export type Frecuencia    = 'diaria' | 'semanal' | 'por_sprint';
export type Cardinalidad  = 'unico' | 'multiple';
export type TipoDato      = 'numerico' | 'texto' | 'booleano' | 'escala';
export type FrecuenciaCaptura = 'por_sprint' | 'semanal' | 'diaria' | 'ilimitada';

/** Operando de una fórmula: variable que el usuario debe ingresar */
export interface OperandoFormula {
  clave:       string;   // nombre interno, ej: "Critico"
  etiqueta:    string;   // texto para mostrar al usuario, ej: "Errores Críticos"
  tipo:        'numerico' | 'escala' | 'booleano';
  pesoFactor?: number;   // peso en la fórmula, ej: 5 para Crítico en ISE
  min?:        number;
  max?:        number;
}

/** Definición estructurada de la fórmula para una variable */
export interface FormulaDefinicion {
  expresion:      string;           // ej: "ISE = Crítico×C + Mayor×M + Medio×Med + Menor×Men"
  operandos:      OperandoFormula[];
  escalaResultado: string;          // ej: "Numérico >= 0"
}

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
  // Campos de fórmula (opcionales — se configuran en Ejecución)
  formulaTexto:     string | null;
  formulaJson:      string | null;
  frecuenciaCaptura: FrecuenciaCaptura;
  // Información de parametrización (UX guidance)
  objetivo:         string | null;
  procedimiento:    string | null;
  escalaDefinicion: string | null;
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

export interface ActualizarFormulaRequest {
  formulaTexto:      string;
  formulaJson:       string;
  frecuenciaCaptura: FrecuenciaCaptura;
}
