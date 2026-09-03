// Autor: Cristian Santiago Martinez Cordoba — PRODOX
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

  /**
   * rol es opcional: si se omite, el body queda exactamente igual que antes
   * de V39 ({ email }) y el backend conserva scrum_member (comportamiento
   * previo).
   */
  invitar(proyectoId: string, email: string, rol?: 'scrum_member' | 'product_owner'): Observable<{ codigo: string; emailEnviado: boolean }> {
    const body = rol ? { email, rol } : { email };
    return this.http.post<{ codigo: string; emailEnviado: boolean }>(`${this.base}/${proyectoId}/invitar`, body);
  }

  unirse(codigo: string): Observable<ProjectMemberDto> {
    return this.http.post<ProjectMemberDto>(`${this.base}/unirse`, { codigo });
  }

  /** Cambia el rol de un miembro existente (solo Scrum Master del proyecto — validado en el backend). */
  cambiarRol(proyectoId: string, userId: string, rol: 'scrum_member' | 'product_owner'): Observable<ProjectMemberDto> {
    return this.http.patch<ProjectMemberDto>(`${this.base}/${proyectoId}/${userId}/rol`, { rol });
  }

  /** Estado público de una invitación (sin requerir sesión) — usado por /invitacion. */
  consultarInvitacion(codigo: string): Observable<{ proyectoId: string | null; proyectoNombre: string | null; estado: string }> {
    return this.http.get<{ proyectoId: string | null; proyectoNombre: string | null; estado: string }>(`${this.base}/invitacion/${codigo}`);
  }
}
