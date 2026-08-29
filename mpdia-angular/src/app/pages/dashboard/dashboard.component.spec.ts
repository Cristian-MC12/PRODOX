// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { AnalyticsService } from '../../services/analytics.service';
import { SprintService } from '../../services/sprint.service';
import { ProjectMemberService } from '../../services/project-member.service';
import { PlaneacionService } from '../../services/planeacion.service';
import { AuthService } from '../../services/auth.service';
import { ProjectOverview, Risk, TrendAnalysis } from '../../models/analytics.model';
import { SprintDto } from '../../models/sprint.model';
import { ProjectMemberDto } from '../../models/project-member.model';
import { ProyectoMetricaDto } from '../../models/planeacion.model';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let mockAnalyticsService: jasmine.SpyObj<AnalyticsService>;
  let mockSprintService: jasmine.SpyObj<SprintService>;
  let mockMemberService: jasmine.SpyObj<ProjectMemberService>;
  let mockPlaneacionService: jasmine.SpyObj<PlaneacionService>;
  let mockAuthService: { currentUser: jasmine.Spy };

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
    promedioHistorico: { 'Significado': 65, 'Flexibilidad': 72 },
    mejorSprint: { numero: 2, scoreGeneral: 91, razon: 'Excelente calidad' },
    peorSprint: { numero: 1, scoreGeneral: 68, razon: 'Primer sprint' },
    datosDisponibles: true
  };

  const mockMetricas: ProyectoMetricaDto[] = [
    {
      metricaId: 'm-1', codigo: 'SIG-CT-01', nombre: 'Capacidad de trabajo',
      descripcion: null, categoria: 'Significado', factor: null,
      seleccionada: true, seleccionadaAt: '2026-07-01T00:00:00Z',
      aprobada: true, aprobadaPor: 'sm@test.com', aprobadaAt: '2026-07-02T00:00:00Z',
      tieneVariable: true
    },
    {
      metricaId: 'm-2', codigo: 'FLX-GAE-01', nombre: 'Aprendiendo de los fracasos',
      descripcion: null, categoria: 'Flexibilidad', factor: null,
      seleccionada: true, seleccionadaAt: '2026-07-01T00:00:00Z',
      aprobada: true, aprobadaPor: 'sm@test.com', aprobadaAt: '2026-07-02T00:00:00Z',
      tieneVariable: true
    },
    {
      metricaId: 'm-3', codigo: 'IMP-CAL-01', nombre: 'Defectos encontrados',
      descripcion: null, categoria: 'Impacto', factor: null,
      seleccionada: true, seleccionadaAt: '2026-07-01T00:00:00Z',
      aprobada: false, aprobadaPor: null, aprobadaAt: null,
      tieneVariable: false
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

  const mockMembers: ProjectMemberDto[] = [
    {
      proyectoId: 'proyecto-123',
      userId: 'user-1',
      userEmail: 'user1@test.com',
      rol: 'SCRUM_MASTER',
      joinedAt: '2026-07-01T00:00:00Z'
    },
    {
      proyectoId: 'proyecto-123',
      userId: 'user-2',
      userEmail: 'user2@test.com',
      rol: 'SCRUM_MEMBER',
      joinedAt: '2026-07-02T00:00:00Z'
    }
  ];

  const mockTrends: TrendAnalysis[] = [
    {
      proyectoId: 'proyecto-123',
      categoria: 'Calidad',
      numeroSprints: 5,
      dataPoints: [
        { sprintNumero: 1, valor: 7.5, fecha: '2026-07-01' },
        { sprintNumero: 2, valor: 8.0, fecha: '2026-07-15' }
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
    mockSprintService = jasmine.createSpyObj('SprintService', ['getActivo', 'listar']);
    mockMemberService = jasmine.createSpyObj('ProjectMemberService', ['listar']);
    mockPlaneacionService = jasmine.createSpyObj('PlaneacionService', ['listarMetricas']);
    mockAuthService = { currentUser: jasmine.createSpy('currentUser') };
    // Por defecto, el usuario autenticado ES el Scrum Master del proyecto activo
    // (su email coincide con mockProyecto.scrumMasterEmail) — mantiene el
    // comportamiento previo (Dashboard completo) en las pruebas existentes que
    // no le conciernen a la visibilidad de las acciones IA.
    mockAuthService.currentUser.and.returnValue({ email: 'test@test.com', role: 'scrum_master', token: 't', userId: 'u1' });

    // Configurar defaultReturnValue para evitar errores
    mockSprintService.listar.and.returnValue(of([]));
    mockPlaneacionService.listarMetricas.and.returnValue(of(mockMetricas));

    await TestBed.configureTestingModule({
      imports: [
        DashboardComponent,
        HttpClientTestingModule,
        RouterTestingModule
      ],
      providers: [
        { provide: AnalyticsService, useValue: mockAnalyticsService },
        { provide: SprintService, useValue: mockSprintService },
        { provide: ProjectMemberService, useValue: mockMemberService },
        { provide: PlaneacionService, useValue: mockPlaneacionService },
        { provide: AuthService, useValue: mockAuthService }
      ]
    }).compileComponents();

    spyOn(localStorage, 'getItem').and.returnValue(JSON.stringify(mockProyecto));

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
  });

  it('debería crearse', () => {
    expect(component).toBeTruthy();
  });

  it('debería obtener proyecto activo desde localStorage', () => {
    mockAnalyticsService.getProjectOverview.and.returnValue(of(mockOverview));
    mockAnalyticsService.identifyRisks.and.returnValue(of(mockRisks));
    mockSprintService.getActivo.and.returnValue(of(mockSprint));
    mockMemberService.listar.and.returnValue(of(mockMembers));
    mockAnalyticsService.getSprintTrends.and.returnValue(of(mockTrends));

    fixture.detectChanges();

    expect(component.proyecto).toBeTruthy();
    expect(component.proyecto?.id).toBe('proyecto-123');
    expect(localStorage.getItem).toHaveBeenCalledWith('mpdia_proyecto_activo');
  });

  it('debería cargar overview correctamente', (done) => {
    mockAnalyticsService.getProjectOverview.and.returnValue(of(mockOverview));
    mockAnalyticsService.identifyRisks.and.returnValue(of(mockRisks));
    mockSprintService.getActivo.and.returnValue(of(mockSprint));
    mockMemberService.listar.and.returnValue(of(mockMembers));
    mockAnalyticsService.getSprintTrends.and.returnValue(of(mockTrends));

    fixture.detectChanges();

    setTimeout(() => {
      expect(component.projectOverview).toEqual(mockOverview);
      expect(component.state()).toBe('success');
      done();
    }, 100);
  });

  it('debería cargar risks correctamente', (done) => {
    mockAnalyticsService.getProjectOverview.and.returnValue(of(mockOverview));
    mockAnalyticsService.identifyRisks.and.returnValue(of(mockRisks));
    mockSprintService.getActivo.and.returnValue(of(mockSprint));
    mockMemberService.listar.and.returnValue(of(mockMembers));
    mockAnalyticsService.getSprintTrends.and.returnValue(of(mockTrends));

    fixture.detectChanges();

    setTimeout(() => {
      expect(component.risks.length).toBeGreaterThan(0);
      expect(component.risks[0].titulo).toBe('Calidad en descenso');
      done();
    }, 100);
  });

  it('debería cargar miembros correctamente', (done) => {
    mockAnalyticsService.getProjectOverview.and.returnValue(of(mockOverview));
    mockAnalyticsService.identifyRisks.and.returnValue(of(mockRisks));
    mockSprintService.getActivo.and.returnValue(of(mockSprint));
    mockMemberService.listar.and.returnValue(of(mockMembers));
    mockAnalyticsService.getSprintTrends.and.returnValue(of(mockTrends));

    fixture.detectChanges();

    setTimeout(() => {
      expect(component.totalMiembros).toBe(2);
      done();
    }, 100);
  });

  it('debería manejar error cuando falla getProjectOverview', (done) => {
    mockAnalyticsService.getProjectOverview.and.returnValue(throwError(() => new Error('API Error')));
    mockAnalyticsService.identifyRisks.and.returnValue(of(mockRisks));
    mockSprintService.getActivo.and.returnValue(of(mockSprint));
    mockMemberService.listar.and.returnValue(of(mockMembers));

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
    mockAnalyticsService.identifyRisks.and.returnValue(of([]));
    mockSprintService.getActivo.and.returnValue(of(mockSprint));
    mockMemberService.listar.and.returnValue(of([]));

    fixture.detectChanges();

    setTimeout(() => {
      expect(component.state()).toBe('insufficient-data');
      done();
    }, 100);
  });

  it('debería permitir retry después de error', (done) => {
    mockAnalyticsService.getProjectOverview.and.returnValue(throwError(() => new Error('Error')));
    mockAnalyticsService.identifyRisks.and.returnValue(of([]));
    mockSprintService.getActivo.and.returnValue(throwError(() => new Error('Error')));
    mockMemberService.listar.and.returnValue(of([]));

    fixture.detectChanges();

    setTimeout(() => {
      expect(component.state()).toBe('error');
      
      // Configurar mocks para éxito en retry
      mockAnalyticsService.getProjectOverview.and.returnValue(of(mockOverview));
      mockAnalyticsService.identifyRisks.and.returnValue(of(mockRisks));
      mockSprintService.getActivo.and.returnValue(of(mockSprint));
      mockMemberService.listar.and.returnValue(of(mockMembers));
      mockAnalyticsService.getSprintTrends.and.returnValue(of(mockTrends));
      
      component.retry();
      
      setTimeout(() => {
        expect(mockAnalyticsService.getProjectOverview).toHaveBeenCalledTimes(2);
        done();
      }, 100);
    }, 100);
  });

  it('debería calcular sprint compliance', () => {
    component.projectOverview = mockOverview;
    const compliance = component.getSprintCompliance();
    expect(compliance).toBeGreaterThan(0);
    expect(compliance).toBeLessThanOrEqual(100);
  });

  it('NO debe multiplicar por 10 el promedio (bug del 503%)', () => {
    // promedioHistorico ya viene en escala 0-100 (ver EvaluacionService /
    // Variable.escalaMin-Max) — dividir entre 10 y volver a multiplicar por
    // 100 inflaba el valor real x10 (ej: 50.3 -> 503%). El promedio de estos
    // dos valores es 68.5 -> debe redondear a 69, nunca a 685+.
    component.projectOverview = { ...mockOverview, promedioHistorico: { A: 50, B: 51 } };
    expect(component.getSprintCompliance()).toBe(51); // avg(50,51)=50.5 -> round 51
  });

  it('nunca debe devolver un porcentaje mayor a 100 ni NaN', () => {
    component.projectOverview = { ...mockOverview, promedioHistorico: { A: 9999 } };
    expect(component.getSprintCompliance()).toBe(100);

    component.projectOverview = { ...mockOverview, promedioHistorico: {} };
    expect(component.getSprintCompliance()).toBe(0);
  });

  it('getTotalMetricas cuenta solo métricas aprobadas del proyecto (no el catálogo global)', () => {
    component.metricas = mockMetricas; // 2 aprobadas, 1 no aprobada
    expect(component.getTotalMetricas()).toBe(2);
  });

  it('el total del donut de distribución coincide con las métricas activas (bug 151 vs 3)', () => {
    component.metricas = mockMetricas;
    const segments = component.getDistributionSegments();
    const total = segments.reduce((sum, s) => sum + s.value, 0);
    expect(total).toBe(component.getTotalMetricas());
    expect(total).toBe(2);
  });

  it('getEvolutionData no inventa puntos cuando no hay tendencias reales', () => {
    component.trends = [];
    component.projectOverview = mockOverview;
    expect(component.getEvolutionData()).toEqual([]);
  });

  it('getSprintsByStatus usa los estados reales del modelo (no Planificación/Cierre inventados)', () => {
    component.sprints = [
      { ...mockSprint, id: 's1', estado: 'finalizado' },
      { ...mockSprint, id: 's2', estado: 'finalizado' },
      { ...mockSprint, id: 's3', estado: 'en_ejecucion' },
      { ...mockSprint, id: 's4', estado: 'pendiente' }
    ];
    const porEstado = component.getSprintsByStatus();
    expect(porEstado.map(s => s.label)).toEqual(['Pendiente', 'En ejecución', 'Finalizado', 'Reabierto']);
    expect(porEstado.find(s => s.label === 'Finalizado')?.value).toBe(2);
    expect(porEstado.find(s => s.label === 'En ejecución')?.value).toBe(1);
    expect(porEstado.find(s => s.label === 'Pendiente')?.value).toBe(1);
    expect(porEstado.find(s => s.label === 'Reabierto')?.value).toBe(0);
  });

  it('Scrum Master del proyecto: ve y puede usar las acciones Insights/Retrospectiva/Reporte', () => {
    mockAnalyticsService.getProjectOverview.and.returnValue(of(mockOverview));
    mockAnalyticsService.identifyRisks.and.returnValue(of(mockRisks));
    mockSprintService.getActivo.and.returnValue(of(mockSprint));
    mockMemberService.listar.and.returnValue(of(mockMembers));
    mockAnalyticsService.getSprintTrends.and.returnValue(of(mockTrends));
    // mockAuthService ya devuelve 'test@test.com', igual a mockProyecto.scrumMasterEmail
    fixture.detectChanges();

    expect(component.esScrumMasterDelProyecto).toBeTrue();

    const botones = fixture.nativeElement.querySelectorAll('.exec-actions button');
    expect(botones.length).toBe(3);
  });

  it('Miembro normal: puede consultar todo el Dashboard pero NO ve las acciones de generación IA', () => {
    mockAuthService.currentUser.and.returnValue({ email: 'miembro@test.com', role: 'scrum_member', token: 't', userId: 'u2' });
    mockAnalyticsService.getProjectOverview.and.returnValue(of(mockOverview));
    mockAnalyticsService.identifyRisks.and.returnValue(of(mockRisks));
    mockSprintService.getActivo.and.returnValue(of(mockSprint));
    mockMemberService.listar.and.returnValue(of(mockMembers));
    mockAnalyticsService.getSprintTrends.and.returnValue(of(mockTrends));
    fixture.detectChanges();

    expect(component.esScrumMasterDelProyecto).toBeFalse();
    expect(component.state()).toBe('success');
    // El Dashboard (KPIs, gráficas, riesgos, sprints, actividad) sigue siendo
    // visible: la restricción es únicamente sobre las 3 acciones IA.
    expect(component.projectOverview).toEqual(mockOverview);
    expect(component.risks.length).toBeGreaterThan(0);

    const botones = fixture.nativeElement.querySelectorAll('.exec-actions button');
    expect(botones.length).toBe(0);
  });

  it('debería manejar error sin proyecto activo', () => {
    (localStorage.getItem as jasmine.Spy).and.returnValue(null);

    const component2 = TestBed.createComponent(DashboardComponent).componentInstance;
    component2.ngOnInit();

    expect(component2.state()).toBe('error');
    expect(component2.alertMsg()).toContain('No hay proyecto activo');
  });
});
