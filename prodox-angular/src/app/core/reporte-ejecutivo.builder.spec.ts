// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { construirSeccionesReporteEjecutivo } from './reporte-ejecutivo.builder';
import { AISprintReport } from '../models/ai-reports.model';

describe('construirSeccionesReporteEjecutivo (Fase reportes: exportación del Reporte Ejecutivo de Sprint)', () => {
  const report: AISprintReport = {
    sprintId: 'sprint-2', sprintNumero: 2, sprintGoal: 'Mejorar la capacidad de trabajo',
    fechaInicio: '2026-07-15', fechaFin: '2026-07-28',
    resumenEjecutivo: 'El sprint fue exitoso, con mejoras en calidad.',
    metricas: { Calidad: 8.5, Productividad: 7.2 },
    highlights: ['Calidad superior', 'Productividad estable'],
    concerns: [],
    insights: [],
    recomendaciones: 'Continuar con las prácticas actuales.',
    generatedAt: '2026-08-11T22:00:00Z'
  };

  it('incluye el nombre del proyecto', () => {
    const secciones = construirSeccionesReporteEjecutivo(report, 'Proyecto Xabi');
    const datosSprint = secciones.find(s => s.titulo === 'Datos del sprint')!.parrafos!.join(' ');
    expect(datosSprint).toContain('Proyecto Xabi');
  });

  it('incluye el número y el goal del Sprint, y su periodo', () => {
    const secciones = construirSeccionesReporteEjecutivo(report, 'Proyecto Xabi');
    const datosSprint = secciones.find(s => s.titulo === 'Datos del sprint')!.parrafos!.join(' ');
    expect(datosSprint).toContain('Sprint: 2');
    expect(datosSprint).toContain('Mejorar la capacidad de trabajo');
    expect(datosSprint).toContain('2026-07-15 a 2026-07-28');
  });

  it('incluye el resumen ejecutivo y las métricas disponibles', () => {
    const secciones = construirSeccionesReporteEjecutivo(report, 'Proyecto Xabi');

    expect(secciones.find(s => s.titulo === 'Resumen Ejecutivo')!.parrafos).toContain(
      'El sprint fue exitoso, con mejoras en calidad.'
    );

    const metricas = secciones.find(s => s.titulo === 'Métricas del Sprint')!.bullets!;
    expect(metricas).toContain('Calidad: 8.5');
    expect(metricas).toContain('Productividad: 7.2');
  });

  it('incluye recomendaciones y highlights ya limpios de Markdown crudo', () => {
    const sucio: AISprintReport = {
      ...report,
      recomendaciones: '**Priorizar** la revisión del backlog.',
      highlights: ['**Calidad superior** al promedio']
    };
    const secciones = construirSeccionesReporteEjecutivo(sucio, 'Proyecto Xabi');

    const recomendaciones = secciones.find(s => s.titulo === 'Recomendaciones')!.parrafos!.join(' ');
    expect(recomendaciones).not.toContain('**');
    expect(recomendaciones).toContain('Priorizar la revisión del backlog.');

    const highlights = secciones.find(s => s.titulo === 'Aspectos Destacados')!.bullets!;
    expect(highlights[0]).not.toContain('**');
    expect(highlights[0]).toContain('Calidad superior al promedio');
  });

  it('sin métricas/highlights/concerns/insights: omite esas secciones, igual que la pantalla, sin inventar contenido', () => {
    const vacio: AISprintReport = { ...report, metricas: {}, highlights: [], concerns: [], insights: [] };
    const secciones = construirSeccionesReporteEjecutivo(vacio, 'Proyecto Xabi');

    expect(secciones.some(s => s.titulo === 'Métricas del Sprint')).toBeFalse();
    expect(secciones.some(s => s.titulo === 'Aspectos Destacados')).toBeFalse();
    expect(secciones.some(s => s.titulo === 'Puntos de Atención')).toBeFalse();
    expect(secciones.some(s => s.titulo === 'Insights Relacionados')).toBeFalse();
    // Resumen y Recomendaciones siempre están (igual que en la pantalla, sin @if).
    expect(secciones.some(s => s.titulo === 'Resumen Ejecutivo')).toBeTrue();
    expect(secciones.some(s => s.titulo === 'Recomendaciones')).toBeTrue();
  });

  it('con concerns e insights: los incluye con severidad, sin inventar ninguno adicional', () => {
    const conDatos: AISprintReport = {
      ...report,
      concerns: ['Baja en productividad detectada'],
      insights: [{
        id: 'i1', proyectoId: 'p1', sprintId: 'sprint-2', type: 'RISK', severity: 'HIGH',
        title: 'Riesgo detectado', description: 'Descripción del riesgo',
        evidence: [], recommendation: null, confidence: 'HIGH',
        dismissed: false, createdAt: '2026-08-01T00:00:00Z', dismissedAt: null
      }]
    };
    const secciones = construirSeccionesReporteEjecutivo(conDatos, 'Proyecto Xabi');

    expect(secciones.find(s => s.titulo === 'Puntos de Atención')!.bullets).toEqual(['Baja en productividad detectada']);
    const insights = secciones.find(s => s.titulo === 'Insights Relacionados')!.bullets!;
    expect(insights[0]).toContain('[HIGH]');
    expect(insights[0]).toContain('Riesgo detectado');
    expect(insights[0]).toContain('Descripción del riesgo');
  });

  it('sin fechas registradas: indica "no registrado" en vez de null/undefined', () => {
    const sinFechas: AISprintReport = { ...report, fechaInicio: '', fechaFin: '' };
    const secciones = construirSeccionesReporteEjecutivo(sinFechas, 'Proyecto Xabi');
    const datosSprint = secciones.find(s => s.titulo === 'Datos del sprint')!.parrafos!.join(' ');
    expect(datosSprint).toContain('Periodo: no registrado');
    expect(datosSprint).not.toContain('null');
    expect(datosSprint).not.toContain('undefined');
  });
});
