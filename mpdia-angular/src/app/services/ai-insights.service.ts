// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AIInsight } from '../models/ai-insights.model';

/**
 * Servicio para gestionar AI Insights del proyecto.
 * 
 * IMPORTANTE:
 * - Los insights son generados por backend usando análisis determinístico + Gemini
 * - Frontend solo presenta y permite descartar insights
 * - NO se calculan métricas en frontend
 */
@Injectable({ providedIn: 'root' })
export class AIInsightsService {

  private readonly base = `${environment.apiBaseUrl}/ai/insights`;

  constructor(private http: HttpClient) {}

  /**
   * Obtiene todos los insights activos (no descartados) de un proyecto.
   */
  getProjectInsights(proyectoId: string): Observable<AIInsight[]> {
    return this.http.get<AIInsight[]>(`${this.base}/${proyectoId}`);
  }

  /**
   * Genera nuevos insights para el proyecto.
   * Puede tardar varios segundos porque involucra:
   * - Cálculos de analytics
   * - Llamadas a Gemini
   * - Persistencia en BD
   */
  generateInsights(proyectoId: string): Observable<AIInsight[]> {
    return this.http.post<AIInsight[]>(`${this.base}/generate/${proyectoId}`, {});
  }

  /**
   * Descarta un insight (lo marca como dismissed).
   * El insight ya no aparecerá en la lista de activos.
   */
  dismissInsight(insightId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${insightId}/dismiss`, {});
  }
}
