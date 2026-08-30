// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError, Subject } from 'rxjs';
import { InvitacionComponent } from './invitacion.component';
import { AuthService } from '../../services/auth.service';
import { ProjectMemberService } from '../../services/project-member.service';
import { ProyectoService } from '../../services/proyecto.service';
import { SprintService } from '../../services/sprint.service';

describe('InvitacionComponent', () => {
  let fixture: ComponentFixture<InvitacionComponent>;
  let component: InvitacionComponent;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let memberServiceSpy: jasmine.SpyObj<ProjectMemberService>;
  let proyectoServiceSpy: jasmine.SpyObj<ProyectoService>;
  let sprintServiceSpy: jasmine.SpyObj<SprintService>;
  let routerSpy: jasmine.SpyObj<Router>;

  const proyectoDto = {
    id: 'uuid-proyecto-1', nombre: 'Proyecto Demo', descripcion: '', metodo: 'scrum' as const,
    timeBoxSemanas: 2, numeroSprints: 3, fechaInicio: '2026-01-01', productGoal: 'x', sprintGoal: '',
    estado: 'activo' as const, scrumMasterEmail: 'sm@mpdia.com', totalMiembros: 2, createdAt: '2026-01-01T00:00:00Z'
  };

  function setup(queryParams: Record<string, string>, loggedIn: boolean): void {
    authServiceSpy = jasmine.createSpyObj('AuthService',
      ['isLoggedIn', 'setInvitacionPendiente', 'clearInvitacionPendiente', 'logout']);
    authServiceSpy.isLoggedIn.and.returnValue(loggedIn);
    memberServiceSpy = jasmine.createSpyObj('ProjectMemberService', ['consultarInvitacion', 'unirse']);
    proyectoServiceSpy = jasmine.createSpyObj('ProyectoService', ['getById']);
    proyectoServiceSpy.getById.and.returnValue(of(proyectoDto));
    sprintServiceSpy = jasmine.createSpyObj('SprintService', ['getActivo']);
    sprintServiceSpy.getActivo.and.returnValue(of(null as any));
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      imports: [InvitacionComponent],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: ProjectMemberService, useValue: memberServiceSpy },
        { provide: ProyectoService, useValue: proyectoServiceSpy },
        { provide: SprintService, useValue: sprintServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: ActivatedRoute, useValue: { queryParams: of(queryParams) } }
      ]
    });

    fixture = TestBed.createComponent(InvitacionComponent);
    component = fixture.componentInstance;
  }

  it('sin código en la URL: estado inválida, no consulta al backend', () => {
    setup({}, false);
    fixture.detectChanges();

    expect(component.estado).toBe('invalida');
    expect(memberServiceSpy.consultarInvitacion).not.toHaveBeenCalled();
  });

  it('código inexistente: estado inválida', () => {
    setup({ codigo: 'PRJ-NOEXISTE' }, false);
    memberServiceSpy.consultarInvitacion.and.returnValue(of({ proyectoId: null, proyectoNombre: null, estado: 'no_existe' }));

    fixture.detectChanges();

    expect(component.estado).toBe('invalida');
  });

  it('código expirado: estado expirada', () => {
    setup({ codigo: 'PRJ-VENCIDO' }, false);
    memberServiceSpy.consultarInvitacion.and.returnValue(of({ proyectoId: 'uuid-proyecto-1', proyectoNombre: 'Proyecto Demo', estado: 'expirada' }));

    fixture.detectChanges();

    expect(component.estado).toBe('expirada');
  });

  it('código ya utilizado: estado usada', () => {
    setup({ codigo: 'PRJ-USADO01' }, false);
    memberServiceSpy.consultarInvitacion.and.returnValue(of({ proyectoId: 'uuid-proyecto-1', proyectoNombre: 'Proyecto Demo', estado: 'usada' }));

    fixture.detectChanges();

    expect(component.estado).toBe('usada');
  });

  it('código válido sin sesión iniciada: guarda el código pendiente y muestra "necesita-login" (no obliga a copiarlo a mano)', () => {
    setup({ codigo: 'PRJ-ABC123' }, false);
    memberServiceSpy.consultarInvitacion.and.returnValue(of({ proyectoId: 'uuid-proyecto-1', proyectoNombre: 'Proyecto Demo', estado: 'valida' }));

    fixture.detectChanges();

    expect(authServiceSpy.setInvitacionPendiente).toHaveBeenCalledWith('PRJ-ABC123');
    expect(component.estado).toBe('necesita-login');
    expect(memberServiceSpy.unirse).not.toHaveBeenCalled();
  });

  it('código válido con sesión iniciada: acepta automáticamente, entra al proyecto y navega a /planeacion', () => {
    setup({ codigo: 'PRJ-ABC123' }, true);
    memberServiceSpy.consultarInvitacion.and.returnValue(of({ proyectoId: 'uuid-proyecto-1', proyectoNombre: 'Proyecto Demo', estado: 'valida' }));
    memberServiceSpy.unirse.and.returnValue(of({
      proyectoId: 'uuid-proyecto-1', userId: 'u', userEmail: 'e@mpdia.com', rol: 'scrum_member', joinedAt: '2026-01-01T00:00:00Z'
    }));

    fixture.detectChanges();

    expect(memberServiceSpy.unirse).toHaveBeenCalledWith('PRJ-ABC123');
    expect(authServiceSpy.clearInvitacionPendiente).toHaveBeenCalled();
    expect(component.estado).toBe('aceptada');
    expect(localStorage.getItem('mpdia_proyecto_activo')).toContain('Proyecto Demo');
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/planeacion']);

    localStorage.removeItem('mpdia_proyecto_activo');
  });

  it('usuario ya pertenece al proyecto: estado "ya-miembro", no rompe la funcionalidad existente', () => {
    setup({ codigo: 'PRJ-ABC123' }, true);
    memberServiceSpy.consultarInvitacion.and.returnValue(of({ proyectoId: 'uuid-proyecto-1', proyectoNombre: 'Proyecto Demo', estado: 'valida' }));
    memberServiceSpy.unirse.and.returnValue(throwError(() => ({ error: { error: 'Ya eres miembro de este proyecto.' } })));

    fixture.detectChanges();

    expect(component.estado).toBe('ya-miembro');
  });

  // Corrección: el backend ahora valida que el correo autenticado coincida
  // con el correo invitado (ProjectMemberService.unirse) — el frontend debe
  // mostrar un mensaje específico, no un error genérico, sin haber aceptado nada.
  it('usuario autenticado con un correo distinto al invitado: estado "otro-correo", no navega al proyecto', () => {
    setup({ codigo: 'PRJ-ABC123' }, true);
    memberServiceSpy.consultarInvitacion.and.returnValue(of({ proyectoId: 'uuid-proyecto-1', proyectoNombre: 'Proyecto Demo', estado: 'valida' }));
    memberServiceSpy.unirse.and.returnValue(throwError(() => ({ error: { error: 'Esta invitación fue enviada a otro correo.' } })));

    fixture.detectChanges();

    expect(component.estado).toBe('otro-correo');
    expect(routerSpy.navigate).not.toHaveBeenCalledWith(['/planeacion']);
    expect(authServiceSpy.clearInvitacionPendiente).not.toHaveBeenCalled();
  });

  it('cambiarDeCuenta: conserva el código pendiente y cierra sesión para permitir entrar con la cuenta correcta', () => {
    setup({ codigo: 'PRJ-ABC123' }, true);
    component.codigo = 'PRJ-ABC123';

    component.cambiarDeCuenta();

    expect(authServiceSpy.setInvitacionPendiente).toHaveBeenCalledWith('PRJ-ABC123');
    expect(authServiceSpy.logout).toHaveBeenCalled();
  });

  it('irALogin navega a /auth', () => {
    setup({}, false);
    fixture.detectChanges();
    component.irALogin();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/auth']);
  });

  it('irARegistro navega a /auth con ?tab=register', () => {
    setup({}, false);
    fixture.detectChanges();
    component.irARegistro();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/auth'], { queryParams: { tab: 'register' } });
  });
});
