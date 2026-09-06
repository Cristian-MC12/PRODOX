// Autor: Cristian Santiago Martinez Cordoba — PRODOX

/**
 * Modelo intermedio, independiente de la librería 'docx', para el contenido
 * de un reporte exportable a Word (Retrospectiva, Reporte General del
 * proyecto). Separar "qué dice el reporte" (este modelo, texto plano) de
 * "cómo se renderiza en .docx" (ver word-report.util.ts) permite testear el
 * CONTENIDO del reporte —lo que realmente importa para evitar mezclar
 * escalas heterogéneas o inventar datos— sin depender de la librería docx
 * ni de un blob binario.
 */
export interface SeccionReporte {
  titulo: string;
  nivel: 1 | 2 | 3;
  parrafos?: string[];
  /** Lista de viñetas. Si está definida y vacía, se muestra "No hay datos registrados." */
  bullets?: string[];
}
