// Autor: Cristian Santiago Martinez Cordoba — MPDIA
// FASE 16 — Tests de EvaluacionComponent: la gráfica ahora usa MiniChartComponent
// (ejes X/Y con numeración) pero la fuente de datos sigue siendo exactamente
// la misma (EvaluacionService.detalle), sin ninguna fuente nueva ni duplicada.
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { EvaluacionComponent } from './evaluacion.component';
import { ShellComponent } from '../../layout/shell/shell.component';
import { EvaluacionService } from '../../services/evaluacion.service';
import { MetricaEvaluacionDetalleDto } from '../../models/evaluacion-detalle.model';

@Component({ selector: 'app-shell', standalone: true, template: '<ng-content></ng-content>' })
class MockShellComponent {}

function metricaDetalle(overrides: Partial<MetricaEvaluacionDetalleDto> = {}): MetricaEvaluacionDetalleDto {
  return {
    variableId: 'v1',
    variableNombre: 'Estado de ánimo del equipo',
    categoria: 'Significado',
    tipoAlcance: 'grupal',
    frecuenciaCaptura: 'por_sprint',
    formulaTexto: null,
    registros: [
      { id: 'r1', valor: 7, registradoAt: '2026-08-21T00:00:00Z', sprintId: 's1', sprintNumero: 1, userId: 'sm@test.com' },
      { id: 'r2', valor: 8, registradoAt: '2026-08-22T00:00:00Z', sprintId: 's1', sprintNumero: 1, userId: 'sm@test.com' }
    ],
    estadisticas: {
      totalRegistros: 2, promedio: 7.5, minimo: 7, maximo: 8, primerValor: 7, ultimoValor: 8,
      cambio: 1, cambioPct: 14.3, tendencia: 'ascendente', pendiente: 1,
      desviacionEstandar: null, coeficienteVariacion: null, variabilidad: null
    },
    porSprint: [{ sprintId: 's1', sprintNumero: 1, totalRegistros: 2, promedio: 7.5, minimo: 7, maximo: 8 }],
    ...overrides
  };
}

describe('EvaluacionComponent', () => {
  let component: EvaluacionComponent;
  let fixture: ComponentFixture<EvaluacionComponent>;
  let evaluacionService: jasmine.SpyObj<EvaluacionService>;

  const mockProyecto = { id: 'proj-1', nombre: 'Sandbox', metodo: 'scrum' as const };

  beforeEach(async () => {
    const evaluacionServiceSpy = jasmine.createSpyObj('EvaluacionService', ['detalle']);
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [EvaluacionComponent],
      providers: [
        { provide: EvaluacionService, useValue: evaluacionServiceSpy },
        { provide: Router, useValue: routerSpy },
      ]
    })
      .overrideComponent(EvaluacionComponent, {
        remove: { imports: [ShellComponent] },
        add: { imports: [MockShellComponent] }
      })
      .compileComponents();

    evaluacionService = TestBed.inject(EvaluacionService) as jasmine.SpyObj<EvaluacionService>;
    localStorage.setItem('mpdia_proyecto_activo', JSON.stringify(mockProyecto));

    fixture = TestBed.createComponent(EvaluacionComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => localStorage.clear());

  it('should create', () => {
    evaluacionService.detalle.and.returnValue(of([]));
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('carga los datos desde EvaluacionService.detalle(proyectoId) — misma fuente que Ejecución', () => {
    evaluacionService.detalle.and.returnValue(of([metricaDetalle()]));
    fixture.detectChanges();

    expect(evaluacionService.detalle).toHaveBeenCalledWith('proj-1');
    expect(component.datos.length).toBe(1);
  });

  it('convierte los registros reales al formato de MiniChartComponent sin inventar datos', () => {
    evaluacionService.detalle.and.returnValue(of([metricaDetalle()]));
    fixture.detectChanges();

    const puntos = component.paraMiniChart(component.datos[0].registros);
    expect(puntos).toEqual([
      { fecha: '2026-08-21T00:00:00Z', valor: 7 },
      { fecha: '2026-08-22T00:00:00Z', valor: 8 }
    ]);
  });

  it('renderiza la gráfica (app-mini-chart) con ejes/numeración para la métrica con datos', () => {
    evaluacionService.detalle.and.returnValue(of([metricaDetalle()]));
    fixture.detectChanges();

    const miniChart = fixture.nativeElement.querySelector('app-mini-chart');
    expect(miniChart).toBeTruthy();
    // MiniChartComponent dibuja sus propios ejes con numeración — ya cubierto
    // por mini-chart.component.spec.ts; aquí solo verificamos que Evaluación
    // efectivamente lo usa en vez del SVG artesanal anterior.
    expect(fixture.nativeElement.querySelector('svg')).toBeTruthy();
    expect(fixture.nativeElement.querySelectorAll('circle').length).toBe(2);
  });

  it('sin datos, muestra el mensaje de "completá Ejecución primero" y ningún gráfico', () => {
    evaluacionService.detalle.and.returnValue(of([]));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Completá la fase de Ejecución primero');
    expect(fixture.nativeElement.querySelector('app-mini-chart')).toBeFalsy();
  });
});
