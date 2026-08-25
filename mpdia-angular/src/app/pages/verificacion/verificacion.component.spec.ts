// Autor: Cristian Santiago Martinez Cordoba — MPDIA
// FASE 10: tests de VerificacionComponent — los contadores de Aprobadas/Rechazadas
// reflejan el estado real en BD (aislado por proyecto activo) al cargar/recargar,
// en vez de depender solo de lo acumulado en memoria durante la sesión
// (ver diagnóstico FASE 9, bloque 4).
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, HttpClientTestingModule } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { VerificacionComponent } from './verificacion.component';
import { ShellComponent } from '../../layout/shell/shell.component';
import { AuthService } from '../../services/auth.service';
import { MetricRankingService } from '../../services/metric-ranking.service';
import { environment } from '../../../environments/environment';

@Component({ selector: 'app-shell', standalone: true, template: '<ng-content></ng-content>' })
class MockShellComponent {}

describe('VerificacionComponent (FASE 10)', () => {
  let component: VerificacionComponent;
  let fixture: ComponentFixture<VerificacionComponent>;
  let httpMock: HttpTestingController;
  let rankingService: jasmine.SpyObj<MetricRankingService>;

  beforeEach(async () => {
    const rankingSpy = jasmine.createSpyObj('MetricRankingService', ['getResumen']);
    rankingSpy.getResumen.and.returnValue(of({ pendientes: 0, aprobadas: 3, rechazadas: 1 }));

    localStorage.setItem('mpdia_user', JSON.stringify({ role: 'scrum_master', email: 'sm@test.com' }));
    localStorage.setItem('mpdia_proyecto_activo', JSON.stringify({ id: 'proj-1', nombre: 'Prueba 1' }));

    await TestBed.configureTestingModule({
      imports: [VerificacionComponent, HttpClientTestingModule],
      providers: [
        { provide: MetricRankingService, useValue: rankingSpy }
      ]
    })
      .overrideComponent(VerificacionComponent, {
        remove: { imports: [ShellComponent] },
        add: { imports: [MockShellComponent] }
      })
      .compileComponents();

    fixture = TestBed.createComponent(VerificacionComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    rankingService = TestBed.inject(MetricRankingService) as jasmine.SpyObj<MetricRankingService>;
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.removeItem('mpdia_user');
    localStorage.removeItem('mpdia_proyecto_activo');
  });

  // ── D.14 ─────────────────────────────────────────────────────────────

  it('al cargar, consulta el resumen persistente en BD y no solo la memoria de sesión', () => {
    component.aprobadas = 0;
    component.rechazadas = 0;

    component.cargar();
    httpMock.expectOne(req => req.url.includes('/metric-ranking/pendientes')).flush([]);

    expect(rankingService.getResumen).toHaveBeenCalledWith('proj-1');
    expect(component.aprobadas).toBe(3);
    expect(component.rechazadas).toBe(1);
  });

  // ── D.15 ─────────────────────────────────────────────────────────────

  it('las pendientes se piden filtradas por el proyecto activo, nunca de todo el sistema', () => {
    component.cargar();

    const req = httpMock.expectOne(req => req.url.includes('/metric-ranking/pendientes'));
    expect(req.request.url).toContain('proyectoId=proj-1');
    req.flush([]);
  });

  // ── FASE 17 (corrección del defecto documentado) ────────────────────────
  // Antes, aprobar() descartaba cualquier error HTTP con catchError(() => of(null))
  // y este mismo subscribe mostraba igual el mensaje de éxito y quitaba la fila de
  // pendientes — el Scrum Master nunca se enteraba de que la aprobación había
  // fallado (ej. indicador de más de 120 caracteres). Ahora debe mostrarse el
  // mensaje real del backend y la fila debe seguir en la lista de pendientes.

  const pendiente = {
    id: 'param-1', factorId: 'f1', factorNombre: 'Estado de ánimo',
    factorCategoria: 'Significado', userEmail: 'dev@test.com',
    objetivo: 'obj', procedimiento: 'proc',
    indicadorVariable: 'a'.repeat(121), escala: 'esc',
    status: 'pendiente', revisadoPor: null, motivoRechazo: null,
    createdAt: '2026-08-21T00:00:00Z'
  };

  it('si el backend rechaza la aprobación, muestra el mensaje real y NO quita la fila de pendientes', () => {
    component.pendientes = [pendiente];
    component.aprobadas = 0;

    component.aprobar(pendiente);

    const req = httpMock.expectOne(r => r.url.includes('/metric-ranking/verificar'));
    req.flush(
      { timestamp: '2026-08-21T00:00:00Z', status: 400,
        error: 'El campo "Indicador y Variables" contiene un valor de 121 caracteres, ' +
               'que excede el máximo de 120 permitido.' },
      { status: 400, statusText: 'Bad Request' }
    );

    expect(component.alertClass).toBe('alert-danger');
    expect(component.alertMsg).toContain('120');
    expect(component.pendientes).toEqual([pendiente]);
    expect(component.aprobadas).toBe(0);
    expect(component.procesando).toBe('');
  });

  it('si el backend aprueba correctamente, muestra éxito y quita la fila de pendientes', () => {
    component.pendientes = [pendiente];
    component.aprobadas = 0;

    component.aprobar(pendiente);

    const req = httpMock.expectOne(r => r.url.includes('/metric-ranking/verificar'));
    req.flush({});

    expect(component.alertClass).toBe('alert-success');
    expect(component.pendientes).toEqual([]);
    expect(component.aprobadas).toBe(1);
  });
});
