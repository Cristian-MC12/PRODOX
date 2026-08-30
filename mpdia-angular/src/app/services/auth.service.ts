// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthRequest, AuthResponse } from '../models/auth.model';

const TOKEN_KEY      = 'mpdia_token';
const USER_KEY       = 'mpdia_user';
const PROYECTO_KEY   = 'mpdia_proyecto_activo';
const INVITACION_KEY = 'mpdia_invitacion_pendiente';

@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly base = `${environment.apiBaseUrl}/auth`;

  /** Signal reactivo con el usuario actual */
  currentUser = signal<AuthResponse | null>(this.loadUser());

  constructor(private http: HttpClient, private router: Router) {}

  login(request: AuthRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/login`, request).pipe(
      tap(res => this.persist(res))
    );
  }

  register(request: AuthRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/register`, request).pipe(
      tap(res => this.persist(res))
    );
  }

  /** Solicita la recuperación de contraseña. El backend siempre responde con
   *  un mensaje genérico, exista o no el correo (no permite enumerar cuentas). */
  forgotPassword(email: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.base}/forgot-password`, { email });
  }

  /** Establece una nueva contraseña a partir del token recibido por correo. */
  resetPassword(token: string, newPassword: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.base}/reset-password`, { token, newPassword });
  }

  /**
   * Preserva un código de invitación a proyecto mientras el usuario pasa por
   * login/registro/Google OAuth (todos esos flujos navegan fuera de, o
   * recargan, /invitacion). localStorage sobrevive tanto a la navegación
   * interna de Angular como a la ida y vuelta completa a Google.
   */
  setInvitacionPendiente(codigo: string): void {
    localStorage.setItem(INVITACION_KEY, codigo);
  }

  getInvitacionPendiente(): string | null {
    return localStorage.getItem(INVITACION_KEY);
  }

  clearInvitacionPendiente(): void {
    localStorage.removeItem(INVITACION_KEY);
  }

  /** Persiste la sesión a partir de un JWT recibido por el callback de OAuth2 (Google). */
  persistFromToken(token: string): void {
    const payload = this.decodeToken(token);
    this.persist({
      token,
      userId: payload.sub,
      email:  payload.email,
      role:   payload.role,
      nombre: payload.nombre
    });
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    // El proyecto activo también se limpia: si no, sobrevive a un logout/login
    // y el próximo inicio de sesión intenta cargar datos de un proyecto que
    // puede ya no existir o no pertenecerle al usuario que inició sesión.
    localStorage.removeItem(PROYECTO_KEY);
    this.currentUser.set(null);
    this.router.navigate(['/auth']);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  private persist(res: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(USER_KEY, JSON.stringify(res));
    this.currentUser.set(res);
  }

  private loadUser(): AuthResponse | null {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  }

  private decodeToken(token: string): any {
    return JSON.parse(atob(token.split('.')[1]));
  }
}
