// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { ComponentFixture, TestBed, fakeAsync, tick, flush } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { FormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { AIReportComponent } from './ai-report.component';
import { AIReportService } from '../../services/ai-report.service';
import { SprintService } from '../../services/sprint.service';
import { AISprintReport } from '../../models/ai-reports.model';
import { SprintDto } from '../../models/sprint.model';
import { AIInsight } from '../../models/ai-insights.model';

describe('AIReportComponent', () => {
  let component: AIReportComponent;
  let fixture: ComponentFixture<AIReportComponent>;
  let reportService: jasmine.SpyObj<AIReportService>;
  let sprintService: jasmine.SpyObj<SprintService>;

  const mockSprints: SprintDto[] = [
    {
      id: 'sprint-1',
      proyectoId: 'proyecto-1',
      proyectoNombre: 'Proyecto Test',
      metodo: 'scrum',
      timeBoxSemanas: 2,
      numero: 1,
      sprintGoal: 'Sprint 1',
      estado: 'finalizado',
      fechaInicio: '2026-07-01',
      fechaFin: '2026-07-14',
      cerradoPor: null,
      cerradoAt: null,
      createdAt: '2026-07-01T00:00:00Z'
    },
    {
      id: 'sprint-2',
      proyectoId: 'proyecto-1',
      proyectoNombre: 'Proyecto Test',
      metodo: 'scrum',
      timeBoxSemanas: 2,
      numero: 2,
      sprintGoal: 'Sprint 2',
      estado: 'finalizado',
      fechaInicio: '2026-07-15',
      fechaFin: '2026-07-28',
      cerradoPor: null,
      cerradoAt: null,
      createdAt: '2026-07-15T00:00:00Z'
    }
  ];

  const mockReport: AISprintReport = {
    sprintId: 'sprint-2',
    sprintNumero: 2,
    sprintGoal: 'Sprint 2',
    fechaInicio: '2026-07-15',
    fechaFin: '2026-07-28',
    resumenEjecutivo: 'Sprint exitoso',
    metricas: { Calidad: 8.5, Productividad: 7.2 },
    highlights: ['Calidad superior', 'Productividad estable'],
    concerns: [],
    insights: [],
    recomendaciones: 'Continuar con prácticas actuales',
    generatedAt: '2026-08-11T22:00:00Z'
  };

  beforeEach(async () => {
    const reportServiceSpy = jasmine.createSpyObj('AIReportService', ['generateSprintReport']);
    const sprintServiceSpy = jasmine.createSpyObj('SprintService', ['listar', 'getActivo']);

    await TestBed.configureTestingModule({
      imports: [
        AIReportComponent,
        HttpClientTestingModule,
        RouterTestingModule,
        FormsModule
      ],
      providers: [
        { provide: AIReportService, useValue: reportServiceSpy },
        { provide: SprintService, useValue: sprintServiceSpy }
      ]
    }).compileComponents();

    reportService = TestBed.inject(AIReportService) as jasmine.SpyObj<AIReportService>;
    sprintService = TestBed.inject(SprintService) as jasmine.SpyObj<SprintService>;
    
    // Mock getActivo para ShellComponent
    sprintService.getActivo.and.returnValue(of({
      id: 'sprint-activo',
      proyectoId: 'proyecto-1',
      proyectoNombre: 'Proyecto Test',
      metodo: 'scrum',
      timeBoxSemanas: 2,
      numero: 3,
      sprintGoal: 'Sprint Activo',
      estado: 'en_ejecucion',
      fechaInicio: '2026-08-01',
      fechaFin: null,
      cerradoPor: null,
      cerradoAt: null,
      createdAt: '2026-08-01T00:00:00Z'
    }));

    // Setup localStorage mock
    localStorage.setItem('mpdia_proyecto_activo', JSON.stringify({
      id: 'proyecto-1',
      nombre: 'Proyecto Test'
    }));

    fixture = TestBed.createComponent(AIReportComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('debería crearse', () => {
    expect(component).toBeTruthy();
  });

  it('debería cargar sprints al iniciar', () => {
    sprintService.listar.and.returnValue(of(mockSprints));

    fixture.detectChanges();

    expect(component.sprints.length).toBe(2);
    expect(sprintService.listar).toHaveBeenCalledWith('proyecto-1');
  });

  it('debería generar reporte exitosamente', fakeAsync(() => {
    sprintService.listar.and.returnValue(of(mockSprints));
    reportService.generateSprintReport.and.returnValue(of(mockReport));

    fixture.detectChanges();

    component.selectedSprintId = 'sprint-2';
    component.generateReport();

    // El Observable se completa inmediatamente (síncrono)
    // Solo necesitamos esperar el setTimeout final de 500ms
    tick(500);

    expect(reportService.generateSprintReport).toHaveBeenCalledWith('sprint-2');
    expect(component.report).toEqual(mockReport);
    expect(component.generating()).toBe(false);
    
    // Flush remaining timers
    flush();
  }));

  it('debería manejar error 403', () => {
    sprintService.listar.and.returnValue(of(mockSprints));
    reportService.generateSprintReport.and.returnValue(
      throwError(() => ({ status: 403 }))
    );

    fixture.detectChanges();

    component.selectedSprintId = 'sprint-2';
    component.generateReport();

    expect(component.report).toBeNull();
    expect(component.alertMsg()).toContain('permisos');
  });

  it('debería manejar error 429 (rate limit)', () => {
    sprintService.listar.and.returnValue(of(mockSprints));
    reportService.generateSprintReport.and.returnValue(
      throwError(() => ({ status: 429 }))
    );

    fixture.detectChanges();

    component.selectedSprintId = 'sprint-2';
    component.generateReport();

    expect(component.report).toBeNull();
    expect(component.alertMsg()).toContain('límite');
  });

  it('debería detectar datos insuficientes', () => {
    const reportWithInsufficientData: AISprintReport = {
      ...mockReport,
      resumenEjecutivo: 'Datos insuficientes para análisis',
      metricas: {}
    };

    component.report = reportWithInsufficientData;

    expect(component.hasInsufficientData()).toBe(true);
  });

  it('debería obtener array de métricas', () => {
    component.report = mockReport;

    const metricas = component.getMetricasArray();

    expect(metricas.length).toBe(2);
    expect(metricas[0].categoria).toBe('Calidad');
    expect(metricas[0].valor).toBe(8.5);
  });

  it('no debería generar sin sprint seleccionado', () => {
    component.selectedSprintId = '';
    component.generateReport();

    expect(reportService.generateSprintReport).not.toHaveBeenCalled();
  });

  it('no debería generar si ya está generando', () => {
    component.generating.set(true);
    component.selectedSprintId = 'sprint-2';
    component.generateReport();

    expect(reportService.generateSprintReport).not.toHaveBeenCalled();
  });

  // RE.3 — PROGRESO DE REPORTES
  describe('RE.3 - Progreso de reportes', () => {
    it('debería tener signal de generationStep', () => {
      expect(component.generationStep).toBeDefined();
      expect(typeof component.generationStep()).toBe('string');
    });
  });

  // FASE 7C.2 — limpieza de Markdown en los insights embebidos del reporte
  describe('presentación de insights embebidos (limpieza de Markdown — FASE 7C.2)', () => {
    beforeEach(() => {
      // ngOnInit dispara loadSprints() en el primer detectChanges(); estos
      // tests no ejercitan esa carga, solo el renderizado de component.report.
      sprintService.listar.and.returnValue(of([]));
    });

    const mockInsightSucio: AIInsight = {
      id: 'insight-1',
      proyectoId: 'proyecto-1',
      sprintId: 'sprint-2',
      type: 'RISK',
      severity: 'HIGH',
      title: '**Riesgo crítico** detectado',
      description: 'Esto es --- una descripción con markdown crudo',
      evidence: [],
      recommendation: null,
      confidence: 'MEDIUM',
      dismissed: false,
      createdAt: '2026-08-11T22:00:00Z',
      dismissedAt: null
    };

    it('muestra el título y la descripción del insight sin marcadores Markdown crudos', () => {
      component.report = { ...mockReport, insights: [mockInsightSucio] };
      fixture.detectChanges();

      const texto: string = fixture.nativeElement.textContent;
      expect(texto).toContain('Riesgo crítico detectado');
      expect(texto).not.toContain('**Riesgo crítico**');
      expect(texto).not.toMatch(/---/);
    });

    it('no altera el texto de un insight que ya llega limpio', () => {
      const insightLimpio: AIInsight = { ...mockInsightSucio, title: 'Riesgo estable', description: 'Sin cambios relevantes en el sprint.' };
      component.report = { ...mockReport, insights: [insightLimpio] };
      fixture.detectChanges();

      const texto: string = fixture.nativeElement.textContent;
      expect(texto).toContain('Riesgo estable');
      expect(texto).toContain('Sin cambios relevantes en el sprint.');
    });

    it('no modifica la severidad ni otros metadatos del insight al limpiar el texto', () => {
      component.report = { ...mockReport, insights: [mockInsightSucio] };
      fixture.detectChanges();

      const texto: string = fixture.nativeElement.textContent;
      expect(texto).toContain('HIGH');
      expect(component.report.insights[0].severity).toBe('HIGH');
      expect(component.report.insights[0].title).toBe('**Riesgo crítico** detectado'); // el dato original en memoria no cambia, solo la presentación
    });

    it('no altera los textos estáticos de la sección de insights', () => {
      component.report = { ...mockReport, insights: [mockInsightSucio] };
      fixture.detectChanges();

      const texto: string = fixture.nativeElement.textContent;
      expect(texto).toContain('Insights Relacionados');
    });
  });

  // FASE 7C.3 — limpieza de Markdown en el contenido de nivel de reporte
  describe('presentación del contenido de nivel de reporte (limpieza de Markdown — FASE 7C.3)', () => {
    beforeEach(() => {
      sprintService.listar.and.returnValue(of([]));
    });

    it('resumenEjecutivo: se muestra sin ** y conserva el contenido semántico', () => {
      component.report = {
        ...mockReport,
        resumenEjecutivo: '**El Sprint concluyó con una caída crítica y sostenida** en el Impacto del producto (20.00).'
      };
      fixture.detectChanges();

      const texto: string = fixture.nativeElement.textContent;
      expect(texto).not.toContain('**');
      expect(texto).toContain('El Sprint concluyó con una caída crítica y sostenida en el Impacto del producto (20.00).');
    });

    it('highlights[]: Markdown crudo se muestra limpio y los elementos siguen siendo los mismos', () => {
      component.report = {
        ...mockReport,
        highlights: ['**Calidad superior** al promedio', 'Productividad --- estable']
      };
      fixture.detectChanges();

      const texto: string = fixture.nativeElement.textContent;
      expect(texto).not.toContain('**Calidad superior**');
      expect(texto).toContain('Calidad superior al promedio');
      expect(texto).toContain('Productividad');
      expect(texto).toContain('estable');
      expect(component.report.highlights.length).toBe(2); // el array original no cambia
    });

    it('concerns[]: Markdown crudo se muestra limpio sin eliminar palabras del contenido', () => {
      component.report = {
        ...mockReport,
        concerns: ['**Severa Desvalorización del Producto (Impacto):** la disminución del 75% representa una alerta crítica.']
      };
      fixture.detectChanges();

      const texto: string = fixture.nativeElement.textContent;
      expect(texto).not.toContain('**Severa Desvalorización del Producto (Impacto):**');
      expect(texto).toContain('Severa Desvalorización del Producto (Impacto):');
      expect(texto).toContain('la disminución del 75% representa una alerta crítica.');
    });

    it('recomendaciones: Markdown crudo se muestra limpio sin alterar el dato original en memoria', () => {
      const sucio = '**Priorizar** la revisión del Product Backlog --- antes del próximo sprint.';
      component.report = { ...mockReport, recomendaciones: sucio };
      fixture.detectChanges();

      const texto: string = fixture.nativeElement.textContent;
      expect(texto).not.toContain('**Priorizar**');
      expect(texto).toContain('Priorizar la revisión del Product Backlog');
      expect(texto).toContain('antes del próximo sprint.');
      expect(component.report.recomendaciones).toBe(sucio); // el dato original no se muta
    });

    it('no modifica severidad, métricas ni fechas del reporte al limpiar el contenido de nivel de reporte', () => {
      component.report = {
        ...mockReport,
        resumenEjecutivo: '**Texto sucio**',
        highlights: ['**Otro** texto sucio'],
        concerns: ['Con --- separador'],
        recomendaciones: '**Recomendación sucia**'
      };
      fixture.detectChanges();

      expect(component.report.metricas).toEqual(mockReport.metricas);
      expect(component.report.sprintNumero).toBe(mockReport.sprintNumero);
      expect(component.report.fechaInicio).toBe(mockReport.fechaInicio);
      expect(component.report.fechaFin).toBe(mockReport.fechaFin);
      const texto: string = fixture.nativeElement.textContent;
      expect(texto).toContain('8.50'); // valor de la métrica "Calidad", sin alterar
    });

    it('un reporte que ya llega limpio permanece exactamente igual', () => {
      component.report = mockReport;
      fixture.detectChanges();

      const texto: string = fixture.nativeElement.textContent;
      expect(texto).toContain(mockReport.resumenEjecutivo);
      expect(texto).toContain(mockReport.highlights[0]);
      expect(texto).toContain(mockReport.recomendaciones);
    });
  });
});
