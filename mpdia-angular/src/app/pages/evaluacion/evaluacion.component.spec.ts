// Autor: Cristian Santiago Martinez Cordoba — MPDIA
// Tests de EvaluacionComponent: la gráfica usa EvaluacionMetricChartComponent
// (Chart.js con 2+ registros reales, tarjeta de dato único con 1, estado vacío
// con 0 — ver metric-chart.component.spec.ts para el detalle de esos 3 estados)
// pero la fuente de datos sigue siendo exactamente la misma
// (EvaluacionService.detalle), sin ninguna fuente nueva ni duplicada.
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
    // Dos sprints DISTINTOS (s1/s2): para frecuenciaCaptura='por_sprint', dos registros
    // del MISMO sprint son 1 solo período (ver EvaluacionMetricChartComponent) — este
    // fixture representa 2 períodos reales y comparables, que es lo que este describe
    // ejercita (tendencia calculable, línea con 2 puntos).
    registros: [
      { id: 'r1', valor: 7, registradoAt: '2026-08-21T00:00:00Z', sprintId: 's1', sprintNumero: 1, userId: 'sm@test.com' },
      { id: 'r2', valor: 8, registradoAt: '2026-08-22T00:00:00Z', sprintId: 's2', sprintNumero: 2, userId: 'sm@test.com' }
    ],
    estadisticas: {
      totalRegistros: 2, promedio: 7.5, minimo: 7, maximo: 8, primerValor: 7, ultimoValor: 8,
      cambio: 1, cambioPct: 14.3, tendencia: 'ascendente', pendiente: 1,
      desviacionEstandar: null, coeficienteVariacion: null, variabilidad: null
    },
    porSprint: [
      { sprintId: 's1', sprintNumero: 1, totalRegistros: 1, promedio: 7, minimo: 7, maximo: 7 },
      { sprintId: 's2', sprintNumero: 2, totalRegistros: 1, promedio: 8, minimo: 8, maximo: 8 }
    ],
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

  it('pasa los registros reales (sin transformar) al componente de gráfica de Evaluación', () => {
    evaluacionService.detalle.and.returnValue(of([metricaDetalle()]));
    fixture.detectChanges();
    // Ver todos los sprints (comparación): el auto-filtro al sprint más reciente
    // (cargar()) es una vista deliberada aparte, no lo que este test ejercita.
    component.sprintFiltro = null;
    fixture.detectChanges();

    const chartEl = fixture.nativeElement.querySelector('app-evaluacion-metric-chart');
    expect(chartEl).toBeTruthy();
    expect(component.registrosParaVista(component.datos[0])).toEqual(component.datos[0].registros);
  });

  it('renderiza Chart.js (canvas) para la métrica con 2 registros reales', () => {
    evaluacionService.detalle.and.returnValue(of([metricaDetalle()]));
    fixture.detectChanges();
    component.sprintFiltro = null; // ver todos los sprints: los 2 registros (2 períodos) juntos
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-evaluacion-metric-chart')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('canvas')).toBeTruthy();
  });

  it('sin datos, muestra el mensaje de "completá Ejecución primero" y ningún gráfico', () => {
    evaluacionService.detalle.and.returnValue(of([]));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Completá la fase de Ejecución primero');
    expect(fixture.nativeElement.querySelector('app-evaluacion-metric-chart')).toBeFalsy();
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
