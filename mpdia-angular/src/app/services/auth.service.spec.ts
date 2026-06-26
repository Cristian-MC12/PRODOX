// Autor: Cristian Santiago Martinez Cordoba — MPDIA
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
});
