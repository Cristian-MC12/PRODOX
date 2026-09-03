// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Component, Input, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { of, throwError } from 'rxjs';
import { ProyectosComponent } from './proyectos.component';
import { ShellComponent } from '../../layout/shell/shell.component';
import { AuthService } from '../../services/auth.service';
import { ProyectoService } from '../../services/proyecto.service';
import { SprintService } from '../../services/sprint.service';
import { ProjectMemberService } from '../../services/project-member.service';
import { ProyectoDto } from '../../models/proyecto.model';

@Component({ selector: 'app-shell', standalone: true, template: '<ng-content></ng-content>' })
class MockShellComponent {
  @Input() title?: string;
  @Input() showBanner?: boolean;
}

function mockProyecto(overrides: Partial<ProyectoDto> = {}): ProyectoDto {
  return {
    id: 'proj-1', nombre: 'Sandbox', descripcion: null, metodo: 'scrum',
    timeBoxSemanas: 2, numeroSprints: 3, fechaInicio: '2026-09-02', productGoal: 'x',
    sprintGoal: '', estado: 'activo', scrumMasterEmail: 'sm@test.com', totalMiembros: 1,
    createdAt: '2026-09-02T00:00:00Z', timeboxUnidad: 'SEMANAS', timeboxDuracion: 2,
    ...overrides
  };
}

describe('ProyectosComponent — Timebox (V41)', () => {
  let component: ProyectosComponent;
  let fixture: ComponentFixture<ProyectosComponent>;
  let proyectoService: jasmine.SpyObj<ProyectoService>;

  async function crearComponente(): Promise<void> {
    const proyectoServiceSpy = jasmine.createSpyObj('ProyectoService', ['getMisProyectos', 'crear', 'eliminar']);
    const sprintServiceSpy = jasmine.createSpyObj('SprintService', ['getActivo']);
    const memberServiceSpy = jasmine.createSpyObj('ProjectMemberService', ['unirse']);
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['logout']);
    (authServiceSpy as any).currentUser = signal({ userId: 'u1', email: 'sm@test.com', role: 'scrum_master', token: 't' });

    proyectoServiceSpy.getMisProyectos.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, FormsModule, ReactiveFormsModule, CommonModule],
      providers: [
        { provide: ProyectoService, useValue: proyectoServiceSpy },
        { provide: SprintService, useValue: sprintServiceSpy },
        { provide: ProjectMemberService, useValue: memberServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: AuthService, useValue: authServiceSpy },
      ]
    })
      .overrideComponent(ProyectosComponent, {
        remove: { imports: [ShellComponent] },
        add: { imports: [MockShellComponent, ProyectosComponent] }
      })
      .compileComponents();

    proyectoService = TestBed.inject(ProyectoService) as jasmine.SpyObj<ProyectoService>;

    fixture = TestBed.createComponent(ProyectosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    component.mostrarFormulario = true;
    fixture.detectChanges();
  }

  beforeEach(async () => crearComponente());

  it('el FormGroup arranca con timebox por defecto en SEMANAS', () => {
    expect(component.form.value.timeboxUnidad).toBe('SEMANAS');
    expect(component.form.value.timeboxDuracion).toBe(2);
  });

  it('selector de unidad: ofrece exactamente Horas, Días y Semanas', () => {
    const compiled = fixture.nativeElement;
    const select = compiled.querySelector('select[formcontrolname="timeboxUnidad"]') as HTMLSelectElement;
    const valores = Array.from(select.options).map(o => o.value);
    expect(valores).toEqual(['HORAS', 'DIAS', 'SEMANAS']);
  });

  it('visualización: al elegir Horas aparece el campo de hora de inicio', () => {
    component.form.patchValue({ timeboxUnidad: 'HORAS' });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const inputHora = compiled.querySelector('input[type="time"]');
    expect(inputHora).toBeTruthy();
  });

  it('visualización: con Días no aparece el campo de hora de inicio', () => {
    component.form.patchValue({ timeboxUnidad: 'DIAS' });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.querySelector('input[type="time"]')).toBeFalsy();
  });

  it('visualización: con Semanas no aparece el campo de hora de inicio (experiencia previa intacta)', () => {
    component.form.patchValue({ timeboxUnidad: 'SEMANAS' });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.querySelector('input[type="time"]')).toBeFalsy();
  });

  it('validación: duración vacía bloquea el envío y no llama al servicio', () => {
    component.form.patchValue({
      nombre: 'P', metodo: 'scrum', timeboxDuracion: '', numeroSprints: 3,
      fechaInicio: '2026-09-02', productGoal: 'Goal'
    });

    component.crearProyecto();

    expect(proyectoService.crear).not.toHaveBeenCalled();
    expect(component.form.get('timeboxDuracion')?.touched).toBeTrue();
  });

  it('validación: duración en 0 es inválida (min 1)', () => {
    component.form.patchValue({ timeboxDuracion: 0 });
    expect(component.form.get('timeboxDuracion')?.valid).toBeFalse();
  });

  it('validación: Horas sin hora de inicio bloquea el envío con un mensaje específico', () => {
    component.form.patchValue({
      nombre: 'P', metodo: 'scrum', timeboxUnidad: 'HORAS', timeboxDuracion: 8, horaInicio: '',
      numeroSprints: 3, fechaInicio: '2026-09-02', productGoal: 'Goal'
    });
    fixture.detectChanges();

    component.crearProyecto();
    fixture.detectChanges();

    expect(proyectoService.crear).not.toHaveBeenCalled();
    expect(component.horaInicioInvalida()).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('Indicá la hora de inicio');
  });

  it('crearProyecto: con Horas y hora de inicio válida, envía timeboxUnidad/timeboxDuracion/horaInicio correctos', () => {
    proyectoService.crear.and.returnValue(of(mockProyecto({ timeboxUnidad: 'HORAS', timeboxDuracion: 8 })));
    component.form.patchValue({
      nombre: 'Proyecto Horas', metodo: 'scrum', timeboxUnidad: 'HORAS', timeboxDuracion: 8,
      horaInicio: '08:00', numeroSprints: 3, fechaInicio: '2026-09-02', productGoal: 'Goal'
    });

    component.crearProyecto();

    expect(proyectoService.crear).toHaveBeenCalledWith(jasmine.objectContaining({
      timeboxUnidad: 'HORAS', timeboxDuracion: 8, horaInicio: '08:00'
    }));
  });

  it('crearProyecto: con Días, envía horaInicio en null (no aplica)', () => {
    proyectoService.crear.and.returnValue(of(mockProyecto({ timeboxUnidad: 'DIAS', timeboxDuracion: 3 })));
    component.form.patchValue({
      nombre: 'Proyecto Días', metodo: 'scrum', timeboxUnidad: 'DIAS', timeboxDuracion: 3,
      numeroSprints: 3, fechaInicio: '2026-09-02', productGoal: 'Goal'
    });

    component.crearProyecto();

    expect(proyectoService.crear).toHaveBeenCalledWith(jasmine.objectContaining({
      timeboxUnidad: 'DIAS', timeboxDuracion: 3, horaInicio: null
    }));
  });

  it('crearProyecto: con Semanas conserva el comportamiento previo (sin horaInicio)', () => {
    proyectoService.crear.and.returnValue(of(mockProyecto()));
    component.form.patchValue({
      nombre: 'Proyecto Semanas', metodo: 'scrum', timeboxUnidad: 'SEMANAS', timeboxDuracion: 2,
      numeroSprints: 3, fechaInicio: '2026-09-02', productGoal: 'Goal'
    });

    component.crearProyecto();

    expect(proyectoService.crear).toHaveBeenCalledWith(jasmine.objectContaining({
      timeboxUnidad: 'SEMANAS', timeboxDuracion: 2, horaInicio: null
    }));
  });

  it('crearProyecto: si el backend rechaza (ej. duración fuera de rango), muestra el error real y no limpia el formulario', () => {
    proyectoService.crear.and.returnValue(throwError(() => ({ error: { error: 'El timebox en días debe estar entre 1 y 30.' } })));
    component.form.patchValue({
      nombre: 'Proyecto Días', metodo: 'scrum', timeboxUnidad: 'DIAS', timeboxDuracion: 31,
      numeroSprints: 3, fechaInicio: '2026-09-02', productGoal: 'Goal'
    });

    component.crearProyecto();

    expect(component.alertMsg).toContain('entre 1 y 30');
    expect(component.creando).toBeFalse();
  });

  it('representación: la tarjeta de proyecto muestra el timebox real (Horas), no siempre semanas', () => {
    proyectoService.getMisProyectos.and.returnValue(of([mockProyecto({ timeboxUnidad: 'HORAS', timeboxDuracion: 8 })]));
    component.cargar();
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.textContent).toContain('8 h');
    expect(compiled.textContent).not.toContain('8 sem');
  });
});
