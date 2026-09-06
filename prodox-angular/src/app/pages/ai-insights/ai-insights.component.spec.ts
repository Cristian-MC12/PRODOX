// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { ComponentFixture, TestBed, fakeAsync, tick, flush } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { Router } from '@angular/router';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { of, throwError } from 'rxjs';
import { AIInsightsComponent } from './ai-insights.component';
import { AIInsightsService } from '../../services/ai-insights.service';
import { SprintService } from '../../services/sprint.service';
import { AIInsight, GenerateInsightsResult } from '../../models/ai-insights.model';
import { ProyectoDto } from '../../models/proyecto.model';
import { SprintDto } from '../../models/sprint.model';
import { ToastService } from '../../shared/toast/toast.service';

describe('AIInsightsComponent', () => {
  let component: AIInsightsComponent;
  let fixture: ComponentFixture<AIInsightsComponent>;
  let insightsService: jasmine.SpyObj<AIInsightsService>;
  let sprintService: jasmine.SpyObj<SprintService>;
  let toastService: jasmine.SpyObj<ToastService>;
  let router: Router;

  function sprint(numero: number, estado: SprintDto['estado']): SprintDto {
    return {
      id: `sprint-${numero}`, proyectoId: 'proyecto-123', proyectoNombre: 'Test Project', metodo: 'scrum',
      timeBoxSemanas: 2, numero, sprintGoal: `Sprint ${numero}`, estado,
      fechaInicio: '2026-07-01', fechaFin: '2026-07-14', cerradoPor: null, cerradoAt: null,
      createdAt: '2026-07-01T00:00:00Z'
    };
  }

  const mockProyecto: ProyectoDto = {
    id: 'proyecto-123',
    nombre: 'Test Project',
    descripcion: 'Test',
    metodo: 'scrum',
    timeBoxSemanas: 2,
    numeroSprints: 3,
    fechaInicio: '2024-01-01',
    productGoal: 'Test goal',
    sprintGoal: 'Sprint goal',
    estado: 'activo',
    scrumMasterEmail: 'test@test.com',
    totalMiembros: 5,
    createdAt: '2024-01-01T00:00:00Z'
  };

  const mockInsights: AIInsight[] = [
    {
      id: 'insight-1',
      proyectoId: 'proyecto-123',
      sprintId: null,
      type: 'TREND',
      severity: 'MEDIUM',
      title: 'Mejora en Calidad',
      description: 'La calidad ha mejorado un 15%',
      evidence: [{
        categoria: 'Calidad',
        valorActual: 8.5,
        valorAnterior: 7.5,
        promedioHistorico: 7.8,
        desviacionEstandar: 0.5,
        variacionPorcentual: 13.33,
        tendencia: 'UP',
        numeroSprints: 3,
        metadata: {}
      }],
      recommendation: 'Mantener las prácticas actuales',
      confidence: 'HIGH',
      dismissed: false,
      createdAt: '2024-01-15T10:00:00Z',
      dismissedAt: null
    }
  ];

  beforeEach(async () => {
    const insightsServiceSpy = jasmine.createSpyObj('AIInsightsService',
      ['getProjectInsights', 'generateInsights', 'dismissInsight']);
    // getActivo también lo usa ShellComponent (renderizado de verdad en este
    // spec, que usa NO_ERRORS_SCHEMA en vez de mockear <app-shell>) — sin
    // configurarlo, el spy sin ese método definido rompe ShellComponent.ngOnInit
    // con "getActivo is not a function" (misma clase de problema que
    // ActivityFeedService con EvaluacionService/AIInsightsService).
    const sprintServiceSpy = jasmine.createSpyObj('SprintService', ['listar', 'getActivo']);
    // ToastContainerComponent (renderizado de verdad dentro del ShellComponent
    // real que este spec monta) lee toastService.toasts() como una signal —
    // sin un valor, "toasts is not a function" rompe cualquier detectChanges().
    const toastServiceSpy = jasmine.createSpyObj('ToastService', ['success', 'error', 'warning', 'info']);
    toastServiceSpy.toasts = jasmine.createSpy('toasts').and.returnValue([]);

    // Por defecto, 2 sprints finalizados (mínimo exacto que permite generar)
    // — así las pruebas existentes, que no ejercitan la regla de mínimo de
    // sprints, siguen viendo el botón habilitado sin cambios.
    sprintServiceSpy.listar.and.returnValue(of([sprint(1, 'finalizado'), sprint(2, 'finalizado')]));
    sprintServiceSpy.getActivo.and.returnValue(of(null));

    await TestBed.configureTestingModule({
      imports: [
        AIInsightsComponent,
        HttpClientTestingModule,
        RouterTestingModule
      ],
      providers: [
        { provide: AIInsightsService, useValue: insightsServiceSpy },
        { provide: SprintService, useValue: sprintServiceSpy },
        { provide: ToastService, useValue: toastServiceSpy }
      ],
      schemas: [NO_ERRORS_SCHEMA] // Ignora componentes hijos como app-shell
    }).compileComponents();

    insightsService = TestBed.inject(AIInsightsService) as jasmine.SpyObj<AIInsightsService>;
    sprintService = TestBed.inject(SprintService) as jasmine.SpyObj<SprintService>;
    toastService = TestBed.inject(ToastService) as jasmine.SpyObj<ToastService>;
    router = TestBed.inject(Router);

    fixture = TestBed.createComponent(AIInsightsComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('ngOnInit', () => {
    it('should load insights when project is selected', () => {
      spyOn(localStorage, 'getItem').and.returnValue(JSON.stringify(mockProyecto));
      insightsService.getProjectInsights.and.returnValue(of(mockInsights));

      component.ngOnInit();

      expect(insightsService.getProjectInsights).toHaveBeenCalledWith('proyecto-123');
      expect(component.insights.length).toBe(1);
    });

    it('should not load insights when no project is selected', () => {
      spyOn(localStorage, 'getItem').and.returnValue(null);

      component.ngOnInit();

      expect(insightsService.getProjectInsights).not.toHaveBeenCalled();
    });
  });

  describe('loadInsights', () => {
    beforeEach(() => {
      component.proyecto = mockProyecto;
    });

    it('should load insights successfully', () => {
      insightsService.getProjectInsights.and.returnValue(of(mockInsights));

      component.loadInsights();

      expect(component.loading()).toBe(false);
      expect(component.insights).toEqual(mockInsights);
    });

    it('should handle empty insights', () => {
      insightsService.getProjectInsights.and.returnValue(of([]));

      component.loadInsights();

      expect(component.insights.length).toBe(0);
      expect(component.loading()).toBe(false);
    });

    it('should handle error', () => {
      insightsService.getProjectInsights.and.returnValue(
        throwError(() => new Error('Network error'))
      );

      component.loadInsights();

      expect(component.loading()).toBe(false);
      expect(component.alertMsg()).toContain('Error al cargar insights');
    });
  });

  describe('presentación de insights (limpieza de Markdown — FASE 7C.1)', () => {
    it('renderiza título y descripción sin marcadores Markdown crudos cuando el insight los trae', () => {
      spyOn(localStorage, 'getItem').and.returnValue(JSON.stringify(mockProyecto));
      insightsService.getProjectInsights.and.returnValue(of([{
        ...mockInsights[0],
        title: '**Riesgo crítico** detectado',
        description: 'Esto es --- una descripción con * viñeta suelta',
        recommendation: 'Revisar **urgente** este punto'
      }]));

      fixture.detectChanges();

      const texto: string = fixture.nativeElement.textContent;
      expect(texto).toContain('Riesgo crítico detectado');
      expect(texto).not.toContain('**Riesgo crítico**');
      expect(texto).not.toMatch(/---/);
    });

    it('no altera el texto de un insight que ya llega limpio', () => {
      spyOn(localStorage, 'getItem').and.returnValue(JSON.stringify(mockProyecto));
      insightsService.getProjectInsights.and.returnValue(of(mockInsights));

      fixture.detectChanges();

      const texto: string = fixture.nativeElement.textContent;
      expect(texto).toContain(mockInsights[0].title);
      expect(texto).toContain(mockInsights[0].description);
    });
  });

  describe('generateInsights', () => {
    beforeEach(() => {
      component.proyecto = mockProyecto;
      // Regla de mínimo 2 sprints finalizados: estos tests ejercitan el flujo
      // de generación en sí (no la regla, cubierta aparte más abajo), así que
      // se seedea directamente con 2 finalizados (el mínimo que la permite).
      component.sprints = [sprint(1, 'finalizado'), sprint(2, 'finalizado')];
      // FASE 23: tras generar, el componente recarga la lista completa
      // (loadInsights()) en vez de reemplazarla solo con la tanda nueva.
      insightsService.getProjectInsights.and.returnValue(of(mockInsights));
    });

    function resultado(overrides: Partial<GenerateInsightsResult>): GenerateInsightsResult {
      return {
        insights: [],
        status: 'COMPLETE',
        senalesDetectadas: 0,
        senalesNuevas: 0,
        senalesOmitidasPorDuplicado: 0,
        errores: [],
        ...overrides
      };
    }

    it('should generate insights successfully (status COMPLETE)', fakeAsync(() => {
      insightsService.generateInsights.and.returnValue(of(resultado({
        insights: mockInsights, status: 'COMPLETE', senalesDetectadas: 1, senalesNuevas: 1
      })));

      component.generateInsights();

      // El Observable se completa inmediatamente (síncrono)
      // Solo necesitamos esperar el setTimeout final de 500ms
      tick(500);

      expect(component.generating()).toBe(false);
      expect(component.insights).toEqual(mockInsights); // recargado vía loadInsights()
      expect(component.alertMsg()).toContain('insight(s) nuevo(s) generado(s)');

      // Flush remaining timers
      flush();
    }));

    it('should handle SIN_DATOS (sin sprints finalizados)', fakeAsync(() => {
      insightsService.generateInsights.and.returnValue(of(resultado({ status: 'SIN_DATOS' })));

      component.generateInsights();
      tick(500);

      expect(component.generating()).toBe(false);
      expect(component.alertMsg()).toContain('No se generaron insights');

      flush();
    }));

    it('should handle SIN_SENALES (datos existen pero sin señales significativas)', fakeAsync(() => {
      insightsService.generateInsights.and.returnValue(of(resultado({ status: 'SIN_SENALES' })));

      component.generateInsights();
      tick(500);

      expect(component.generating()).toBe(false);
      expect(component.alertMsg()).toContain('No se generaron insights');

      flush();
    }));

    it('should handle PARTIAL (fallo parcial de Gemini) sin presentarlo como éxito total', fakeAsync(() => {
      insightsService.generateInsights.and.returnValue(of(resultado({
        insights: mockInsights, status: 'PARTIAL', senalesDetectadas: 2, senalesNuevas: 1,
        errores: ['TREND (Calidad): Gemini error 429']
      })));

      component.generateInsights();
      tick(500);

      expect(component.generating()).toBe(false);
      expect(component.alertMsg()).toContain('no pudieron procesarse');

      flush();
    }));

    it('should handle FAILED (Gemini no respondió para ninguna señal)', fakeAsync(() => {
      insightsService.generateInsights.and.returnValue(of(resultado({
        status: 'FAILED', senalesDetectadas: 1, errores: ['TREND (Calidad): Gemini error 429']
      })));

      component.generateInsights();
      tick(500);

      expect(component.generating()).toBe(false);
      expect(component.alertMsg()).toContain('No se pudo generar ningún insight');

      flush();
    }));

    it('should handle 403 error', () => {
      insightsService.generateInsights.and.returnValue(
        throwError(() => ({ status: 403 }))
      );

      component.generateInsights();

      expect(component.generating()).toBe(false);
      expect(component.alertMsg()).toContain('No tienes permisos');
    });

    it('should handle generic error', () => {
      insightsService.generateInsights.and.returnValue(
        throwError(() => ({ status: 500 }))
      );

      component.generateInsights();

      expect(component.generating()).toBe(false);
      expect(component.alertMsg()).toContain('Error al generar insights');
    });

    // Corrección de auditoría (regla de mínimo 2 sprints finalizados): el
    // backend rechaza con 409 CONFLICT cuando hay menos de 2 sprints
    // finalizados (ver AIInsightsService.generateInsights). El frontend
    // debería impedir llegar a este caso (botón deshabilitado), pero se
    // maneja igual por si los datos de sprints quedaron desactualizados.
    it('should handle 409 error (backend rejects: not enough finished sprints) with the exact backend message', () => {
      insightsService.generateInsights.and.returnValue(
        throwError(() => ({ status: 409, error: { error: 'Se requieren al menos 2 sprints finalizados para generar Insights. Actualmente hay 1.' } }))
      );

      component.generateInsights();

      expect(component.generating()).toBe(false);
      expect(component.alertClass()).toBe('alert-warning');
      expect(component.alertMsg()).toContain('al menos 2 sprints finalizados');
      expect(toastService.error).toHaveBeenCalledWith('No se pudieron generar los Insights.');
    });

    it('should show a success toast in addition to the inline banner on COMPLETE', fakeAsync(() => {
      insightsService.generateInsights.and.returnValue(of(resultado({ insights: mockInsights, senalesNuevas: 1 })));

      component.generateInsights();
      tick(500);

      expect(toastService.success).toHaveBeenCalledWith('Insights generados correctamente.');
      flush();
    }));

    it('should prevent double generation', () => {
      insightsService.generateInsights.and.returnValue(of(resultado({ insights: mockInsights })));
      component.generating.set(true);

      component.generateInsights();

      expect(insightsService.generateInsights).not.toHaveBeenCalled();
    });
  });

  // Corrección de auditoría: regla de negocio "AI Insights requiere como
  // mínimo 2 sprints finalizados" — replicada en el frontend (ver
  // AIInsightsComponent.puedeGenerarInsights) con el MISMO criterio que
  // valida el backend (AIInsightsService.generateInsights: estado="finalizado",
  // nunca en_ejecucion/pendiente/reabierto).
  describe('regla de mínimo 2 sprints finalizados', () => {
    beforeEach(() => {
      component.proyecto = mockProyecto;
    });

    it('0 sprints finalizados: puedeGenerarInsights es false y el mensaje no menciona un conteo parcial', () => {
      component.sprints = [];
      expect(component.sprintsFinalizadosCount).toBe(0);
      expect(component.puedeGenerarInsights).toBeFalse();
      expect(component.getMensajeRequisitoInsights()).toBe(
        'No puedes generar Insights todavía. Se requieren al menos 2 sprints finalizados.'
      );
    });

    it('1 sprint finalizado: puedeGenerarInsights es false y el mensaje indica el conteo actual', () => {
      component.sprints = [sprint(1, 'finalizado')];
      expect(component.sprintsFinalizadosCount).toBe(1);
      expect(component.puedeGenerarInsights).toBeFalse();
      expect(component.getMensajeRequisitoInsights()).toBe(
        'No puedes generar Insights todavía. Se requieren al menos 2 sprints finalizados. Actualmente tienes 1 sprint finalizado.'
      );
    });

    it('2 sprints finalizados: puedeGenerarInsights es true y no hay mensaje de bloqueo', () => {
      component.sprints = [sprint(1, 'finalizado'), sprint(2, 'finalizado')];
      expect(component.sprintsFinalizadosCount).toBe(2);
      expect(component.puedeGenerarInsights).toBeTrue();
      expect(component.getMensajeRequisitoInsights()).toBe('');
    });

    it('3 o más sprints finalizados: puedeGenerarInsights sigue siendo true', () => {
      component.sprints = [sprint(1, 'finalizado'), sprint(2, 'finalizado'), sprint(3, 'finalizado')];
      expect(component.sprintsFinalizadosCount).toBe(3);
      expect(component.puedeGenerarInsights).toBeTrue();
    });

    it('sprint en_ejecucion NO cuenta para el mínimo', () => {
      component.sprints = [sprint(1, 'finalizado'), sprint(2, 'en_ejecucion')];
      expect(component.sprintsFinalizadosCount).toBe(1);
      expect(component.puedeGenerarInsights).toBeFalse();
    });

    it('sprint pendiente NO cuenta para el mínimo', () => {
      component.sprints = [sprint(1, 'finalizado'), sprint(2, 'pendiente')];
      expect(component.sprintsFinalizadosCount).toBe(1);
      expect(component.puedeGenerarInsights).toBeFalse();
    });

    it('getProgresoSprintsLabel refleja el conteo real sobre el mínimo requerido', () => {
      component.sprints = [sprint(1, 'finalizado')];
      expect(component.getProgresoSprintsLabel()).toBe('Progreso: 1 / 2 sprints finalizados');
    });

    it('generateInsights(): con menos de 2 sprints finalizados, NO llama al servicio de IA y muestra el motivo', () => {
      component.sprints = [sprint(1, 'finalizado')];

      component.generateInsights();

      expect(insightsService.generateInsights).not.toHaveBeenCalled();
      expect(component.alertClass()).toBe('alert-warning');
      expect(component.alertMsg()).toContain('al menos 2 sprints finalizados');
    });

    it('generateInsights(): con 2 sprints finalizados, sí llama al servicio de IA normalmente', () => {
      component.sprints = [sprint(1, 'finalizado'), sprint(2, 'finalizado')];
      insightsService.generateInsights.and.returnValue(of({
        insights: [], status: 'SIN_SENALES', senalesDetectadas: 0, senalesNuevas: 0,
        senalesOmitidasPorDuplicado: 0, errores: []
      }));

      component.generateInsights();

      expect(insightsService.generateInsights).toHaveBeenCalledWith('proyecto-123');
    });

    it('ngOnInit carga los sprints del proyecto activo (loadSprints)', () => {
      spyOn(localStorage, 'getItem').and.returnValue(JSON.stringify(mockProyecto));
      insightsService.getProjectInsights.and.returnValue(of([]));
      sprintService.listar.and.returnValue(of([sprint(1, 'finalizado'), sprint(2, 'finalizado')]));

      component.ngOnInit();

      expect(sprintService.listar).toHaveBeenCalledWith('proyecto-123');
      expect(component.sprintsFinalizadosCount).toBe(2);
    });
  });

  describe('dismissInsight', () => {
    beforeEach(() => {
      component.insights = [...mockInsights];
      spyOn(window, 'confirm').and.returnValue(true);
    });

    it('should dismiss insight successfully', () => {
      insightsService.dismissInsight.and.returnValue(of(undefined));

      component.dismissInsight(mockInsights[0]);

      expect(insightsService.dismissInsight).toHaveBeenCalledWith('insight-1');
      expect(component.insights.length).toBe(0);
      expect(component.alertMsg()).toContain('Insight descartado');
    });

    it('should handle dismiss error', () => {
      insightsService.dismissInsight.and.returnValue(
        throwError(() => new Error('Network error'))
      );

      component.dismissInsight(mockInsights[0]);

      expect(component.alertMsg()).toContain('Error al descartar');
    });

    it('should cancel dismiss when not confirmed', () => {
      (window.confirm as jasmine.Spy).and.returnValue(false);

      component.dismissInsight(mockInsights[0]);

      expect(insightsService.dismissInsight).not.toHaveBeenCalled();
      expect(component.insights.length).toBe(1);
    });
  });

  describe('UI helpers', () => {
    it('should return correct severity badge class', () => {
      expect(component.getSeverityBadgeClass('CRITICAL')).toBe('bg-danger');
      expect(component.getSeverityBadgeClass('HIGH')).toBe('bg-warning');
      expect(component.getSeverityBadgeClass('MEDIUM')).toBe('bg-info');
      expect(component.getSeverityBadgeClass('LOW')).toBe('bg-secondary');
    });

    it('should return correct type icon', () => {
      expect(component.getTypeIcon('TREND')).toBe('bi-graph-up-arrow');
      expect(component.getTypeIcon('ANOMALY')).toBe('bi-exclamation-triangle');
      expect(component.getTypeIcon('RISK')).toBe('bi-shield-exclamation');
      expect(component.getTypeIcon('COMPARISON')).toBe('bi-arrow-left-right');
    });

    it('should return correct confidence badge class', () => {
      expect(component.getConfidenceBadgeClass('HIGH')).toBe('badge-success');
      expect(component.getConfidenceBadgeClass('MEDIUM')).toBe('badge-warning');
      expect(component.getConfidenceBadgeClass('LOW')).toBe('badge-secondary');
    });

    it('should format evidence correctly', () => {
      const evidence = mockInsights[0].evidence;
      const formatted = component.formatEvidence(evidence);

      expect(formatted).toContain('Calidad');
      expect(formatted).toContain('8.50');
      expect(formatted).toContain('13.3%');
    });

    it('should handle empty evidence', () => {
      const formatted = component.formatEvidence([]);
      expect(formatted).toBe('Sin datos de evidencia');
    });
  });

  // IN.2 — PROGRESO DE GENERACIÓN
  describe('IN.2 - Progreso de generación', () => {
    beforeEach(() => {
      component.proyecto = mockProyecto;
    });

    it('debería tener signal de generationStep', () => {
      expect(component.generationStep).toBeDefined();
      expect(typeof component.generationStep()).toBe('string');
    });
  });

  // IN.5 — PRIORIZACIÓN DE INSIGHTS
  describe('IN.5 - Priorización de insights', () => {
    it('debería ordenar por severidad por defecto', () => {
      expect(component.sortBy()).toBe('severidad');
    });

    it('debería ordenar descendente por defecto', () => {
      expect(component.sortDirection()).toBe('desc');
    });

    it('debería priorizar CRITICAL sobre otros', () => {
      component.insights = [
        { 
          id: '1', proyectoId: 'p1', sprintId: null, type: 'TREND', severity: 'LOW', 
          title: 'Low', description: '', evidence: [], recommendation: '', 
          confidence: 'HIGH', dismissed: false, createdAt: '2026-08-11T10:00:00Z', dismissedAt: null 
        },
        { 
          id: '2', proyectoId: 'p1', sprintId: null, type: 'PATTERN', severity: 'CRITICAL', 
          title: 'Critical', description: '', evidence: [], recommendation: '', 
          confidence: 'HIGH', dismissed: false, createdAt: '2026-08-11T09:00:00Z', dismissedAt: null 
        },
        { 
          id: '3', proyectoId: 'p1', sprintId: null, type: 'PREDICTION', severity: 'MEDIUM', 
          title: 'Medium', description: '', evidence: [], recommendation: '', 
          confidence: 'HIGH', dismissed: false, createdAt: '2026-08-11T11:00:00Z', dismissedAt: null 
        }
      ];
      component.sortBy.set('severidad');
      component.sortDirection.set('desc');

      const sorted = component.filteredInsights;
      expect(sorted[0].severity).toBe('CRITICAL');
    });
  });

  // Auditoría de reportes: exportarAWord() no tenía cobertura de tests —
  // "no asumas que funciona porque el botón existe". Estos tests ejecutan
  // el código real (import dinámico de 'docx'/'file-saver', ambos
  // dependencias reales del proyecto, ver package.json) para confirmar que
  // la exportación efectivamente genera un documento y dispara la descarga,
  // sin mockear la librería. No aplica un caso "proyecto no autorizado": a
  // diferencia de generateInsights()/dismissInsight(), exportarAWord() no
  // hace ninguna llamada HTTP nueva — solo serializa this.insights, que ya
  // llegó autorizado por proyecto vía getProjectInsights() (AIInsightsService,
  // ver validateProjectAccess en el backend).
  describe('exportarAWord (FASE reportes)', () => {
    it('sin insights: muestra advertencia y no intenta generar el documento', async () => {
      component.insights = [];

      await component.exportarAWord();

      expect(component.alertMsg()).toContain('No hay insights para exportar');
      expect(component.alertClass()).toBe('alert-warning');
    });

    it('con insights: genera y descarga el documento Word sin lanzar excepción', async () => {
      component.proyecto = mockProyecto;
      component.insights = [...mockInsights];

      await expectAsync(component.exportarAWord()).toBeResolved();

      expect(component.alertClass()).toBe('alert-success');
      expect(component.alertMsg()).toContain('exportado correctamente');
    });
  });
});
