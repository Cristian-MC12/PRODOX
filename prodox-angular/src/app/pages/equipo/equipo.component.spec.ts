// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Component, Input, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { of, throwError } from 'rxjs';
import { EquipoComponent } from './equipo.component';
import { ShellComponent } from '../../layout/shell/shell.component';
import { AuthService } from '../../services/auth.service';
import { ProjectMemberService } from '../../services/project-member.service';
import { ProjectMemberDto } from '../../models/project-member.model';

@Component({ selector: 'app-shell', standalone: true, template: '<ng-content></ng-content>' })
class MockShellComponent {
  @Input() title?: string;
  @Input() showBanner?: boolean;
}

function mockProyecto(miRol: string) {
  return {
    id: 'proj-1', nombre: 'Sandbox', descripcion: null, metodo: 'scrum' as const,
    timeBoxSemanas: 1, numeroSprints: 3, fechaInicio: '2026-08-01', productGoal: 'x',
    sprintGoal: '', estado: 'activo' as const, scrumMasterEmail: 'sm@test.com', totalMiembros: 3,
    createdAt: '2026-08-01T00:00:00Z', miRol
  };
}

function miembro(userId: string, email: string, rol: string): ProjectMemberDto {
  return { proyectoId: 'proj-1', userId, userEmail: email, rol, joinedAt: '2026-08-01T00:00:00Z' };
}

describe('EquipoComponent', () => {
  let component: EquipoComponent;
  let fixture: ComponentFixture<EquipoComponent>;
  let memberService: jasmine.SpyObj<ProjectMemberService>;

  async function crearComponente(miRol: string): Promise<void> {
    const memberServiceSpy = jasmine.createSpyObj('ProjectMemberService', ['listar', 'invitar', 'unirse', 'cambiarRol']);
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['logout']);
    (authServiceSpy as any).currentUser = signal({ userId: 'u1', email: 'sm@test.com', role: 'scrum_master', token: 't' });

    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, FormsModule, CommonModule],
      providers: [
        { provide: ProjectMemberService, useValue: memberServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: AuthService, useValue: authServiceSpy },
      ]
    })
      .overrideComponent(EquipoComponent, {
        remove: { imports: [ShellComponent] },
        add: { imports: [MockShellComponent, EquipoComponent] }
      })
      .compileComponents();

    memberService = TestBed.inject(ProjectMemberService) as jasmine.SpyObj<ProjectMemberService>;
    localStorage.setItem('mpdia_proyecto_activo', JSON.stringify(mockProyecto(miRol)));

    fixture = TestBed.createComponent(EquipoComponent);
    component = fixture.componentInstance;
  }

  afterEach(() => localStorage.removeItem('mpdia_proyecto_activo'));

  describe('Scrum Master', () => {
    beforeEach(async () => {
      await crearComponente('scrum_master');
      memberService.listar.and.returnValue(of([
        miembro('u1', 'sm@test.com', 'scrum_master'),
        miembro('u2', 'po@test.com', 'product_owner'),
        miembro('u3', 'member@test.com', 'scrum_member'),
      ]));
      fixture.detectChanges();
    });

    it('muestra correctamente los tres roles — nunca Product Owner como Scrum Member', () => {
      const compiled = fixture.nativeElement;
      const texto = compiled.textContent as string;
      expect(texto).toContain('Product Owner');
      expect(component.miembros.find(m => m.userId === 'u2')?.rol).toBe('product_owner');
      expect(component.etiquetaRol('product_owner')).toBe('Product Owner');
      expect(component.etiquetaRol('product_owner')).not.toBe('Scrum Member');
    });

    it('V40: hayProductOwner() es true — el selector de invitar NO ofrece "Product Owner" y muestra el mensaje informativo', () => {
      expect(component.hayProductOwner()).toBeTrue();

      const compiled = fixture.nativeElement;
      const selectInvitar = compiled.querySelectorAll('.card-body select')[0] as HTMLSelectElement;
      const valores = Array.from(selectInvitar.options).map(o => (o as HTMLOptionElement).value);
      expect(valores).toEqual(['scrum_member']);
      expect(compiled.textContent).toContain('Este proyecto ya tiene un Product Owner.');
    });

    it('V40: puedeOfrecerProductOwner es true solo para la fila del Product Owner actual', () => {
      const po = component.miembros.find(m => m.userId === 'u2')!;
      const member = component.miembros.find(m => m.userId === 'u3')!;

      expect(component.puedeOfrecerProductOwner(po)).toBeTrue();
      expect(component.puedeOfrecerProductOwner(member)).toBeFalse();
    });

    it('invitar: envía el rol elegido en el selector', () => {
      memberService.invitar.and.returnValue(of({ codigo: 'PRJ-XYZ', emailEnviado: true }));
      component.emailInvitar = 'nuevo@test.com';
      component.rolInvitar = 'product_owner';

      component.invitar();

      expect(memberService.invitar).toHaveBeenCalledWith('proj-1', 'nuevo@test.com', 'product_owner');
    });

    it('cambiarRolMiembro: llama al servicio y actualiza el rol localmente al confirmar', () => {
      const target = component.miembros.find(m => m.userId === 'u3')!;
      memberService.cambiarRol.and.returnValue(of({ ...target, rol: 'product_owner' }));

      component.cambiarRolMiembro(target, 'product_owner');

      expect(memberService.cambiarRol).toHaveBeenCalledWith('proj-1', 'u3', 'product_owner');
      expect(target.rol).toBe('product_owner');
    });

    it('cambiarRolMiembro: si el backend rechaza, revierte el rol visualmente y muestra alerta', () => {
      const target = component.miembros.find(m => m.userId === 'u3')!;
      const rolOriginal = target.rol;
      memberService.cambiarRol.and.returnValue(throwError(() => ({ error: { error: 'No autorizado' } })));

      component.cambiarRolMiembro(target, 'product_owner');

      expect(target.rol).toBe(rolOriginal);
      expect(component.alertMsg).toContain('No autorizado');
    });

    it('no ofrece cambiar el rol del propio Scrum Master (fila sin selector)', () => {
      fixture.detectChanges();
      const compiled = fixture.nativeElement;
      const filas: HTMLTableRowElement[] = Array.from(compiled.querySelectorAll('tbody tr'));
      const filaSm = filas.find(f => f.textContent?.includes('sm@test.com'))!;
      const filaPo = filas.find(f => f.textContent?.includes('po@test.com'))!;

      expect(filaSm.querySelector('select')).toBeFalsy();
      expect(filaPo.querySelector('select')).toBeTruthy();
    });
  });

  describe('V40: Scrum Master — proyecto SIN Product Owner todavía', () => {
    beforeEach(async () => {
      await crearComponente('scrum_master');
      memberService.listar.and.returnValue(of([
        miembro('u1', 'sm@test.com', 'scrum_master'),
        miembro('u3', 'member@test.com', 'scrum_member'),
      ]));
      fixture.detectChanges();
    });

    it('hayProductOwner() es false y el selector de invitar SÍ ofrece "Product Owner"', () => {
      expect(component.hayProductOwner()).toBeFalse();

      const compiled = fixture.nativeElement;
      const selectInvitar = compiled.querySelectorAll('.card-body select')[0] as HTMLSelectElement;
      const valores = Array.from(selectInvitar.options).map(o => (o as HTMLOptionElement).value);
      expect(valores).toEqual(['scrum_member', 'product_owner']);
      expect(compiled.textContent).not.toContain('Este proyecto ya tiene un Product Owner.');
    });

    it('puedeOfrecerProductOwner es true para cualquier fila mientras no haya PO', () => {
      const member = component.miembros.find(m => m.userId === 'u3')!;
      expect(component.puedeOfrecerProductOwner(member)).toBeTrue();
    });

    it('al delegar un Product Owner (cambiarRolMiembro), la opción deja de ofrecerse para el resto y aparece el mensaje', () => {
      const target = component.miembros.find(m => m.userId === 'u3')!;
      memberService.cambiarRol.and.returnValue(of({ ...target, rol: 'product_owner' }));

      component.cambiarRolMiembro(target, 'product_owner');
      fixture.detectChanges();

      expect(component.hayProductOwner()).toBeTrue();
      const compiled = fixture.nativeElement;
      const selectInvitar = compiled.querySelectorAll('.card-body select')[0] as HTMLSelectElement;
      const valores = Array.from(selectInvitar.options).map(o => (o as HTMLOptionElement).value);
      expect(valores).toEqual(['scrum_member']);
    });

    it('cargarMiembros: si rolInvitar quedó en "product_owner" y ya hay PO tras recargar, se resetea a "scrum_member"', () => {
      component.rolInvitar = 'product_owner';
      memberService.listar.and.returnValue(of([
        miembro('u1', 'sm@test.com', 'scrum_master'),
        miembro('u2', 'po@test.com', 'product_owner'),
      ]));

      component.cargarMiembros();

      expect(component.rolInvitar).toBe('scrum_member');
    });
  });

  describe('Compatibilidad: proyecto activo sin miRol (caché previa a V39 o backend no reiniciado)', () => {
    beforeEach(async () => {
      await crearComponente('scrum_master');
      // Simula un ProyectoDto sin el campo miRol (respuesta de un backend
      // todavía no reiniciado con V39, o un objeto cacheado en localStorage
      // antes de que miRol existiera).
      const sinMiRol = { ...JSON.parse(localStorage.getItem('mpdia_proyecto_activo')!) };
      delete sinMiRol.miRol;
      localStorage.setItem('mpdia_proyecto_activo', JSON.stringify(sinMiRol));

      memberService.listar.and.returnValue(of([miembro('u1', 'sm@test.com', 'scrum_master')]));
    });

    it('el Scrum Master real (mismo email que scrumMasterEmail) sigue viendo el panel de invitar', () => {
      fixture = TestBed.createComponent(EquipoComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(component.esScrumMaster).toBeTrue();
      const compiled = fixture.nativeElement;
      expect(compiled.textContent).toContain('Invitar miembro a este proyecto');
      expect(compiled.querySelector('select')).toBeTruthy();
    });
  });

  describe('Product Owner (no ve controles exclusivos del Scrum Master)', () => {
    beforeEach(async () => {
      await crearComponente('product_owner');
      memberService.listar.and.returnValue(of([
        miembro('u1', 'sm@test.com', 'scrum_master'),
        miembro('u2', 'po@test.com', 'product_owner'),
      ]));
      fixture.detectChanges();
    });

    it('esScrumMaster es false: no ve el panel de invitar ni el selector de cambio de rol', () => {
      expect(component.esScrumMaster).toBeFalse();
      const compiled = fixture.nativeElement;
      expect(compiled.textContent).not.toContain('Invitar miembro a este proyecto');
      expect(compiled.querySelector('select')).toBeFalsy();
    });
  });

  describe('Scrum Member (no ve controles exclusivos del Scrum Master)', () => {
    beforeEach(async () => {
      await crearComponente('scrum_member');
      memberService.listar.and.returnValue(of([
        miembro('u1', 'sm@test.com', 'scrum_master'),
        miembro('u3', 'member@test.com', 'scrum_member'),
      ]));
      fixture.detectChanges();
    });

    it('esScrumMaster es false: no ve el panel de invitar ni el selector de cambio de rol', () => {
      expect(component.esScrumMaster).toBeFalse();
      const compiled = fixture.nativeElement;
      expect(compiled.textContent).not.toContain('Invitar miembro a este proyecto');
      expect(compiled.querySelector('select')).toBeFalsy();
    });
  });
});
