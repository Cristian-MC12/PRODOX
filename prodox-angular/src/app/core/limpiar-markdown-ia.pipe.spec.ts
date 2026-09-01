// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { LimpiarMarkdownIAPipe } from './limpiar-markdown-ia.pipe';

describe('LimpiarMarkdownIAPipe', () => {
  let pipe: LimpiarMarkdownIAPipe;

  beforeEach(() => {
    pipe = new LimpiarMarkdownIAPipe();
  });

  it('quita null/undefined/vacío sin lanzar error', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
    expect(pipe.transform('')).toBe('');
  });

  it('no toca texto ya limpio (contenido semántico intacto)', () => {
    const texto = 'La métrica de Impacto ha experimentado una tendencia descendente aguda.';
    expect(pipe.transform(texto)).toBe(texto);
  });

  it('quita negrita Markdown (**texto**) sin dejar los asteriscos visibles', () => {
    const sucio = 'Esta caída **definitivamente requiere atención inmediata** del equipo.';
    const limpio = pipe.transform(sucio);
    expect(limpio).not.toContain('**');
    expect(limpio).toContain('definitivamente requiere atención inmediata');
  });

  it('quita viñetas Markdown ("* ...") al inicio de línea sin dejar el marcador visible', () => {
    const sucio = '* ¿Hubo cambios en el equipo?\n* ¿Cómo se definió la métrica?';
    const limpio = pipe.transform(sucio);
    expect(limpio).not.toMatch(/^\*\s/m);
    expect(limpio).toContain('¿Hubo cambios en el equipo?');
    expect(limpio).toContain('¿Cómo se definió la métrica?');
  });

  it('quita separadores tipo regla horizontal (---) cuando son artefacto de formato', () => {
    const sucio = 'Alerta de Desempeño --- Caída del 37.5% en la Flexibilidad';
    const limpio = pipe.transform(sucio);
    expect(limpio).not.toContain('---');
    expect(limpio).toContain('Alerta de Desempeño');
    expect(limpio).toContain('Caída del 37.5% en la Flexibilidad');
  });

  it('quita el preámbulo conversacional conocido "Aquí tienes..." solo por estar al inicio (boilerplate confirmado)', () => {
    const sucio = 'Aquí tienes la comparación de desempeño y el insight generado: '
      + '--- **** Alerta de Desempeño: Caída del 37.5% en la Flexibilidad del Producto/Equipo **';
    const limpio = pipe.transform(sucio);
    expect(limpio).not.toMatch(/^aqu[ií]\s+tienes/i);
    expect(limpio).toBe('Alerta de Desempeño: Caída del 37.5% en la Flexibilidad del Producto/Equipo');
  });

  it('NO quita "Aquí tienes" si aparece en medio del texto (no es preámbulo, podría ser contenido real)', () => {
    const texto = 'El equipo dijo: aquí tienes un ejemplo de mejora sostenida en el sprint.';
    expect(pipe.transform(texto)).toBe(texto);
  });

  it('caso combinado real: título con preámbulo + separadores + negrita mal cerrada se reduce al título real', () => {
    const sucio = 'A continuación el análisis: --- **Riesgo crítico** detectado en Calidad';
    const limpio = pipe.transform(sucio);
    expect(limpio).not.toContain('A continuación');
    expect(limpio).not.toContain('---');
    expect(limpio).not.toContain('**');
    expect(limpio).toBe('Riesgo crítico detectado en Calidad');
  });

  it('preserva números, porcentajes y nombres de categoría exactamente (no altera datos)', () => {
    const texto = 'La variación fue del 37.5% en Sprint 4, categoría Flexibilidad (n=5).';
    expect(pipe.transform(texto)).toBe(texto);
  });
});
