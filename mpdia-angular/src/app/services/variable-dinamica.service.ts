// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface VariableConValor {
  id: string;
  nombre: string;
  descripcion: string;
  tipoDato: string;
  obligatorio: boolean;
  unidad?: string;
  escalaMin?: number;
  escalaMax?: number;
  valorNum?: number;
  valorTexto?: string;
  valorBool?: boolean;
}

export interface VariablesMetricaResponse {
  parametrizacionId: string;
  version: number;
  status: string;
  variables: VariableConValor[];
}

export interface ValorVariable {
  variableId: string;
  valorNum?: number;
  valorTexto?: string;
  valorBool?: boolean;
  observacion?: string;
}

export interface GuardarValoresRequest {
  proyectoId: string;
  sprintId: string;
  valores: ValorVariable[];
}

/**
 * DTO para resultado de cálculo de métrica (Fase 16.8).
 */
export interface ResultadoMetricaDto {
  resultadoId: string;
  metricaId: string;
  metricaNombre: string;
  proyectoId: string;
  sprintId: string;
  parametrizacionId: string;
  parametrizacionVersion: number;
  tipoCalculo: string;
  expresion?: string;
  valoresUtilizados: string;
  resultado: number;
  unidad?: string;
  estado: string;
  mensajeError?: string;
  calculadoAt: string;
}

/**
 * Servicio para captura dinámica de variables desde parametrizaciones aprobadas.
 * Fase 16.7
 */
@Injectable({
  providedIn: 'root'
})
export class VariableDinamicaService {

  private baseUrl = `${environment.apiBaseUrl}/metricas`;

  constructor(private http: HttpClient) {}

  obtenerVariables(metricaId: string, proyectoId: string, sprintId: string): Observable<VariablesMetricaResponse> {
    const params = new HttpParams()
      .set('proyectoId', proyectoId)
      .set('sprintId', sprintId);
    
    return this.http.get<VariablesMetricaResponse>(`${this.baseUrl}/${metricaId}/variables`, { params });
  }

  guardarValores(metricaId: string, request: GuardarValoresRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${metricaId}/valores`, request);
  }
  
  /**
   * Calcula una métrica para un sprint (Fase 16.8).
   */
  calcularMetrica(metricaId: string, proyectoId: string, sprintId: string): Observable<ResultadoMetricaDto> {
    return this.http.post<ResultadoMetricaDto>(
      `${this.baseUrl}/${metricaId}/calcular`,
      { proyectoId, sprintId }
    );
  }
}
