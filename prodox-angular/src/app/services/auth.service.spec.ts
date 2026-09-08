// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { AuthRequest, AuthResponse } from '../models/auth.model';
import { environment } from '../../environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let routerSpy: jasmine.SpyObj<Router>;

  const mockResponse: AuthResponse = {
    token: 'jwt.test.token',
    userId: 'uuid-123',
    email: 'test@mpdia.com',
    role: 'scrum_master'
  };

  beforeEach(() => {
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    localStorage.clear();

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        AuthService,
        { provide: Router, useValue: routerSpy }
      ]
    });

    service  = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  // ── login ───────────────────────────────────────────────────────────────

  it('login: debe hacer POST a /auth/login y persistir el token', () => {
    const req: AuthRequest = { email: 'test@mpdia.com', password: 'password123' };

    service.login(req).subscribe(res => {
      expect(res.token).toBe('jwt.test.token');
      expect(res.role).toBe('scrum_master');
    });

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`);
    expect(http.request.method).toBe('POST');
    expect(http.request.body).toEqual(req);
    http.flush(mockResponse);

    expect(localStorage.getItem('mpdia_token')).toBe('jwt.test.token');
    expect(service.currentUser()?.email).toBe('test@mpdia.com');
  });

  // ── register ────────────────────────────────────────────────────────────

  it('register: debe hacer POST a /auth/register con rol', () => {
    const req: AuthRequest = { email: 'nuevo@mpdia.com', password: 'password123', role: 'scrum_member' };
    const res: AuthResponse = { ...mockResponse, email: 'nuevo@mpdia.com', role: 'scrum_member' };

    service.register(req).subscribe(r => {
      expect(r.role).toBe('scrum_member');
    });

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/auth/register`);
    expect(http.request.method).toBe('POST');
    http.flush(res);
  });

  // ── logout ──────────────────────────────────────────────────────────────

  it('logout: debe limpiar localStorage y redirigir a /auth', () => {
    localStorage.setItem('mpdia_token', 'algún_token');
    localStorage.setItem('mpdia_user', JSON.stringify(mockResponse));

    service.logout();

    expect(localStorage.getItem('mpdia_token')).toBeNull();
    expect(localStorage.getItem('mpdia_user')).toBeNull();
    expect(service.currentUser()).toBeNull();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/auth']);
  });

  // Corrección: un proyecto activo que ya no existe (o ya no es del usuario)
  // no debe sobrevivir a un logout — si no, el siguiente login vuelve a
  // apuntar a ese mismo proyecto inválido y las llamadas que dependen de él
  // (ej. GET /sprints/{proyectoId}/activo) devuelven 403 apenas se entra.
  it('logout: debe limpiar también el proyecto activo en localStorage', () => {
    localStorage.setItem('mpdia_token', 'algún_token');
    localStorage.setItem('mpdia_user', JSON.stringify(mockResponse));
    localStorage.setItem('mpdia_proyecto_activo', JSON.stringify({ id: 'proyecto-eliminado' }));

    service.logout();

    expect(localStorage.getItem('mpdia_proyecto_activo')).toBeNull();
  });

  // ── getToken ────────────────────────────────────────────────────────────

  it('getToken: retorna el token almacenado en localStorage', () => {
    localStorage.setItem('mpdia_token', 'mi_token');
    expect(service.getToken()).toBe('mi_token');
  });

  it('getToken: retorna null si no hay token', () => {
    expect(service.getToken()).toBeNull();
  });

  // ── isLoggedIn ──────────────────────────────────────────────────────────

  it('isLoggedIn: retorna true si hay token', () => {
    localStorage.setItem('mpdia_token', 'token');
    expect(service.isLoggedIn()).toBeTrue();
  });

  it('isLoggedIn: retorna false si no hay token', () => {
    expect(service.isLoggedIn()).toBeFalse();
  });

  // ── currentUser signal ──────────────────────────────────────────────────

  it('currentUser: carga el usuario desde localStorage al inicializar', () => {
    localStorage.setItem('mpdia_user', JSON.stringify(mockResponse));
    // Re-instanciar para simular carga inicial
    const newService = new AuthService(TestBed.inject(HttpClient), routerSpy);
    expect(newService.currentUser()?.email).toBe('test@mpdia.com');
  });

  // ── exchangeOAuth2Code (callback de Google OAuth2) ─────────────────────
  // Bloque de seguridad JWT/OAuth2: el callback ya no recibe el JWT en la
  // URL (?token=...), solo un código opaco de un solo uso (?code=...) que
  // se canjea aquí por HTTPS POST. El JWT solo existe en el cuerpo JSON de
  // esta respuesta, nunca en la URL ni en el historial del navegador.

  it('exchangeOAuth2Code: hace POST a /auth/oauth2/exchange con el código y persiste la sesión con la misma clave que login/register', () => {
    const googleResponse: AuthResponse = {
      token: 'jwt.google.token',
      userId: 'uuid-google-1',
      email: 'google@mpdia.com',
      role: 'scrum_member',
      nombre: 'Google User'
    };

    service.exchangeOAuth2Code('codigo-opaco-de-un-solo-uso').subscribe(res => {
      expect(res.token).toBe('jwt.google.token');
      expect(res.email).toBe('google@mpdia.com');
    });

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/auth/oauth2/exchange`);
    expect(http.request.method).toBe('POST');
    expect(http.request.body).toEqual({ code: 'codigo-opaco-de-un-solo-uso' });
    http.flush(googleResponse);

    expect(localStorage.getItem('mpdia_token')).toBe('jwt.google.token');
    expect(service.currentUser()?.email).toBe('google@mpdia.com');
    expect(service.currentUser()?.role).toBe('scrum_member');
    expect(service.currentUser()?.userId).toBe('uuid-google-1');
    expect(service.currentUser()?.nombre).toBe('Google User');
    expect(service.isLoggedIn()).toBeTrue();
  });

  it('exchangeOAuth2Code: si el código es inválido o expirado, propaga el error y no persiste ninguna sesión', () => {
    let errorRecibido: unknown = null;

    service.exchangeOAuth2Code('codigo-invalido').subscribe({
      next: () => fail('no debería emitir un valor exitoso'),
      error: (err) => (errorRecibido = err)
    });

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/auth/oauth2/exchange`);
    http.flush({ error: 'Código de intercambio inválido o expirado.' }, { status: 400, statusText: 'Bad Request' });

    expect(errorRecibido).not.toBeNull();
    expect(localStorage.getItem('mpdia_token')).toBeNull();
    expect(service.currentUser()).toBeNull();
  });

  // ── invitación pendiente (preservada durante login/registro/Google) ────

  it('setInvitacionPendiente / getInvitacionPendiente: guarda y recupera el código', () => {
    service.setInvitacionPendiente('PRJ-ABC123');
    expect(service.getInvitacionPendiente()).toBe('PRJ-ABC123');
  });

  it('getInvitacionPendiente: retorna null si no hay ninguna guardada', () => {
    expect(service.getInvitacionPendiente()).toBeNull();
  });

  it('clearInvitacionPendiente: elimina el código guardado', () => {
    service.setInvitacionPendiente('PRJ-ABC123');
    service.clearInvitacionPendiente();
    expect(service.getInvitacionPendiente()).toBeNull();
  });
});
