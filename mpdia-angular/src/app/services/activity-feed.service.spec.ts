// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { TestBed } from '@angular/core/testing';
import { ActivityFeedService } from './activity-feed.service';
import { EvaluacionService } from './evaluacion.service';
import { SprintService } from './sprint.service';
import { ProjectMemberService } from './project-member.service';
import { AIInsightsService } from './ai-insights.service';
import { of } from 'rxjs';

describe('ActivityFeedService', () => {
  let service: ActivityFeedService;
  let mockEvaluacionService: jasmine.SpyObj<EvaluacionService>;
  let mockSprintService: jasmine.SpyObj<SprintService>;
  let mockMemberService: jasmine.SpyObj<ProjectMemberService>;
  let mockInsightsService: jasmine.SpyObj<AIInsightsService>;

  beforeEach(() => {
    mockEvaluacionService = jasmine.createSpyObj('EvaluacionService', ['detalle']);
    mockSprintService = jasmine.createSpyObj('SprintService', ['listar']);
    mockMemberService = jasmine.createSpyObj('ProjectMemberService', ['listar']);
    mockInsightsService = jasmine.createSpyObj('AIInsightsService', ['getProjectInsights']);

    mockEvaluacionService.detalle.and.returnValue(of([]));
    mockSprintService.listar.and.returnValue(of([]));
    mockMemberService.listar.and.returnValue(of([]));
    mockInsightsService.getProjectInsights.and.returnValue(of([]));

    TestBed.configureTestingModule({
      providers: [
        ActivityFeedService,
        { provide: EvaluacionService, useValue: mockEvaluacionService },
        { provide: SprintService, useValue: mockSprintService },
        { provide: ProjectMemberService, useValue: mockMemberService },
        { provide: AIInsightsService, useValue: mockInsightsService }
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
