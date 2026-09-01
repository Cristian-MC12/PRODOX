// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  RankingMetrica,
  MetricParametrizacionBase,
  GuardarParametrizacionRequest,
  TopParametrizacion,
  PendienteNotificacion
} from '../models/metric-ranking.model';

@Injectable({ providedIn: 'root' })
export class MetricRankingService {

  private readonly base = `${environment.apiBaseUrl}/metric-ranking`;

  constructor(private http: HttpClient) {}

  /** Top 5 métricas más usadas */
  getRanking(): Observable<RankingMetrica[]> {
    return this.http.get<RankingMetrica[]>(this.base);
  }

  /** Parametrización base de una métrica (la del usuario original) */
  getBase(factorId: string): Observable<MetricParametrizacionBase> {
    return this.http.get<MetricParametrizacionBase>(`${this.base}/${factorId}/base`);
  }

  /** Incrementar uso cuando alguien selecciona una métrica rankeada */
  incrementarUso(factorId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${factorId}/uso`, {});
  }

  /** Guardar parametrización (nueva o copia) */
  guardar(request: GuardarParametrizacionRequest): Observable<MetricParametrizacionBase> {
    return this.http.post<MetricParametrizacionBase>(`${this.base}/parametrizacion`, request);
  }

  /** Resumen persistente (BD) de pendientes/aprobadas/rechazadas de un proyecto. */
  getResumen(proyectoId: string): Observable<{ pendientes: number; aprobadas: number; rechazadas: number }> {
    return this.http.get<{ pendientes: number; aprobadas: number; rechazadas: number }>(
      `${this.base}/resumen?proyectoId=${proyectoId}`
    );
  }

  /**
   * Revisión de navegación: parametrizaciones pendientes de revisión de un proyecto.
   * Mismo endpoint que ya consumían (por HttpClient crudo, cada uno por su lado)
   * planeacion/resumen-seleccion/verificacion — se centraliza acá para que la
   * campanita de notificaciones (y cualquier otro consumidor futuro) no duplique
   * la llamada cruda. No es un endpoint nuevo.
   */
  getPendientes(proyectoId: string): Observable<PendienteNotificacion[]> {
    return this.http.get<PendienteNotificacion[]>(`${this.base}/pendientes?proyectoId=${proyectoId}`);
  }

  /** Top 3 parametrizaciones más usadas de un factor */
  getTop3(factorId: string): Observable<TopParametrizacion[]> {
    return this.http.get<TopParametrizacion[]>(`${this.base}/${factorId}/top3`);
  }

  /** Top 3 parametrizaciones por metricaId (flujo desde Planeación) */
  getTop3ByMetricaId(metricaId: string): Observable<TopParametrizacion[]> {
    return this.http.get<TopParametrizacion[]>(`${this.base}/metrica/${metricaId}/top3`);
  }

  /** Parametrización base por metricaId */
  getBaseByMetricaId(metricaId: string): Observable<MetricParametrizacionBase> {
    return this.http.get<MetricParametrizacionBase>(`${this.base}/metrica/${metricaId}/base`);
  }
}
