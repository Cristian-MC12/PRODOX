// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { SprintDto } from '../models/sprint.model';

@Injectable({ providedIn: 'root' })
export class SprintService {

  private readonly base = `${environment.apiBaseUrl}/sprints`;

  constructor(private http: HttpClient) {}

  getActivo(proyectoId: string): Observable<SprintDto> {
    return this.http.get<SprintDto>(`${this.base}/${proyectoId}/activo`);
  }

  getById(sprintId: string): Observable<SprintDto> {
    return this.http.get<SprintDto>(`${this.base}/detalle/${sprintId}`);
  }

  listar(proyectoId: string): Observable<SprintDto[]> {
    return this.http.get<SprintDto[]>(`${this.base}/${proyectoId}`);
  }

  cerrarEIniciarSiguiente(proyectoId: string, sprintGoal: string): Observable<SprintDto> {
    return this.http.post<SprintDto>(`${this.base}/${proyectoId}/siguiente`, { sprintGoal });
  }

  reabrir(sprintId: string): Observable<SprintDto> {
    return this.http.patch<SprintDto>(`${this.base}/${sprintId}/reabrir`, {});
  }
}
