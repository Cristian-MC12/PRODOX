// Autor: Cristian Santiago Martinez Cordoba — MPDIA
// FASE 16: Tests de EjecucionComponent reescrito — métricas dinámicas
// (fuente de verdad = métricas aprobadas del proyecto), captura por
// variable con fecha explícita, gráfica alimentada por datos reales.
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
import { PlaneacionService } from '../../services/planeacion.service';
import { MetricaAcademicaService } from '../../services/metrica-academica.service';
import { VariableDinamicaService, VariablesMetricaResponse } from '../../services/variable-dinamica.service';
import { EvaluacionService } from '../../services/evaluacion.service';
import { ProyectoMetricaDto } from '../../models/planeacion.model';
import { MetricaEvaluacionDetalleDto } from '../../models/evaluacion-detalle.model';

@Component({ selector: 'app-shell', standalone: true, template: '<ng-content></ng-content>' })
class MockShellComponent {}

const METRICA_DEFECTOS = 'ec0d74fe-0bf4-4970-af89-dcaa0736c8ed'; // una de las 5 oficiales
const METRICA_IA = '1b5e6182-f6bc-4292-8ac9-6702ffe14f19';       // una creada con IA (FASE 15)

function metrica(id: string, nombre: string, aprobada: boolean, codigo = 'X'): ProyectoMetricaDto {
  return {
    metricaId: id, codigo, nombre, descripcion: null, categoria: 'Significado', factor: null,
    seleccionada: true, seleccionadaAt: '2026-08-21T00:00:00Z',
    aprobada, aprobadaPor: aprobada ? 'sm@test.com' : null, aprobadaAt: aprobada ? '2026-08-21T00:00:00Z' : null,
    tieneVariable: aprobada
  };
}

describe('EjecucionComponent (FASE 16 — métricas dinámicas)', () => {
  let component: EjecucionComponent;
  let fixture: ComponentFixture<EjecucionComponent>;
  let planeacionService: jasmine.SpyObj<PlaneacionService>;
  let metricaAcademicaService: jasmine.SpyObj<MetricaAcademicaService>;
  let variableService: jasmine.SpyObj<VariableDinamicaService>;
  let evaluacionService: jasmine.SpyObj<EvaluacionService>;
  let sprintService: jasmine.SpyObj<SprintService>;

  const mockProyecto = {
    id: 'proj-1', nombre: 'Sandbox', descripcion: null, metodo: 'scrum' as const,
    timeBoxSemanas: 1, numeroSprints: 1, fechaInicio: '2026-08-21', productGoal: 'x',
    sprintGoal: '', estado: 'activo' as const, scrumMasterEmail: 'sm@test.com', totalMiembros: 1,
    createdAt: '2026-08-21T00:00:00Z'
  };

  const mockSprint = {
    id: 'sprint-1', proyectoId: 'proj-1', proyectoNombre: 'Sandbox', metodo: 'scrum',
    timeBoxSemanas: 1, numero: 1, sprintGoal: 'Sprint 1', estado: 'en_ejecucion' as const,
    fechaInicio: '2026-08-21', fechaFin: '2026-08-27', cerradoPor: null, cerradoAt: null,
    createdAt: '2026-08-21T00:00:00Z'
  };

  // Revisión de Ejecución — navegación entre sprints: 2-5 son 'pendiente'
  // (todavía no arrancaron), igual que crearía SprintService.crearSprintsIniciales()
  // para cualquier proyecto real recién creado.
  function sprintFuturo(numero: number, fechaInicio: string, fechaFin: string) {
    return {
      id: `sprint-${numero}`, proyectoId: 'proj-1', proyectoNombre: 'Sandbox', metodo: 'scrum',
      timeBoxSemanas: 1, numero, sprintGoal: `Sprint ${numero}`, estado: 'pendiente' as const,
      fechaInicio, fechaFin, cerradoPor: null, cerradoAt: null, createdAt: '2026-08-21T00:00:00Z'
    };
  }
  const mockSprint2 = sprintFuturo(2, '2026-08-28', '2026-09-03');
  const mockSprint3 = sprintFuturo(3, '2026-09-04', '2026-09-10');
  const mockSprint4 = sprintFuturo(4, '2026-09-11', '2026-09-17');
  const mockSprint5 = sprintFuturo(5, '2026-09-18', '2026-09-24');
  const todosLosSprints = [mockSprint, mockSprint2, mockSprint3, mockSprint4, mockSprint5];

  function param(metricaId: string) {
    return {
      id: 'param-' + metricaId, status: 'aprobada' as const, version: 1, metricaId, proyectoId: 'proj-1',
      fuenteAcademica: 'x', formulaAcademica: 'x', tipoOperacion: 'PROMEDIO', unidadResultado: 'pts',
      objetivo: '', procedimiento: '', indicadorVariable: '', escala: '', frecuenciaCaptura: 'por_sprint',
      createdAt: '2026-08-21T00:00:00Z'
    };
  }

  function variables(metricaId: string, nombres: string[]): VariablesMetricaResponse {
    return {
      parametrizacionId: 'param-' + metricaId, version: 1, status: 'aprobada',
      variables: nombres.map((n, i) => ({
        id: 'v-' + metricaId + '-' + i, nombre: n, descripcion: n, tipoDato: 'numerico',
        obligatorio: true, unidad: 'pts', frecuenciaCaptura: 'por_sprint'
      }))
    };
  }

  function detalleVacio(): MetricaEvaluacionDetalleDto[] { return []; }

  beforeEach(async () => {
    const planeacionSpy = jasmine.createSpyObj('PlaneacionService', ['listarMetricas']);
    const metricaAcademicaSpy = jasmine.createSpyObj('MetricaAcademicaService', ['obtenerParametrizacionAprobada']);
    const variableSpy = jasmine.createSpyObj('VariableDinamicaService', ['obtenerVariables', 'guardarValores']);
    const evaluacionSpy = jasmine.createSpyObj('EvaluacionService', ['detalle']);
    const sprintServiceSpy = jasmine.createSpyObj('SprintService', ['listar']);
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['logout']);
    (authServiceSpy as any).currentUser = signal({ userId: 'u1', email: 'sm@test.com', role: 'scrum_master', token: 't' });

    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, FormsModule, CommonModule],
      providers: [
        { provide: PlaneacionService, useValue: planeacionSpy },
        { provide: MetricaAcademicaService, useValue: metricaAcademicaSpy },
        { provide: VariableDinamicaService, useValue: variableSpy },
        { provide: EvaluacionService, useValue: evaluacionSpy },
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

    planeacionService = TestBed.inject(PlaneacionService) as jasmine.SpyObj<PlaneacionService>;
    metricaAcademicaService = TestBed.inject(MetricaAcademicaService) as jasmine.SpyObj<MetricaAcademicaService>;
    variableService = TestBed.inject(VariableDinamicaService) as jasmine.SpyObj<VariableDinamicaService>;
    evaluacionService = TestBed.inject(EvaluacionService) as jasmine.SpyObj<EvaluacionService>;
    sprintService = TestBed.inject(SprintService) as jasmine.SpyObj<SprintService>;

    sprintService.listar.and.returnValue(of([mockSprint]));
    evaluacionService.detalle.and.returnValue(of(detalleVacio()));
    metricaAcademicaService.obtenerParametrizacionAprobada.and.callFake((id: string) => of(param(id) as any));

    localStorage.setItem('mpdia_proyecto_activo', JSON.stringify(mockProyecto));

    fixture = TestBed.createComponent(EjecucionComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => localStorage.clear());

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  // 1. Fuente de verdad dinámica: ninguna lista hardcodeada.
  it('carga las métricas desde PlaneacionService.listarMetricas() del proyecto activo, sin ningún array fijo', () => {
    planeacionService.listarMetricas.and.returnValue(of([
      metrica(METRICA_DEFECTOS, 'Defectos', true),
      metrica('otra-no-aprobada', 'Sin aprobar', false)
    ]));
    variableService.obtenerVariables.and.returnValue(of(variables(METRICA_DEFECTOS, ['defectos_totales'])));

    component.ngOnInit();

    expect(planeacionService.listarMetricas).toHaveBeenCalledWith('proj-1');
    expect(component.metricas.length).toBe(1);
    expect(component.metricas[0].metricaId).toBe(METRICA_DEFECTOS);
  });

  // 2. Solo aparecen las métricas con aprobada:true.
  it('una métrica no aprobada NO aparece como ejecutable', () => {
    planeacionService.listarMetricas.and.returnValue(of([
      metrica(METRICA_DEFECTOS, 'Defectos', true),
      metrica(METRICA_IA, 'Estado de ánimo del equipo', false, 'IA-001')
    ]));
    variableService.obtenerVariables.and.returnValue(of(variables(METRICA_DEFECTOS, ['defectos_totales'])));

    component.ngOnInit();

    expect(component.metricas.some(m => m.metricaId === METRICA_IA)).toBe(false);
  });

  // 3. Una métrica creada con IA, una vez aprobada, aparece exactamente igual
  // que cualquier otra — sin ninguna condición especial por código/UUID.
  it('una métrica creada con IA y aprobada aparece igual que cualquier otra métrica aprobada', () => {
    planeacionService.listarMetricas.and.returnValue(of([
      metrica(METRICA_IA, 'Estado de ánimo del equipo', true, 'IA-001')
    ]));
    variableService.obtenerVariables.and.returnValue(of(variables(METRICA_IA, ['animo_promedio'])));

    component.ngOnInit();

    expect(component.metricas.length).toBe(1);
    expect(component.metricas[0].metricaId).toBe(METRICA_IA);
    expect(component.metricas[0].variables[0].nombre).toBe('Animo promedio');
  });

  // 4. La fecha de captura es visible, editable y por defecto es hoy.
  it('cada variable trae una fecha de captura editable, con valor por defecto', () => {
    planeacionService.listarMetricas.and.returnValue(of([metrica(METRICA_DEFECTOS, 'Defectos', true)]));
    variableService.obtenerVariables.and.returnValue(of(variables(METRICA_DEFECTOS, ['defectos_totales'])));

    component.ngOnInit();

    const variable = component.metricas[0].variables[0];
    expect(variable.fecha).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  // 5. Registrar valor envía la fecha explícita (convertida a instante ISO), no "ahora" implícito.
  it('registrarValor() envía fechaCaptura explícita al backend, sin depender de la fecha del servidor', () => {
    planeacionService.listarMetricas.and.returnValue(of([metrica(METRICA_DEFECTOS, 'Defectos', true)]));
    variableService.obtenerVariables.and.returnValue(of(variables(METRICA_DEFECTOS, ['defectos_totales'])));
    variableService.guardarValores.and.returnValue(of(undefined));

    component.ngOnInit();

    const m = component.metricas[0];
    const v = m.variables[0];
    v.fecha = '2026-08-22';
    v.valorNum = 8;

    component.registrarValor(m, v);

    expect(variableService.guardarValores).toHaveBeenCalledWith(METRICA_DEFECTOS, jasmine.objectContaining({
      proyectoId: 'proj-1',
      sprintId: 'sprint-1',
      valores: [jasmine.objectContaining({
        variableId: v.variableId,
        valorNum: 8,
        fechaCaptura: '2026-08-22T00:00:00Z'
      })]
    }));
  });

  // 6. Tras registrar, la gráfica se reconstruye con datos reales (no simulados).
  it('tras registrar un valor, recarga los puntos reales desde EvaluacionService.detalle()', () => {
    planeacionService.listarMetricas.and.returnValue(of([metrica(METRICA_DEFECTOS, 'Defectos', true)]));
    variableService.obtenerVariables.and.returnValue(of(variables(METRICA_DEFECTOS, ['defectos_totales'])));
    variableService.guardarValores.and.returnValue(of(undefined));

    component.ngOnInit();
    const m = component.metricas[0];
    const v = m.variables[0];

    evaluacionService.detalle.and.returnValue(of([{
      variableId: v.variableId, variableNombre: v.nombre, categoria: 'Significado', tipoAlcance: 'grupal',
      frecuenciaCaptura: 'por_sprint', formulaTexto: null,
      registros: [
        { id: 'r1', valor: 7, registradoAt: '2026-08-21T00:00:00Z', sprintId: 'sprint-1', sprintNumero: 1, userId: 'sm@test.com' },
        { id: 'r2', valor: 8, registradoAt: '2026-08-22T00:00:00Z', sprintId: 'sprint-1', sprintNumero: 1, userId: 'sm@test.com' }
      ],
      estadisticas: {} as any, porSprint: []
    }]));

    v.valorNum = 8;
    v.fecha = '2026-08-22';
    component.registrarValor(m, v);

    expect(v.puntos.length).toBe(2);
    expect(v.puntos.map(p => p.valor)).toEqual([7, 8]);
  });

  // 7. Sin valor cargado, no llama al backend.
  it('registrarValor() no llama al backend si falta el valor numérico', () => {
    planeacionService.listarMetricas.and.returnValue(of([metrica(METRICA_DEFECTOS, 'Defectos', true)]));
    variableService.obtenerVariables.and.returnValue(of(variables(METRICA_DEFECTOS, ['defectos_totales'])));

    component.ngOnInit();
    const m = component.metricas[0];
    const v = m.variables[0];
    v.valorNum = null;

    component.registrarValor(m, v);

    expect(variableService.guardarValores).not.toHaveBeenCalled();
    expect(v.error).toContain('Ingresá un valor');
  });

  // 8. Error del backend se muestra, no rompe la interfaz.
  it('si falla el registro, muestra el error y no marca "Valor registrado"', () => {
    planeacionService.listarMetricas.and.returnValue(of([metrica(METRICA_DEFECTOS, 'Defectos', true)]));
    variableService.obtenerVariables.and.returnValue(of(variables(METRICA_DEFECTOS, ['defectos_totales'])));
    variableService.guardarValores.and.returnValue(throwError(() => ({ status: 500 })));

    component.ngOnInit();
    const m = component.metricas[0];
    const v = m.variables[0];
    v.valorNum = 5;

    component.registrarValor(m, v);

    expect(v.error).toContain('No se pudo registrar');
    expect(v.ultimoMensaje).toBe('');
  });

  // 9. Sin ninguna métrica aprobada, no se muestra ningún bloque.
  it('sin métricas aprobadas, no se renderiza ningún bloque de captura', () => {
    planeacionService.listarMetricas.and.returnValue(of([metrica(METRICA_DEFECTOS, 'Defectos', false)]));

    component.ngOnInit();
    fixture.detectChanges();

    expect(component.metricas.length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('Todavía no hay ninguna métrica aprobada');
  });

  // ══════════════════════════════════════════════════════════════════════
  // Validación de rango (escala) — revisión de Ejecución.
  // ══════════════════════════════════════════════════════════════════════

  function variablesConEscala(metricaId: string, min: number, max: number): VariablesMetricaResponse {
    return {
      parametrizacionId: 'param-' + metricaId, version: 1, status: 'aprobada',
      variables: [{
        id: 'v-' + metricaId + '-0', nombre: 'clima_emocional', descripcion: 'Clima emocional del equipo',
        tipoDato: 'numerico', obligatorio: true, unidad: undefined, escalaMin: min, escalaMax: max,
        frecuenciaCaptura: 'por_sprint'
      }]
    };
  }

  it('10. un valor fuera del rango de la escala (7 en una escala 1-5) se rechaza en el frontend sin llamar al backend', () => {
    planeacionService.listarMetricas.and.returnValue(of([metrica(METRICA_DEFECTOS, 'Clima', true)]));
    variableService.obtenerVariables.and.returnValue(of(variablesConEscala(METRICA_DEFECTOS, 1, 5)));

    component.ngOnInit();
    const m = component.metricas[0];
    const v = m.variables[0];
    v.fecha = '2026-08-22';
    v.valorNum = 7;

    component.registrarValor(m, v);

    expect(variableService.guardarValores).not.toHaveBeenCalled();
    expect(v.error).toContain('entre 1 y 5');
  });

  it('11. un valor dentro del rango de la escala sí se registra normalmente', () => {
    planeacionService.listarMetricas.and.returnValue(of([metrica(METRICA_DEFECTOS, 'Clima', true)]));
    variableService.obtenerVariables.and.returnValue(of(variablesConEscala(METRICA_DEFECTOS, 1, 5)));
    variableService.guardarValores.and.returnValue(of(undefined));

    component.ngOnInit();
    const m = component.metricas[0];
    const v = m.variables[0];
    v.fecha = '2026-08-22';
    v.valorNum = 4;

    component.registrarValor(m, v);

    expect(variableService.guardarValores).toHaveBeenCalled();
    expect(v.error).toBe('');
  });

  it('12. una escala pequeña y discreta (1-5) usa el selector de botones, no un input numérico libre', () => {
    planeacionService.listarMetricas.and.returnValue(of([metrica(METRICA_DEFECTOS, 'Clima', true)]));
    variableService.obtenerVariables.and.returnValue(of(variablesConEscala(METRICA_DEFECTOS, 1, 5)));

    component.ngOnInit();
    const v = component.metricas[0].variables[0];

    expect(component.esEscalaDiscretaPequena(v)).toBe(true);
    expect(component.opcionesEscala(v)).toEqual([1, 2, 3, 4, 5]);
  });

  it('una escala amplia (0-100) NO usa el selector de botones', () => {
    planeacionService.listarMetricas.and.returnValue(of([metrica(METRICA_DEFECTOS, 'Amplia', true)]));
    variableService.obtenerVariables.and.returnValue(of(variablesConEscala(METRICA_DEFECTOS, 0, 100)));

    component.ngOnInit();
    const v = component.metricas[0].variables[0];

    expect(component.esEscalaDiscretaPequena(v)).toBe(false);
  });

  // ══════════════════════════════════════════════════════════════════════
  // Estado de captura y edición — revisión de Ejecución.
  // ══════════════════════════════════════════════════════════════════════

  it('13. por_sprint SIN valor registrado todavía: arranca en modo edición (formulario visible)', () => {
    planeacionService.listarMetricas.and.returnValue(of([metrica(METRICA_DEFECTOS, 'Defectos', true)]));
    variableService.obtenerVariables.and.returnValue(of(variables(METRICA_DEFECTOS, ['defectos_totales'])));
    evaluacionService.detalle.and.returnValue(of(detalleVacio()));

    component.ngOnInit();
    const v = component.metricas[0].variables[0];

    expect(v.capturas.length).toBe(0);
    expect(v.editando).toBe(true);
  });

  it('13b. por_sprint CON valor ya registrado: arranca colapsado (resumen de solo lectura)', () => {
    planeacionService.listarMetricas.and.returnValue(of([metrica(METRICA_DEFECTOS, 'Defectos', true)]));
    variableService.obtenerVariables.and.returnValue(of(variables(METRICA_DEFECTOS, ['defectos_totales'])));

    // El registro ya existe ANTES de ngOnInit — simula "ya se capturó el sprint".
    planeacionService.listarMetricas.and.returnValue(of([metrica(METRICA_DEFECTOS, 'Defectos', true)]));
    const variableId = 'v-' + METRICA_DEFECTOS + '-0';
    evaluacionService.detalle.and.returnValue(of([{
      variableId, variableNombre: 'defectos_totales', categoria: 'Significado', tipoAlcance: 'grupal',
      frecuenciaCaptura: 'por_sprint', formulaTexto: null,
      registros: [
        { id: 'r1', valor: 6, registradoAt: '2026-08-22T00:00:00Z', sprintId: 'sprint-1', sprintNumero: 1, userId: 'sm@test.com' }
      ],
      estadisticas: {} as any, porSprint: []
    }]));

    component.ngOnInit();
    const v = component.metricas[0].variables[0];

    expect(v.capturas.length).toBe(1);
    expect(v.editando).toBe(false);
    expect(v.fecha).toBe('2026-08-22');
  });

  it('14. editarValorExistente() carga la captura en el formulario y activa el modo edición, sin crear una fila nueva', () => {
    planeacionService.listarMetricas.and.returnValue(of([metrica(METRICA_DEFECTOS, 'Defectos', true)]));
    variableService.obtenerVariables.and.returnValue(of(variables(METRICA_DEFECTOS, ['defectos_totales'])));

    component.ngOnInit();
    const v = component.metricas[0].variables[0];
    const registro = { id: 'r1', valor: 9, registradoAt: '2026-08-23T00:00:00Z', sprintId: 'sprint-1', sprintNumero: 1, userId: 'sm@test.com' };

    component.editarValorExistente(v, registro);

    expect(v.fecha).toBe('2026-08-23');
    expect(v.valorNum).toBe(9);
    expect(v.editando).toBe(true);
    expect(variableService.guardarValores).not.toHaveBeenCalled(); // cargar al formulario no persiste nada
  });

  it('cancelarEdicion() vuelve al resumen de solo lectura sin guardar cambios', () => {
    planeacionService.listarMetricas.and.returnValue(of([metrica(METRICA_DEFECTOS, 'Defectos', true)]));
    variableService.obtenerVariables.and.returnValue(of(variables(METRICA_DEFECTOS, ['defectos_totales'])));

    component.ngOnInit();
    const v = component.metricas[0].variables[0];
    v.editando = true;

    component.cancelarEdicion(v);

    expect(v.editando).toBe(false);
    expect(variableService.guardarValores).not.toHaveBeenCalled();
  });

  // ══════════════════════════════════════════════════════════════════════
  // Aislamiento por sprint en la gráfica — también tras registrar un valor
  // (bug real encontrado en esta revisión: el refresco post-guardado no
  // aplicaba el filtro de sprint que sí aplica la carga inicial).
  // ══════════════════════════════════════════════════════════════════════

  it('15. tras registrar un valor, la gráfica NO mezcla registros de otros sprints', () => {
    planeacionService.listarMetricas.and.returnValue(of([metrica(METRICA_DEFECTOS, 'Defectos', true)]));
    variableService.obtenerVariables.and.returnValue(of(variables(METRICA_DEFECTOS, ['defectos_totales'])));
    variableService.guardarValores.and.returnValue(of(undefined));

    component.ngOnInit();
    const m = component.metricas[0];
    const v = m.variables[0];

    evaluacionService.detalle.and.returnValue(of([{
      variableId: v.variableId, variableNombre: v.nombre, categoria: 'Significado', tipoAlcance: 'grupal',
      frecuenciaCaptura: 'por_sprint', formulaTexto: null,
      registros: [
        { id: 'r-sprint1', valor: 8, registradoAt: '2026-08-22T00:00:00Z', sprintId: 'sprint-1', sprintNumero: 1, userId: 'sm@test.com' },
        { id: 'r-sprint2', valor: 3, registradoAt: '2026-08-29T00:00:00Z', sprintId: 'sprint-2', sprintNumero: 2, userId: 'sm@test.com' }
      ],
      estadisticas: {} as any, porSprint: []
    }]));

    v.valorNum = 8;
    v.fecha = '2026-08-22';
    component.registrarValor(m, v);

    // Sprint actual es el Sprint 1 (mockSprint.numero === 1): el registro del
    // Sprint 2 nunca debe aparecer en la gráfica ni en las capturas.
    expect(v.puntos.length).toBe(1);
    expect(v.puntos[0].valor).toBe(8);
    expect(v.capturas.length).toBe(1);
    expect(v.capturas[0].id).toBe('r-sprint1');
  });

  // ══════════════════════════════════════════════════════════════════════
  // Revisión de Ejecución — navegación entre sprints para pruebas manuales.
  // La selección del sprint ya NO depende de la fecha actual ni del estado
  // ('pendiente' deja de bloquear la selección/captura); las reglas de
  // ejecución (rango de fechas, frecuencia, escala, aislamiento) siguen
  // intactas y se validan igual sea cual sea el sprint seleccionado.
  // ══════════════════════════════════════════════════════════════════════
  describe('Navegación entre sprints (revisión de Ejecución)', () => {
    beforeEach(() => {
      sprintService.listar.and.returnValue(of(todosLosSprints));
      planeacionService.listarMetricas.and.returnValue(of([metrica(METRICA_DEFECTOS, 'Defectos', true)]));
      variableService.obtenerVariables.and.returnValue(of(variables(METRICA_DEFECTOS, ['defectos_totales'])));
      evaluacionService.detalle.and.returnValue(of(detalleVacio()));
    });

    it('navega Sprint 1 -> 2 -> 3 -> 4 -> 5, recargando el sprint activo en cada paso', () => {
      component.ngOnInit();
      expect(component.sprintActual?.numero).toBe(1); // auto-seleccionado (en_ejecucion)

      for (const s of [mockSprint2, mockSprint3, mockSprint4, mockSprint5]) {
        component.sprintSeleccionadoId = s.id;
        component.onSprintChange();

        expect(component.sprintActual?.id).toBe(s.id);
        expect(component.sprintActual?.numero).toBe(s.numero);
        expect(variableService.obtenerVariables)
          .toHaveBeenCalledWith(METRICA_DEFECTOS, 'proj-1', s.id);
      }
    });

    it('un sprint futuro (pendiente) es seleccionable y no bloquea la captura', () => {
      component.ngOnInit();
      component.sprintSeleccionadoId = mockSprint3.id;
      component.onSprintChange();

      expect(component.sprintActual?.estado).toBe('pendiente');
      expect(component.sprintBloqueado).toBe(false);
    });

    it('un sprint finalizado sigue bloqueado para nuevas capturas (regla de negocio sin cambios)', () => {
      sprintService.listar.and.returnValue(of([{ ...mockSprint2, estado: 'finalizado' as const }]));
      component.ngOnInit();
      component.sprintSeleccionadoId = 'sprint-2';
      component.onSprintChange();

      expect(component.sprintBloqueado).toBe(true);
    });

    it('captura dentro del rango de fechas del sprint seleccionado (futuro) se acepta', () => {
      variableService.guardarValores.and.returnValue(of(undefined));
      component.ngOnInit();
      component.sprintSeleccionadoId = mockSprint2.id; // 2026-08-28 al 2026-09-03
      component.onSprintChange();

      const m = component.metricas[0];
      const v = m.variables[0];
      v.fecha = '2026-08-30'; // dentro del rango de Sprint 2
      v.valorNum = 5;

      component.registrarValor(m, v);

      expect(variableService.guardarValores).toHaveBeenCalledWith(METRICA_DEFECTOS, jasmine.objectContaining({
        sprintId: mockSprint2.id,
        valores: [jasmine.objectContaining({ fechaCaptura: '2026-08-30T00:00:00Z' })]
      }));
      expect(v.error).toBe('');
    });

    it('captura fuera del rango de fechas del sprint seleccionado es rechazada por el backend y se muestra el error', () => {
      variableService.guardarValores.and.returnValue(throwError(() => ({
        status: 400,
        error: { error: 'La fecha de captura (2026-09-10) es posterior al fin del sprint (2026-09-03).' }
      })));
      component.ngOnInit();
      component.sprintSeleccionadoId = mockSprint2.id; // 2026-08-28 al 2026-09-03
      component.onSprintChange();

      const m = component.metricas[0];
      const v = m.variables[0];
      v.fecha = '2026-09-10'; // fuera del rango de Sprint 2
      v.valorNum = 5;

      component.registrarValor(m, v);

      expect(v.error).toContain('posterior al fin del sprint');
      expect(v.ultimoMensaje).toBe('');
    });

    it('duplicado por frecuencia (por_sprint) es rechazado por el backend y se muestra el error', () => {
      variableService.guardarValores.and.returnValue(throwError(() => ({
        status: 400,
        error: { error: "Ya existe un valor registrado para 'defectos_totales' en este sprint (fecha 2026-08-22). Editá ese valor en vez de crear una captura nueva." }
      })));
      component.ngOnInit();

      const m = component.metricas[0];
      const v = m.variables[0];
      v.fecha = '2026-08-23';
      v.valorNum = 5;

      component.registrarValor(m, v);

      expect(v.error).toContain('Editá ese valor en vez de crear una captura nueva');
    });

    it('editar el mismo registro (registroId) se permite y se envía al backend', () => {
      variableService.guardarValores.and.returnValue(of(undefined));
      component.ngOnInit();

      const m = component.metricas[0];
      const v = m.variables[0];
      const registro = { id: 'r1', valor: 6, registradoAt: '2026-08-23T00:00:00Z', sprintId: 'sprint-1', sprintNumero: 1, userId: 'sm@test.com' };

      component.editarValorExistente(v, registro);
      v.fecha = '2026-08-22'; // cambia la fecha al editar — el bug real reportado
      v.valorNum = 9;
      component.registrarValor(m, v);

      expect(variableService.guardarValores).toHaveBeenCalledWith(METRICA_DEFECTOS, jasmine.objectContaining({
        valores: [jasmine.objectContaining({
          registroId: 'r1',
          fechaCaptura: '2026-08-22T00:00:00Z',
          valorNum: 9
        })]
      }));
      expect(v.error).toBe('');
    });

    it('editar un registro moviéndolo a una fecha/ventana que choca con OTRO registro real es rechazado', () => {
      variableService.guardarValores.and.returnValue(throwError(() => ({
        status: 400,
        error: { error: "Ya existe un valor registrado para 'defectos_totales' en este sprint (fecha 2026-08-25). Editá ese valor en vez de crear una captura nueva." }
      })));
      component.ngOnInit();

      const m = component.metricas[0];
      const v = m.variables[0];
      const registro = { id: 'r1', valor: 6, registradoAt: '2026-08-23T00:00:00Z', sprintId: 'sprint-1', sprintNumero: 1, userId: 'sm@test.com' };

      component.editarValorExistente(v, registro);
      v.fecha = '2026-08-25';
      v.valorNum = 9;
      component.registrarValor(m, v);

      expect(variableService.guardarValores).toHaveBeenCalledWith(METRICA_DEFECTOS, jasmine.objectContaining({
        valores: [jasmine.objectContaining({ registroId: 'r1' })]
      }));
      expect(v.error).toContain('Editá ese valor en vez de crear una captura nueva');
    });

    it('tras registrar en Sprint 2, navegar de vuelta a Sprint 1 no arrastra las capturas de Sprint 2 (aislamiento)', () => {
      variableService.guardarValores.and.returnValue(of(undefined));
      component.ngOnInit();

      // Captura en Sprint 1.
      evaluacionService.detalle.and.returnValue(of([{
        variableId: 'v-' + METRICA_DEFECTOS + '-0', variableNombre: 'defectos_totales', categoria: 'Significado',
        tipoAlcance: 'grupal', frecuenciaCaptura: 'por_sprint', formulaTexto: null,
        registros: [
          { id: 'r-sprint1', valor: 4, registradoAt: '2026-08-22T00:00:00Z', sprintId: 'sprint-1', sprintNumero: 1, userId: 'sm@test.com' },
          { id: 'r-sprint2', valor: 7, registradoAt: '2026-08-29T00:00:00Z', sprintId: mockSprint2.id, sprintNumero: 2, userId: 'sm@test.com' }
        ],
        estadisticas: {} as any, porSprint: []
      }]));

      // Navega a Sprint 2.
      component.sprintSeleccionadoId = mockSprint2.id;
      component.onSprintChange();
      let v = component.metricas[0].variables[0];
      expect(v.capturas.length).toBe(1);
      expect(v.capturas[0].id).toBe('r-sprint2');

      // Vuelve a Sprint 1: solo debe ver la captura de Sprint 1.
      component.sprintSeleccionadoId = 'sprint-1';
      component.onSprintChange();
      v = component.metricas[0].variables[0];
      expect(v.capturas.length).toBe(1);
      expect(v.capturas[0].id).toBe('r-sprint1');
    });
  });

  // ══════════════════════════════════════════════════════════════════════
  // Revisión de Ejecución — botones "Sprint anterior" / "Sprint siguiente".
  // Navegación complementaria al selector existente: reutiliza
  // onSprintChange() para heredar exactamente el mismo aislamiento,
  // validaciones y reglas de estado que ya tiene el selector manual.
  // ══════════════════════════════════════════════════════════════════════
  describe('Navegación con botones Sprint anterior/siguiente (revisión de Ejecución)', () => {
    beforeEach(() => {
      sprintService.listar.and.returnValue(of(todosLosSprints));
      planeacionService.listarMetricas.and.returnValue(of([metrica(METRICA_DEFECTOS, 'Defectos', true)]));
      variableService.obtenerVariables.and.returnValue(of(variables(METRICA_DEFECTOS, ['defectos_totales'])));
      evaluacionService.detalle.and.returnValue(of(detalleVacio()));
    });

    it('A. en el primer sprint: "Sprint anterior" deshabilitado, "Sprint siguiente" habilitado', () => {
      component.ngOnInit();
      expect(component.sprintActual?.numero).toBe(1);
      expect(component.haySprintAnterior).toBe(false);
      expect(component.haySprintSiguiente).toBe(true);
    });

    it('B. en un sprint intermedio: ambos botones habilitados', () => {
      component.ngOnInit();
      component.sprintSeleccionadoId = mockSprint3.id; // numero 3, de 5
      component.onSprintChange();

      expect(component.haySprintAnterior).toBe(true);
      expect(component.haySprintSiguiente).toBe(true);
    });

    it('C. en el último sprint: "Sprint siguiente" deshabilitado, "Sprint anterior" habilitado', () => {
      component.ngOnInit();
      component.sprintSeleccionadoId = mockSprint5.id;
      component.onSprintChange();

      expect(component.haySprintSiguiente).toBe(false);
      expect(component.haySprintAnterior).toBe(true);
    });

    it('los botones en el DOM reflejan haySprintAnterior/haySprintSiguiente (primer sprint)', () => {
      component.ngOnInit();
      fixture.detectChanges();

      const botones: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
      const anterior = botones.find(b => b.textContent?.includes('Sprint anterior'));
      const siguiente = botones.find(b => b.textContent?.includes('Sprint siguiente'));

      expect(anterior?.disabled).toBe(true);
      expect(siguiente?.disabled).toBe(false);
    });

    it('D. irASprintSiguiente() cambia el sprint seleccionado, recarga sus variables y no arrastra capturas del sprint anterior', () => {
      evaluacionService.detalle.and.returnValue(of([{
        variableId: 'v-' + METRICA_DEFECTOS + '-0', variableNombre: 'defectos_totales', categoria: 'Significado',
        tipoAlcance: 'grupal', frecuenciaCaptura: 'por_sprint', formulaTexto: null,
        registros: [
          { id: 'r-sprint1', valor: 4, registradoAt: '2026-08-22T00:00:00Z', sprintId: 'sprint-1', sprintNumero: 1, userId: 'sm@test.com' },
          { id: 'r-sprint2', valor: 7, registradoAt: '2026-08-29T00:00:00Z', sprintId: mockSprint2.id, sprintNumero: 2, userId: 'sm@test.com' }
        ],
        estadisticas: {} as any, porSprint: []
      }]));
      component.ngOnInit();

      component.irASprintSiguiente();

      expect(component.sprintSeleccionadoId).toBe(mockSprint2.id);
      expect(component.sprintActual?.id).toBe(mockSprint2.id);
      expect(variableService.obtenerVariables).toHaveBeenCalledWith(METRICA_DEFECTOS, 'proj-1', mockSprint2.id);
      const v = component.metricas[0].variables[0];
      expect(v.capturas.length).toBe(1);
      expect(v.capturas[0].id).toBe('r-sprint2'); // nunca la de sprint-1
    });

    it('E. irASprintAnterior() cambia el sprint seleccionado en sentido contrario y no arrastra capturas del sprint siguiente', () => {
      evaluacionService.detalle.and.returnValue(of([{
        variableId: 'v-' + METRICA_DEFECTOS + '-0', variableNombre: 'defectos_totales', categoria: 'Significado',
        tipoAlcance: 'grupal', frecuenciaCaptura: 'por_sprint', formulaTexto: null,
        registros: [
          { id: 'r-sprint1', valor: 4, registradoAt: '2026-08-22T00:00:00Z', sprintId: 'sprint-1', sprintNumero: 1, userId: 'sm@test.com' },
          { id: 'r-sprint2', valor: 7, registradoAt: '2026-08-29T00:00:00Z', sprintId: mockSprint2.id, sprintNumero: 2, userId: 'sm@test.com' }
        ],
        estadisticas: {} as any, porSprint: []
      }]));
      component.ngOnInit();
      component.sprintSeleccionadoId = mockSprint2.id;
      component.onSprintChange();

      component.irASprintAnterior();

      expect(component.sprintSeleccionadoId).toBe('sprint-1');
      expect(component.sprintActual?.numero).toBe(1);
      const v = component.metricas[0].variables[0];
      expect(v.capturas.length).toBe(1);
      expect(v.capturas[0].id).toBe('r-sprint1'); // nunca la de sprint-2
    });

    it('F. si se estaba editando una captura, navegar no guarda automáticamente y limpia el estado de edición', () => {
      component.ngOnInit();
      const v = component.metricas[0].variables[0];
      const registro = { id: 'r1', valor: 6, registradoAt: '2026-08-23T00:00:00Z', sprintId: 'sprint-1', sprintNumero: 1, userId: 'sm@test.com' };
      component.editarValorExistente(v, registro);
      expect(v.registroEditandoId).toBe('r1');
      expect(v.editando).toBe(true);

      component.irASprintSiguiente();

      expect(variableService.guardarValores).not.toHaveBeenCalled();
      // Los BloqueVariable del sprint anterior se descartan por completo: los
      // nuevos siempre nacen con registroEditandoId: null (construirBloque()).
      const nuevaVariable = component.metricas[0].variables[0];
      expect(nuevaVariable.registroEditandoId).toBeNull();
    });

    it('G. navegar hacia un sprint pendiente lo selecciona y conserva el comportamiento de captura/pruebas ya existente', () => {
      component.ngOnInit();

      component.irASprintSiguiente(); // -> Sprint 2, pendiente

      expect(component.sprintActual?.estado).toBe('pendiente');
      expect(component.sprintBloqueado).toBe(false); // pendiente NO bloquea, regla ya existente
    });

    it('H. navegar hacia un sprint finalizado lo selecciona y conserva su condición de solo lectura', () => {
      sprintService.listar.and.returnValue(of([mockSprint, { ...mockSprint2, estado: 'finalizado' as const }]));
      component.ngOnInit();

      component.irASprintSiguiente(); // -> Sprint 2, ahora finalizado

      expect(component.sprintActual?.estado).toBe('finalizado');
      expect(component.sprintBloqueado).toBe(true); // finalizado SIGUE bloqueado, regla sin cambios
    });

    it('I. tras navegar, las reglas existentes de rango de fechas del sprint siguen aplicando sin cambios', () => {
      variableService.guardarValores.and.returnValue(throwError(() => ({
        status: 400,
        error: { error: 'La fecha de captura (2026-09-10) es posterior al fin del sprint (2026-09-03).' }
      })));
      component.ngOnInit();

      component.irASprintSiguiente(); // -> Sprint 2 (2026-08-28 al 2026-09-03)

      const m = component.metricas[0];
      const v = m.variables[0];
      v.fecha = '2026-09-10'; // fuera del rango del sprint al que se navegó
      v.valorNum = 5;

      component.registrarValor(m, v);

      expect(v.error).toContain('posterior al fin del sprint');
    });

    it('irASprintAnterior()/irASprintSiguiente() no hacen nada si el botón correspondiente está deshabilitado', () => {
      component.ngOnInit(); // Sprint 1: sin anterior

      component.irASprintAnterior();

      expect(component.sprintSeleccionadoId).toBe('sprint-1'); // sin cambios
      expect(variableService.obtenerVariables).toHaveBeenCalledTimes(1); // solo la carga inicial
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
      sprintService.listar.and.returnValue(of([{ ...mockSprint, estado: 'pendiente' as const }]));
      component.ngOnInit();
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Seleccioná un sprint');
    });
  });
});
