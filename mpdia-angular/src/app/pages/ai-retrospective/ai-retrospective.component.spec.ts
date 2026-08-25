// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { FormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { AIRetrospectiveComponent } from './ai-retrospective.component';
import { AIReportService } from '../../services/ai-report.service';
import { SprintService } from '../../services/sprint.service';
import { AIRetrospective } from '../../models/ai-reports.model';
import { SprintDto } from '../../models/sprint.model';

describe('AIRetrospectiveComponent', () => {
  let component: AIRetrospectiveComponent;
  let fixture: ComponentFixture<AIRetrospectiveComponent>;
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
    },
    {
      id: 'sprint-3',
      proyectoId: 'proyecto-1',
      proyectoNombre: 'Proyecto Test',
      metodo: 'scrum',
      timeBoxSemanas: 2,
      numero: 3,
      sprintGoal: 'Sprint 3',
      estado: 'en_ejecucion',
      fechaInicio: '2026-07-29',
      fechaFin: null,
      cerradoPor: null,
      cerradoAt: null,
      createdAt: '2026-07-29T00:00:00Z'
    }
  ];

  const mockRetro: AIRetrospective = {
    sprintId: 'sprint-2',
    sprintNumero: 2,
    sprintGoal: 'Sprint 2',
    fechaInicio: '2026-07-15',
    fechaFin: '2026-07-28',
    whatWentWell: ['Calidad mejoró 12%', 'Equipo colaborativo'],
    whatCouldImprove: ['Mejorar documentación', 'Reducir reuniones'],
    risks: ['Tendencia descendente en productividad'],
    recommendations: ['Establecer daily standup más eficiente'],
    questionsForTeam: ['¿Qué obstáculos encontramos?', '¿Cómo mejorar la comunicación?'],
    generatedAt: '2026-08-11T22:00:00Z'
  };

  beforeEach(async () => {
    const reportServiceSpy = jasmine.createSpyObj('AIReportService', ['generateRetrospective']);
    const sprintServiceSpy = jasmine.createSpyObj('SprintService', ['listar', 'getActivo']);

    await TestBed.configureTestingModule({
      imports: [
        AIRetrospectiveComponent,
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

    fixture = TestBed.createComponent(AIRetrospectiveComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('debería crearse', () => {
    expect(component).toBeTruthy();
  });

  it('debería cargar solo sprints finalizados', () => {
    sprintService.listar.and.returnValue(of(mockSprints));

    fixture.detectChanges();

    expect(component.sprints.length).toBe(2);
    expect(component.sprints.every(s => s.estado === 'finalizado')).toBe(true);
  });

  it('debería generar retrospectiva exitosamente', () => {
    sprintService.listar.and.returnValue(of(mockSprints));
    reportService.generateRetrospective.and.returnValue(of(mockRetro));

    fixture.detectChanges();

    component.selectedSprintId = 'sprint-2';
    component.generateRetrospective();

    expect(reportService.generateRetrospective).toHaveBeenCalledWith('sprint-2');
    expect(component.retrospective).toEqual(mockRetro);
    expect(component.generating()).toBe(false);
  });

  it('debería manejar error 400', () => {
    sprintService.listar.and.returnValue(of(mockSprints));
    reportService.generateRetrospective.and.returnValue(
      throwError(() => ({ status: 400 }))
    );

    fixture.detectChanges();

    component.selectedSprintId = 'sprint-2';
    component.generateRetrospective();

    expect(component.retrospective).toBeNull();
    expect(component.alertMsg()).toContain('no encontrado');
  });

  it('debería manejar error 403', () => {
    sprintService.listar.and.returnValue(of(mockSprints));
    reportService.generateRetrospective.and.returnValue(
      throwError(() => ({ status: 403 }))
    );

    fixture.detectChanges();

    component.selectedSprintId = 'sprint-2';
    component.generateRetrospective();

    expect(component.retrospective).toBeNull();
    expect(component.alertMsg()).toContain('permisos');
  });

  it('debería manejar error 429 (rate limit)', () => {
    sprintService.listar.and.returnValue(of(mockSprints));
    reportService.generateRetrospective.and.returnValue(
      throwError(() => ({ status: 429 }))
    );

    fixture.detectChanges();

    component.selectedSprintId = 'sprint-2';
    component.generateRetrospective();

    expect(component.retrospective).toBeNull();
    expect(component.alertMsg()).toContain('límite');
  });

  it('debería manejar error 503 (servicio de IA no disponible) sin caer en el mensaje genérico', () => {
    sprintService.listar.and.returnValue(of(mockSprints));
    reportService.generateRetrospective.and.returnValue(
      throwError(() => ({ status: 503, error: { error: 'No se pudo generar la retrospectiva: el servicio de IA no respondió correctamente. Intenta nuevamente en unos segundos.' } }))
    );

    fixture.detectChanges();

    component.selectedSprintId = 'sprint-2';
    component.generateRetrospective();

    expect(component.retrospective).toBeNull(); // no se fabrica una retrospectiva falsa
    expect(component.generating()).toBe(false); // el usuario puede reintentar
    expect(component.alertMsg()).toContain('servicio de IA no respondió correctamente');
  });

  it('debería permitir reintentar después de un 503 y generar correctamente', () => {
    sprintService.listar.and.returnValue(of(mockSprints));
    reportService.generateRetrospective.and.returnValue(
      throwError(() => ({ status: 503, error: { error: 'El servicio de IA no está disponible en este momento.' } }))
    );

    fixture.detectChanges();
    component.selectedSprintId = 'sprint-2';
    component.generateRetrospective();

    expect(component.retrospective).toBeNull();
    expect(component.generating()).toBe(false);

    // Reintento: ahora Gemini responde bien.
    reportService.generateRetrospective.and.returnValue(of(mockRetro));
    component.generateRetrospective();

    expect(component.retrospective).toEqual(mockRetro);
    expect(component.generating()).toBe(false);
  });

  it('debería detectar datos insuficientes', () => {
    const retroWithInsufficientData: AIRetrospective = {
      ...mockRetro,
      whatWentWell: ['Datos insuficientes para identificar aspectos positivos']
    };

    component.retrospective = retroWithInsufficientData;

    expect(component.hasInsufficientData()).toBe(true);
  });

  it('debería detectar primer sprint', () => {
    const firstSprintRetro: AIRetrospective = {
      ...mockRetro,
      whatWentWell: ['Primer sprint completado exitosamente']
    };

    component.retrospective = firstSprintRetro;

    expect(component.isFirstSprint()).toBe(true);
  });

  it('no debería generar sin sprint seleccionado', () => {
    component.selectedSprintId = '';
    component.generateRetrospective();

    expect(reportService.generateRetrospective).not.toHaveBeenCalled();
  });

  it('no debería generar si ya está generando', () => {
    component.generating.set(true);
    component.selectedSprintId = 'sprint-2';
    component.generateRetrospective();

    expect(reportService.generateRetrospective).not.toHaveBeenCalled();
  });
});
