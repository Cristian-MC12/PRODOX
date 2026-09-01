// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ProyectoDto, CrearProyectoRequest } from '../models/proyecto.model';

@Injectable({ providedIn: 'root' })
export class ProyectoService {

  private readonly base = `${environment.apiBaseUrl}/proyectos`;

  constructor(private http: HttpClient) {}

  crear(request: CrearProyectoRequest): Observable<ProyectoDto> {
    return this.http.post<ProyectoDto>(this.base, request);
  }

  /** Proyectos donde el usuario es miembro (SM y Scrum Member) */
  getMisProyectos(): Observable<ProyectoDto[]> {
    return this.http.get<ProyectoDto[]>(`${this.base}/mios`);
  }

  getById(id: string): Observable<ProyectoDto> {
    return this.http.get<ProyectoDto>(`${this.base}/${id}`);
  }

  finalizar(id: string): Observable<ProyectoDto> {
    return this.http.patch<ProyectoDto>(`${this.base}/${id}/finalizar`, {});
  }

  /** Eliminar proyecto (solo Scrum Master dueño, validado también en backend) */
  eliminar(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
