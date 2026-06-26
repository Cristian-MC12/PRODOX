// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { MetricaDto } from '../models/metrica.model';

@Injectable({ providedIn: 'root' })
export class MetricaService {

  private readonly base = `${environment.apiBaseUrl}/metricas`;

  constructor(private http: HttpClient) {}

  listar(): Observable<MetricaDto[]> {
    return this.http.get<MetricaDto[]>(this.base);
  }

  listarPorCategoria(categoria: string): Observable<MetricaDto[]> {
    return this.http.get<MetricaDto[]>(this.base, { params: { categoria } });
  }
}
