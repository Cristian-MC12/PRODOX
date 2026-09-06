// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { construirSeccionesRetrospectiva } from './retrospectiva-reporte.builder';
import { AIRetrospective } from '../models/ai-reports.model';

describe('construirSeccionesRetrospectiva (Fase reportes: exportación de Retrospectiva)', () => {
  const retro: AIRetrospective = {
    sprintId: 'sprint-2', sprintNumero: 2, sprintGoal: 'Mejorar la capacidad de trabajo',
    fechaInicio: '2026-07-15', fechaFin: '2026-07-28',
    whatWentWell: ['Calidad mejoró 12%', 'Equipo colaborativo'],
    whatCouldImprove: ['Mejorar documentación'],
    risks: ['Tendencia descendente en productividad'],
    recommendations: ['Establecer daily standup más eficiente'],
    questionsForTeam: ['¿Qué obstáculos encontramos?'],
    generatedAt: '2026-08-11T22:00:00Z'
  };

  it('incluye únicamente los datos que AIRetrospectiveDto realmente trae, sin inventar contenido adicional', () => {
    const secciones = construirSeccionesRetrospectiva(retro, 'Proyecto Test');

    expect(secciones.find(s => s.titulo === 'Qué funcionó bien')!.bullets).toEqual(retro.whatWentWell);
    expect(secciones.find(s => s.titulo === 'Qué se puede mejorar')!.bullets).toEqual(retro.whatCouldImprove);
    expect(secciones.find(s => s.titulo === 'Riesgos identificados')!.bullets).toEqual(retro.risks);
    expect(secciones.find(s => s.titulo === 'Recomendaciones')!.bullets).toEqual(retro.recommendations);
    expect(secciones.find(s => s.titulo === 'Preguntas para el equipo')!.bullets).toEqual(retro.questionsForTeam);

    const datosSprint = secciones.find(s => s.titulo === 'Datos del sprint')!.parrafos!.join(' ');
    expect(datosSprint).toContain('Proyecto Test');
    expect(datosSprint).toContain('Sprint: 2');
    expect(datosSprint).toContain('Mejorar la capacidad de trabajo');
    expect(datosSprint).toContain('2026-07-15 a 2026-07-28');
  });

  it('sin fecha de inicio/fin registrada: indica "no registrado" en vez de mostrar null/undefined', () => {
    const retroSinFechas: AIRetrospective = { ...retro, fechaInicio: '', fechaFin: '' };
    const secciones = construirSeccionesRetrospectiva(retroSinFechas, 'Proyecto Test');

    const datosSprint = secciones.find(s => s.titulo === 'Datos del sprint')!.parrafos!.join(' ');
    expect(datosSprint).toContain('Periodo: no registrado');
    expect(datosSprint).not.toContain('null');
    expect(datosSprint).not.toContain('undefined');
  });

  it('listas vacías (ej. sin riesgos identificados): la sección queda con bullets vacío, sin fabricar contenido', () => {
    const retroSinRiesgos: AIRetrospective = { ...retro, risks: [] };
    const secciones = construirSeccionesRetrospectiva(retroSinRiesgos, 'Proyecto Test');

    expect(secciones.find(s => s.titulo === 'Riesgos identificados')!.bullets).toEqual([]);
  });
});
