// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Pipe, PipeTransform } from '@angular/core';

/**
 * Preámbulos conversacionales conocidos y confirmados como artefacto del
 * formato de respuesta de Gemini, NO como contenido semántico del insight.
 *
 * Confirmado en el backend (ver AIInsightsService.java, FASE 22/23): antes
 * de que el parseo por marcador (TÍTULO:/DESCRIPCIÓN:/RECOMENDACIÓN:) se
 * volviera independiente de la posición, un preámbulo como este quedaba
 * persistido como si fuera el título/descripción real. El backend ya lo
 * corrigió para las generaciones nuevas; esta lista cubre además cualquier
 * insight histórico que haya quedado persistido con el preámbulo crudo.
 *
 * Solo se recorta si aparece literalmente al inicio del texto (anclado con
 * ^), nunca en medio de una oración — así no se arriesga a cortar contenido
 * semántico real que simplemente mencione palabras similares.
 */
const PREAMBULOS_CONOCIDOS: RegExp[] = [
  /^\s*aqu[ií]\s+tienes\b[^:.\n—-]*[:.\-—]\s*/i,
  /^\s*a\s+continuaci[oó]n\b[^:.\n—-]*[:.\-—]\s*/i,
];

/**
 * Limpia artefactos de formato Markdown crudo (negrita, viñetas, separadores
 * tipo regla horizontal) y preámbulos conversacionales conocidos del texto
 * de insights generados por IA — puramente de presentación, determinista,
 * sin innerHTML. NO reescribe, resume ni traduce el contenido: solo quita
 * los marcadores de formato que Gemini a veces antepone o intercala.
 *
 * No cubre itálica de un solo asterisco (`*texto*`) a propósito: un asterisco
 * huérfano sin pareja cercana podría abarcar accidentalmente varias oraciones
 * reales y corromper contenido semántico — riesgo mayor que el beneficio,
 * dado que los prompts de generación no piden ese formato.
 */
@Pipe({ name: 'limpiarMarkdownIA', standalone: true })
export class LimpiarMarkdownIAPipe implements PipeTransform {
  transform(texto: string | null | undefined): string {
    if (!texto) return '';

    let limpio = texto;

    for (const preambulo of PREAMBULOS_CONOCIDOS) {
      limpio = limpio.replace(preambulo, '');
    }

    limpio = limpio
      // separadores tipo regla horizontal: ---, ***, ___ (3 o más repeticiones)
      .replace(/[-*_]{3,}/g, ' ')
      // negrita: **texto** -> texto
      .replace(/\*\*(.+?)\*\*/g, '$1')
      // viñetas de lista al inicio de línea: "* texto" o "- texto" -> "texto"
      .replace(/^[ \t]*[*-][ \t]+/gm, '')
      // asteriscos huérfanos sueltos que puedan quedar tras las limpiezas anteriores
      .replace(/(^|\s)\*{1,2}(?=\s|$)/g, '$1')
      // colapsar espacios y saltos de línea resultantes de las remociones
      .replace(/[ \t]{2,}/g, ' ')
      .replace(/\n{3,}/g, '\n\n')
      .trim();

    return limpio;
  }
}
