// Autor: Cristian Santiago Martinez Cordoba — PRODOX

/**
 * FASE 15 — "Crear métrica con IA". Es SOLO una propuesta: nada se persiste
 * al generarla. Si la IA no pudo determinar razonablemente un campo, su
 * valor es exactamente "No determinado" (nunca un dato inventado).
 */
export interface MetricaIAPropuestaDto {
  nombre: string;
  descripcion: string;
  objetivo: string;
  queMide: string;
  variablesSugeridas: string;
  tipoOperacionSugerido: string;
  formulaSugerida: string;
  unidadResultado: string;
  fuenteSugerida: string;
}

export interface CrearMetricaIARequest {
  proyectoId: string;
  categoriaId: number;
  nombre: string;
  descripcion: string;
  /**
   * FASE 23 — opcionales, nunca se persisten en Metrica: solo se usan como
   * señal adicional para detectar posibles duplicados conceptuales.
   */
  objetivo?: string;
  queMide?: string;
  variablesSugeridas?: string;
  /**
   * true cuando el Scrum Master ya vio el aviso de posible duplicado
   * conceptual y decidió explícitamente crear la métrica como distinta.
   */
  confirmarCreacionDiferente?: boolean;
}

export interface MetricaIACreadaDto {
  metricaId: string;
  codigo: string;
  nombre: string;
  proyectoId: string;
}

/**
 * Metrica ya existente en el catálogo global con el mismo nombre normalizado
 * que la propuesta que se intentó crear. La devuelve el backend (HTTP 409)
 * en vez de crear una fila duplicada, para que el Scrum Master pueda optar
 * por reutilizarla en lugar de crear una nueva.
 */
export interface MetricaExistenteDto {
  id: string;
  codigo: string;
  nombre: string;
  descripcion: string;
  categoria: string;
}

/**
 * FASE 23 — candidato a duplicado CONCEPTUAL (no nombre exacto): el catálogo
 * ya tiene una métrica que probablemente mide el mismo concepto. score y
 * razones existen para que el aviso sea explicable, nunca una caja negra.
 */
export interface PosibleDuplicadoDto {
  metrica: MetricaExistenteDto;
  score: number;
  razones: string[];
}
