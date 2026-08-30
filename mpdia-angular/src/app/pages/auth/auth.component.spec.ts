// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { Subject, of, throwError } from 'rxjs';
import { AuthComponent } from './auth.component';
import { AuthService } from '../../services/auth.service';

describe('AuthComponent — callback de Google OAuth2', () => {
  let fixture: ComponentFixture<AuthComponent>;
  let component: AuthComponent;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;
  let queryParamsSubject: Subject<any>;

  function setup(queryParams: Record<string, string>): void {
    queryParamsSubject = new Subject();
    authServiceSpy = jasmine.createSpyObj('AuthService', ['login', 'register', 'persistFromToken', 'getInvitacionPendiente']);
    authServiceSpy.getInvitacionPendiente.and.returnValue(null);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      imports: [AuthComponent, HttpClientTestingModule],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: ActivatedRoute, useValue: { queryParams: queryParamsSubject.asObservable() } }
      ]
    });

    fixture = TestBed.createComponent(AuthComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    queryParamsSubject.next(queryParams);
  }

  it('token recibido: persiste la sesión vía AuthService (misma clave que login normal) y navega a la app', () => {
    setup({ token: 'jwt.callback.token' });

    expect(authServiceSpy.persistFromToken).toHaveBeenCalledWith('jwt.callback.token');
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/']);
  });

  // Corrección: el enlace de invitación por correo apuntaba a una ruta que
  // nunca existió (/proyectos/unirse) — el código pendiente guardado antes
  // de ir a Google debe recuperar exactamente el mismo flujo de aceptación.
  it('token recibido con una invitación pendiente: redirige a /invitacion con el código en vez de a la app', () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['login', 'register', 'persistFromToken', 'getInvitacionPendiente']);
    authServiceSpy.getInvitacionPendiente.and.returnValue('PRJ-ABC123');
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    queryParamsSubject = new Subject();

    TestBed.configureTestingModule({
      imports: [AuthComponent, HttpClientTestingModule],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: ActivatedRoute, useValue: { queryParams: queryParamsSubject.asObservable() } }
      ]
    });
    fixture = TestBed.createComponent(AuthComponent);
    fixture.detectChanges();
    queryParamsSubject.next({ token: 'jwt.callback.token' });

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/invitacion'], { queryParams: { codigo: 'PRJ-ABC123' } });
    expect(routerSpy.navigate).not.toHaveBeenCalledWith(['/']);
  });

  it('?tab=register en la URL abre directamente el formulario de registro (usado por /invitacion cuando el usuario no tiene cuenta)', () => {
    setup({ tab: 'register' });

    expect(component.tab).toBe('register');
  });

  it('Google OAuth falla: muestra error controlado y no navega ni entra en loop hacia la app', () => {
    setup({ error: 'oauth_failed' });

    expect(component.errorMsg).toBeTruthy();
    expect(authServiceSpy.persistFromToken).not.toHaveBeenCalled();
    expect(routerSpy.navigate).not.toHaveBeenCalledWith(['/']);
  });

  it('sin token ni error (carga normal de /auth): no persiste sesión ni redirige automáticamente', () => {
    setup({});

    expect(authServiceSpy.persistFromToken).not.toHaveBeenCalled();
    expect(routerSpy.navigate).not.toHaveBeenCalledWith(['/']);
  });
});

describe('AuthComponent — selector de rol en registro', () => {
  let fixture: ComponentFixture<AuthComponent>;
  let component: AuthComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AuthComponent, HttpClientTestingModule],
      providers: [
        { provide: ActivatedRoute, useValue: { queryParams: new Subject().asObservable() } }
      ]
    });

    fixture = TestBed.createComponent(AuthComponent);
    component = fixture.componentInstance;
    component.switchTab('register');
    fixture.detectChanges();
  });

  // Corrección: el <select> de rol enviaba valores en mayúsculas
  // (DEVELOPER/SCRUM_MASTER/PRODUCT_OWNER) que nunca coincidían con los
  // roles que AuthService.register acepta en el backend (scrum_master,
  // scrum_member) — el rol elegido en el formulario se ignoraba siempre y
  // el registro caía en el rol por defecto sin que el usuario lo supiera.
  it('las opciones del selector usan exactamente los roles soportados por el backend', () => {
    const options: NodeListOf<HTMLOptionElement> = fixture.nativeElement.querySelectorAll('select[formControlName="role"] option');
    const values = Array.from(options).map(o => o.value);

    expect(values).toEqual(['scrum_member', 'scrum_master']);
  });

  it('registrar con un rol y nombre seleccionados envía esos mismos valores al backend', () => {
    component.form.setValue({
      email: 'nuevo@mpdia.com',
      password: 'password123',
      role: 'scrum_master',
      nombre: 'Juan Pérez'
    });

    const authServiceSpy = jasmine.createSpyObj('AuthService', ['register']);
    authServiceSpy.register.and.returnValue({ subscribe: () => {} });
    (component as any).authService = authServiceSpy;

    component.submit();

    expect(authServiceSpy.register).toHaveBeenCalledWith({
      email: 'nuevo@mpdia.com',
      password: 'password123',
      role: 'scrum_master',
      nombre: 'Juan Pérez'
    });
  });
});

describe('AuthComponent — recuperar contraseña', () => {
  let fixture: ComponentFixture<AuthComponent>;
  let component: AuthComponent;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['login', 'register', 'persistFromToken', 'forgotPassword']);

    TestBed.configureTestingModule({
      imports: [AuthComponent, HttpClientTestingModule],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: ActivatedRoute, useValue: { queryParams: new Subject().asObservable() } }
      ]
    });

    fixture = TestBed.createComponent(AuthComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('"¿Olvidaste tu contraseña?" cambia al formulario de recuperación (tab "forgot")', () => {
    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('.forgot-link');
    link.click();
    fixture.detectChanges();

    expect(component.tab).toBe('forgot');
    const forgotInput = fixture.nativeElement.querySelector('input[formControlName="email"]');
    expect(forgotInput).toBeTruthy();
  });

  it('formulario inválido (email vacío): no llama al backend', () => {
    component.switchTab('forgot');
    component.sendForgotPassword();

    expect(authServiceSpy.forgotPassword).not.toHaveBeenCalled();
  });

  it('envío exitoso: muestra el mensaje genérico del backend y apaga el loading', () => {
    component.switchTab('forgot');
    component.forgotForm.setValue({ email: 'usuario@mpdia.com' });
    authServiceSpy.forgotPassword.and.returnValue(of({
      message: 'Si el correo está registrado, recibirás instrucciones para recuperar tu contraseña.'
    }));

    component.sendForgotPassword();

    expect(authServiceSpy.forgotPassword).toHaveBeenCalledWith('usuario@mpdia.com');
    expect(component.forgotMsg).toBe('Si el correo está registrado, recibirás instrucciones para recuperar tu contraseña.');
    expect(component.forgotLoading).toBeFalse();
  });

  it('estado de carga: se activa mientras la petición está en curso', () => {
    component.switchTab('forgot');
    component.forgotForm.setValue({ email: 'usuario@mpdia.com' });
    authServiceSpy.forgotPassword.and.returnValue(new Subject<{ message: string }>().asObservable());

    component.sendForgotPassword();

    expect(component.forgotLoading).toBeTrue();
  });

  it('error de red/backend: muestra un mensaje de error sin filtrar si el correo existe', () => {
    component.switchTab('forgot');
    component.forgotForm.setValue({ email: 'usuario@mpdia.com' });
    authServiceSpy.forgotPassword.and.returnValue(throwError(() => ({ error: { error: 'Error de conexión.' } })));

    component.sendForgotPassword();

    expect(component.errorMsg).toBe('Error de conexión.');
    expect(component.forgotLoading).toBeFalse();
  });

  it('"Volver a iniciar sesión" regresa al tab de login', () => {
    component.switchTab('forgot');
    fixture.detectChanges();

    component.switchTab('login');

    expect(component.tab).toBe('login');
  });
});

describe('AuthComponent — redirección después de login con invitación pendiente', () => {
  let fixture: ComponentFixture<AuthComponent>;
  let component: AuthComponent;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  function setup(): void {
    authServiceSpy = jasmine.createSpyObj('AuthService',
      ['login', 'register', 'persistFromToken', 'getInvitacionPendiente']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      imports: [AuthComponent, HttpClientTestingModule],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: ActivatedRoute, useValue: { queryParams: new Subject().asObservable() } }
      ]
    });

    fixture = TestBed.createComponent(AuthComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('login exitoso sin invitación pendiente: navega a la app normalmente (no rompe el login existente)', () => {
    setup();
    authServiceSpy.getInvitacionPendiente.and.returnValue(null);
    authServiceSpy.login.and.returnValue(of({ token: 't', userId: 'u', email: 'e@mpdia.com', role: 'scrum_member' }));
    component.form.setValue({ email: 'e@mpdia.com', password: 'password123', role: null, nombre: null });

    component.submit();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/']);
  });

  it('login exitoso con invitación pendiente: redirige a /invitacion con el código en vez de a la app', () => {
    setup();
    authServiceSpy.getInvitacionPendiente.and.returnValue('PRJ-XYZ789');
    authServiceSpy.login.and.returnValue(of({ token: 't', userId: 'u', email: 'e@mpdia.com', role: 'scrum_member' }));
    component.form.setValue({ email: 'e@mpdia.com', password: 'password123', role: null, nombre: null });

    component.submit();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/invitacion'], { queryParams: { codigo: 'PRJ-XYZ789' } });
  });

  it('registro exitoso con invitación pendiente: redirige a /invitacion con el código', () => {
    setup();
    component.switchTab('register');
    authServiceSpy.getInvitacionPendiente.and.returnValue('PRJ-NEW001');
    authServiceSpy.register.and.returnValue(of({ token: 't', userId: 'u', email: 'e@mpdia.com', role: 'scrum_member' }));
    component.form.setValue({ email: 'nuevo@mpdia.com', password: 'password123', role: 'scrum_member', nombre: 'Nuevo' });

    component.submit();

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/invitacion'], { queryParams: { codigo: 'PRJ-NEW001' } });
  });
});
