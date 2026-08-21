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
});
