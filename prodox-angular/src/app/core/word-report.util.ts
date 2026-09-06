// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { SeccionReporte } from './reporte-word.model';

/**
 * Carga dinámica de 'docx' + 'file-saver' — mismo patrón ya usado por AI
 * Insights (ai-insights.component.ts:exportarAWord), reutilizado aquí para
 * las exportaciones nuevas (Retrospectiva, Reporte General) sin tocar ni
 * duplicar la exportación de AI Insights, que ya funciona. Devuelve null si
 * las librerías no están disponibles, en vez de lanzar una excepción sin
 * manejar.
 */
export async function cargarLibreriasWord(): Promise<{ docx: any; saveAs: (blob: Blob, name: string) => void } | null> {
  try {
    const docx = await import('docx');
    const fileSaverMod: any = await import('file-saver');
    // Corrección de auditoría (reportes): 'file-saver' es un módulo UMD/CJS
    // (module.exports = saveAs, con saveAs.saveAs = saveAs) — según el
    // bundler, el interop de import() dinámico puede exponer la función
    // como .saveAs, como .default, o como el propio objeto del módulo.
    // Tomar solo fileSaverMod.saveAs (como hacía antes) puede resolver a
    // undefined y romper la exportación con "saveAs is not a function"
    // sin que nada lo haya avisado — confirmado con un test real.
    const saveAsFn = fileSaverMod.saveAs ?? fileSaverMod.default ?? fileSaverMod;
    if (typeof saveAsFn !== 'function') return null;
    return { docx, saveAs: saveAsFn };
  } catch {
    return null;
  }
}

function encabezado(docx: any, texto: string, nivel: 1 | 2 | 3) {
  const heading = nivel === 1 ? docx.HeadingLevel.HEADING_1
    : nivel === 2 ? docx.HeadingLevel.HEADING_2
    : docx.HeadingLevel.HEADING_3;
  return new docx.Paragraph({ text: texto, heading, spacing: { before: nivel === 1 ? 200 : 300, after: 150 } });
}

/**
 * Convierte las secciones (texto plano, ya construido y testeado por los
 * builders de cada reporte) en párrafos reales de 'docx'. Es intencionalmente
 * mecánico: no decide qué contenido va en el reporte, solo lo renderiza.
 */
export function renderSeccionesADocx(docx: any, secciones: SeccionReporte[]): any[] {
  const children: any[] = [];
  for (const s of secciones) {
    children.push(encabezado(docx, s.titulo, s.nivel));
    for (const p of s.parrafos ?? []) {
      children.push(new docx.Paragraph({ text: p, spacing: { after: 120 } }));
    }
    if (s.bullets !== undefined) {
      if (s.bullets.length === 0) {
        children.push(new docx.Paragraph({ text: 'No hay datos registrados.', spacing: { after: 120 } }));
      } else {
        for (const b of s.bullets) {
          children.push(new docx.Paragraph({ text: `• ${b}`, spacing: { after: 80 } }));
        }
      }
    }
  }
  return children;
}

/**
 * Genera un documento Word a partir de un título y una lista de secciones,
 * y dispara la descarga en el navegador. Devuelve false (sin lanzar) si las
 * librerías de exportación no están disponibles.
 */
export async function generarYDescargarDocumento(
  titulo: string, secciones: SeccionReporte[], fileName: string
): Promise<boolean> {
  const libs = await cargarLibreriasWord();
  if (!libs) return false;
  const { docx, saveAs } = libs;

  const doc = new docx.Document({
    sections: [{
      properties: {},
      children: [
        new docx.Paragraph({
          text: titulo,
          heading: docx.HeadingLevel.HEADING_1,
          alignment: docx.AlignmentType.CENTER,
          spacing: { after: 400 }
        }),
        ...renderSeccionesADocx(docx, secciones)
      ]
    }]
  });

  const blob = await docx.Packer.toBlob(doc);
  saveAs(blob, fileName);
  return true;
}
