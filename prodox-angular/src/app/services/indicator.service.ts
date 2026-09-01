import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { CreateIndicatorRequest, Indicator, RejectIndicatorRequest } from '../models/indicator.model';

@Injectable({ providedIn: 'root' })
export class IndicatorService {

  private readonly base = `${environment.apiBaseUrl}/indicators`;

  constructor(private http: HttpClient) {}

  list(): Observable<Indicator[]> {
    return this.http.get<Indicator[]>(this.base);
  }

  create(request: CreateIndicatorRequest): Observable<Indicator> {
    return this.http.post<Indicator>(this.base, request);
  }

  /** Genera métricas automáticamente para un factor (RF07 - simulado por Copiloto) */
  generateForFactor(factorId: string): Observable<Indicator[]> {
    return this.http.post<Indicator[]>(`${this.base}/generate`, { factorId });
  }

  approve(id: string): Observable<Indicator> {
    return this.http.patch<Indicator>(`${this.base}/${id}/approve`, {});
  }

  /** RF11 - Rechazar métrica con motivo */
  reject(id: string, request: RejectIndicatorRequest): Observable<Indicator> {
    return this.http.patch<Indicator>(`${this.base}/${id}/reject`, request);
  }
}
