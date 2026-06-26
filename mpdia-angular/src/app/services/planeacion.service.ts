// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ProyectoMetricaDto } from '../models/planeacion.model';
import { VariableDto } from '../models/variable.model';

@Injectable({ providedIn: 'root' })
export class PlaneacionService {

  constructor(private http: HttpClient) {}

  private base(proyectoId: string) {
    return `${environment.apiBaseUrl}/planeacion/${proyectoId}`;
  }

  listarMetricas(proyectoId: string): Observable<ProyectoMetricaDto[]> {
    return this.http.get<ProyectoMetricaDto[]>(`${this.base(proyectoId)}/metricas`);
  }

  listarSeleccionadas(proyectoId: string): Observable<ProyectoMetricaDto[]> {
    return this.http.get<ProyectoMetricaDto[]>(`${this.base(proyectoId)}/metricas/seleccionadas`);
  }

  seleccionar(proyectoId: string, metricaId: string): Observable<void> {
    return this.http.post<void>(`${this.base(proyectoId)}/metricas/${metricaId}/seleccionar`, {});
  }

  deseleccionar(proyectoId: string, metricaId: string): Observable<void> {
    return this.http.delete<void>(`${this.base(proyectoId)}/metricas/${metricaId}/seleccionar`);
  }

  aprobar(proyectoId: string, metricaId: string): Observable<VariableDto> {
    return this.http.post<VariableDto>(`${this.base(proyectoId)}/metricas/${metricaId}/aprobar`, {});
  }

  desaprobar(proyectoId: string, metricaId: string): Observable<void> {
    return this.http.delete<void>(`${this.base(proyectoId)}/metricas/${metricaId}/aprobar`);
  }

  listarVariables(proyectoId: string): Observable<VariableDto[]> {
    return this.http.get<VariableDto[]>(`${this.base(proyectoId)}/variables`);
  }

  sincronizarVariables(proyectoId: string): Observable<VariableDto[]> {
    return this.http.post<VariableDto[]>(`${this.base(proyectoId)}/variables/sincronizar`, {});
  }
}
