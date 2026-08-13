// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { ChatRequest, ChatResponse } from '../models/ai-copilot.model';

@Injectable({ providedIn: 'root' })
export class AICopilotService {

  private readonly base = `${environment.apiBaseUrl}/ai/copilot`;

  constructor(private http: HttpClient) {}

  /**
   * Envía un mensaje al AI Copilot
   */
  chat(request: ChatRequest): Observable<ChatResponse> {
    return this.http.post<ChatResponse>(`${this.base}/chat`, request).pipe(
      catchError(this.handleError)
    );
  }

  private handleError(error: HttpErrorResponse): Observable<never> {
    let errorMessage = 'Ocurrió un error inesperado';

    if (error.error instanceof ErrorEvent) {
      // Error del lado del cliente
      errorMessage = 'No se pudo conectar con el servidor';
    } else {
      // Error del backend
      switch (error.status) {
        case 400:
          errorMessage = error.error?.error || 'Revisa el mensaje o el proyecto seleccionado';
          break;
        case 401:
          errorMessage = 'Tu sesión ha expirado';
          break;
        case 403:
          errorMessage = error.error?.error || 'No tienes acceso a este proyecto';
          break;
        case 429:
          errorMessage = error.error?.error || 'Has alcanzado el límite de consultas. Intenta nuevamente en unos minutos';
          break;
        case 500:
          errorMessage = 'El AI Copilot no está disponible en este momento';
          break;
        case 0:
          errorMessage = 'No se pudo conectar con el servidor';
          break;
        default:
          errorMessage = error.error?.error || 'Ocurrió un error inesperado';
      }
    }

    return throwError(() => new Error(errorMessage));
  }
}
