// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { EvaluacionSprintDto } from '../models/planeacion.model';
import { MetricaEvaluacionDetalleDto } from '../models/evaluacion-detalle.model';

@Injectable({ providedIn: 'root' })
export class EvaluacionService {

  private readonly base = `${environment.apiBaseUrl}/evaluacion`;

  constructor(private http: HttpClient) {}

  porProyecto(proyectoId: string): Observable<EvaluacionSprintDto[]> {
    return this.http.get<EvaluacionSprintDto[]>(`${this.base}/proyecto/${proyectoId}`);
  }

  porSprint(sprintId: string): Observable<EvaluacionSprintDto[]> {
    return this.http.get<EvaluacionSprintDto[]>(`${this.base}/sprint/${sprintId}`);
  }

  detalle(proyectoId: string): Observable<MetricaEvaluacionDetalleDto[]> {
    return this.http.get<MetricaEvaluacionDetalleDto[]>(`${this.base}/proyecto/${proyectoId}/detalle`);
  }
}
