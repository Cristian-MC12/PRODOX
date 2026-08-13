// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AISprintReport, AIRetrospective } from '../models/ai-reports.model';

/**
 * Servicio para generar reportes ejecutivos y retrospectivas de sprint usando IA.
 * 
 * IMPORTANTE:
 * - Los reportes se generan bajo demanda (NO hay persistencia)
 * - Gemini NO inventa métricas, solo interpreta datos reales del backend
 * - Rate limiting aplicado: 10 requests/minuto
 * - Requiere JWT (HttpClient interceptor maneja autenticación)
 */
@Injectable({ providedIn: 'root' })
export class AIReportService {

  private readonly reportBase = `${environment.apiBaseUrl}/ai/reports`;
  private readonly retroBase = `${environment.apiBaseUrl}/ai/retrospectives`;

  constructor(private http: HttpClient) {}

  /**
   * Genera un reporte ejecutivo para el sprint especificado.
   * 
   * Combina:
   * - Métricas reales del sprint (AgileAnalyticsService)
   * - Insights existentes (AIInsightsService)
   * - Interpretación y recomendaciones (Gemini)
   * 
   * Puede tardar varios segundos porque involucra llamada a Gemini.
   * 
   * Errores posibles:
   * - 400: Sprint no encontrado
   * - 403: Usuario no autorizado
   * - 429: Rate limit excedido
   * - 500: Error interno
   */
  generateSprintReport(sprintId: string): Observable<AISprintReport> {
    return this.http.post<AISprintReport>(
      `${this.reportBase}/sprint/${sprintId}/generate`,
      {}
    );
  }

  /**
   * Genera una retrospectiva inteligente para el sprint especificado.
   * 
   * Incluye:
   * - Métricas del sprint actual
   * - Comparación con sprint anterior (si existe)
   * - Insights y riesgos detectados
   * - Observaciones y preguntas generadas por Gemini
   * 
   * Si es el primer sprint del proyecto, NO intentará comparación.
   * 
   * Puede tardar varios segundos porque involucra llamada a Gemini.
   * 
   * Errores posibles:
   * - 400: Sprint no encontrado
   * - 403: Usuario no autorizado
   * - 429: Rate limit excedido
   * - 500: Error interno
   */
  generateRetrospective(sprintId: string): Observable<AIRetrospective> {
    return this.http.post<AIRetrospective>(
      `${this.retroBase}/sprint/${sprintId}/generate`,
      {}
    );
  }
}
