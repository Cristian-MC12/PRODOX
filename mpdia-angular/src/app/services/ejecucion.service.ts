// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { RegistroValorDto, RegistrarValorRequest } from '../models/ejecucion.model';

@Injectable({ providedIn: 'root' })
export class EjecucionService {

  private readonly base = `${environment.apiBaseUrl}/ejecucion`;

  constructor(private http: HttpClient) {}

  listarPorSprint(sprintId: string): Observable<RegistroValorDto[]> {
    return this.http.get<RegistroValorDto[]>(`${this.base}/sprint/${sprintId}`);
  }

  listarPorVariable(variableId: string, sprintId: string): Observable<RegistroValorDto[]> {
    return this.http.get<RegistroValorDto[]>(
      `${this.base}/variable/${variableId}/sprint/${sprintId}`
    );
  }

  registrar(request: RegistrarValorRequest): Observable<RegistroValorDto> {
    return this.http.post<RegistroValorDto>(this.base, request);
  }
}
