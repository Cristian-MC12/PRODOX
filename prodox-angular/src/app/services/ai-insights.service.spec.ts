// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AIInsightsService } from './ai-insights.service';
import { AIInsight, GenerateInsightsResult } from '../models/ai-insights.model';
import { environment } from '../../environments/environment';

describe('AIInsightsService', () => {
  let service: AIInsightsService;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiBaseUrl}/ai/insights`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AIInsightsService]
    });
    service = TestBed.inject(AIInsightsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getProjectInsights', () => {
    it('should fetch project insights', () => {
      const proyectoId = 'proyecto-123';
      const mockInsights: AIInsight[] = [
        {
          id: 'insight-1',
          proyectoId,
          sprintId: null,
          type: 'TREND',
          severity: 'MEDIUM',
          title: 'Mejora en Calidad',
          description: 'La calidad ha aumentado 15%',
          evidence: [],
          recommendation: 'Mantener las prácticas actuales',
          confidence: 'HIGH',
          dismissed: false,
          createdAt: '2024-01-15T10:00:00Z',
          dismissedAt: null
        }
      ];

      service.getProjectInsights(proyectoId).subscribe(insights => {
        expect(insights).toEqual(mockInsights);
        expect(insights.length).toBe(1);
      });

      const req = httpMock.expectOne(`${baseUrl}/${proyectoId}`);
      expect(req.request.method).toBe('GET');
      req.flush(mockInsights);
    });

    it('should handle empty insights list', () => {
      const proyectoId = 'proyecto-123';

      service.getProjectInsights(proyectoId).subscribe(insights => {
        expect(insights).toEqual([]);
      });

      const req = httpMock.expectOne(`${baseUrl}/${proyectoId}`);
      req.flush([]);
    });

    it('should handle HTTP error', () => {
      const proyectoId = 'proyecto-123';
      const errorMessage = 'Error fetching insights';

      service.getProjectInsights(proyectoId).subscribe({
        next: () => fail('should have failed'),
        error: (error) => {
          expect(error.status).toBe(500);
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/${proyectoId}`);
      req.flush(errorMessage, { status: 500, statusText: 'Server Error' });
    });
  });

  describe('generateInsights', () => {
    it('should generate insights for a project', () => {
      const proyectoId = 'proyecto-123';
      const mockInsights: AIInsight[] = [
        {
          id: 'insight-2',
          proyectoId,
          sprintId: 'sprint-1',
          type: 'ANOMALY',
          severity: 'HIGH',
          title: 'Anomalía detectada',
          description: 'Valor inusual en productividad',
          evidence: [],
          recommendation: 'Revisar factores externos',
          confidence: 'MEDIUM',
          dismissed: false,
          createdAt: '2024-01-15T10:05:00Z',
          dismissedAt: null
        }
      ];

      const mockResultado: GenerateInsightsResult = {
        insights: mockInsights,
        status: 'COMPLETE',
        senalesDetectadas: 1,
        senalesNuevas: 1,
        senalesOmitidasPorDuplicado: 0,
        errores: []
      };

      service.generateInsights(proyectoId).subscribe(resultado => {
        expect(resultado).toEqual(mockResultado);
      });

      const req = httpMock.expectOne(`${baseUrl}/generate/${proyectoId}`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(mockResultado);
    });

    it('should handle insufficient data gracefully', () => {
      const proyectoId = 'proyecto-new';
      const mockResultado: GenerateInsightsResult = {
        insights: [],
        status: 'SIN_DATOS',
        senalesDetectadas: 0,
        senalesNuevas: 0,
        senalesOmitidasPorDuplicado: 0,
        errores: []
      };

      service.generateInsights(proyectoId).subscribe(resultado => {
        expect(resultado.insights).toEqual([]);
        expect(resultado.status).toBe('SIN_DATOS');
      });

      const req = httpMock.expectOne(`${baseUrl}/generate/${proyectoId}`);
      req.flush(mockResultado);
    });

    it('should handle generation error', () => {
      const proyectoId = 'proyecto-123';

      service.generateInsights(proyectoId).subscribe({
        next: () => fail('should have failed'),
        error: (error) => {
          expect(error.status).toBe(403);
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/generate/${proyectoId}`);
      req.flush('Unauthorized', { status: 403, statusText: 'Forbidden' });
    });
  });

  describe('dismissInsight', () => {
    it('should dismiss an insight', () => {
      const insightId = 'insight-123';

      service.dismissInsight(insightId).subscribe(response => {
        expect(response).toBeNull();
      });

      const req = httpMock.expectOne(`${baseUrl}/${insightId}/dismiss`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(null);
    });

    it('should handle dismiss error', () => {
      const insightId = 'insight-123';

      service.dismissInsight(insightId).subscribe({
        next: () => fail('should have failed'),
        error: (error) => {
          expect(error.status).toBe(404);
        }
      });

      const req = httpMock.expectOne(`${baseUrl}/${insightId}/dismiss`);
      req.flush('Not found', { status: 404, statusText: 'Not Found' });
    });
  });
});
