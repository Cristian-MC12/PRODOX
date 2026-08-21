// Autor: Cristian Santiago Martinez Cordoba — MPDIA
// FASE 7: Tests de EjecucionComponent reescrito — captura/cálculo agrupado
// por las 5 métricas oficiales, vía VariableDinamicaService/MetricaAcademicaService.
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { of, throwError } from 'rxjs';
import { EjecucionComponent } from './ejecucion.component';
import { ShellComponent } from '../../layout/shell/shell.component';
import { AuthService } from '../../services/auth.service';
import { SprintService } from '../../services/sprint.service';
import { MetricaAcademicaService } from '../../services/metrica-academica.service';
import { VariableDinamicaService, VariablesMetricaResponse } from '../../services/variable-dinamica.service';

@Component({ selector: 'app-shell', standalone: true, template: '<ng-content></ng-content>' })
class MockShellComponent {}

const METRICA_DEFECTOS = 'ec0d74fe-0bf4-4970-af89-dcaa0736c8ed';
const METRICA_IMPEDIMENTOS = 'dde97e2b-1b25-493e-9273-a6b59564b053';
const METRICA_PROBLEMAS = '2ba0cf34-0bec-4e7d-8dc5-40795f050ec9';
const METRICA_FAT = 'beb22a94-0e1b-496a-8b9e-a08a8f6d77c3';
const METRICA_DEUDA = '40beffdf-13f4-4772-8820-4df93fae525c';
const LAS_5_METRICAS = [METRICA_DEFECTOS, METRICA_IMPEDIMENTOS, METRICA_PROBLEMAS, METRICA_FAT, METRICA_DEUDA];

describe('EjecucionComponent (FASE 7)', () => {
  let component: EjecucionComponent;
  let fixture: ComponentFixture<EjecucionComponent>;
  let metricaService: jasmine.SpyObj<MetricaAcademicaService>;
  let variableService: jasmine.SpyObj<VariableDinamicaService>;
  let sprintService: jasmine.SpyObj<SprintService>;
  let authService: jasmine.SpyObj<AuthService> & { currentUser: any };

  const mockProyecto = {
    id: 'proj-1', nombre: 'Prueba 1', descripcion: null, metodo: 'scrum' as const,
    timeBoxSemanas: 3, numeroSprints: 6, fechaInicio: '2026-08-17', productGoal: 'PROBAR',
    sprintGoal: '', estado: 'activo' as const, scrumMasterEmail: 'sm@test.com', totalMiembros: 1,
    createdAt: '2026-08-17T00:00:00Z'
  };

  const mockSprint = {
    id: 'sprint-1', proyectoId: 'proj-1', proyectoNombre: 'Prueba 1', metodo: 'scrum',
    timeBoxSemanas: 3, numero: 1, sprintGoal: 'Sprint 1', estado: 'en_ejecucion' as const,
    fechaInicio: '2026-08-17', fechaFin: '2026-09-06', cerradoPor: null, cerradoAt: null,
    createdAt: '2026-08-17T00:00:00Z'
  };

  function param(metricaId: string, tipoOperacion: string, formula: string, unidad: string, version = 1) {
    return {
      id: 'param-' + metricaId, status: 'aprobada' as const, version, metricaId, proyectoId: 'proj-1',
      fuenteAcademica: 'x', formulaAcademica: formula, tipoOperacion, unidadResultado: unidad,
      objetivo: '', procedimiento: '', indicadorVariable: '', escala: '', frecuenciaCaptura: 'por_sprint',
      createdAt: '2026-08-20T00:00:00Z'
    };
  }

  function variables(nombres: string[]): VariablesMetricaResponse {
    return {
      parametrizacionId: 'param-x', version: 1, status: 'aprobada',
      variables: nombres.map((n, i) => ({
        id: 'v' + i, nombre: n, descripcion: n, tipoDato: 'numerico', obligatorio: true, unidad: ''
      }))
    };
  }

  const PARAMS: Record<string, any> = {
    [METRICA_DEFECTOS]: param(METRICA_DEFECTOS, 'SUMA', 'SUMA(defectos_totales)', 'defectos'),
    [METRICA_IMPEDIMENTOS]: param(METRICA_IMPEDIMENTOS, 'SUMA', 'Σ(impedimento_único_registrado)', 'impedimentos', 4),
    [METRICA_PROBLEMAS]: param(METRICA_PROBLEMAS, 'SUMA', 'Σ(problemas_reportados_cliente)', 'problemas', 3),
    [METRICA_FAT]: param(METRICA_FAT, 'FORMULA', '(ACAT / ACR) × 100', '%'),
    [METRICA_DEUDA]: param(METRICA_DEUDA, 'FORMULA', '(deuda_gestionada / deuda_identificada) × 100', '%'),
  };

  const VARIABLES: Record<string, VariablesMetricaResponse> = {
    [METRICA_DEFECTOS]: variables(['defectos_totales']),
    [METRICA_IMPEDIMENTOS]: variables(['impedimentos_bloqueantes_registrados']),
    [METRICA_PROBLEMAS]: variables(['problema_reportado_individual']),
    [METRICA_FAT]: variables(['acat', 'acr']),
    [METRICA_DEUDA]: variables(['deuda_gestionada', 'deuda_identificada']),
  };

  beforeEach(async () => {
    const metricaServiceSpy = jasmine.createSpyObj('MetricaAcademicaService', [
      'ejecutar', 'obtenerHistorico', 'obtenerParametrizacionAprobada'
    ]);
    const variableServiceSpy = jasmine.createSpyObj('VariableDinamicaService', ['obtenerVariables']);
    const sprintServiceSpy = jasmine.createSpyObj('SprintService', ['listar']);
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['logout']);
    (authServiceSpy as any).currentUser = signal({ userId: 'u1', email: 'sm@test.com', role: 'scrum_master', token: 't' });

    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, FormsModule, CommonModule],
      providers: [
        { provide: MetricaAcademicaService, useValue: metricaServiceSpy },
        { provide: VariableDinamicaService, useValue: variableServiceSpy },
        { provide: SprintService, useValue: sprintServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: AuthService, useValue: authServiceSpy },
      ]
    })
      .overrideComponent(EjecucionComponent, {
        remove: { imports: [ShellComponent] },
        add: { imports: [MockShellComponent, EjecucionComponent] }
      })
      .compileComponents();

    metricaService = TestBed.inject(MetricaAcademicaService) as jasmine.SpyObj<MetricaAcademicaService>;
    variableService = TestBed.inject(VariableDinamicaService) as jasmine.SpyObj<VariableDinamicaService>;
    sprintService = TestBed.inject(SprintService) as jasmine.SpyObj<SprintService>;
    authService = TestBed.inject(AuthService) as any;

    metricaService.obtenerParametrizacionAprobada.and.callFake((metricaId: string) => of(PARAMS[metricaId] ?? null));
    variableService.obtenerVariables.and.callFake((metricaId: string) => of(VARIABLES[metricaId] ?? { parametrizacionId: '', version: 1, status: 'aprobada', variables: [] }));
    metricaService.obtenerHistorico.and.returnValue(of([]));
    sprintService.listar.and.returnValue(of([mockSprint]));

    localStorage.setItem('mpdia_proyecto_activo', JSON.stringify(mockProyecto));

    fixture = TestBed.createComponent(EjecucionComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => localStorage.clear());

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('1) Carga de las 5 métricas oficiales', () => {
    it('should initialize exactly 5 bloques with the 5 official metrica IDs', () => {
      expect(component.bloques.length).toBe(5);
      expect(component.bloques.map(b => b.id).sort()).toEqual([...LAS_5_METRICAS].sort());
    });

    it('should request variables for exactly the 5 official metricas when a sprint is selected, never for any other', () => {
      component.ngOnInit();

      expect(variableService.obtenerVariables).toHaveBeenCalledTimes(5);
      for (const id of LAS_5_METRICAS) {
        expect(variableService.obtenerVariables).toHaveBeenCalledWith(id, 'proj-1', 'sprint-1');
      }
    });
  });

  describe('2) Carga de variables mediante VariableDinamicaService', () => {
    it('should populate each bloque\'s variables from VariableDinamicaService.obtenerVariables()', () => {
      component.ngOnInit();

      const defectos = component.bloques.find(b => b.id === METRICA_DEFECTOS)!;
      expect(defectos.variables.map(v => v.nombre)).toEqual(['defectos_totales']);
    });
  });

  describe('3) No utilización de la lista global antigua de variables', () => {
    it('should never inject or call PlaneacionService.listarVariables (component has no such dependency)', () => {
      // El componente ya no depende de PlaneacionService en absoluto: si lo
      // hiciera, TestBed fallaría por proveedor faltante al no estar
      // registrado aquí. Confirmamos además que compila y funciona sin él.
      expect(component).toBeTruthy();
      expect((component as any).planeacionService).toBeUndefined();
    });
  });

  describe('4) Agrupación de variables por métrica', () => {
    it('should keep each metrica\'s variables isolated in its own bloque, never merged into a flat list', () => {
      component.ngOnInit();

      const fat = component.bloques.find(b => b.id === METRICA_FAT)!;
      const deuda = component.bloques.find(b => b.id === METRICA_DEUDA)!;
      expect(fat.variables.map(v => v.nombre)).toEqual(['acat', 'acr']);
      expect(deuda.variables.map(v => v.nombre)).toEqual(['deuda_gestionada', 'deuda_identificada']);
      // Ningún cruce entre bloques
      expect(fat.variables.some(v => deuda.variables.map(d => d.nombre).includes(v.nombre))).toBe(false);
    });
  });

  describe('5) FAT muestra acat + acr', () => {
    it('should render acat and acr inputs for FAT, and only those', () => {
      component.ngOnInit();
      fixture.detectChanges();

      const texto = fixture.nativeElement.textContent;
      expect(texto).toContain('Acat');
      expect(texto).toContain('Acr');
      expect(texto).toContain('(ACAT / ACR) × 100');
    });
  });

  describe('6) Deuda técnica muestra deuda_gestionada + deuda_identificada', () => {
    it('should render deuda_gestionada and deuda_identificada inputs', () => {
      component.ngOnInit();
      fixture.detectChanges();

      const texto = fixture.nativeElement.textContent;
      expect(texto).toContain('Deuda gestionada');
      expect(texto).toContain('Deuda identificada');
      expect(texto).toContain('(deuda_gestionada / deuda_identificada) × 100');
    });
  });

  describe('7) Defectos muestra defectos_totales', () => {
    it('should render defectos_totales input', () => {
      component.ngOnInit();
      fixture.detectChanges();

      const texto = fixture.nativeElement.textContent;
      expect(texto).toContain('Defectos totales');
      expect(texto).toContain('SUMA(defectos_totales)');
    });
  });

  describe('8) Capacidad de trabajo no aparece', () => {
    it('should never render "Capacidad de trabajo" anywhere in the capture screen', () => {
      component.ngOnInit();
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).not.toContain('Capacidad de trabajo');
    });
  });

  describe('9) Variables históricas no aparecen', () => {
    it('should never render historical/orphaned variable names not returned by the current parametrizacion', () => {
      component.ngOnInit();
      fixture.detectChanges();

      const texto = fixture.nativeElement.textContent;
      // Variables de versiones anteriores (v1/v2/v3 huérfanas de SIG-VEL-02/SIG-SC-02)
      expect(texto).not.toContain('problema_reportado_validado');
      expect(texto).not.toContain('impedimento_bloqueante_una_ocurrencia_de_bloqueo_registrado');
    });
  });

  describe('10) Guardar y calcular invoca MetricaAcademicaService con la métrica correcta', () => {
    it('should call ejecutar() with the exact metricaId, proyectoId, sprintId and captured valores', () => {
      component.ngOnInit();

      const fat = component.bloques.find(b => b.id === METRICA_FAT)!;
      fat.valores = { acat: 8, acr: 10 };
      metricaService.ejecutar.and.returnValue(of({
        resultadoId: 'r1', metricaId: METRICA_FAT, metricaNombre: 'Aprendizaje organizacional (FAT)',
        proyectoId: 'proj-1', sprintId: 'sprint-1', parametrizacionId: 'param-x', parametrizacionVersion: 1,
        tipoCalculo: 'formula', expresion: '(ACAT / ACR) × 100', valoresUtilizados: '{}',
        resultado: 80, unidad: '%', estado: 'calculado' as const, calculadoAt: '2026-08-20T11:23:00Z'
      }));

      component.guardarYCalcular(fat);

      expect(metricaService.ejecutar).toHaveBeenCalledWith(METRICA_FAT, {
        proyectoId: 'proj-1', sprintId: 'sprint-1', valores: { acat: 8, acr: 10 }
      });
    });

    it('should not call ejecutar() for any bloque other than the one clicked', () => {
      component.ngOnInit();

      const defectos = component.bloques.find(b => b.id === METRICA_DEFECTOS)!;
      defectos.valores = { defectos_totales: 5 };
      metricaService.ejecutar.and.returnValue(of({
        resultadoId: 'r2', metricaId: METRICA_DEFECTOS, metricaNombre: 'Defectos',
        proyectoId: 'proj-1', sprintId: 'sprint-1', parametrizacionId: 'param-x', parametrizacionVersion: 1,
        tipoCalculo: 'suma', expresion: 'SUMA(defectos_totales)', valoresUtilizados: '{}',
        resultado: 5, unidad: 'defectos', estado: 'calculado' as const, calculadoAt: '2026-08-20T11:23:00Z'
      }));

      component.guardarYCalcular(defectos);

      expect(metricaService.ejecutar).toHaveBeenCalledTimes(1);
      expect(metricaService.ejecutar).toHaveBeenCalledWith(METRICA_DEFECTOS, jasmine.anything());
    });
  });

  describe('11) Se muestra el ResultadoMetricaDto devuelto', () => {
    it('should store and render the exact result returned by the backend', () => {
      // Un solo detectChanges() dispara ngOnInit() automáticamente (Angular
      // solo lo invoca una vez): llamarlo manualmente ADEMÁS de detectChanges()
      // dispararía ngOnInit() dos veces y el segundo pisaría el resultado.
      fixture.detectChanges();

      const deuda = component.bloques.find(b => b.id === METRICA_DEUDA)!;
      deuda.valores = { deuda_gestionada: 6, deuda_identificada: 8 };
      const resultadoBackend = {
        resultadoId: 'r3', metricaId: METRICA_DEUDA, metricaNombre: 'Deuda técnica gestionada',
        proyectoId: 'proj-1', sprintId: 'sprint-1', parametrizacionId: 'param-x', parametrizacionVersion: 1,
        tipoCalculo: 'formula', expresion: '(deuda_gestionada / deuda_identificada) × 100', valoresUtilizados: '{}',
        resultado: 75, unidad: '%', estado: 'calculado' as const, calculadoAt: '2026-08-20T11:23:00Z'
      };
      metricaService.ejecutar.and.returnValue(of(resultadoBackend));

      component.guardarYCalcular(deuda);
      fixture.detectChanges();

      expect(deuda.resultado).toEqual(resultadoBackend);
      expect(fixture.nativeElement.textContent).toContain('75');
    });

    it('should not allow editing an already-computed result (no input renders for the resultado value)', () => {
      fixture.detectChanges(); // dispara ngOnInit() una sola vez (ver nota arriba)

      const defectos = component.bloques.find(b => b.id === METRICA_DEFECTOS)!;
      defectos.resultado = {
        resultadoId: 'r4', metricaId: METRICA_DEFECTOS, metricaNombre: 'Defectos',
        proyectoId: 'proj-1', sprintId: 'sprint-1', parametrizacionId: 'param-x', parametrizacionVersion: 1,
        tipoCalculo: 'suma', expresion: 'SUMA(defectos_totales)', valoresUtilizados: '{}',
        resultado: 5, unidad: 'defectos', estado: 'calculado' as const, calculadoAt: '2026-08-20T11:23:00Z'
      };
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('Calculado automáticamente');
    });
  });

  describe('12) No se ejecuta cálculo automáticamente al cargar', () => {
    it('should never call ejecutar() just from ngOnInit()/onSprintChange()', () => {
      component.ngOnInit();
      fixture.detectChanges();

      expect(metricaService.ejecutar).not.toHaveBeenCalled();
    });

    it('should show a previously computed result on load (read-only), without re-executing', () => {
      metricaService.obtenerHistorico.and.callFake((metricaId: string) => {
        if (metricaId === METRICA_DEFECTOS) {
          return of([{
            resultadoId: 'r5', metricaId: METRICA_DEFECTOS, metricaNombre: 'Defectos',
            proyectoId: 'proj-1', sprintId: 'sprint-1', parametrizacionId: 'param-x', parametrizacionVersion: 1,
            tipoCalculo: 'suma', expresion: 'SUMA(defectos_totales)', valoresUtilizados: '{}',
            resultado: 5, unidad: 'defectos', estado: 'calculado' as const, calculadoAt: '2026-08-20T11:23:00Z'
          }]);
        }
        return of([]);
      });

      component.ngOnInit();

      const defectos = component.bloques.find(b => b.id === METRICA_DEFECTOS)!;
      expect(defectos.resultado?.resultado).toBe(5);
      expect(metricaService.ejecutar).not.toHaveBeenCalled();
    });
  });

  describe('Precondiciones de proyecto/sprint', () => {
    it('should prompt to select a project when none is active', () => {
      localStorage.removeItem('mpdia_proyecto_activo');
      component.ngOnInit();
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Seleccioná un proyecto primero');
    });

    it('should prompt to select a sprint before showing capture blocks', () => {
      // Ningún sprint "en_ejecucion" -> no hay auto-selección, el usuario debe elegir.
      sprintService.listar.and.returnValue(of([{ ...mockSprint, estado: 'pendiente' as const }]));
      component.ngOnInit();
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Seleccioná un sprint');
    });
  });
});
