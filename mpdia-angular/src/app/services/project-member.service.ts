// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ProjectMemberDto } from '../models/project-member.model';

@Injectable({ providedIn: 'root' })
export class ProjectMemberService {

  private readonly base = `${environment.apiBaseUrl}/project-members`;

  constructor(private http: HttpClient) {}

  listar(proyectoId: string): Observable<ProjectMemberDto[]> {
    return this.http.get<ProjectMemberDto[]>(`${this.base}/${proyectoId}`);
  }

  invitar(proyectoId: string, email: string): Observable<{ codigo: string }> {
    return this.http.post<{ codigo: string }>(`${this.base}/${proyectoId}/invitar`, { email });
  }

  unirse(codigo: string): Observable<ProjectMemberDto> {
    return this.http.post<ProjectMemberDto>(`${this.base}/unirse`, { codigo });
  }
}
