// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Factor, SelectFactorRequest, SprintSelection } from '../models/factor.model';

@Injectable({ providedIn: 'root' })
export class FactorService {

  private readonly base = `${environment.apiBaseUrl}/factors`;

  constructor(private http: HttpClient) {}

  list(): Observable<Factor[]> {
    return this.http.get<Factor[]>(this.base);
  }

  listSelections(sprintName = 'Sprint Actual'): Observable<SprintSelection[]> {
    const params = new HttpParams().set('sprintName', sprintName);
    return this.http.get<SprintSelection[]>(`${this.base}/selections`, { params });
  }

  select(request: SelectFactorRequest): Observable<void> {
    return this.http.post<void>(`${this.base}/selections`, request);
  }

  unselect(factorId: string, sprintName = 'Sprint Actual'): Observable<void> {
    const params = new HttpParams().set('sprintName', sprintName);
    return this.http.delete<void>(`${this.base}/selections/${factorId}`, { params });
  }
}
