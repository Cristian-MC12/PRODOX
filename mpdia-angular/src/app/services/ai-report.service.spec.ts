// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AIReportService } from './ai-report.service';
import { AISprintReport, AIRetrospective } from '../models/ai-reports.model';
import { environment } from '../../environments/environment';

describe('AIReportService', () => {
  let service: AIReportService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AIReportService]
    });
    service = TestBed.inject(AIReportService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('debería crearse', () => {
    expect(service).toBeTruthy();
  });

  describe('generateSprintReport', () => {
    it('debería generar un reporte exitosamente', () => {
      const sprintId = 'sprint-123';
      const mockReport: AISprintReport = {
        sprintId: 'sprint-123',
        sprintNumero: 5,
        sprintGoal: 'Implementar reportes',
        fechaInicio: '2026-07-28',
        fechaFin: '2026-08-04',
        resumenEjecutivo: 'Sprint exitoso',
        metricas: { Calidad: 8.5, Productividad: 7.2 },
        highlights: ['Calidad superior'],
        concerns: [],
        insights: [],
        recomendaciones: 'Continuar',
        generatedAt: '2026-08-11T22:00:00Z'
      };

      service.generateSprintReport(sprintId).subscribe(report => {
        expect(report).toEqual(mockReport);
        expect(report.sprintId).toBe(sprintId);
        expect(report.metricas['Calidad']).toBe(8.5);
      });

      const req = httpMock.expectOne(
        `${environment.apiBaseUrl}/ai/reports/sprint/${sprintId}/generate`
      );
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(mockReport);
    });

    it('debería manejar error 403', () => {
      const sprintId = 'sprint-123';

      service.generateSprintReport(sprintId).subscribe({
        next: () => fail('should have failed'),
        error: (error) => {
          expect(error.status).toBe(403);
        }
      });

      const req = httpMock.expectOne(
        `${environment.apiBaseUrl}/ai/reports/sprint/${sprintId}/generate`
      );
      req.flush('Forbidden', { status: 403, statusText: 'Forbidden' });
    });

    it('debería manejar error 429 (rate limit)', () => {
      const sprintId = 'sprint-123';

      service.generateSprintReport(sprintId).subscribe({
        next: () => fail('should have failed'),
        error: (error) => {
          expect(error.status).toBe(429);
        }
      });

      const req = httpMock.expectOne(
        `${environment.apiBaseUrl}/ai/reports/sprint/${sprintId}/generate`
      );
      req.flush('Too Many Requests', { status: 429, statusText: 'Too Many Requests' });
    });
  });

  describe('generateRetrospective', () => {
    it('debería generar una retrospectiva exitosamente', () => {
      const sprintId = 'sprint-123';
      const mockRetro: AIRetrospective = {
        sprintId: 'sprint-123',
        sprintNumero: 5,
        sprintGoal: 'Implementar retrospectivas',
        fechaInicio: '2026-07-28',
        fechaFin: '2026-08-04',
        whatWentWell: ['Calidad mejoró'],
        whatCouldImprove: ['Mejorar documentación'],
        risks: ['Tendencia descendente'],
        recommendations: ['Establecer daily'],
        questionsForTeam: ['¿Qué obstáculos?'],
        generatedAt: '2026-08-11T22:00:00Z'
      };

      service.generateRetrospective(sprintId).subscribe(retro => {
        expect(retro).toEqual(mockRetro);
        expect(retro.sprintId).toBe(sprintId);
        expect(retro.whatWentWell.length).toBeGreaterThan(0);
      });

      const req = httpMock.expectOne(
        `${environment.apiBaseUrl}/ai/retrospectives/sprint/${sprintId}/generate`
      );
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(mockRetro);
    });

    it('debería manejar error 400 (sprint no encontrado)', () => {
      const sprintId = 'sprint-invalid';

      service.generateRetrospective(sprintId).subscribe({
        next: () => fail('should have failed'),
        error: (error) => {
          expect(error.status).toBe(400);
        }
      });

      const req = httpMock.expectOne(
        `${environment.apiBaseUrl}/ai/retrospectives/sprint/${sprintId}/generate`
      );
      req.flush('Bad Request', { status: 400, statusText: 'Bad Request' });
    });
  });
});
