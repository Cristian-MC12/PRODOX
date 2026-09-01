// Autor: Cristian Santiago Martinez Cordoba — PRODOX
// Fase 16.9.2: Tests para servicio de métricas académicas
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { MetricaAcademicaService } from './metrica-academica.service';
import {
  MetricaAcademicaRequest,
  GuardarPropuestaAcademicaRequest,
  EjecutarMetricaAcademicaRequest
} from '../models/metrica-academica.model';
import { environment } from '../../environments/environment';

describe('MetricaAcademicaService', () => {
  let service: MetricaAcademicaService;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiBaseUrl}/metricas-academicas`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [MetricaAcademicaService]
    });
    service = TestBed.inject(MetricaAcademicaService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('generarPropuesta', () => {
    it('should call POST /propuesta', () => {
      const request: MetricaAcademicaRequest = {
        proyectoId: 'proj-123',
        metricaId: 'met-456',
        codigoMetrica: 'SIG-SC-02',
        nombreMetrica: 'Problemas reportados',
        definicion: 'Definición',
        fuenteAcademica: 'Guerrero-Calvache & Hernández (2024)',
        formulaAcademica: 'Σ problemas_reportados',
        tipoOperacion: 'SUMA',
        unidadResultado: 'problemas',
        frecuencia: 'por_sprint'
      };

      const mockResponse = {
        titulo: 'Propuesta',
        objetivo: 'Medir problemas',
        procedimiento: 'Contar problemas',
        indicadorVariable: 'problemas_reportados',
        escala: 'Numérica',
        justificacion: 'Académica'
      };

      service.generarPropuesta(request).subscribe(response => {
        expect(response).toEqual(mockResponse);
      });

      const req = httpMock.expectOne(`${baseUrl}/propuesta`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      req.flush(mockResponse);
    });
  });

  describe('guardarPropuesta', () => {
    it('should call POST /guardar-propuesta', () => {
      const request: GuardarPropuestaAcademicaRequest = {
        proyectoId: 'proj-123',
        metricaId: 'met-456',
        fuenteAcademica: 'Fuente',
        formulaAcademica: 'Formula',
        tipoOperacion: 'SUMA',
        unidadResultado: 'unidad',
        objetivo: 'Objetivo',
        procedimiento: 'Procedimiento',
        indicadorVariable: 'Variable',
        escala: 'Escala',
        frecuenciaCaptura: 'por_sprint'
      };

      const mockResponse = {
        id: 'param-789',
        status: 'propuesta' as const,
        version: 1,
        metricaId: 'met-456',
        proyectoId: 'proj-123',
        fuenteAcademica: 'Fuente',
        formulaAcademica: 'Formula',
        tipoOperacion: 'SUMA',
        unidadResultado: 'unidad',
        objetivo: 'Objetivo',
        procedimiento: 'Procedimiento',
        indicadorVariable: 'Variable',
        escala: 'Escala',
        frecuenciaCaptura: 'por_sprint',
        createdAt: '2026-08-16T10:00:00Z'
      };

      service.guardarPropuesta(request).subscribe(response => {
        expect(response).toEqual(mockResponse);
      });

      const req = httpMock.expectOne(`${baseUrl}/guardar-propuesta`);
      expect(req.request.method).toBe('POST');
      req.flush(mockResponse);
    });
  });

  describe('ejecutar', () => {
    it('should call POST /{metricaId}/ejecutar', () => {
      const metricaId = 'met-456';
      const request: EjecutarMetricaAcademicaRequest = {
        proyectoId: 'proj-123',
        sprintId: 'sprint-789',
        valores: { problemas_reportados: 7 }
      };

      const mockResponse = {
        resultadoId: 'res-abc',
        metricaId: 'met-456',
        metricaNombre: 'SIG-SC-02',
        proyectoId: 'proj-123',
        sprintId: 'sprint-789',
        parametrizacionId: 'param-789',
        parametrizacionVersion: 1,
        tipoCalculo: 'SUMA',
        expresion: 'Σ problemas_reportados',
        valoresUtilizados: '{"problemas_reportados":7}',
        resultado: 7,
        unidad: 'problemas',
        estado: 'calculado' as const,
        calculadoAt: '2026-08-16T10:00:00Z'
      };

      service.ejecutar(metricaId, request).subscribe(response => {
        expect(response).toEqual(mockResponse);
      });

      const req = httpMock.expectOne(`${baseUrl}/${metricaId}/ejecutar`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      req.flush(mockResponse);
    });
  });

  describe('obtenerHistorico', () => {
    it('should call GET /{metricaId}/historico with proyectoId param', () => {
      const metricaId = 'met-456';
      const proyectoId = 'proj-123';

      const mockResponse = [
        {
          resultadoId: 'res-1',
          metricaId: 'met-456',
          metricaNombre: 'SIG-SC-02',
          proyectoId: 'proj-123',
          sprintId: 'sprint-1',
          parametrizacionId: 'param-789',
          parametrizacionVersion: 1,
          tipoCalculo: 'SUMA',
          expresion: 'Σ',
          valoresUtilizados: '{}',
          resultado: 5,
          unidad: 'problemas',
          estado: 'calculado' as const,
          calculadoAt: '2026-08-15T10:00:00Z'
        }
      ];

      service.obtenerHistorico(metricaId, proyectoId).subscribe(response => {
        expect(response).toEqual(mockResponse);
      });

      const req = httpMock.expectOne(`${baseUrl}/${metricaId}/historico?proyectoId=${proyectoId}`);
      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);
    });
  });

  describe('solicitarInterpretacion', () => {
    it('should call POST /resultados/{resultadoId}/interpretar', () => {
      const resultadoId = 'res-abc';

      const mockResponse = {
        resultadoId: 'res-abc',
        metricaNombre: 'SIG-SC-02',
        resultado: 7,
        unidad: 'problemas',
        interpretacion: 'Interpretación IA',
        generadoAt: '2026-08-16T10:00:00Z'
      };

      service.solicitarInterpretacion(resultadoId).subscribe(response => {
        expect(response).toEqual(mockResponse);
      });

      const req = httpMock.expectOne(`${baseUrl}/resultados/${resultadoId}/interpretar`);
      expect(req.request.method).toBe('POST');
      req.flush(mockResponse);
    });
  });
});
