// Autor: Cristian Santiago Martinez Cordoba — PRODOX
// FASE 10: tests de ResumenSeleccionComponent.aceptar() — el error HTTP ya no se oculta
// navegando a Verificación como si todo hubiera salido bien (ver diagnóstico FASE 9,
// bloque 7).
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { Subject, of, throwError } from 'rxjs';
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

  // ── FASE 20 (envío duplicado al Scrum Master) ───────────────────────────
  // Confirmado empíricamente en un navegador real: dos clics con ~26ms de
  // diferencia en "Enviar al Scrum Master" generaban dos filas "pendiente"
  // idénticas, porque aceptar() no tenía ningún guard propio — dependía por
  // completo del binding [disabled]="enviando" del template, que no alcanza a
  // reflejarse en el DOM a tiempo para bloquear el segundo clic.

  it('un clic llama a guardar() exactamente una vez por métrica seleccionada', () => {
    component.seleccionadas = [seleccionCompleta('a', 'm-1')];
    rankingService.guardar.and.returnValue(of({ id: 'p-1' } as any));

    component.aceptar();

    expect(rankingService.guardar).toHaveBeenCalledTimes(1);
  });

  it('dos clics rápidos (aceptar() llamado dos veces mientras enviando=true) generan una sola operación', () => {
    component.seleccionadas = [seleccionCompleta('a', 'm-1')];
    // Subject: nunca emite hasta que se le indique explícitamente — simula una
    // petición HTTP real todavía en curso cuando llega el "segundo clic".
    const enCurso = new Subject<any>();
    rankingService.guardar.and.returnValue(enCurso.asObservable());

    component.aceptar(); // primer clic: enviando pasa a true, guardar() se invoca
    component.aceptar(); // segundo clic "rápido": debe bloquearse por enviando=true

    expect(rankingService.guardar).toHaveBeenCalledTimes(1);
    expect(component.enviando).toBeTrue();
  });

  it('tras un fallo en el primer intento (enviando vuelve a false), un reintento sí funciona y navega', () => {
    component.seleccionadas = [seleccionCompleta('a', 'm-1')];
    rankingService.guardar.and.returnValue(
      throwError(() => ({ status: 500, error: { error: 'fallo de guardado' } }))
    );

    component.aceptar();

    expect(component.enviando).toBeFalse();
    expect(router.navigate).not.toHaveBeenCalled();

    // Reintento: esta vez el backend responde bien.
    rankingService.guardar.and.returnValue(of({ id: 'p-1' } as any));
    component.aceptar();

    expect(rankingService.guardar).toHaveBeenCalledTimes(2);
    expect(router.navigate).toHaveBeenCalledWith(['/verificacion']);
  });

  // Revisión de frecuencia de captura: aceptar() no propagaba frecuenciaCaptura al
  // enviar al Scrum Master — el backend la persistía siempre como "por_sprint" sin
  // importar lo elegido en Parametrización, aunque el dato sí estaba disponible acá
  // en s.parametrizacion.frecuenciaCaptura.
  it('aceptar() propaga la frecuenciaCaptura de cada parametrización al backend', () => {
    const conFrecuenciaDiaria = {
      ...seleccionCompleta('a', 'm-1'),
      parametrizacion: { objetivo: 'obj', procedimiento: 'proc', indicadorVariable: 'ind', escala: 'esc', frecuenciaCaptura: 'diaria' }
    };
    component.seleccionadas = [conFrecuenciaDiaria];
    rankingService.guardar.and.returnValue(of({ id: 'p-1' } as any));

    component.aceptar();

    expect(rankingService.guardar).toHaveBeenCalledWith(
      jasmine.objectContaining({ frecuenciaCaptura: 'diaria' })
    );
  });

  it('aceptar() sin frecuenciaCaptura informada sigue enviando "por_sprint" (comportamiento preexistente)', () => {
    component.seleccionadas = [seleccionCompleta('a', 'm-1')]; // sin frecuenciaCaptura
    rankingService.guardar.and.returnValue(of({ id: 'p-1' } as any));

    component.aceptar();

    expect(rankingService.guardar).toHaveBeenCalledWith(
      jasmine.objectContaining({ frecuenciaCaptura: 'por_sprint' })
    );
  });

  // ── Corrección de duplicados en Verificación ────────────────────────────
  // Causa raíz real del defecto reportado (dos filas "pendiente" idénticas para
  // "Aprendizaje organizacional (FAT)" / "Problemas Recurrentes de Software"):
  // este payload no propagaba la escala estructurada, así que un metric ya
  // enviado desde Parametrización (CON escala) se reenviaba acá SIN escala,
  // y el backend (esMismoContenido()) lo trataba como contenido distinto,
  // creando una versión "pendiente" nueva en vez de reconocerlo como el mismo
  // envío. Enviar los mismos campos que parametrizacion.component.ts cierra
  // ese hueco en el origen, antes de que el backend tenga que deduplicar nada.
  it('aceptar() propaga la escala estructurada de cada parametrización al backend', () => {
    const conEscala = {
      ...seleccionCompleta('a', 'm-1'),
      parametrizacion: {
        objetivo: 'obj', procedimiento: 'proc', indicadorVariable: 'ind', escala: 'esc',
        escalaTipo: 'NUMERICA_ENTERA' as const, escalaMin: 0, escalaMax: null,
        escalaPaso: 1, escalaSinLimite: true, escalaDescripcion: 'Cantidad de defectos.'
      }
    };
    component.seleccionadas = [conEscala];
    rankingService.guardar.and.returnValue(of({ id: 'p-1' } as any));

    component.aceptar();

    expect(rankingService.guardar).toHaveBeenCalledWith(
      jasmine.objectContaining({
        escalaTipo: 'NUMERICA_ENTERA', escalaMin: 0, escalaMax: null,
        escalaPaso: 1, escalaSinLimite: true, escalaDescripcion: 'Cantidad de defectos.'
      })
    );
  });

  it('aceptar() sin escala estructurada informada la envía como null (compatibilidad histórica)', () => {
    component.seleccionadas = [seleccionCompleta('a', 'm-1')]; // sin campos de escala
    rankingService.guardar.and.returnValue(of({ id: 'p-1' } as any));

    component.aceptar();

    expect(rankingService.guardar).toHaveBeenCalledWith(
      jasmine.objectContaining({
        escalaTipo: null, escalaMin: null, escalaMax: null,
        escalaPaso: null, escalaSinLimite: null, escalaDescripcion: null
      })
    );
  });
});
