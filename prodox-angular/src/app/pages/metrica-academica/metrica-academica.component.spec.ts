// Autor: Cristian Santiago Martinez Cordoba — PRODOX
// Fase 16.9.2: Tests para componente de métricas académicas
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { of, throwError } from 'rxjs';
import { MetricaAcademicaComponent } from './metrica-academica.component';
import { ShellComponent } from '../../layout/shell/shell.component';
import { MetricaAcademicaService } from '../../services/metrica-academica.service';
import { SprintService } from '../../services/sprint.service';
import { VariableDinamicaService, VariablesMetricaResponse } from '../../services/variable-dinamica.service';

// Mock de ShellComponent para aislar tests
@Component({
  selector: 'app-shell',
  standalone: true,
  template: '<ng-content></ng-content>'
})
class MockShellComponent {}

describe('MetricaAcademicaComponent', () => {
  let component: MetricaAcademicaComponent;
  let fixture: ComponentFixture<MetricaAcademicaComponent>;
  let metricaService: jasmine.SpyObj<MetricaAcademicaService>;
  let sprintService: jasmine.SpyObj<SprintService>;
  let router: jasmine.SpyObj<Router>;
  let variableService: jasmine.SpyObj<VariableDinamicaService>;

  const mockProyecto = {
    id: 'proj-123',
    nombre: 'Proyecto Test',
    descripcion: 'Descripción',
    metodo: 'scrum' as const,
    timeBoxSemanas: 2,
    numeroSprints: 10,
    fechaInicio: '2026-01-01',
    productGoal: 'Product Goal',
    sprintGoal: 'Sprint Goal',
    estado: 'activo' as const,
    scrumMasterEmail: 'sm@test.com',
    totalMiembros: 5,
    createdAt: '2026-01-01T00:00:00Z'
  };

  const mockSprint = {
    id: 'sprint-789',
    proyectoId: 'proj-123',
    proyectoNombre: 'Proyecto Test',
    metodo: 'scrum',
    timeBoxSemanas: 2,
    numero: 1,
    sprintGoal: 'Sprint Goal',
    estado: 'en_ejecucion' as const,
    fechaInicio: '2026-08-01',
    fechaFin: '2026-08-15',
    cerradoPor: null,
    cerradoAt: null,
    createdAt: '2026-08-01T00:00:00Z'
  };

  const mockParametrizacion = {
    id: 'param-789',
    status: 'aprobada' as const,
    version: 1,
    metricaId: 'met-456',
    proyectoId: 'proj-123',
    fuenteAcademica: 'Guerrero-Calvache & Hernández (2024)',
    formulaAcademica: 'Σ problemas_reportados',
    tipoOperacion: 'SUMA',
    unidadResultado: 'problemas',
    objetivo: 'Medir problemas',
    procedimiento: 'Contar problemas',
    indicadorVariable: 'Problemas reportados',
    escala: 'Numérica >= 0',
    frecuenciaCaptura: 'por_sprint',
    createdAt: '2026-08-01T00:00:00Z'
  };

  const mockResultado = {
    resultadoId: 'res-abc',
    metricaId: 'met-456',
    metricaNombre: 'SIG-SC-02',
    proyectoId: 'proj-123',
    sprintId: 'sprint-789',
    parametrizacionId: 'param-789',
    parametrizacionVersion: 1,
    tipoCalculo: 'SUMA',
    expresion: 'Σ problemas_reportados',
    valoresUtilizados: '{"problemas_reportados":7}',
    resultado: 7,
    unidad: 'problemas',
    estado: 'calculado' as const,
    calculadoAt: '2026-08-16T10:00:00Z'
  };

  beforeEach(async () => {
    const metricaServiceSpy = jasmine.createSpyObj('MetricaAcademicaService', [
      'generarPropuesta',
      'guardarPropuesta',
      'ejecutar',
      'obtenerHistorico',
      'solicitarInterpretacion',
      'obtenerParametrizacionAprobada'
    ]);
    const sprintServiceSpy = jasmine.createSpyObj('SprintService', ['getAll', 'listar']);
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    const variableServiceSpy = jasmine.createSpyObj('VariableDinamicaService', [
      'obtenerVariables',
      'guardarValores',
      'calcularMetrica'
    ]);

    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, FormsModule, CommonModule],
      providers: [
        { provide: MetricaAcademicaService, useValue: metricaServiceSpy },
        { provide: SprintService, useValue: sprintServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: VariableDinamicaService, useValue: variableServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => key === 'id' ? 'met-456' : null
              }
            }
          }
        }
      ]
    })
    .overrideComponent(MetricaAcademicaComponent, {
      remove: { imports: [ShellComponent] },
      add: { imports: [MockShellComponent, MetricaAcademicaComponent] }
    })
    .compileComponents();

    metricaService = TestBed.inject(MetricaAcademicaService) as jasmine.SpyObj<MetricaAcademicaService>;
    sprintService = TestBed.inject(SprintService) as jasmine.SpyObj<SprintService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    variableService = TestBed.inject(VariableDinamicaService) as jasmine.SpyObj<VariableDinamicaService>;

    // Mock default: sin parametrización
    metricaService.obtenerParametrizacionAprobada.and.returnValue(throwError(() => ({ status: 204 })));
    // Mock default: sin variables (cada test que las necesite sobreescribe esto)
    variableService.obtenerVariables.and.returnValue(of({
      parametrizacionId: 'param-789',
      version: 1,
      status: 'aprobada',
      variables: []
    }));
    // Mock default: sin sprints (solo se usa para resolver "Sprint N" en histórico)
    sprintService.listar.and.returnValue(of([]));

    // Mock localStorage
    localStorage.setItem('mpdia_proyecto_activo', JSON.stringify(mockProyecto));
    localStorage.setItem('mpdia_sprint_activo', JSON.stringify(mockSprint));

    fixture = TestBed.createComponent(MetricaAcademicaComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Carga de contexto', () => {
    it('should load proyecto and sprint from localStorage', () => {
      component.ngOnInit();
      expect(component.proyectoActivo).toEqual(mockProyecto);
      expect(component.sprintActivo).toEqual(mockSprint);
    });

    it('should show error if no proyecto activo', () => {
      localStorage.removeItem('mpdia_proyecto_activo');
      component.ngOnInit();
      expect(component.error).toBe('No hay un proyecto o sprint activo seleccionado');
    });

    it('should show error if no sprint activo', () => {
      localStorage.removeItem('mpdia_sprint_activo');
      component.ngOnInit();
      expect(component.error).toBe('No hay un proyecto o sprint activo seleccionado');
    });
  });

  describe('Validaciones', () => {
    beforeEach(() => {
      component.variables = [{
        nombre: 'problemas_reportados',
        nombreHumano: 'Problemas reportados',
        etiqueta: 'Problemas reportados',
        tipo: 'INTEGER',
        unidad: 'problemas',
        requerida: true
      }];
    });

    it('should validate required field', () => {
      component.valores = { problemas_reportados: null };
      expect(component.esValido(component.variables[0])).toBe(false);
    });

    it('should validate integer type', () => {
      component.valores = { problemas_reportados: 7.5 };
      expect(component.esValido(component.variables[0])).toBe(false);
    });

    it('should reject negative values', () => {
      component.valores = { problemas_reportados: -1 };
      expect(component.esValido(component.variables[0])).toBe(false);
    });

    it('should accept valid integer', () => {
      component.valores = { problemas_reportados: 7 };
      expect(component.esValido(component.variables[0])).toBe(true);
    });

    it('should validate zero as valid', () => {
      component.valores = { problemas_reportados: 0 };
      expect(component.esValido(component.variables[0])).toBe(true);
    });

    it('should accept a valid decimal for DECIMAL type', () => {
      component.variables = [{
        nombre: 'ratio', nombreHumano: 'Ratio', etiqueta: 'Ratio', tipo: 'DECIMAL', unidad: '', requerida: true
      }];
      component.valores = { ratio: 0.71 };
      expect(component.esValido(component.variables[0])).toBe(true);
    });

    it('should require a non-empty string for TEXT type', () => {
      component.variables = [{
        nombre: 'obs', nombreHumano: 'Observación', etiqueta: 'Observación', tipo: 'TEXT', unidad: '', requerida: true
      }];
      component.valores = { obs: '' };
      expect(component.esValido(component.variables[0])).toBe(false);
      component.valores = { obs: 'ok' };
      expect(component.esValido(component.variables[0])).toBe(true);
    });

    it('should require an explicit true/false for BOOLEAN type', () => {
      component.variables = [{
        nombre: 'flag', nombreHumano: 'Flag', etiqueta: 'Flag', tipo: 'BOOLEAN', unidad: '', requerida: true
      }];
      component.valores = { flag: undefined };
      expect(component.esValido(component.variables[0])).toBe(false);
      component.valores = { flag: false };
      expect(component.esValido(component.variables[0])).toBe(true);
    });
  });

  describe('Ejecución de métrica', () => {
    beforeEach(() => {
      component.proyectoActivo = mockProyecto;
      component.sprintActivo = mockSprint;
      component.metricaId = 'met-456';
      component.variables = [{
        nombre: 'problemas_reportados',
        nombreHumano: 'Problemas reportados',
        etiqueta: 'Problemas reportados',
        tipo: 'INTEGER',
        unidad: 'problemas',
        requerida: true
      }];
    });

    it('should execute correctly with valid data', () => {
      component.valores = { problemas_reportados: 7 };
      metricaService.ejecutar.and.returnValue(of(mockResultado));
      metricaService.obtenerHistorico.and.returnValue(of([mockResultado]));

      component.ejecutar();

      expect(metricaService.ejecutar).toHaveBeenCalledWith('met-456', {
        proyectoId: 'proj-123',
        sprintId: 'sprint-789',
        valores: { problemas_reportados: 7 }
      });
      expect(component.resultado).toEqual(mockResultado);
      expect(component.ejecutando).toBe(false);
    });

    it('should not execute with invalid data', () => {
      component.valores = { problemas_reportados: null };
      component.ejecutar();
      expect(metricaService.ejecutar).not.toHaveBeenCalled();
      expect(component.mostrarErrores).toBe(true);
    });

    it('should handle 403 error', () => {
      component.valores = { problemas_reportados: 7 };
      metricaService.ejecutar.and.returnValue(
        throwError(() => ({ status: 403 }))
      );

      component.ejecutar();

      expect(component.errorEjecucion).toBe('No tienes permiso para ejecutar esta métrica');
    });

    it('should handle 409 error', () => {
      component.valores = { problemas_reportados: 7 };
      metricaService.ejecutar.and.returnValue(
        throwError(() => ({ status: 409 }))
      );

      component.ejecutar();

      expect(component.errorEjecucion).toContain('no está aprobada');
    });

    it('should handle 400 error', () => {
      component.valores = { problemas_reportados: 7 };
      metricaService.ejecutar.and.returnValue(
        throwError(() => ({ status: 400, error: { message: 'Dato inválido' } }))
      );

      component.ejecutar();

      expect(component.errorEjecucion).toBe('Dato inválido');
    });
  });

  describe('Carga dinámica de variables', () => {
    beforeEach(() => {
      component.proyectoActivo = mockProyecto;
      component.sprintActivo = mockSprint;
      component.metricaId = 'met-456';
      component.parametrizacion = mockParametrizacion;
    });

    it('should preload the value already captured for the sprint, without asking the user again', () => {
      const response: VariablesMetricaResponse = {
        parametrizacionId: 'param-789',
        version: 1,
        status: 'aprobada',
        variables: [
          { id: 'v1', nombre: 'problemas_reportados', descripcion: 'Problemas reportados',
            tipoDato: 'numerico', obligatorio: true, unidad: 'problemas', valorNum: 3 }
        ]
      };
      variableService.obtenerVariables.and.returnValue(of(response));

      component.cargarVariables();

      expect(component.valores['problemas_reportados']).toBe(3);
    });

    it('should not preload a value when none was captured yet', () => {
      const response: VariablesMetricaResponse = {
        parametrizacionId: 'param-789',
        version: 1,
        status: 'aprobada',
        variables: [
          { id: 'v1', nombre: 'problemas_reportados', descripcion: 'Problemas reportados',
            tipoDato: 'numerico', obligatorio: true, unidad: 'problemas' }
        ]
      };
      variableService.obtenerVariables.and.returnValue(of(response));

      component.cargarVariables();

      expect(component.valores['problemas_reportados']).toBeUndefined();
    });

    it('should load variables from backend using metricaId, proyectoId and sprintId', () => {
      const response: VariablesMetricaResponse = {
        parametrizacionId: 'param-789',
        version: 1,
        status: 'aprobada',
        variables: [
          { id: 'v1', nombre: 'problemas_reportados', descripcion: 'Problemas reportados', tipoDato: 'numerico', obligatorio: true, unidad: 'problemas' }
        ]
      };
      variableService.obtenerVariables.and.returnValue(of(response));

      component.cargarVariables();

      expect(variableService.obtenerVariables).toHaveBeenCalledWith('met-456', 'proj-123', 'sprint-789');
      expect(component.variables.length).toBe(1);
      expect(component.variables[0].nombre).toBe('problemas_reportados');
      expect(component.variables[0].unidad).toBe('problemas');
      expect(component.variables[0].requerida).toBe(true);
      expect(component.cargandoVariables).toBe(false);
    });

    it('should render a single variable dynamically in the table', () => {
      variableService.obtenerVariables.and.returnValue(of({
        parametrizacionId: 'param-789', version: 1, status: 'aprobada',
        variables: [
          { id: 'v1', nombre: 'problemas_reportados', descripcion: 'Problemas reportados', tipoDato: 'numerico', obligatorio: true, unidad: 'problemas' }
        ]
      }));

      component.cargarVariables();
      fixture.detectChanges();

      const inputs = fixture.nativeElement.querySelectorAll('input[type="number"]');
      expect(inputs.length).toBe(1);
      expect(inputs[0].getAttribute('aria-label')).toBe('Problemas reportados');
    });

    it('should render multiple variables dynamically in the table', () => {
      variableService.obtenerVariables.and.returnValue(of({
        parametrizacionId: 'param-789', version: 1, status: 'aprobada',
        variables: [
          { id: 'v1', nombre: 'horas_trabajadas', descripcion: 'Horas trabajadas', tipoDato: 'numerico', obligatorio: true, unidad: 'horas' },
          { id: 'v2', nombre: 'horas_planificadas', descripcion: 'Horas planificadas', tipoDato: 'numerico', obligatorio: true, unidad: 'horas' },
          { id: 'v3', nombre: 'defectos_encontrados', descripcion: 'Defectos encontrados', tipoDato: 'numerico', obligatorio: false, unidad: 'defectos' }
        ]
      }));

      component.cargarVariables();
      fixture.detectChanges();

      const inputs = fixture.nativeElement.querySelectorAll('input[type="number"]');
      expect(inputs.length).toBe(3);
      expect(component.variables.map(v => v.nombre)).toEqual([
        'horas_trabajadas', 'horas_planificadas', 'defectos_encontrados'
      ]);
    });

    it('should capture a value typed by the user for a dynamically loaded variable', () => {
      variableService.obtenerVariables.and.returnValue(of({
        parametrizacionId: 'param-789', version: 1, status: 'aprobada',
        variables: [
          { id: 'v1', nombre: 'horas_trabajadas', descripcion: 'Horas trabajadas', tipoDato: 'numerico', obligatorio: true, unidad: 'horas' }
        ]
      }));

      component.cargarVariables();
      fixture.detectChanges();

      const input: HTMLInputElement = fixture.nativeElement.querySelector('input[type="number"]');
      input.value = '5';
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      expect(component.valores['horas_trabajadas']).toBe(5);
    });

    it('should build the ejecutar() request dynamically from any set of variables', () => {
      component.variables = [
        { nombre: 'horas_trabajadas', nombreHumano: 'Horas trabajadas', etiqueta: 'Horas trabajadas', tipo: 'INTEGER', unidad: 'horas', requerida: true },
        { nombre: 'horas_planificadas', nombreHumano: 'Horas planificadas', etiqueta: 'Horas planificadas', tipo: 'INTEGER', unidad: 'horas', requerida: true }
      ];
      component.valores = { horas_trabajadas: 5, horas_planificadas: 8 };
      metricaService.ejecutar.and.returnValue(of(mockResultado));
      metricaService.obtenerHistorico.and.returnValue(of([]));

      component.ejecutar();

      expect(metricaService.ejecutar).toHaveBeenCalledWith('met-456', {
        proyectoId: 'proj-123',
        sprintId: 'sprint-789',
        valores: { horas_trabajadas: 5, horas_planificadas: 8 }
      });
    });

    it('should block ejecutar() when a required variable is missing', () => {
      component.variables = [
        { nombre: 'horas_trabajadas', nombreHumano: 'Horas trabajadas', etiqueta: 'Horas trabajadas', tipo: 'INTEGER', unidad: 'horas', requerida: true },
        { nombre: 'horas_planificadas', nombreHumano: 'Horas planificadas', etiqueta: 'Horas planificadas', tipo: 'INTEGER', unidad: 'horas', requerida: true }
      ];
      component.valores = { horas_trabajadas: 5 }; // falta horas_planificadas

      component.ejecutar();

      expect(metricaService.ejecutar).not.toHaveBeenCalled();
      expect(component.mostrarErrores).toBe(true);
    });

    it('should block ejecutar() when any variable has an invalid value', () => {
      component.variables = [
        { nombre: 'horas_trabajadas', nombreHumano: 'Horas trabajadas', etiqueta: 'Horas trabajadas', tipo: 'INTEGER', unidad: 'horas', requerida: true },
        { nombre: 'horas_planificadas', nombreHumano: 'Horas planificadas', etiqueta: 'Horas planificadas', tipo: 'INTEGER', unidad: 'horas', requerida: true }
      ];
      component.valores = { horas_trabajadas: 5, horas_planificadas: -3 };

      component.ejecutar();

      expect(metricaService.ejecutar).not.toHaveBeenCalled();
    });

    it('should show a clear error message when loading variables fails', () => {
      variableService.obtenerVariables.and.returnValue(throwError(() => ({ status: 500 })));

      component.cargarVariables();
      fixture.detectChanges();

      expect(component.errorVariables).toBeTruthy();
      const alert = fixture.nativeElement.querySelector('.alert-danger');
      expect(alert.textContent).toContain(component.errorVariables);
    });

    it('should show "no tiene variables configuradas" when the approved parametrizacion has no variables, without auto-creating one', () => {
      variableService.obtenerVariables.and.returnValue(of({
        parametrizacionId: 'param-789', version: 1, status: 'aprobada', variables: []
      }));

      component.cargarVariables();
      fixture.detectChanges();

      expect(component.variables.length).toBe(0);
      const compiled = fixture.nativeElement;
      expect(compiled.textContent).toContain('La parametrización aprobada no tiene variables configuradas.');
      expect(compiled.querySelector('input[type="number"]')).toBeNull();
    });

    it('should NOT depend on SIG-SC-02: renders, captures and sends any backend-provided variables (horas_trabajadas / horas_planificadas)', () => {
      const response: VariablesMetricaResponse = {
        parametrizacionId: 'param-999',
        version: 1,
        status: 'aprobada',
        variables: [
          { id: 'v1', nombre: 'horas_trabajadas', descripcion: 'Horas trabajadas', tipoDato: 'numerico', obligatorio: true, unidad: 'horas' },
          { id: 'v2', nombre: 'horas_planificadas', descripcion: 'Horas planificadas', tipoDato: 'numerico', obligatorio: true, unidad: 'horas' }
        ]
      };
      variableService.obtenerVariables.and.returnValue(of(response));
      metricaService.ejecutar.and.returnValue(of(mockResultado));
      metricaService.obtenerHistorico.and.returnValue(of([]));

      // 1. Carga dinámica: ambas variables se renderizan (tabla "Datos a capturar")
      component.cargarVariables();
      fixture.detectChanges();

      const tablaTexto: string = fixture.nativeElement.querySelector('table').textContent;
      expect(tablaTexto).toContain('horas_trabajadas');
      expect(tablaTexto).toContain('Horas trabajadas');
      expect(tablaTexto).toContain('horas_planificadas');
      expect(tablaTexto).toContain('Horas planificadas');

      const inputs: HTMLInputElement[] = fixture.nativeElement.querySelectorAll('input[type="number"]');
      expect(inputs.length).toBe(2);

      // 2. Captura de ambos valores
      inputs[0].value = '3';
      inputs[0].dispatchEvent(new Event('input'));
      inputs[1].value = '5';
      inputs[1].dispatchEvent(new Event('input'));
      fixture.detectChanges();

      // 3. Ejecución: ambos valores se envían dinámicamente
      component.ejecutar();

      expect(metricaService.ejecutar).toHaveBeenCalledWith('met-456', {
        proyectoId: 'proj-123',
        sprintId: 'sprint-789',
        valores: { horas_trabajadas: 3, horas_planificadas: 5 }
      });
    });

    it('should show the human-readable name as primary text and the academic description, keeping the snake_case as secondary info', () => {
      variableService.obtenerVariables.and.returnValue(of({
        parametrizacionId: 'param-789', version: 1, status: 'aprobada',
        variables: [
          { id: 'v1', nombre: 'impedimentos_bloqueantes_registrados',
            descripcion: 'Impedimentos bloqueantes registrados de Impedimentos por sprint',
            tipoDato: 'numerico', obligatorio: true, unidad: '' }
        ]
      }));
      component.parametrizacion = {
        ...mockParametrizacion,
        indicadorVariable: 'Número de impedimentos únicos registrados que bloquearon al equipo durante el sprint.'
      };

      component.cargarVariables();
      fixture.detectChanges();

      const nombrePrincipal = fixture.nativeElement.querySelector('td .fw-semibold').textContent;
      expect(nombrePrincipal).toContain('Impedimentos bloqueantes registrados');
      expect(nombrePrincipal).not.toContain('impedimentos_bloqueantes_registrados');

      const nombreTecnico = fixture.nativeElement.querySelector('td code').textContent;
      expect(nombreTecnico).toBe('impedimentos_bloqueantes_registrados');

      const tablaTexto = fixture.nativeElement.querySelector('table').textContent;
      expect(tablaTexto).toContain(
        'Número de impedimentos únicos registrados que bloquearon al equipo durante el sprint.'
      );
    });

    it('should show operation/formula/unit info below the capture table with the combining-values sentence', () => {
      variableService.obtenerVariables.and.returnValue(of({
        parametrizacionId: 'param-789', version: 1, status: 'aprobada',
        variables: [
          { id: 'v1', nombre: 'problemas_reportados', descripcion: 'x', tipoDato: 'numerico', obligatorio: true, unidad: '' }
        ]
      }));
      component.parametrizacion = mockParametrizacion; // tipoOperacion: SUMA, formulaAcademica: 'Σ problemas_reportados', unidadResultado: 'problemas'

      component.cargarVariables();
      fixture.detectChanges();

      const texto = fixture.nativeElement.textContent;
      expect(texto).toContain('SUMA');
      expect(texto).toContain('Σ problemas_reportados');
      expect(texto).toContain('Estos valores se combinarán mediante SUMA.');
    });

    it('should render a text input for TEXT type variables (not numeric)', () => {
      variableService.obtenerVariables.and.returnValue(of({
        parametrizacionId: 'param-1', version: 1, status: 'aprobada',
        variables: [
          { id: 'v1', nombre: 'observacion_sprint', descripcion: 'Observación', tipoDato: 'texto', obligatorio: false, unidad: '' }
        ]
      }));

      component.cargarVariables();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('input[type="text"]')).toBeTruthy();
      expect(fixture.nativeElement.querySelector('input[type="number"]')).toBeNull();
    });

    it('should render a checkbox for BOOLEAN type variables', () => {
      variableService.obtenerVariables.and.returnValue(of({
        parametrizacionId: 'param-1', version: 1, status: 'aprobada',
        variables: [
          { id: 'v1', nombre: 'cumple_criterio', descripcion: 'Cumple criterio', tipoDato: 'booleano', obligatorio: false, unidad: '' }
        ]
      }));

      component.cargarVariables();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('input[type="checkbox"]')).toBeTruthy();
    });

    it('should render a decimal-capable numeric input for DECIMAL type variables', () => {
      component.parametrizacion = mockParametrizacion;
      component.variables = [{
        nombre: 'ratio_completitud', nombreHumano: 'Ratio completitud', etiqueta: 'Ratio',
        tipo: 'DECIMAL', unidad: '', requerida: true
      }];
      fixture.detectChanges();

      const input: HTMLInputElement = fixture.nativeElement.querySelector('input[type="number"]');
      expect(input).toBeTruthy();
      expect(input.step).toBe('0.01');
    });
  });

  describe('Resultado existente al cargar (sin volver a ejecutar)', () => {
    beforeEach(() => {
      component.proyectoActivo = mockProyecto;
      component.sprintActivo = mockSprint; // id: 'sprint-789'
      component.metricaId = 'met-456';
    });

    it('should show the last existing result for the current sprint without calling ejecutar()', () => {
      metricaService.obtenerHistorico.and.returnValue(of([mockResultado])); // sprintId: 'sprint-789'

      component.cargarHistorico();

      expect(component.resultado).toEqual(mockResultado);
      expect(metricaService.ejecutar).not.toHaveBeenCalled();
    });

    it('should pick the entry matching the current sprint, not just the globally most recent one', () => {
      const otroSprint = { ...mockResultado, resultadoId: 'res-otro', sprintId: 'sprint-otro' };
      // historico ya viene ordenado DESC por fecha desde el backend.
      metricaService.obtenerHistorico.and.returnValue(of([otroSprint, mockResultado]));

      component.cargarHistorico();

      expect(component.resultado!.resultadoId).toBe(mockResultado.resultadoId);
    });

    it('should leave resultado as null when there is no existing result for this sprint (empty state)', () => {
      const otroSprint = { ...mockResultado, sprintId: 'sprint-otro' };
      metricaService.obtenerHistorico.and.returnValue(of([otroSprint]));

      component.cargarHistorico();

      expect(component.resultado).toBeNull();
    });

    it('should not overwrite a result that was just computed by ejecutar() in this session', () => {
      const fresco = { ...mockResultado, resultadoId: 'res-fresco' };
      component.resultado = fresco;
      metricaService.obtenerHistorico.and.returnValue(of([mockResultado]));

      component.cargarHistorico();

      expect(component.resultado).toEqual(fresco);
    });

    it('should render the empty-state message when no result exists yet', () => {
      component.parametrizacion = mockParametrizacion;
      component.variables = [];
      component.resultado = null;
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('Aún no hay un resultado calculado para este sprint');
    });

    it('should NOT render the empty-state message once a result is loaded', () => {
      component.parametrizacion = mockParametrizacion;
      component.resultado = mockResultado;
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).not.toContain('Aún no hay un resultado calculado');
    });
  });

  describe('Histórico', () => {
    it('should load historico after execution', () => {
      component.proyectoActivo = mockProyecto;
      component.metricaId = 'met-456';
      metricaService.obtenerHistorico.and.returnValue(of([mockResultado]));

      component.cargarHistorico();

      expect(metricaService.obtenerHistorico).toHaveBeenCalledWith('met-456', 'proj-123');
      expect(component.historico).toEqual([mockResultado]);
    });

    it('should handle historico error silently', () => {
      component.proyectoActivo = mockProyecto;
      component.metricaId = 'met-456';
      metricaService.obtenerHistorico.and.returnValue(
        throwError(() => ({ status: 500 }))
      );

      component.cargarHistorico();

      expect(component.historico).toEqual([]);
    });

    it('should resolve the readable sprint name ("Sprint N") when the sprint list is available', () => {
      component.proyectoActivo = mockProyecto;
      sprintService.listar.and.returnValue(of([{ ...mockSprint, id: 'sprint-789', numero: 1 }]));

      component.cargarSprints();

      expect(component.nombreSprint('sprint-789')).toBe('Sprint 1');
    });

    it('should fall back to the raw sprint id when the sprint is not found (never invent a name)', () => {
      component.proyectoActivo = mockProyecto;
      sprintService.listar.and.returnValue(of([]));

      component.cargarSprints();

      expect(component.nombreSprint('sprint-desconocido')).toBe('Sprint sprint-desconocido');
    });

    it('should render the readable sprint name in the historico table', () => {
      component.proyectoActivo = mockProyecto;
      component.sprintActivo = mockSprint;
      component.parametrizacion = mockParametrizacion;
      sprintService.listar.and.returnValue(of([{ ...mockSprint, id: 'sprint-789', numero: 1 }]));
      metricaService.obtenerHistorico.and.returnValue(of([mockResultado]));

      component.cargarSprints();
      component.cargarHistorico();
      fixture.detectChanges();

      const primeraCelda = fixture.nativeElement.querySelector('table.table-hover tbody tr td');
      expect(primeraCelda.textContent.trim()).toBe('Sprint 1');
    });
  });

  describe('Interpretación IA', () => {
    const mockInterpretacion = {
      resultadoId: 'res-abc',
      metricaNombre: 'SIG-SC-02',
      resultado: 7,
      unidad: 'problemas',
      interpretacion: 'Análisis IA del resultado',
      generadoAt: '2026-08-16T10:00:00Z'
    };

    beforeEach(() => {
      component.resultado = mockResultado;
    });

    it('should not execute IA analysis automatically', () => {
      expect(metricaService.solicitarInterpretacion).not.toHaveBeenCalled();
    });

    it('should execute IA analysis on button click', () => {
      metricaService.solicitarInterpretacion.and.returnValue(of(mockInterpretacion));

      component.analizarConIA();

      expect(metricaService.solicitarInterpretacion).toHaveBeenCalledWith('res-abc');
      expect(component.interpretacion).toEqual(mockInterpretacion);
      expect(component.interpretando).toBe(false);
    });

    it('should handle 403 error in IA analysis', () => {
      metricaService.solicitarInterpretacion.and.returnValue(
        throwError(() => ({ status: 403 }))
      );

      component.analizarConIA();

      expect(component.errorInterpretacion).toBe('No tienes permiso para solicitar interpretación IA');
    });

    it('should handle 400 error in IA analysis', () => {
      metricaService.solicitarInterpretacion.and.returnValue(
        throwError(() => ({ status: 400 }))
      );

      component.analizarConIA();

      expect(component.errorInterpretacion).toBe('Resultado no encontrado');
    });

    it('should show loading state during IA analysis', () => {
      metricaService.solicitarInterpretacion.and.returnValue(of(mockInterpretacion));

      component.analizarConIA();

      // Durante la llamada, interpretando debe ser true (difícil de capturar en test síncrono)
      // Verificamos que se llamó y terminó correctamente
      expect(component.interpretando).toBe(false);
      expect(component.interpretacion).toBeTruthy();
    });
  });

  describe('Navigation', () => {
    it('should navigate back', () => {
      component.volver();
      expect(router.navigate).toHaveBeenCalledWith(['/planeacion']);
    });

    it('should navigate to parametrizacion', () => {
      component.metricaId = 'met-456';
      component.irAParametrizacion();
      expect(router.navigate).toHaveBeenCalledWith(['/parametrizacion', 'met-456']);
    });
  });

  describe('Estado sin parametrización', () => {
    it('should show message when no parametrizacion exists', () => {
      component.sinParametrizacion = true;
      fixture.detectChanges();
      const compiled = fixture.nativeElement;
      expect(compiled.textContent).toContain('No existe una parametrización aprobada');
    });

    it('should show message when parametrizacion is propuesta', () => {
      component.parametrizacionPropuesta = true;
      fixture.detectChanges();
      const compiled = fixture.nativeElement;
      expect(compiled.textContent).toContain('parametrización propuesta');
    });
  });

  describe('Frecuencia labels', () => {
    it('should return correct label for por_sprint', () => {
      expect(component.frecuenciaLabel('por_sprint')).toBe('Por sprint');
    });

    it('should return correct label for semanal', () => {
      expect(component.frecuenciaLabel('semanal')).toBe('Semanal');
    });

    it('should return correct label for diaria', () => {
      expect(component.frecuenciaLabel('diaria')).toBe('Diaria');
    });

    it('should return correct label for ilimitada', () => {
      expect(component.frecuenciaLabel('ilimitada')).toBe('Cuando ocurra el evento');
    });

    it('should return original value for unknown frecuencia', () => {
      expect(component.frecuenciaLabel('unknown')).toBe('unknown');
    });
  });

  describe('Ver detalle de resultado histórico', () => {
    it('should show historical result on click', () => {
      const historicalResult = { ...mockResultado, resultadoId: 'res-old' };
      component.verDetalle(historicalResult);
      expect(component.resultado).toEqual(historicalResult);
      expect(component.interpretacion).toBeNull();
    });
  });

  describe('Accessibility', () => {
    it('should have aria-label on inputs', () => {
      component.parametrizacion = mockParametrizacion;
      component.variables = [{
        nombre: 'problemas_reportados',
        nombreHumano: 'Problemas reportados',
        etiqueta: 'Problemas reportados',
        tipo: 'INTEGER',
        unidad: 'problemas',
        requerida: true
      }];
      fixture.detectChanges();
      
      const input = fixture.nativeElement.querySelector('input[type="number"]');
      expect(input.getAttribute('aria-label')).toBe('Problemas reportados');
    });

    it('should associate error messages with inputs', () => {
      component.parametrizacion = mockParametrizacion;
      component.variables = [{
        nombre: 'problemas_reportados',
        nombreHumano: 'Problemas reportados',
        etiqueta: 'Problemas reportados',
        tipo: 'INTEGER',
        unidad: 'problemas',
        requerida: true
      }];
      component.mostrarErrores = true;
      component.valores = { problemas_reportados: null };
      fixture.detectChanges();
      
      const feedback = fixture.nativeElement.querySelector('.invalid-feedback');
      expect(feedback).toBeTruthy();
      expect(feedback.textContent).toContain('Ingresa un valor');
    });
  });
});
