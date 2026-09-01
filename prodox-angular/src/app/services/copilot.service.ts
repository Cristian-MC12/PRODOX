import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { CopilotConfig } from '../models/copilot.model';

@Injectable({ providedIn: 'root' })
export class CopilotService {

  private readonly base = `${environment.apiBaseUrl}/copilot`;

  constructor(private http: HttpClient) {}

  get(): Observable<CopilotConfig> {
    return this.http.get<CopilotConfig>(`${this.base}/config`);
  }

  save(config: CopilotConfig): Observable<CopilotConfig> {
    return this.http.put<CopilotConfig>(`${this.base}/config`, config);
  }

  syncNow(): Observable<CopilotConfig> {
    return this.http.post<CopilotConfig>(`${this.base}/sync`, {});
  }
}
