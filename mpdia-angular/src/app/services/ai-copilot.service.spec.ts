import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AICopilotService } from './ai-copilot.service';
import { ChatRequest, ChatResponse } from '../models/ai-copilot.model';
import { environment } from '../../environments/environment';

describe('AICopilotService', () => {
  let service: AICopilotService;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiBaseUrl}/ai/copilot`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AICopilotService]
    });
    service = TestBed.inject(AICopilotService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should send chat request correctly', () => {
    const request: ChatRequest = {
      message: '¿Cuáles son las métricas?',
      proyectoId: 'test-project-id',
      sprintId: null
    };

    const mockResponse: ChatResponse = {
      message: 'Las métricas del sprint son...',
      toolsUsed: ['getActiveSprintMetrics'],
      timestamp: '2026-08-10T21:52:57.420Z',
      hasData: true
    };

    service.chat(request).subscribe(response => {
      expect(response).toEqual(mockResponse);
      expect(response.message).toContain('Las métricas');
      expect(response.toolsUsed.length).toBe(1);
    });

    const req = httpMock.expectOne(`${baseUrl}/chat`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(mockResponse);
  });

  it('should handle 400 error', () => {
    const request: ChatRequest = {
      message: '',
      proyectoId: 'test-project-id',
      sprintId: null
    };

    service.chat(request).subscribe({
      next: () => fail('should have failed'),
      error: (error) => {
        expect(error.message).toBe('El mensaje no puede estar vacío');
      }
    });

    const req = httpMock.expectOne(`${baseUrl}/chat`);
    req.flush({ error: 'El mensaje no puede estar vacío' }, { status: 400, statusText: 'Bad Request' });
  });

  it('should handle 401 error', () => {
    const request: ChatRequest = {
      message: 'test',
      proyectoId: 'test-project-id',
      sprintId: null
    };

    service.chat(request).subscribe({
      next: () => fail('should have failed'),
      error: (error) => {
        expect(error.message).toBe('Tu sesión ha expirado');
      }
    });

    const req = httpMock.expectOne(`${baseUrl}/chat`);
    req.flush({}, { status: 401, statusText: 'Unauthorized' });
  });

  it('should handle 403 error', () => {
    const request: ChatRequest = {
      message: 'test',
      proyectoId: 'test-project-id',
      sprintId: null
    };

    service.chat(request).subscribe({
      next: () => fail('should have failed'),
      error: (error) => {
        expect(error.message).toContain('No tienes acceso');
      }
    });

    const req = httpMock.expectOne(`${baseUrl}/chat`);
    req.flush({ error: 'No tienes acceso a este proyecto' }, { status: 403, statusText: 'Forbidden' });
  });

  it('should handle 500 error', () => {
    const request: ChatRequest = {
      message: 'test',
      proyectoId: 'test-project-id',
      sprintId: null
    };

    service.chat(request).subscribe({
      next: () => fail('should have failed'),
      error: (error) => {
        expect(error.message).toContain('no está disponible');
      }
    });

    const req = httpMock.expectOne(`${baseUrl}/chat`);
    req.flush({}, { status: 500, statusText: 'Internal Server Error' });
  });

  it('should handle network error', () => {
    const request: ChatRequest = {
      message: 'test',
      proyectoId: 'test-project-id',
      sprintId: null
    };

    service.chat(request).subscribe({
      next: () => fail('should have failed'),
      error: (error) => {
        expect(error.message).toContain('conectar con el servidor');
      }
    });

    const req = httpMock.expectOne(`${baseUrl}/chat`);
    req.flush({}, { status: 0, statusText: 'Unknown Error' });
  });
});
