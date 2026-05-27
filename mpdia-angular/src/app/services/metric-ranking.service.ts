// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  RankingMetrica,
  MetricParametrizacionBase,
  GuardarParametrizacionRequest,
  TopParametrizacion
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

  /** Top 3 parametrizaciones más usadas de un factor */
  getTop3(factorId: string): Observable<TopParametrizacion[]> {
    return this.http.get<TopParametrizacion[]>(`${this.base}/${factorId}/top3`);
  }
}
