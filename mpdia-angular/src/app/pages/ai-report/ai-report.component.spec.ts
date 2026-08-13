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
});
