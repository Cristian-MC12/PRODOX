// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';

describe('authGuard', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  // JWT válido: exp en el año 2099
  const validToken = (() => {
    const header  = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
    const payload = btoa(JSON.stringify({ sub: 'uuid', exp: 4070908800 }));
    return `${header}.${payload}.signature`;
  })();

  // JWT expirado: exp en 2000
  const expiredToken = (() => {
    const header  = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
    const payload = btoa(JSON.stringify({ sub: 'uuid', exp: 946684800 }));
    return `${header}.${payload}.signature`;
  })();

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['getToken', 'logout']);
    routerSpy      = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router,      useValue: routerSpy }
      ]
    });
  });

  function runGuard(): boolean | Promise<boolean> {
    return TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    ) as boolean;
  }

  it('permite acceso con token válido', () => {
    authServiceSpy.getToken.and.returnValue(validToken);
    expect(runGuard()).toBeTrue();
  });

  it('bloquea acceso si no hay token y redirige a /auth', () => {
    authServiceSpy.getToken.and.returnValue(null);
    expect(runGuard()).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/auth']);
  });

  it('bloquea acceso con token expirado y llama logout', () => {
    authServiceSpy.getToken.and.returnValue(expiredToken);
    expect(runGuard()).toBeFalse();
    expect(authServiceSpy.logout).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/auth']);
  });
});
