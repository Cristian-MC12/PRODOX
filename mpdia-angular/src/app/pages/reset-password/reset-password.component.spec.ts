// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { Subject, of, throwError } from 'rxjs';
import { ResetPasswordComponent } from './reset-password.component';
import { AuthService } from '../../services/auth.service';

describe('ResetPasswordComponent', () => {
  let fixture: ComponentFixture<ResetPasswordComponent>;
  let component: ResetPasswordComponent;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  function setup(queryParams: Record<string, string>): void {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['resetPassword']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      imports: [ResetPasswordComponent, HttpClientTestingModule],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: ActivatedRoute, useValue: { queryParams: of(queryParams) } }
      ]
    });

    fixture = TestBed.createComponent(ResetPasswordComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('sin token en la URL: muestra un error y no renderiza el formulario', () => {
    setup({});

    expect(component.token).toBe('');
    const form = fixture.nativeElement.querySelector('form');
    expect(form).toBeFalsy();
    expect(fixture.nativeElement.textContent).toContain('no es válido');
  });

  it('token presente: renderiza el formulario de nueva contraseña', () => {
    setup({ token: 'token-valido' });

    expect(component.token).toBe('token-valido');
    const form = fixture.nativeElement.querySelector('form');
    expect(form).toBeTruthy();
  });

  it('confirmación no coincide: el formulario es inválido y no se llama al backend', () => {
    setup({ token: 'token-valido' });
    component.form.setValue({ newPassword: 'password123', confirmPassword: 'otraPassword' });

    component.submit();

    expect(component.form.errors?.['passwordsNoCoinciden']).toBeTrue();
    expect(authServiceSpy.resetPassword).not.toHaveBeenCalled();
  });

  it('contraseña muy corta: el formulario es inválido y no se llama al backend', () => {
    setup({ token: 'token-valido' });
    component.form.setValue({ newPassword: '1234', confirmPassword: '1234' });

    component.submit();

    expect(component.form.invalid).toBeTrue();
    expect(authServiceSpy.resetPassword).not.toHaveBeenCalled();
  });

  it('éxito: llama a authService.resetPassword con el token y la nueva contraseña, muestra el mensaje de éxito', () => {
    setup({ token: 'token-valido' });
    authServiceSpy.resetPassword.and.returnValue(of({ message: 'Contraseña actualizada correctamente.' }));
    component.form.setValue({ newPassword: 'password123', confirmPassword: 'password123' });

    component.submit();

    expect(authServiceSpy.resetPassword).toHaveBeenCalledWith('token-valido', 'password123');
    expect(component.exito).toBeTrue();
    expect(component.mensaje).toBe('Contraseña actualizada correctamente.');
    expect(component.loading).toBeFalse();
  });

  it('token expirado: muestra el mensaje de error específico del backend', () => {
    setup({ token: 'token-expirado' });
    authServiceSpy.resetPassword.and.returnValue(throwError(() => ({ error: { error: 'El enlace de recuperación expiró.' } })));
    component.form.setValue({ newPassword: 'password123', confirmPassword: 'password123' });

    component.submit();

    expect(component.errorMsg).toBe('El enlace de recuperación expiró.');
    expect(component.exito).toBeFalse();
    expect(component.loading).toBeFalse();
  });

  it('token inválido: muestra el mensaje de error específico del backend', () => {
    setup({ token: 'token-invalido' });
    authServiceSpy.resetPassword.and.returnValue(throwError(() => ({ error: { error: 'Token de recuperación inválido.' } })));
    component.form.setValue({ newPassword: 'password123', confirmPassword: 'password123' });

    component.submit();

    expect(component.errorMsg).toBe('Token de recuperación inválido.');
  });

  it('token ya utilizado: muestra el mensaje de error específico del backend', () => {
    setup({ token: 'token-usado' });
    authServiceSpy.resetPassword.and.returnValue(throwError(() => ({ error: { error: 'Este enlace de recuperación ya fue utilizado.' } })));
    component.form.setValue({ newPassword: 'password123', confirmPassword: 'password123' });

    component.submit();

    expect(component.errorMsg).toBe('Este enlace de recuperación ya fue utilizado.');
  });

  it('irALogin: navega a /auth', () => {
    setup({ token: 'token-valido' });

    component.irALogin();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/auth']);
  });
});
