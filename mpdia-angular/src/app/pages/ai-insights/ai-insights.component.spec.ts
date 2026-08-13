// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { ComponentFixture, TestBed, fakeAsync, tick, flush } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { Router } from '@angular/router';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { of, throwError } from 'rxjs';
import { AIInsightsComponent } from './ai-insights.component';
import { AIInsightsService } from '../../services/ai-insights.service';
import { AIInsight } from '../../models/ai-insights.model';
import { ProyectoDto } from '../../models/proyecto.model';

describe('AIInsightsComponent', () => {
  let component: AIInsightsComponent;
  let fixture: ComponentFixture<AIInsightsComponent>;
  let insightsService: jasmine.SpyObj<AIInsightsService>;
  let router: Router;

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

    await TestBed.configureTestingModule({
      imports: [
        AIInsightsComponent, 
        HttpClientTestingModule,
        RouterTestingModule
      ],
      providers: [
        { provide: AIInsightsService, useValue: insightsServiceSpy }
      ],
      schemas: [NO_ERRORS_SCHEMA] // Ignora componentes hijos como app-shell
    }).compileComponents();

    insightsService = TestBed.inject(AIInsightsService) as jasmine.SpyObj<AIInsightsService>;
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

  describe('generateInsights', () => {
    beforeEach(() => {
      component.proyecto = mockProyecto;
    });

    it('should generate insights successfully', fakeAsync(() => {
      insightsService.generateInsights.and.returnValue(of(mockInsights));

      component.generateInsights();

      // El Observable se completa inmediatamente (síncrono)
      // Solo necesitamos esperar el setTimeout final de 500ms
      tick(500);

      expect(component.generating()).toBe(false);
      expect(component.insights).toEqual(mockInsights);
      expect(component.alertMsg()).toContain('generado(s) exitosamente');
      
      // Flush remaining timers
      flush();
    }));

    it('should handle insufficient data', fakeAsync(() => {
      insightsService.generateInsights.and.returnValue(of([]));

      component.generateInsights();

      // El Observable se completa inmediatamente (síncrono)
      // Solo necesitamos esperar el setTimeout final de 500ms
      tick(500);

      expect(component.generating()).toBe(false);
      expect(component.alertMsg()).toContain('No se generaron insights');
      
      // Flush remaining timers
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

    it('should prevent double generation', () => {
      insightsService.generateInsights.and.returnValue(of(mockInsights));
      component.generating.set(true);

      component.generateInsights();

      expect(insightsService.generateInsights).not.toHaveBeenCalled();
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
});
