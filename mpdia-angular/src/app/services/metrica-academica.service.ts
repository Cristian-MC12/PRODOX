// Autor: Cristian Santiago Martinez Cordoba — MPDIA
// Fase 16.9.2: Servicio para métricas académicas
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  MetricaAcademicaRequest,
  PropuestaParametrizacionAcademica,
  GuardarPropuestaAcademicaRequest,
  ParametrizacionAcademicaDto,
  EjecutarMetricaAcademicaRequest,
  ResultadoMetricaDto,
  InterpretacionIADto
} from '../models/metrica-academica.model';

@Injectable({ providedIn: 'root' })
export class MetricaAcademicaService {

  private readonly base = `${environment.apiBaseUrl}/metricas-academicas`;

  constructor(private http: HttpClient) {}

  /**
   * Genera propuesta de parametrización con asistencia de Gemini.
   */
  generarPropuesta(request: MetricaAcademicaRequest): Observable<PropuestaParametrizacionAcademica> {
    return this.http.post<PropuestaParametrizacionAcademica>(
      `${this.base}/propuesta`, request
    );
  }

  /**
   * Guarda propuesta de parametrización académica con estado "propuesta".
   */
  guardarPropuesta(request: GuardarPropuestaAcademicaRequest): Observable<ParametrizacionAcademicaDto> {
    return this.http.post<ParametrizacionAcademicaDto>(
      `${this.base}/guardar-propuesta`, request
    );
  }

  /**
   * Ejecuta una métrica académica en un sprint específico.
   */
  ejecutar(metricaId: string, request: EjecutarMetricaAcademicaRequest): Observable<ResultadoMetricaDto> {
    return this.http.post<ResultadoMetricaDto>(
      `${this.base}/${metricaId}/ejecutar`, request
    );
  }

  /**
   * Obtiene el histórico de resultados de una métrica.
   */
  obtenerHistorico(metricaId: string, proyectoId: string): Observable<ResultadoMetricaDto[]> {
    const params = new HttpParams().set('proyectoId', proyectoId);
    return this.http.get<ResultadoMetricaDto[]>(
      `${this.base}/${metricaId}/historico`, { params }
    );
  }

  /**
   * Solicita interpretación IA de un resultado ya calculado.
   */
  solicitarInterpretacion(resultadoId: string): Observable<InterpretacionIADto> {
    return this.http.post<InterpretacionIADto>(
      `${this.base}/resultados/${resultadoId}/interpretar`, {}
    );
  }

  /**
   * Obtiene la última parametrización aprobada para una métrica en un proyecto.
   */
  obtenerParametrizacionAprobada(metricaId: string, proyectoId: string): Observable<ParametrizacionAcademicaDto | null> {
    const params = new HttpParams()
      .set('metricaId', metricaId)
      .set('proyectoId', proyectoId);
    return this.http.get<ParametrizacionAcademicaDto>(
      `${environment.apiBaseUrl}/parametrizacion/ultima-aprobada`, { params }
    );
  }
}
