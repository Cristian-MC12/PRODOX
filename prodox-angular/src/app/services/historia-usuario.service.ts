// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  ActualizarHistoriaUsuarioRequest,
  CrearHistoriaUsuarioRequest,
  EstadoHistoria,
  HistoriaUsuarioDto,
  PrioridadHistoria
} from '../models/historia-usuario.model';

@Injectable({ providedIn: 'root' })
export class HistoriaUsuarioService {

  private readonly base = `${environment.apiBaseUrl}/historias`;

  constructor(private http: HttpClient) {}

  listar(proyectoId: string): Observable<HistoriaUsuarioDto[]> {
    return this.http.get<HistoriaUsuarioDto[]>(`${this.base}/${proyectoId}`);
  }

  detalle(historiaId: string): Observable<HistoriaUsuarioDto> {
    return this.http.get<HistoriaUsuarioDto>(`${this.base}/detalle/${historiaId}`);
  }

  crear(proyectoId: string, request: CrearHistoriaUsuarioRequest): Observable<HistoriaUsuarioDto> {
    return this.http.post<HistoriaUsuarioDto>(`${this.base}/${proyectoId}`, request);
  }

  actualizar(historiaId: string, request: ActualizarHistoriaUsuarioRequest): Observable<HistoriaUsuarioDto> {
    return this.http.patch<HistoriaUsuarioDto>(`${this.base}/${historiaId}`, request);
  }

  cambiarPrioridad(historiaId: string, prioridad: PrioridadHistoria): Observable<HistoriaUsuarioDto> {
    return this.http.patch<HistoriaUsuarioDto>(`${this.base}/${historiaId}/prioridad`, { prioridad });
  }

  cambiarEstado(historiaId: string, estado: EstadoHistoria): Observable<HistoriaUsuarioDto> {
    return this.http.patch<HistoriaUsuarioDto>(`${this.base}/${historiaId}/estado`, { estado });
  }

  /** sprintId en null desasigna la historia del sprint (vuelve al backlog). */
  asignarSprint(historiaId: string, sprintId: string | null): Observable<HistoriaUsuarioDto> {
    return this.http.patch<HistoriaUsuarioDto>(`${this.base}/${historiaId}/sprint`, { sprintId });
  }

  eliminar(historiaId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${historiaId}`);
  }
}
