// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ProjectOverview, Risk, TrendAnalysis } from '../models/analytics.model';

/**
 * Servicio para consumir AgileAnalyticsService del backend.
 * 
 * IMPORTANTE:
 * - Todos los datos son calculados por backend (NO inventa métricas)
 * - NO llama a Gemini (solo datos determinísticos)
 * - Requiere JWT (HttpClient interceptor maneja autenticación)
 */
@Injectable({ providedIn: 'root' })
export class AnalyticsService {

  private readonly base = `${environment.apiBaseUrl}/analytics`;

  constructor(private http: HttpClient) {}

  /**
   * Obtiene resumen general del proyecto con métricas agregadas.
   * 
   * Retorna datosDisponibles=false si no hay sprints finalizados.
   */
  getProjectOverview(proyectoId: string): Observable<ProjectOverview> {
    return this.http.get<ProjectOverview>(`${this.base}/project/${proyectoId}/overview`);
  }

  /**
   * Identifica riesgos en el proyecto basándose en señales objetivas.
   * 
   * Requiere mínimo 3 sprints para tendencias.
   * NO incluye interpretación de IA, solo hechos detectables.
   */
  identifyRisks(proyectoId: string): Observable<Risk[]> {
    return this.http.get<Risk[]>(`${this.base}/project/${proyectoId}/risks`);
  }

  /**
   * Analiza tendencias de métricas a lo largo de los últimos N sprints.
   * 
   * @param proyectoId ID del proyecto
   * @param categoria Categoría específica (opcional). Si null, retorna todas.
   * @param numberOfSprints Número de sprints a analizar (default: 5)
   * 
   * Requiere mínimo 2 sprints finalizados.
   */
  getSprintTrends(
    proyectoId: string,
    categoria: string | null = null,
    numberOfSprints: number = 5
  ): Observable<TrendAnalysis[]> {
    let url = `${this.base}/project/${proyectoId}/trends?numberOfSprints=${numberOfSprints}`;
    if (categoria) {
      url += `&categoria=${encodeURIComponent(categoria)}`;
    }
    return this.http.get<TrendAnalysis[]>(url);
  }
}
