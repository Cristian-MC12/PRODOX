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
  /** FASE 16 — diaria | semanal | por_sprint | ilimitada. */
  frecuenciaCaptura?: string;
  /** Corrección del manejo de escalas: NUMERICA_ENTERA | NUMERICA_DECIMAL, undefined = sin escala estructurada. */
  escalaTipo?: 'NUMERICA_ENTERA' | 'NUMERICA_DECIMAL';
  escalaPaso?: number;
  escalaSinLimite?: boolean;
  /** Revisión de captura individual: grupal | individual (Variable.tipoAlcance).
   *  'individual' habilita el formulario de captura para cualquier miembro del
   *  proyecto, no solo el Scrum Master (ver ejecucion.component). */
  tipoAlcance?: 'grupal' | 'individual';
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
  /**
   * FASE 16 — fecha de captura explícita (ISO-8601 instant, ej.
   * "2026-08-21T00:00:00Z"). Opcional: si se omite, el backend usa
   * Instant.now() (comportamiento previo, sin cambios). Permite registrar
   * varias capturas de la misma variable dentro del mismo sprint sin que la
   * fecha real del servidor las sustituya silenciosamente.
   */
  fechaCaptura?: string;
  /**
   * Revisión de Ejecución — id del RegistroValor que se está editando.
   * Omitido/undefined = captura nueva (sin cambios). Informado = el backend
   * actualiza SIEMPRE esa misma fila por ID y la excluye de la comprobación
   * de duplicados por frecuencia, sin importar si la fecha cambió.
   */
  registroId?: string;
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
