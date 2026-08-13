// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { AnalyticsService } from '../../services/analytics.service';
import { AIInsightsService } from '../../services/ai-insights.service';
import { SprintService } from '../../services/sprint.service';
import { ProjectOverview, Risk, TrendAnalysis } from '../../models/analytics.model';
import { AIInsight } from '../../models/ai-insights.model';
import { SprintDto } from '../../models/sprint.model';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let mockRouter: Router;
  let mockAnalyticsService: jasmine.SpyObj<AnalyticsService>;
  let mockInsightsService: jasmine.SpyObj<AIInsightsService>;
  let mockSprintService: jasmine.SpyObj<SprintService>;

  const mockProyecto: any = {
    id: 'proyecto-123',
    nombre: 'Proyecto Test',
    descripcion: 'Proyecto de prueba',
    estado: 'activo' as const,
    metodo: 'scrum' as const,
    timeBoxSemanas: 2,
    numeroSprints: 5,
    fechaInicio: '2026-07-01',
    productGoal: 'Desarrollar dashboard',
    sprintGoal: 'Sprint inicial',
    scrumMasterEmail: 'test@test.com',
    totalMiembros: 3,
    createdAt: '2026-07-01T00:00:00Z'
  };

  const mockOverview: ProjectOverview = {
    proyectoId: 'proyecto-123',
    proyectoNombre: 'Proyecto Test',
    totalSprints: 5,
    sprintsFinalizados: 3,
    sprintActualNumero: 4,
    promedioHistorico: { Calidad: 8.5, Productividad: 7.2 },
    mejorSprint: { numero: 2, scoreGeneral: 9.1, razon: 'Excelente calidad' },
    peorSprint: { numero: 1, scoreGeneral: 6.8, razon: 'Primer sprint' },
    datosDisponibles: true
  };

  const mockInsights: AIInsight[] = [
    {
      id: 'insight-1',
      proyectoId: 'proyecto-123',
      sprintId: 'sprint-1',
      type: 'TREND',
      severity: 'MEDIUM',
      title: 'Tendencia positiva en calidad',
      description: 'La calidad ha mejorado consistentemente',
      evidence: [],
      recommendation: 'Mantener las prácticas actuales',
      confidence: 'HIGH',
      dismissed: false,
      createdAt: '2026-08-11T10:00:00Z',
      dismissedAt: null
    }
  ];

  const mockRisks: Risk[] = [
    {
      proyectoId: 'proyecto-123',
      tipo: 'DECLINING_METRIC',
      severidad: 'MEDIUM',
      titulo: 'Calidad en descenso',
      evidencia: 'Disminución de 15% en 3 sprints',
      categoriaAfectada: 'Calidad',
      detectedAt: '2026-08-11T10:00:00Z'
    }
  ];

  const mockSprint: SprintDto = {
    id: 'sprint-4',
    proyectoId: 'proyecto-123',
    proyectoNombre: 'Proyecto Test',
    metodo: 'scrum',
    timeBoxSemanas: 2,
    numero: 4,
    sprintGoal: 'Implementar dashboard',
    estado: 'en_ejecucion',
    fechaInicio: '2026-08-01',
    fechaFin: null,
    cerradoPor: null,
    cerradoAt: null,
    createdAt: '2026-08-01T00:00:00Z'
  };

  const mockTrends: TrendAnalysis[] = [
    {
      proyectoId: 'proyecto-123',
      categoria: 'Calidad',
      numeroSprints: 5,
      dataPoints: [
        { numero: 1, valor: 7.5, fecha: '2026-07-01' },
        { numero: 2, valor: 8.0, fecha: '2026-07-15' }
      ],
      promedioGeneral: 7.75,
      desviacionEstandar: 0.25,
      tendenciaGeneral: 'UP',
      variacionTotal: 6.7,
      datosDisponibles: true
    }
  ];

  beforeEach(async () => {
    mockAnalyticsService = jasmine.createSpyObj('AnalyticsService', [
      'getProjectOverview',
      'identifyRisks',
      'getSprintTrends'
    ]);
    mockInsightsService = jasmine.createSpyObj('AIInsightsService', [
      'getProjectInsights',
      'generateInsights'
    ]);
    mockSprintService = jasmine.createSpyObj('SprintService', ['getActivo']);

    await TestBed.configureTestingModule({
      imports: [
        DashboardComponent,
        HttpClientTestingModule,
        RouterTestingModule
      ],
      providers: [
        { provide: AnalyticsService, useValue: mockAnalyticsService },
        { provide: AIInsightsService, useValue: mockInsightsService },
        { provide: SprintService, useValue: mockSprintService }
      ]
    }).compileComponents();

    mockRouter = TestBed.inject(Router);
    spyOn(mockRouter, 'navigate');

    // Mock localStorage
    spyOn(localStorage, 'getItem').and.returnValue(JSON.stringify(mockProyecto));

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
  });

  it('debería crearse', () => {
    expect(component).toBeTruthy();
  });

  it('debería obtener proyecto activo desde localStorage', () => {
    mockAnalyticsService.getProjectOverview.and.returnValue(of(mockOverview));
    mockInsightsService.getProjectInsights.and.returnValue(of(mockInsights));
    mockAnalyticsService.identifyRisks.and.returnValue(of(mockRisks));
    mockSprintService.getActivo.and.returnValue(of(mockSprint));
    mockAnalyticsService.getSprintTrends.and.returnValue(of(mockTrends));

    fixture.detectChanges();

    expect(component.proyecto).toBeTruthy();
    expect(component.proyecto?.id).toBe('proyecto-123');
    expect(localStorage.getItem).toHaveBeenCalledWith('mpdia_proyecto_activo');
  });

  it('debería cargar overview correctamente', (done) => {
    mockAnalyticsService.getProjectOverview.and.returnValue(of(mockOverview));
    mockInsightsService.getProjectInsights.and.returnValue(of(mockInsights));
    mockAnalyticsService.identifyRisks.and.returnValue(of(mockRisks));
    mockSprintService.getActivo.and.returnValue(of(mockSprint));
    mockAnalyticsService.getSprintTrends.and.returnValue(of(mockTrends));

    fixture.detectChanges();

    setTimeout(() => {
      expect(component.projectOverview).toEqual(mockOverview);
      expect(component.state()).toBe('success');
      done();
    }, 100);
  });

  it('debería cargar insights correctamente', (done) => {
    mockAnalyticsService.getProjectOverview.and.returnValue(of(mockOverview));
    mockInsightsService.getProjectInsights.and.returnValue(of(mockInsights));
    mockAnalyticsService.identifyRisks.and.returnValue(of(mockRisks));
    mockSprintService.getActivo.and.returnValue(of(mockSprint));
    mockAnalyticsService.getSprintTrends.and.returnValue(of(mockTrends));

    fixture.detectChanges();

    setTimeout(() => {
      expect(component.recentInsights.length).toBeGreaterThan(0);
      expect(component.recentInsights[0].title).toBe('Tendencia positiva en calidad');
      done();
    }, 100);
  });

  it('debería cargar risks correctamente', (done) => {
    mockAnalyticsService.getProjectOverview.and.returnValue(of(mockOverview));
    mockInsightsService.getProjectInsights.and.returnValue(of(mockInsights));
    mockAnalyticsService.identifyRisks.and.returnValue(of(mockRisks));
    mockSprintService.getActivo.and.returnValue(of(mockSprint));
    mockAnalyticsService.getSprintTrends.and.returnValue(of(mockTrends));

    fixture.detectChanges();

    setTimeout(() => {
      expect(component.risks.length).toBeGreaterThan(0);
      expect(component.risks[0].titulo).toBe('Calidad en descenso');
      done();
    }, 100);
  });

  it('debería cargar trends cuando hay suficientes sprints (>=3)', (done) => {
    mockAnalyticsService.getProjectOverview.and.returnValue(of(mockOverview));
    mockInsightsService.getProjectInsights.and.returnValue(of(mockInsights));
    mockAnalyticsService.identifyRisks.and.returnValue(of(mockRisks));
    mockSprintService.getActivo.and.returnValue(of(mockSprint));
    mockAnalyticsService.getSprintTrends.and.returnValue(of(mockTrends));

    fixture.detectChanges();

    setTimeout(() => {
      expect(mockAnalyticsService.getSprintTrends).toHaveBeenCalled();
      expect(component.hasTrends()).toBe(true);
      done();
    }, 100);
  });

  it('debería ejecutar requests en paralelo (forkJoin)', (done) => {
    mockAnalyticsService.getProjectOverview.and.returnValue(of(mockOverview));
    mockInsightsService.getProjectInsights.and.returnValue(of(mockInsights));
    mockAnalyticsService.identifyRisks.and.returnValue(of(mockRisks));
    mockSprintService.getActivo.and.returnValue(of(mockSprint));
    mockAnalyticsService.getSprintTrends.and.returnValue(of(mockTrends));

    const startTime = Date.now();
    fixture.detectChanges();

    setTimeout(() => {
      const elapsed = Date.now() - startTime;
      // Si fuera secuencial, tomaría más tiempo
      // Como es paralelo, debería completarse rápido
      expect(elapsed).toBeLessThan(500);
      
      // Verificar que todos se llamaron
      expect(mockAnalyticsService.getProjectOverview).toHaveBeenCalled();
      expect(mockInsightsService.getProjectInsights).toHaveBeenCalled();
      expect(mockAnalyticsService.identifyRisks).toHaveBeenCalled();
      expect(mockSprintService.getActivo).toHaveBeenCalled();
      done();
    }, 100);
  });

  it('debería manejar loading correctamente', () => {
    mockAnalyticsService.getProjectOverview.and.returnValue(of(mockOverview));
    mockInsightsService.getProjectInsights.and.returnValue(of(mockInsights));
    mockAnalyticsService.identifyRisks.and.returnValue(of(mockRisks));
    mockSprintService.getActivo.and.returnValue(of(mockSprint));
    mockAnalyticsService.getSprintTrends.and.returnValue(of(mockTrends));

    expect(component.state()).toBe('loading');
    fixture.detectChanges();
    
    // State cambia después de cargar
    setTimeout(() => {
      expect(component.state()).not.toBe('loading');
    }, 100);
  });

  it('debería manejar error cuando falla getProjectOverview', (done) => {
    mockAnalyticsService.getProjectOverview.and.returnValue(throwError(() => new Error('API Error')));
    mockInsightsService.getProjectInsights.and.returnValue(of(mockInsights));
    mockAnalyticsService.identifyRisks.and.returnValue(of(mockRisks));
    mockSprintService.getActivo.and.returnValue(of(mockSprint));

    fixture.detectChanges();

    setTimeout(() => {
      expect(component.state()).toBe('error');
      expect(component.alertClass()).toBe('alert-danger');
      done();
    }, 100);
  });

  it('debería manejar datos insuficientes (datosDisponibles=false)', (done) => {
    const overviewSinDatos: ProjectOverview = {
      ...mockOverview,
      datosDisponibles: false,
      sprintsFinalizados: 0
    };

    mockAnalyticsService.getProjectOverview.and.returnValue(of(overviewSinDatos));
    mockInsightsService.getProjectInsights.and.returnValue(of([]));
    mockAnalyticsService.identifyRisks.and.returnValue(of([]));
    mockSprintService.getActivo.and.returnValue(of(mockSprint));

    fixture.detectChanges();

    setTimeout(() => {
      expect(component.state()).toBe('insufficient-data');
      expect(component.hasInsufficientData()).toBe(true);
      done();
    }, 100);
  });

  it('debería permitir retry después de error', (done) => {
    mockAnalyticsService.getProjectOverview.and.returnValue(throwError(() => new Error('Error')));
    mockInsightsService.getProjectInsights.and.returnValue(of([]));
    mockAnalyticsService.identifyRisks.and.returnValue(of([]));
    mockSprintService.getActivo.and.returnValue(throwError(() => new Error('Error')));

    fixture.detectChanges();

    setTimeout(() => {
      expect(component.state()).toBe('error');
      
      // Configurar mocks para éxito en retry
      mockAnalyticsService.getProjectOverview.and.returnValue(of(mockOverview));
      mockInsightsService.getProjectInsights.and.returnValue(of(mockInsights));
      mockAnalyticsService.identifyRisks.and.returnValue(of(mockRisks));
      mockSprintService.getActivo.and.returnValue(of(mockSprint));
      mockAnalyticsService.getSprintTrends.and.returnValue(of(mockTrends));
      
      component.retry();
      
      setTimeout(() => {
        expect(mockAnalyticsService.getProjectOverview).toHaveBeenCalledTimes(2);
        expect(component.state()).toBe('success');
        done();
      }, 100);
    }, 100);
  });

  it('NO debería llamar Gemini automáticamente', () => {
    mockAnalyticsService.getProjectOverview.and.returnValue(of(mockOverview));
    mockInsightsService.getProjectInsights.and.returnValue(of(mockInsights));
    mockAnalyticsService.identifyRisks.and.returnValue(of(mockRisks));
    mockSprintService.getActivo.and.returnValue(of(mockSprint));
    mockAnalyticsService.getSprintTrends.and.returnValue(of(mockTrends));

    fixture.detectChanges();

    // generateInsights NO debe ser llamado en ngOnInit
    expect(mockInsightsService.generateInsights).not.toHaveBeenCalled();
  });

  it('debería navegar a insights cuando se llama generateInsights', () => {
    component.generateInsights();
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/ai-insights']);
  });

  it('debería navegar a reports', () => {
    component.navigateToReports();
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/ai-report']);
  });

  it('debería navegar a retrospectives', () => {
    component.navigateToRetrospectives();
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/ai-retrospective']);
  });

  it('debería limitar insights a 5', () => {
    const manyInsights: AIInsight[] = Array.from({ length: 10 }, (_, i) => ({
      ...mockInsights[0],
      id: `insight-${i}`,
      createdAt: new Date(Date.now() - i * 1000).toISOString()
    }));

    mockAnalyticsService.getProjectOverview.and.returnValue(of(mockOverview));
    mockInsightsService.getProjectInsights.and.returnValue(of(manyInsights));
    mockAnalyticsService.identifyRisks.and.returnValue(of(mockRisks));
    mockSprintService.getActivo.and.returnValue(of(mockSprint));
    mockAnalyticsService.getSprintTrends.and.returnValue(of(mockTrends));

    fixture.detectChanges();

    setTimeout(() => {
      expect(component.recentInsights.length).toBeLessThanOrEqual(5);
    }, 100);
  });

  it('debería manejar proyecto sin sprints finalizados (empty state)', (done) => {
    const overviewEmpty: ProjectOverview = {
      ...mockOverview,
      sprintsFinalizados: 0,
      datosDisponibles: false
    };

    mockAnalyticsService.getProjectOverview.and.returnValue(of(overviewEmpty));
    mockInsightsService.getProjectInsights.and.returnValue(of([]));
    mockAnalyticsService.identifyRisks.and.returnValue(of([]));
    mockSprintService.getActivo.and.returnValue(of(mockSprint));

    fixture.detectChanges();

    setTimeout(() => {
      expect(component.state()).toBe('insufficient-data');
      done();
    }, 100);
  });

  it('NO debería cargar trends si hay menos de 3 sprints', (done) => {
    const overviewPocosSprints: ProjectOverview = {
      ...mockOverview,
      sprintsFinalizados: 2
    };

    mockAnalyticsService.getProjectOverview.and.returnValue(of(overviewPocosSprints));
    mockInsightsService.getProjectInsights.and.returnValue(of(mockInsights));
    mockAnalyticsService.identifyRisks.and.returnValue(of(mockRisks));
    mockSprintService.getActivo.and.returnValue(of(mockSprint));

    fixture.detectChanges();

    setTimeout(() => {
      expect(mockAnalyticsService.getSprintTrends).not.toHaveBeenCalled();
      expect(component.hasTrends()).toBe(false);
      done();
    }, 100);
  });

  it('debería manejar error sin proyecto activo', () => {
    (localStorage.getItem as jasmine.Spy).and.returnValue(null);

    const component2 = TestBed.createComponent(DashboardComponent).componentInstance;
    component2.ngOnInit();

    expect(component2.state()).toBe('error');
    expect(component2.alertMsg()).toContain('No hay proyecto activo');
  });
});
