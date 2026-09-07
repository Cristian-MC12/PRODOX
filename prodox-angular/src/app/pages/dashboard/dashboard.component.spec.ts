// Autor: Cristian Santiago Martinez Cordoba — PRODOX
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
import { EvaluacionService } from '../../services/evaluacion.service';
import { AIInsightsService } from '../../services/ai-insights.service';
import { ProjectOverview, Risk, TrendAnalysis } from '../../models/analytics.model';
import { SprintDto } from '../../models/sprint.model';
import { ProjectMemberDto } from '../../models/project-member.model';
import { ProyectoMetricaDto } from '../../models/planeacion.model';
import { MetricaEvaluacionDetalleDto } from '../../models/evaluacion-detalle.model';
import { AIInsight } from '../../models/ai-insights.model';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let mockAnalyticsService: jasmine.SpyObj<AnalyticsService>;
  let mockSprintService: jasmine.SpyObj<SprintService>;
  let mockMemberService: jasmine.SpyObj<ProjectMemberService>;
  let mockPlaneacionService: jasmine.SpyObj<PlaneacionService>;
  let mockAuthService: { currentUser: jasmine.Spy };
  let mockEvaluacionService: jasmine.SpyObj<EvaluacionService>;
  let mockAiInsightsService: jasmine.SpyObj<AIInsightsService>;

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
    mockEvaluacionService = jasmine.createSpyObj('EvaluacionService', ['detalle']);
    mockAiInsightsService = jasmine.createSpyObj('AIInsightsService', ['getProjectInsights']);
    mockAuthService = { currentUser: jasmine.createSpy('currentUser') };
    // Por defecto, el usuario autenticado ES el Scrum Master del proyecto activo
    // (su email coincide con mockProyecto.scrumMasterEmail) — mantiene el
    // comportamiento previo (Dashboard completo) en las pruebas existentes que
    // no le conciernen a la visibilidad de las acciones IA.
    mockAuthService.currentUser.and.returnValue({ email: 'test@test.com', role: 'scrum_master', token: 't', userId: 'u1' });

    // Configurar defaultReturnValue para evitar errores
    mockSprintService.listar.and.returnValue(of([]));
    mockPlaneacionService.listarMetricas.and.returnValue(of(mockMetricas));
    // EvaluacionService/AIInsightsService también los usa ActivityFeedService
    // (componente hijo real, renderizado por fixture.detectChanges()) — sin
    // un valor por defecto, el spy sin configurar devuelve undefined y
    // ActivityFeedService.getProjectActivities() revienta en '.pipe()' antes
    // de llegar a los tests específicos de exportarReporteGeneral, que sí
    // configuran su propio retorno.
    mockEvaluacionService.detalle.and.returnValue(of([]));
    mockAiInsightsService.getProjectInsights.and.returnValue(of([]));

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
        { provide: AuthService, useValue: mockAuthService },
        { provide: EvaluacionService, useValue: mockEvaluacionService },
        { provide: AIInsightsService, useValue: mockAiInsightsService }
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

  // Corrección de auditoría (Dashboard/Evaluación): mockOverview.promedioHistorico
  // tiene 2 categorías ('Significado', 'Flexibilidad') — con la regla corregida
  // (ver dashboard.component.ts.hasComplianceData()), 2+ categorías heterogéneas
  // ya NO producen un compliance válido, así que este test usa explícitamente
  // una sola categoría para seguir probando el cálculo básico.
  it('debería calcular sprint compliance con una sola categoría (única combinación matemáticamente válida)', () => {
    component.projectOverview = { ...mockOverview, promedioHistorico: { Significado: 72 } };
    const compliance = component.getSprintCompliance();
    expect(compliance).toBe(72);
    expect(compliance).toBeGreaterThan(0);
    expect(compliance).toBeLessThanOrEqual(100);
  });

  it('NO debe multiplicar por 10 el promedio (bug del 503%)', () => {
    // promedioHistorico ya viene en escala 0-100 (ver EvaluacionService /
    // Variable.escalaMin-Max) — dividir entre 10 y volver a multiplicar por
    // 100 inflaba el valor real x10 (ej: 50.3 -> 503%). Con una sola categoría
    // (única combinación válida tras la corrección de mezcla de escalas, ver
    // hasComplianceData()), 50.3 debe redondear a 50, nunca a 503.
    component.projectOverview = { ...mockOverview, promedioHistorico: { A: 50.3 } };
    expect(component.getSprintCompliance()).toBe(50);
  });

  it('nunca debe devolver un porcentaje mayor a 100 ni NaN', () => {
    component.projectOverview = { ...mockOverview, promedioHistorico: { A: 9999 } };
    expect(component.getSprintCompliance()).toBe(100);

    component.projectOverview = { ...mockOverview, promedioHistorico: {} };
    expect(component.getSprintCompliance()).toBe(0);
  });

  // ════════════════════════════════════════════════════════════════════
  // Corrección de auditoría (Dashboard/Evaluación): el Dashboard promediaba
  // directamente categorías con escalas/unidades distintas (ej. Velocidad en
  // Story Points junto con Satisfacción en %) para producir un único
  // "Cumplimiento" Bueno/Regular/Malo — matemáticamente inválido sin
  // normalizar. Caso real: proyecto "Creación de un avatar Xabi".
  // ════════════════════════════════════════════════════════════════════

  it('con 2+ categorías heterogéneas (ej. Story Points de Velocidad y % de Satisfacción): NO promedia directamente, compliance no disponible', () => {
    // Valores elegidos a propósito para que un promedio directo diera un
    // número "creíble" (20 Story Points + 85% -> promedio ingenuo 52.5) —
    // el punto es que ESE número no debe calcularse en absoluto, sin
    // importar qué tan razonable luzca.
    component.projectOverview = {
      ...mockOverview,
      promedioHistorico: { Velocidad: 20, Satisfaccion: 85 }
    };

    expect(component.hasComplianceData()).toBeFalse();
    expect(component.getSprintCompliance()).toBe(0);
  });

  it('categoría sin resultados (promedioHistorico vacío): compliance no disponible, sin división por cero', () => {
    component.projectOverview = { ...mockOverview, promedioHistorico: {} };

    expect(component.hasComplianceData()).toBeFalse();
    expect(() => component.getSprintCompliance()).not.toThrow();
    expect(component.getSprintCompliance()).toBe(0);
  });

  it('con exactamente una categoría: compliance SÍ está disponible y refleja ese único valor', () => {
    component.projectOverview = { ...mockOverview, promedioHistorico: { Flexibilidad: 78 } };

    expect(component.hasComplianceData()).toBeTrue();
    expect(component.getSprintCompliance()).toBe(78);
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

  it('Scrum Master del proyecto: ve y puede usar las acciones Insights/Retrospectiva/Reporte/Reporte General', () => {
    mockAnalyticsService.getProjectOverview.and.returnValue(of(mockOverview));
    mockAnalyticsService.identifyRisks.and.returnValue(of(mockRisks));
    mockSprintService.getActivo.and.returnValue(of(mockSprint));
    mockMemberService.listar.and.returnValue(of(mockMembers));
    mockAnalyticsService.getSprintTrends.and.returnValue(of(mockTrends));
    // mockAuthService ya devuelve 'test@test.com', igual a mockProyecto.scrumMasterEmail
    fixture.detectChanges();

    expect(component.esScrumMasterDelProyecto).toBeTrue();

    // 4 acciones: Insights, Retrospectiva, Reporte (navegación) y Reporte
    // General (descarga directa de un .docx, ver exportarReporteGeneral()).
    const botones = fixture.nativeElement.querySelectorAll('.exec-actions button');
    expect(botones.length).toBe(4);
    expect(fixture.nativeElement.textContent).toContain('Reporte General');
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

  // Auditoría de reportes (Fase 4): Reporte General del proyecto — combina
  // projectOverview (ya cargado), EvaluacionService.detalle() y
  // AIInsightsService.getProjectInsights(), todos ya autorizados por
  // proyecto en el backend. proyectoId siempre sale de this.proyecto (el
  // proyecto activo cargado desde localStorage), nunca de un valor
  // editable por el usuario — no hay superficie IDOR nueva.
  describe('exportarReporteGeneral (Fase reportes)', () => {
    const mockMetricasDetalle: MetricaEvaluacionDetalleDto[] = [{
      variableId: 'v1', variableNombre: 'defectos', metricaNombre: 'Defectos por sprint',
      categoria: 'Calidad', tipoAlcance: 'grupal', frecuenciaCaptura: 'por_sprint',
      formulaTexto: null, registros: [], porSprint: [],
      estadisticas: {
        totalRegistros: 3, promedio: 5, minimo: 3, maximo: 7, primerValor: 7, ultimoValor: 3,
        cambio: -4, cambioPct: -57.1, tendencia: 'descendente', pendiente: -2,
        desviacionEstandar: 1.6, coeficienteVariacion: 32, variabilidad: 'media'
      }
    }];

    const mockInsightsDashboard: AIInsight[] = [{
      id: 'i1', proyectoId: 'proyecto-123', sprintId: null, type: 'TREND', severity: 'MEDIUM',
      title: 'Calidad en mejora', description: 'Los defectos bajaron', evidence: [],
      recommendation: null, confidence: 'HIGH', dismissed: false,
      createdAt: '2026-08-01T00:00:00Z', dismissedAt: null
    }];

    beforeEach(() => {
      mockAnalyticsService.getProjectOverview.and.returnValue(of(mockOverview));
      mockAnalyticsService.identifyRisks.and.returnValue(of(mockRisks));
      mockSprintService.getActivo.and.returnValue(of(mockSprint));
      mockMemberService.listar.and.returnValue(of(mockMembers));
      mockAnalyticsService.getSprintTrends.and.returnValue(of(mockTrends));
    });

    it('sin proyecto activo: no hace nada (no llama a los servicios)', () => {
      component.proyecto = null;
      component.exportarReporteGeneral();

      expect(mockEvaluacionService.detalle).not.toHaveBeenCalled();
      expect(mockAiInsightsService.getProjectInsights).not.toHaveBeenCalled();
    });

    it('con datos disponibles: genera el reporte sin lanzar excepción y muestra éxito', (done) => {
      mockEvaluacionService.detalle.and.returnValue(of(mockMetricasDetalle));
      mockAiInsightsService.getProjectInsights.and.returnValue(of(mockInsightsDashboard));

      fixture.detectChanges();

      setTimeout(() => {
        expect(component.projectOverview).toBeTruthy();
        expect(() => component.exportarReporteGeneral()).not.toThrow();

        // La descarga real involucra import() dinámico de 'docx'/'file-saver'
        // y Packer.toBlob() (serialización real del documento) — se le da
        // margen generoso para resolver antes de comprobar el resultado.
        setTimeout(() => {
          expect(component.alertClass()).toBe('alert-success');
          expect(component.alertMsg()).toContain('exportado correctamente');
          done();
        }, 1000);
      }, 100);
    }, 10000);

    it('si falla la carga de métricas o insights: degrada a listas vacías en vez de romper la exportación', (done) => {
      mockEvaluacionService.detalle.and.returnValue(throwError(() => new Error('falló')));
      mockAiInsightsService.getProjectInsights.and.returnValue(throwError(() => new Error('falló')));

      fixture.detectChanges();

      setTimeout(() => {
        expect(() => component.exportarReporteGeneral()).not.toThrow();
        setTimeout(() => {
          expect(component.alertClass()).toBe('alert-success');
          done();
        }, 1000);
      }, 100);
    }, 10000);
  });

  // ════════════════════════════════════════════════════════════════════
  // Auditoría Dashboard (selector de métrica individual): Satisfacción del
  // Cliente (escala 0-100 válida) y Velocidad en Story Points (sin escala
  // acotada) comparten a propósito la misma categoría "Impacto" — es
  // exactamente el escenario real reportado (dos métricas heterogéneas que
  // el Dashboard mezclaba antes de esta corrección).
  // ════════════════════════════════════════════════════════════════════
  describe('Selector de métrica individual (auditoría Dashboard)', () => {
    const mockSatisfaccion: MetricaEvaluacionDetalleDto = {
      variableId: 'v-satisfaccion',
      variableNombre: 'satisfaccion_cliente',
      variableDescripcion: 'Satisfacción del cliente',
      metricaNombre: 'Satisfacción del Cliente',
      categoria: 'Impacto',
      tipoAlcance: 'grupal',
      frecuenciaCaptura: 'por_sprint',
      formulaTexto: null,
      registros: [],
      estadisticas: {
        totalRegistros: 3, promedio: 80, minimo: 70, maximo: 90, primerValor: 70, ultimoValor: 80,
        cambio: 10, cambioPct: 14.3, tendencia: 'estable', pendiente: 0,
        desviacionEstandar: 10, coeficienteVariacion: 12.5, variabilidad: 'baja'
      },
      porSprint: [
        { sprintId: 's1', sprintNumero: 1, totalRegistros: 1, promedio: 70, minimo: 70, maximo: 70 },
        { sprintId: 's2', sprintNumero: 2, totalRegistros: 1, promedio: 90, minimo: 90, maximo: 90 },
        { sprintId: 's3', sprintNumero: 3, totalRegistros: 1, promedio: 80, minimo: 80, maximo: 80 }
      ],
      resultadosCalculados: [],
      escalaMin: 0, escalaMax: 100, escalaTipo: 'NUMERICA_ENTERA', tipoDato: 'numerico'
    };

    const mockVelocidad: MetricaEvaluacionDetalleDto = {
      variableId: 'v-velocidad',
      variableNombre: 'velocidad_story_points',
      variableDescripcion: 'Story points completados por sprint',
      metricaNombre: 'Velocidad (Story Points)',
      categoria: 'Impacto', // misma categoría que Satisfacción, a propósito
      tipoAlcance: 'grupal',
      frecuenciaCaptura: 'por_sprint',
      formulaTexto: null,
      registros: [],
      estadisticas: {
        totalRegistros: 3, promedio: 85, minimo: 80, maximo: 90, primerValor: 90, ultimoValor: 85,
        cambio: -5, cambioPct: -5.6, tendencia: 'estable', pendiente: -2.5,
        desviacionEstandar: 5, coeficienteVariacion: 5.9, variabilidad: 'baja'
      },
      porSprint: [
        { sprintId: 's1', sprintNumero: 1, totalRegistros: 1, promedio: 90, minimo: 90, maximo: 90 },
        { sprintId: 's2', sprintNumero: 2, totalRegistros: 1, promedio: 80, minimo: 80, maximo: 80 },
        { sprintId: 's3', sprintNumero: 3, totalRegistros: 1, promedio: 85, minimo: 85, maximo: 85 }
      ],
      resultadosCalculados: [],
      escalaMin: null, escalaMax: null, escalaTipo: null, tipoDato: 'numerico' // sin techo natural
    };

    const risksGlobal: Risk[] = [{
      proyectoId: 'proyecto-123', tipo: 'HIGH_VARIABILITY', severidad: 'MEDIUM',
      titulo: 'Alta variabilidad en Impacto', evidencia: 'CV 40%', categoriaAfectada: 'Impacto',
      detectedAt: '2026-08-11T10:00:00Z'
    }];
    const risksVelocidad: Risk[] = [{
      proyectoId: 'proyecto-123', tipo: 'DECLINING_METRIC', severidad: 'LOW',
      titulo: 'Velocidad en descenso', evidencia: 'Bajó 5.9% en 3 sprints', categoriaAfectada: 'Impacto',
      detectedAt: '2026-08-11T10:00:00Z'
    }];

    function cargarConMetricas(metricas: MetricaEvaluacionDetalleDto[]): void {
      mockAnalyticsService.getProjectOverview.and.returnValue(of(mockOverview));
      mockAnalyticsService.identifyRisks.and.callFake((_id: string, variableId?: string | null) => {
        if (variableId === 'v-velocidad') return of(risksVelocidad);
        return of(risksGlobal);
      });
      mockSprintService.getActivo.and.returnValue(of(mockSprint));
      mockMemberService.listar.and.returnValue(of(mockMembers));
      mockAnalyticsService.getSprintTrends.and.returnValue(of(mockTrends));
      mockEvaluacionService.detalle.and.returnValue(of(metricas));
      fixture.detectChanges();
    }

    // ── A. Selector ──────────────────────────────────────────────────
    it('A) carga "Todas las métricas" por defecto (metricaSeleccionada = null)', () => {
      cargarConMetricas([mockSatisfaccion, mockVelocidad]);
      expect(component.metricaSeleccionada()).toBeNull();
      expect(component.metricaActual).toBeNull();
    });

    it('A) carga las métricas activas con datos reales (metricasDetalle), no el catálogo completo', () => {
      cargarConMetricas([mockSatisfaccion, mockVelocidad]);
      expect(component.metricasDetalle.length).toBe(2);
      expect(component.metricasDetalle.map(m => m.metricaNombre)).toEqual(
        jasmine.arrayContaining(['Satisfacción del Cliente', 'Velocidad (Story Points)'])
      );
    });

    it('A) seleccionar la métrica A (Satisfacción) cambia la vista', () => {
      cargarConMetricas([mockSatisfaccion, mockVelocidad]);
      component.onMetricaChange('v-satisfaccion');
      expect(component.metricaSeleccionada()).toBe('v-satisfaccion');
      expect(component.metricaActual?.metricaNombre).toBe('Satisfacción del Cliente');
    });

    it('A) seleccionar la métrica B (Velocidad) cambia la vista', () => {
      cargarConMetricas([mockSatisfaccion, mockVelocidad]);
      component.onMetricaChange('v-velocidad');
      expect(component.metricaSeleccionada()).toBe('v-velocidad');
      expect(component.metricaActual?.metricaNombre).toBe('Velocidad (Story Points)');
    });

    it('A) volver a "" (Todas las métricas) restablece metricaSeleccionada a null', () => {
      cargarConMetricas([mockSatisfaccion, mockVelocidad]);
      component.onMetricaChange('v-satisfaccion');
      component.onMetricaChange('');
      expect(component.metricaSeleccionada()).toBeNull();
    });

    // ── B. Caso concreto: Satisfacción 70/90/80, Velocidad 90/80/85 ────
    it('B) seleccionando Satisfacción: la evolución es EXACTAMENTE 70, 90, 80 (sin mezclar con Velocidad)', () => {
      cargarConMetricas([mockSatisfaccion, mockVelocidad]);
      const evolucion = component.getEvolutionDataParaMetrica(mockSatisfaccion);
      expect(evolucion.map(p => p.value)).toEqual([70, 90, 80]);
    });

    it('B) seleccionando Velocidad: la evolución es EXACTAMENTE 90, 80, 85 (sin mezclar con Satisfacción)', () => {
      cargarConMetricas([mockSatisfaccion, mockVelocidad]);
      const evolucion = component.getEvolutionDataParaMetrica(mockVelocidad);
      expect(evolucion.map(p => p.value)).toEqual([90, 80, 85]);
    });

    it('B) "Todas las métricas": las dos series quedan separadas, cada una accesible de forma independiente', () => {
      cargarConMetricas([mockSatisfaccion, mockVelocidad]);
      expect(component.metricaActual).toBeNull(); // modo "Todas"
      const serieSatisfaccion = component.getEvolutionDataParaMetrica(component.metricasDetalle[0]).map(p => p.value);
      const serieVelocidad = component.getEvolutionDataParaMetrica(component.metricasDetalle[1]).map(p => p.value);
      expect(serieSatisfaccion).toEqual([70, 90, 80]);
      expect(serieVelocidad).toEqual([90, 80, 85]);
    });

    it('B) NUNCA aparece el promedio combinado 80/85/82.5 (mezcla de Satisfacción y Velocidad)', () => {
      cargarConMetricas([mockSatisfaccion, mockVelocidad]);
      const combinadoProhibido = [80, 85, 82.5];
      expect(component.getEvolutionDataParaMetrica(mockSatisfaccion).map(p => p.value)).not.toEqual(combinadoProhibido);
      expect(component.getEvolutionDataParaMetrica(mockVelocidad).map(p => p.value)).not.toEqual(combinadoProhibido);
    });

    it('B) NUNCA aparece un "cumplimiento general" del 83% fabricado combinando las dos métricas', () => {
      cargarConMetricas([mockSatisfaccion, mockVelocidad]);
      // En modo "Todas las métricas" el único dato agregado válido es un
      // conteo de métricas analizadas — nunca un porcentaje combinado.
      expect(component.metricasDetalle.length).toBe(2);
      expect(component.getCumplimientoMetrica(mockSatisfaccion)).toBe(0);
      expect(component.getCumplimientoMetrica(mockVelocidad)).toBe(0);
    });

    // ── C. Cumplimiento ─────────────────────────────────────────────
    // Corrección de auditoría (revisión final pre-commit): escalaMin/escalaMax
    // en el modelo de datos son el rango de VALIDACIÓN DE CAPTURA de la
    // variable (ver ParametrizacionService.validarEscalaEstructurada,
    // EjecucionService), NUNCA una meta u objetivo de cumplimiento — no existe
    // ningún campo "meta"/"sentidoMejora" en Variable/MetricParametrizacion.
    // Por eso NINGUNA métrica, tenga o no escala configurada, produce un %
    // de cumplimiento — "No aplica cumplimiento para esta métrica" es la
    // única respuesta honesta hoy.
    it('C) métrica CON escala configurada (0-100, ej. Satisfacción): tampoco se calcula cumplimiento — escalaMin/escalaMax es rango de captura, no una meta', () => {
      cargarConMetricas([mockSatisfaccion, mockVelocidad]);
      expect(mockSatisfaccion.escalaMin).toBe(0);
      expect(mockSatisfaccion.escalaMax).toBe(100);
      expect(component.tieneEscalaValidaParaCumplimiento(mockSatisfaccion)).toBeFalse();
      expect(component.getCumplimientoMetrica(mockSatisfaccion)).toBe(0);
    });

    it('C) métrica SIN escala configurada (Story Points sin techo): tampoco se calcula cumplimiento, mismo resultado que con escala', () => {
      cargarConMetricas([mockSatisfaccion, mockVelocidad]);
      expect(component.tieneEscalaValidaParaCumplimiento(mockVelocidad)).toBeFalse();
      expect(component.getCumplimientoMetrica(mockVelocidad)).toBe(0);
    });

    it('C) ningún valor de ultimoValor (incluyendo extremos) produce jamás un % de cumplimiento fabricado', () => {
      cargarConMetricas([mockSatisfaccion, mockVelocidad]);
      const metricaExtrema: MetricaEvaluacionDetalleDto = {
        ...mockSatisfaccion,
        estadisticas: { ...mockSatisfaccion.estadisticas, ultimoValor: 9999 }
      };
      expect(component.tieneEscalaValidaParaCumplimiento(metricaExtrema)).toBeFalse();
      expect(component.getCumplimientoMetrica(metricaExtrema)).toBe(0);
    });

    // ── D. Riesgos ──────────────────────────────────────────────────
    it('D) seleccionar una métrica vuelve a consultar identifyRisks con su variableId y filtra los riesgos mostrados', (done) => {
      cargarConMetricas([mockSatisfaccion, mockVelocidad]);
      component.onMetricaChange('v-velocidad');

      setTimeout(() => {
        expect(mockAnalyticsService.identifyRisks).toHaveBeenCalledWith('proyecto-123', 'v-velocidad');
        expect(component.risks).toEqual(risksVelocidad);
        done();
      }, 50);
    });

    it('D) "Todas las métricas" mantiene el comportamiento global existente (identifyRisks sin variableId)', () => {
      cargarConMetricas([mockSatisfaccion, mockVelocidad]);
      expect(mockAnalyticsService.identifyRisks).toHaveBeenCalledWith('proyecto-123');
      expect(component.risks).toEqual(risksGlobal);
    });

    // ── E. Mejor / peor sprint ──────────────────────────────────────
    it('E) mejor/peor sprint se calculan exclusivamente sobre la métrica indicada', () => {
      cargarConMetricas([mockSatisfaccion, mockVelocidad]);

      const mejorSatisfaccion = component.getMejorSprintMetrica(mockSatisfaccion);
      const peorSatisfaccion = component.getPeorSprintMetrica(mockSatisfaccion);
      expect(mejorSatisfaccion?.sprintNumero).toBe(2); // 90
      expect(peorSatisfaccion?.sprintNumero).toBe(1); // 70

      const mejorVelocidad = component.getMejorSprintMetrica(mockVelocidad);
      const peorVelocidad = component.getPeorSprintMetrica(mockVelocidad);
      expect(mejorVelocidad?.sprintNumero).toBe(1); // 90
      expect(peorVelocidad?.sprintNumero).toBe(2); // 80
    });

    it('E) mejor/peor sprint de una métrica no se ve afectado por los valores de otra métrica', () => {
      cargarConMetricas([mockSatisfaccion, mockVelocidad]);
      // Sprint 2 es el MEJOR para Satisfacción (90) pero el PEOR para
      // Velocidad (80) — cada métrica calcula su propio resultado, sin cruzar datos.
      expect(component.getMejorSprintMetrica(mockSatisfaccion)?.sprintNumero).toBe(2);
      expect(component.getPeorSprintMetrica(mockVelocidad)?.sprintNumero).toBe(2);
    });

    // ── F. Regresión ────────────────────────────────────────────────
    it('F) Dashboard con una sola métrica activa sigue funcionando', () => {
      cargarConMetricas([mockSatisfaccion]);
      expect(component.metricasDetalle.length).toBe(1);
      expect(component.state()).toBe('success');
      component.onMetricaChange('v-satisfaccion');
      expect(component.metricaActual?.metricaNombre).toBe('Satisfacción del Cliente');
    });

    it('F) Dashboard con cero métricas con datos no rompe (selector solo con "Todas las métricas")', () => {
      cargarConMetricas([]);
      expect(component.metricasDetalle).toEqual([]);
      expect(component.metricaActual).toBeNull();
      expect(() => component.getMejorSprintMetrica).not.toThrow();
    });

    it('F) Dashboard con múltiples métricas (3+) funciona sin mezclarlas', () => {
      const tercera: MetricaEvaluacionDetalleDto = {
        ...mockVelocidad, variableId: 'v-defectos', metricaNombre: 'Defectos encontrados', categoria: 'Flexibilidad'
      };
      cargarConMetricas([mockSatisfaccion, mockVelocidad, tercera]);
      expect(component.metricasDetalle.length).toBe(3);
      component.onMetricaChange('v-defectos');
      expect(component.metricaActual?.metricaNombre).toBe('Defectos encontrados');
    });

    it('F) cambiar de proyecto NO conserva la métrica seleccionada del proyecto anterior', () => {
      cargarConMetricas([mockSatisfaccion, mockVelocidad]);
      component.onMetricaChange('v-satisfaccion');
      expect(component.metricaSeleccionada()).toBe('v-satisfaccion');

      // Simula cambio de proyecto activo y recarga del Dashboard.
      component.proyecto = { ...mockProyecto, id: 'proyecto-999' };
      (component as any).loadDashboardData();

      expect(component.metricaSeleccionada()).toBeNull();
    });
  });
});
