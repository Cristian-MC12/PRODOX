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

  // FASE 8C — título de card usa variableDescripcion (nombre amigable) cuando existe,
  // con fallback a variableNombre (el dato crudo, sin formatear) cuando no.
  describe('título de card: variableDescripcion con fallback a variableNombre (FASE 8C)', () => {
    it('si variableDescripcion existe, se muestra en vez del nombre crudo', () => {
      const metrica = metricaDetalle({
        variableNombre: 'tareas_retrabajadas',
        variableDescripcion: 'Tareas retrabajadas por sprint'
      });
      evaluacionService.detalle.and.returnValue(of([metrica]));
      fixture.detectChanges();

      const texto: string = fixture.nativeElement.textContent;
      expect(texto).toContain('Tareas retrabajadas por sprint');
      expect(texto).not.toContain('tareas_retrabajadas');
    });

    it('si variableDescripcion no existe (null), se muestra variableNombre tal cual', () => {
      const metrica = metricaDetalle({
        variableNombre: 'tareas_retrabajadas',
        variableDescripcion: null
      });
      evaluacionService.detalle.and.returnValue(of([metrica]));
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('tareas_retrabajadas');
    });

    it('no muta variableNombre ni ningún otro dato del objeto recibido', () => {
      const metrica = metricaDetalle({
        variableNombre: 'tareas_retrabajadas',
        variableDescripcion: 'Tareas retrabajadas por sprint'
      });
      evaluacionService.detalle.and.returnValue(of([metrica]));
      fixture.detectChanges();

      // El dato en memoria permanece exactamente igual al que devolvió el servicio;
      // la sustitución es solo de presentación en el template, nunca una reasignación.
      expect(component.datos[0].variableNombre).toBe('tareas_retrabajadas');
      expect(component.datos[0].variableDescripcion).toBe('Tareas retrabajadas por sprint');
    });

    it('los datos estadísticos de la métrica no se alteran por el nombre mostrado', () => {
      const metrica = metricaDetalle({
        variableDescripcion: 'Tareas retrabajadas por sprint'
      });
      evaluacionService.detalle.and.returnValue(of([metrica]));
      fixture.detectChanges();

      expect(component.datos[0].estadisticas).toEqual(metrica.estadisticas);
      expect(component.datos[0].registros).toEqual(metrica.registros);
    });

    it('filtros (categoría) y tabs existentes siguen funcionando igual con el nuevo campo presente', () => {
      const conDescripcion = metricaDetalle({
        variableId: 'v1', categoria: 'Significado', variableDescripcion: 'Tareas retrabajadas por sprint'
      });
      const sinDescripcion = metricaDetalle({
        variableId: 'v2', categoria: 'Impacto', variableDescripcion: null
      });
      evaluacionService.detalle.and.returnValue(of([conDescripcion, sinDescripcion]));
      fixture.detectChanges();

      expect(component.metricasFiltradas.length).toBe(2);

      component.categoriaFiltro = 'Impacto';
      expect(component.metricasFiltradas.length).toBe(1);
      expect(component.metricasFiltradas[0].variableId).toBe('v2');

      component.categoriaFiltro = '';
      component.tab = 'estadisticas';
      fixture.detectChanges();
      const activo = fixture.nativeElement.querySelector('.nav-link.active');
      expect(activo?.textContent).toContain('Estadísticas');
    });
  });
});
