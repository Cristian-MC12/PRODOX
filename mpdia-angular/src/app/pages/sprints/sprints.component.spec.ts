// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, Input, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { of, throwError } from 'rxjs';
import { SprintsComponent } from './sprints.component';
import { ShellComponent } from '../../layout/shell/shell.component';
import { AuthService } from '../../services/auth.service';
import { SprintService } from '../../services/sprint.service';
import { SprintDto } from '../../models/sprint.model';

@Component({ selector: 'app-shell', standalone: true, template: '<ng-content></ng-content>' })
class MockShellComponent {
  @Input() title?: string;
  @Input() showBanner?: boolean;
}

const mockProyecto = {
  id: 'proj-1', nombre: 'Sandbox', descripcion: null, metodo: 'scrum' as const,
  timeBoxSemanas: 1, numeroSprints: 3, fechaInicio: '2026-08-01', productGoal: 'x',
  sprintGoal: '', estado: 'activo' as const, scrumMasterEmail: 'sm@test.com', totalMiembros: 2,
  createdAt: '2026-08-01T00:00:00Z'
};

function sprint(numero: number, estado: SprintDto['estado']): SprintDto {
  return {
    id: `sprint-${numero}`, proyectoId: 'proj-1', proyectoNombre: 'Sandbox', metodo: 'scrum',
    timeBoxSemanas: 1, numero, sprintGoal: `Sprint ${numero}`, estado,
    fechaInicio: '2026-08-01', fechaFin: '2026-08-07', cerradoPor: null, cerradoAt: null,
    createdAt: '2026-08-01T00:00:00Z'
  };
}

describe('SprintsComponent', () => {
  let component: SprintsComponent;
  let fixture: ComponentFixture<SprintsComponent>;
  let sprintService: jasmine.SpyObj<SprintService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  async function crearComponente(): Promise<void> {
    const sprintServiceSpy = jasmine.createSpyObj('SprintService', [
      'listar', 'cerrarEIniciarSiguiente', 'reabrir', 'finalizarReabierto'
    ]);
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    authServiceSpy = jasmine.createSpyObj('AuthService', ['logout']);
    (authServiceSpy as any).currentUser = signal({ userId: 'u1', email: 'sm@test.com', role: 'scrum_master', token: 't' });

    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, FormsModule, CommonModule],
      providers: [
        { provide: SprintService, useValue: sprintServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: AuthService, useValue: authServiceSpy },
      ]
    })
      .overrideComponent(SprintsComponent, {
        remove: { imports: [ShellComponent] },
        add: { imports: [MockShellComponent, SprintsComponent] }
      })
      .compileComponents();

    sprintService = TestBed.inject(SprintService) as jasmine.SpyObj<SprintService>;

    localStorage.setItem('mpdia_proyecto_activo', JSON.stringify(mockProyecto));

    fixture = TestBed.createComponent(SprintsComponent);
    component = fixture.componentInstance;
  }

  afterEach(() => localStorage.removeItem('mpdia_proyecto_activo'));

  describe('Scrum Master', () => {
    beforeEach(async () => {
      await crearComponente();
      sprintService.listar.and.returnValue(of([
        sprint(1, 'finalizado'), sprint(2, 'en_ejecucion'), sprint(3, 'pendiente')
      ]));
      fixture.detectChanges();
    });

    it('ve la acción "Finalizar Sprint N e iniciar Sprint N+1" para el sprint en ejecución', () => {
      const compiled = fixture.nativeElement;
      expect(compiled.textContent).toContain('Finalizar Sprint 2 e iniciar Sprint 3');
    });

    it('ve el botón "Reabrir" en la fila del sprint finalizado', () => {
      const compiled = fixture.nativeElement;
      const botones = Array.from(compiled.querySelectorAll('button')) as HTMLButtonElement[];
      expect(botones.some(b => b.textContent?.includes('Reabrir'))).toBeTrue();
    });

    it('pedirCerrarSiguiente: no llama al servicio de inmediato, abre confirmación', () => {
      component.nuevoSprintGoal = 'Meta sprint 3';
      component.pedirCerrarSiguiente();

      expect(component.accionPendiente).toEqual({ tipo: 'cerrar' });
      expect(sprintService.cerrarEIniciarSiguiente).not.toHaveBeenCalled();
    });

    it('cancelarAccion: no envía ninguna petición', () => {
      component.nuevoSprintGoal = 'Meta sprint 3';
      component.pedirCerrarSiguiente();
      component.cancelarAccion();

      expect(component.accionPendiente).toBeNull();
      expect(sprintService.cerrarEIniciarSiguiente).not.toHaveBeenCalled();
    });

    it('confirmarAccion (cerrar): llama a cerrarEIniciarSiguiente y recarga la lista', () => {
      const nuevoSprint = sprint(3, 'en_ejecucion');
      sprintService.cerrarEIniciarSiguiente.and.returnValue(of(nuevoSprint));
      sprintService.listar.and.returnValue(of([sprint(1, 'finalizado'), sprint(2, 'finalizado'), nuevoSprint]));

      component.nuevoSprintGoal = 'Meta sprint 3';
      component.pedirCerrarSiguiente();
      component.confirmarAccion();

      expect(sprintService.cerrarEIniciarSiguiente).toHaveBeenCalledWith('proj-1', 'Meta sprint 3');
      expect(component.accionPendiente).toBeNull();
      expect(JSON.parse(localStorage.getItem('mpdia_sprint_activo')!).numero).toBe(3);
    });

    it('pedirReabrir + confirmarAccion: llama a reabrir(sprintId)', () => {
      const reabierto = sprint(1, 'reabierto');
      sprintService.reabrir.and.returnValue(of(reabierto));
      sprintService.listar.and.returnValue(of([reabierto, sprint(2, 'en_ejecucion'), sprint(3, 'pendiente')]));

      component.pedirReabrir(sprint(1, 'finalizado'));
      expect(component.accionPendiente?.tipo).toBe('reabrir');

      component.confirmarAccion();

      expect(sprintService.reabrir).toHaveBeenCalledWith('sprint-1');
      expect(component.accionPendiente).toBeNull();
    });

    it('pedirFinalizar + confirmarAccion: llama a finalizarReabierto(sprintId)', () => {
      const finalizado = sprint(1, 'finalizado');
      sprintService.finalizarReabierto.and.returnValue(of(finalizado));
      sprintService.listar.and.returnValue(of([finalizado, sprint(2, 'en_ejecucion'), sprint(3, 'pendiente')]));

      component.pedirFinalizar(sprint(1, 'reabierto'));
      expect(component.accionPendiente?.tipo).toBe('finalizar');

      component.confirmarAccion();

      expect(sprintService.finalizarReabierto).toHaveBeenCalledWith('sprint-1');
      expect(component.accionPendiente).toBeNull();
    });

    it('confirmarAccion: un error del backend muestra alerta y no rompe el estado', () => {
      sprintService.reabrir.and.returnValue(throwError(() => ({ error: { error: 'Solo el Scrum Master del proyecto puede realizar esta acción' } })));

      component.pedirReabrir(sprint(1, 'finalizado'));
      component.confirmarAccion();

      expect(component.alertMsg).toContain('Solo el Scrum Master');
      expect(component.alertClass).toBe('alert-danger');
      expect(component.accionPendiente).toBeNull();
      expect(component.procesando).toBeFalse();
    });

    it('doble clic: mientras procesando=true, una segunda confirmación no duplica la petición', () => {
      sprintService.reabrir.and.returnValue(of(sprint(1, 'reabierto')));
      component.pedirReabrir(sprint(1, 'finalizado'));
      component.procesando = true;

      component.confirmarAccion();

      expect(sprintService.reabrir).not.toHaveBeenCalled();
    });
  });

  describe('Miembro normal', () => {
    beforeEach(async () => {
      await crearComponente();
      (authServiceSpy as any).currentUser.set({ userId: 'u2', email: 'member@test.com', role: 'scrum_member', token: 't' });
      sprintService.listar.and.returnValue(of([
        sprint(1, 'finalizado'), sprint(2, 'en_ejecucion'), sprint(3, 'pendiente')
      ]));
      fixture.detectChanges();
    });

    it('NO ve la acción de finalizar el sprint en ejecución', () => {
      const compiled = fixture.nativeElement;
      expect(compiled.textContent).not.toContain('Finalizar Sprint 2 e iniciar Sprint 3');
    });

    it('NO ve ningún botón de Reabrir/Finalizar en el historial', () => {
      const compiled = fixture.nativeElement;
      const botones = Array.from(compiled.querySelectorAll('button')) as HTMLButtonElement[];
      expect(botones.some(b => b.textContent?.includes('Reabrir'))).toBeFalse();
      expect(botones.some(b => b.textContent?.includes('Finalizar'))).toBeFalse();
    });

    it('sigue viendo el historial de sprints (consulta permitida)', () => {
      const compiled = fixture.nativeElement;
      expect(compiled.textContent).toContain('Sprint 1');
      expect(compiled.textContent).toContain('Sprint 2');
    });
  });
});
