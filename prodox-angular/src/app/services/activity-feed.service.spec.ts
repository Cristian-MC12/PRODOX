// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { TestBed } from '@angular/core/testing';
import { ActivityFeedService } from './activity-feed.service';
import { EvaluacionService } from './evaluacion.service';
import { SprintService } from './sprint.service';
import { ProjectMemberService } from './project-member.service';
import { AIInsightsService } from './ai-insights.service';
import { HistoriaUsuarioService } from './historia-usuario.service';
import { of } from 'rxjs';

describe('ActivityFeedService', () => {
  let service: ActivityFeedService;
  let mockEvaluacionService: jasmine.SpyObj<EvaluacionService>;
  let mockSprintService: jasmine.SpyObj<SprintService>;
  let mockMemberService: jasmine.SpyObj<ProjectMemberService>;
  let mockInsightsService: jasmine.SpyObj<AIInsightsService>;
  let mockHistoriaService: jasmine.SpyObj<HistoriaUsuarioService>;

  beforeEach(() => {
    mockEvaluacionService = jasmine.createSpyObj('EvaluacionService', ['detalle']);
    mockSprintService = jasmine.createSpyObj('SprintService', ['listar']);
    mockMemberService = jasmine.createSpyObj('ProjectMemberService', ['listar']);
    mockInsightsService = jasmine.createSpyObj('AIInsightsService', ['getProjectInsights']);
    mockHistoriaService = jasmine.createSpyObj('HistoriaUsuarioService', ['listar']);

    mockEvaluacionService.detalle.and.returnValue(of([]));
    mockSprintService.listar.and.returnValue(of([]));
    mockMemberService.listar.and.returnValue(of([]));
    mockInsightsService.getProjectInsights.and.returnValue(of([]));
    mockHistoriaService.listar.and.returnValue(of([]));

    TestBed.configureTestingModule({
      providers: [
        ActivityFeedService,
        { provide: EvaluacionService, useValue: mockEvaluacionService },
        { provide: SprintService, useValue: mockSprintService },
        { provide: ProjectMemberService, useValue: mockMemberService },
        { provide: AIInsightsService, useValue: mockInsightsService },
        { provide: HistoriaUsuarioService, useValue: mockHistoriaService }
      ]
    });

    service = TestBed.inject(ActivityFeedService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should call all services when fetching activities', (done) => {
    service.getProjectActivities('test-proyecto-id', 10).subscribe(() => {
      expect(mockEvaluacionService.detalle).toHaveBeenCalledWith('test-proyecto-id');
      expect(mockSprintService.listar).toHaveBeenCalledWith('test-proyecto-id');
      expect(mockMemberService.listar).toHaveBeenCalledWith('test-proyecto-id');
      expect(mockInsightsService.getProjectInsights).toHaveBeenCalledWith('test-proyecto-id');
      expect(mockHistoriaService.listar).toHaveBeenCalledWith('test-proyecto-id');
      done();
    });
  });

  it('V39: incluye historias creadas recientemente en el feed', (done) => {
    mockHistoriaService.listar.and.returnValue(of([{
      id: 'h1', proyectoId: 'test-proyecto-id', sprintId: null,
      titulo: 'Como usuario quiero X', descripcion: null, criteriosAceptacion: null,
      prioridad: 'alta', estado: 'pendiente', creadoPor: 'po@prodox.com',
      createdAt: new Date().toISOString(), updatedAt: new Date().toISOString()
    }] as any));

    service.getProjectActivities('test-proyecto-id', 10).subscribe(activities => {
      const historiaActivity = activities.find(a => a.type === 'historia_created');
      expect(historiaActivity).toBeTruthy();
      expect(historiaActivity?.description).toBe('Como usuario quiero X');
      done();
    });
  });

  it('should return empty array when no activities', (done) => {
    service.getProjectActivities('test-proyecto-id', 10).subscribe(activities => {
      expect(activities.length).toBe(0);
      done();
    });
  });
});
