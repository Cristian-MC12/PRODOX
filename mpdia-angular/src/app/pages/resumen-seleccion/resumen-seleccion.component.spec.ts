// Autor: Cristian Santiago Martinez Cordoba — MPDIA
// FASE 10: tests de ResumenSeleccionComponent.aceptar() — el error HTTP ya no se oculta
// navegando a Verificación como si todo hubiera salido bien (ver diagnóstico FASE 9,
// bloque 7).
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { ResumenSeleccionComponent } from './resumen-seleccion.component';
import { ShellComponent } from '../../layout/shell/shell.component';
import { MetricRankingService } from '../../services/metric-ranking.service';
import { SeleccionService } from '../../services/seleccion.service';

@Component({ selector: 'app-shell', standalone: true, template: '<ng-content></ng-content>' })
class MockShellComponent {}

describe('ResumenSeleccionComponent.aceptar() (FASE 10)', () => {
  let component: ResumenSeleccionComponent;
  let fixture: ComponentFixture<ResumenSeleccionComponent>;
  let rankingService: jasmine.SpyObj<MetricRankingService>;
  let seleccionService: SeleccionService;
  let router: Router;

  const seleccionCompleta = (id: string, metricaId: string) => ({
    id,
    factorId: metricaId,
    factorNombre: 'Métrica ' + id,
    factorCategoria: 'Impacto',
    metricaNombre: 'Métrica ' + id,
    metricaDescripcion: '',
    proyectoId: 'proj-1',
    creadoEn: '2026-08-20T00:00:00Z',
    estadoParametrizacion: 'completa' as const,
    parametrizacion: {
      objetivo: 'obj', procedimiento: 'proc', indicadorVariable: 'ind', escala: 'esc'
    }
  });

  beforeEach(async () => {
    const rankingSpy = jasmine.createSpyObj('MetricRankingService', ['guardar']);

    await TestBed.configureTestingModule({
      imports: [ResumenSeleccionComponent, HttpClientTestingModule],
      providers: [
        { provide: MetricRankingService, useValue: rankingSpy }
      ]
    })
      .overrideComponent(ResumenSeleccionComponent, {
        remove: { imports: [ShellComponent] },
        add: { imports: [MockShellComponent] }
      })
      .compileComponents();

    localStorage.setItem('mpdia_proyecto_activo', JSON.stringify({ id: 'proj-1', nombre: 'Prueba 1' }));

    fixture = TestBed.createComponent(ResumenSeleccionComponent);
    component = fixture.componentInstance;
    rankingService = TestBed.inject(MetricRankingService) as jasmine.SpyObj<MetricRankingService>;
    seleccionService = TestBed.inject(SeleccionService);
    router = TestBed.inject(Router);

    spyOn(router, 'navigate').and.resolveTo(true);
  });

  afterEach(() => {
    localStorage.removeItem('mpdia_proyecto_activo');
    localStorage.removeItem('mpdia_selecciones');
  });

  // ── C.9 ──────────────────────────────────────────────────────────────

  it('si todas las operaciones responden 200, navega a Verificación y limpia la selección', () => {
    component.seleccionadas = [seleccionCompleta('a', 'm-1')];
    rankingService.guardar.and.returnValue(of({ id: 'p-1' } as any));

    component.aceptar();

    expect(router.navigate).toHaveBeenCalledWith(['/verificacion']);
    expect(component.errorMsg).toBe('');
    expect(JSON.parse(localStorage.getItem('mpdia_selecciones') ?? '[]')).toEqual([]);
  });

  // ── C.10 / C.11 / C.13 ───────────────────────────────────────────────

  it('si una operación falla, permanece en Resumen, no limpia la selección y no navega', () => {
    localStorage.setItem('mpdia_selecciones', JSON.stringify([seleccionCompleta('a', 'm-1')]));

    component.seleccionadas = [seleccionCompleta('a', 'm-1')];
    rankingService.guardar.and.returnValue(
      throwError(() => ({ status: 500, error: { error: 'fallo de guardado' } }))
    );

    component.aceptar();

    expect(router.navigate).not.toHaveBeenCalled();
    expect(component.enviando).toBeFalse();
    // La selección local sigue intacta en localStorage (no se llamó a limpiar()).
    expect(JSON.parse(localStorage.getItem('mpdia_selecciones') ?? '[]')).not.toEqual([]);
  });

  // ── C.12 ─────────────────────────────────────────────────────────────

  it('muestra un mensaje de error visible que identifica qué métrica falló', () => {
    component.seleccionadas = [seleccionCompleta('a', 'm-1')];
    rankingService.guardar.and.returnValue(
      throwError(() => ({ status: 500, error: { error: 'fallo de guardado' } }))
    );

    component.aceptar();

    expect(component.errorMsg).toContain('Métrica a');
    expect(component.errorMsg).toContain('fallo de guardado');
  });

  it('con varias métricas, si solo una falla, informa cuántas fallaron sin perder ninguna selección', () => {
    component.seleccionadas = [seleccionCompleta('a', 'm-1'), seleccionCompleta('b', 'm-2')];
    rankingService.guardar.and.callFake((req: any) =>
      req.metricaId === 'm-2'
        ? throwError(() => ({ status: 500, error: { error: 'boom' } }))
        : of({ id: 'ok' } as any)
    );

    component.aceptar();

    expect(router.navigate).not.toHaveBeenCalled();
    expect(component.errorMsg).toContain('1 de 2');
    expect(component.errorMsg).toContain('Métrica b');
  });
});
