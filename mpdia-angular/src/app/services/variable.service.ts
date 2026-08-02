// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ActualizarFormulaRequest, CrearVariableRequest, VariableDto } from '../models/variable.model';

@Injectable({ providedIn: 'root' })
export class VariableService {

  private base(proyectoId: string): string {
    return `${environment.apiBaseUrl}/proyectos/${proyectoId}/variables`;
  }

  constructor(private http: HttpClient) {}

  listar(proyectoId: string): Observable<VariableDto[]> {
    return this.http.get<VariableDto[]>(this.base(proyectoId));
  }

  crear(proyectoId: string, request: CrearVariableRequest): Observable<VariableDto> {
    return this.http.post<VariableDto>(this.base(proyectoId), request);
  }

  actualizarFormula(proyectoId: string, variableId: string, req: ActualizarFormulaRequest): Observable<VariableDto> {
    return this.http.patch<VariableDto>(`${this.base(proyectoId)}/${variableId}/formula`, req);
  }

  desactivar(proyectoId: string, variableId: string): Observable<void> {
    return this.http.delete<void>(`${this.base(proyectoId)}/${variableId}`);
  }
}
