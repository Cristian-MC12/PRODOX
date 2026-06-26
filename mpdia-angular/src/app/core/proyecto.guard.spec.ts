// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { proyectoGuard } from './proyecto.guard';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';

describe('proyectoGuard', () => {
  let routerSpy: jasmine.SpyObj<Router>;

  const proyectoActivo = JSON.stringify({
    id: 'uuid-proyecto-1',
    nombre: 'MPDIA',
    estado: 'activo',
    metodo: 'scrum',
    timeBoxSemanas: 2,
    productGoal: 'Goal',
    sprintGoal: 'Sprint',
    scrumMasterEmail: 'sm@mpdia.com',
    totalMiembros: 1,
    createdAt: new Date().toISOString()
  });

  beforeEach(() => {
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [{ provide: Router, useValue: routerSpy }]
    });
  });

  afterEach(() => localStorage.clear());

  function runGuard(): boolean {
    return TestBed.runInInjectionContext(() =>
      proyectoGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)
    ) as boolean;
  }

  it('permite acceso si hay proyecto activo en localStorage', () => {
    localStorage.setItem('mpdia_proyecto_activo', proyectoActivo);
    expect(runGuard()).toBeTrue();
  });

  it('bloquea acceso si no hay proyecto activo', () => {
    expect(runGuard()).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/proyectos']);
  });

  it('bloquea acceso si el proyecto está finalizado', () => {
    const finalizado = JSON.parse(proyectoActivo);
    finalizado.estado = 'finalizado';
    localStorage.setItem('mpdia_proyecto_activo', JSON.stringify(finalizado));

    expect(runGuard()).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/proyectos']);
    expect(localStorage.getItem('mpdia_proyecto_activo')).toBeNull();
  });

  it('bloquea acceso si el JSON es inválido', () => {
    localStorage.setItem('mpdia_proyecto_activo', 'json_invalido{{');
    expect(runGuard()).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/proyectos']);
  });
});
