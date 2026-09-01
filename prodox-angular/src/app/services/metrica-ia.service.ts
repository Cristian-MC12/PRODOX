// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { CrearMetricaIARequest, MetricaIACreadaDto, MetricaIAPropuestaDto } from '../models/metrica-ia.model';

@Injectable({ providedIn: 'root' })
export class MetricaIAService {

  private readonly base = `${environment.apiBaseUrl}/metricas-ia`;

  constructor(private http: HttpClient) {}

  generarPropuesta(necesidad: string): Observable<MetricaIAPropuestaDto> {
    return this.http.post<MetricaIAPropuestaDto>(`${this.base}/propuesta`, { necesidad });
  }

  crear(request: CrearMetricaIARequest): Observable<MetricaIACreadaDto> {
    return this.http.post<MetricaIACreadaDto>(`${this.base}/crear`, request);
  }
}
