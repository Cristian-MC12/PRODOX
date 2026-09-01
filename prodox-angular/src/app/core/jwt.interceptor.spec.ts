// Autor: Cristian Santiago Martinez Cordoba — PRODOX
// Corrección del bug de inicio de sesión: un 403 de autorización sobre un
// recurso puntual (ej. un proyecto del que el usuario ya no es miembro) no
// debe cerrar una sesión válida — solo un 401 (sesión realmente inválida,
// ver SecurityConfig.authenticationEntryPoint en el backend) debe hacerlo.
import { TestBed } from '@angular/core/testing';
import { HttpRequest, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { throwError, of } from 'rxjs';
import { jwtInterceptor } from './jwt.interceptor';
import { AuthService } from '../services/auth.service';

describe('jwtInterceptor', () => {
  let routerSpy: jasmine.SpyObj<Router>;
  let authSpy: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    authSpy = jasmine.createSpyObj('AuthService', ['getToken', 'logout']);
    authSpy.getToken.and.returnValue('un-token');

    TestBed.configureTestingModule({
      providers: [
        { provide: Router, useValue: routerSpy },
        { provide: AuthService, useValue: authSpy },
      ]
    });
  });

  function runInterceptor(url: string, errorStatus: number) {
    const req = new HttpRequest('GET', url);
    const error = new HttpErrorResponse({ status: errorStatus, url });
    const next = (r: HttpRequest<unknown>) => throwError(() => error);

    let caught: any = null;
    TestBed.runInInjectionContext(() => {
      jwtInterceptor(req, next as any).subscribe({
        error: (e) => { caught = e; }
      });
    });
    return caught;
  }

  it('401 en un endpoint protegido: cierra sesión y navega a /auth (sesión realmente inválida)', () => {
    runInterceptor('http://localhost:8080/api/sprints/x/activo', 401);

    expect(authSpy.logout).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/auth']);
  });

  it('403 en un endpoint protegido: NO cierra sesión (autorización de un recurso puntual, no la sesión)', () => {
    runInterceptor('http://localhost:8080/api/sprints/x/activo', 403);

    expect(authSpy.logout).not.toHaveBeenCalled();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('401 en /auth/login (credenciales incorrectas): NO cierra sesión — no hay sesión que cerrar', () => {
    runInterceptor('http://localhost:8080/api/auth/login', 401);

    expect(authSpy.logout).not.toHaveBeenCalled();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('el error siempre se propaga, se cierre sesión o no', () => {
    const err403 = runInterceptor('http://localhost:8080/api/sprints/x/activo', 403);
    const err401 = runInterceptor('http://localhost:8080/api/sprints/x/activo', 401);

    expect(err403.status).toBe(403);
    expect(err401.status).toBe(401);
  });
});
