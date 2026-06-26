// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { EvaluacionSprintDto } from '../models/planeacion.model';

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
}
