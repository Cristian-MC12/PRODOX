// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Component, Input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { of, throwError } from 'rxjs';
import { BacklogComponent } from './backlog.component';
import { ShellComponent } from '../../layout/shell/shell.component';
import { HistoriaUsuarioService } from '../../services/historia-usuario.service';
import { SprintService } from '../../services/sprint.service';
import { HistoriaUsuarioDto } from '../../models/historia-usuario.model';
import { SprintDto } from '../../models/sprint.model';

@Component({ selector: 'app-shell', standalone: true, template: '<ng-content></ng-content>' })
class MockShellComponent {
  @Input() title?: string;
  @Input() showBanner?: boolean;
}

function mockProyecto(miRol: string) {
  return {
    id: 'proj-1', nombre: 'Sandbox', descripcion: null, metodo: 'scrum' as const,
    timeBoxSemanas: 1, numeroSprints: 3, fechaInicio: '2026-08-01', productGoal: 'x',
    sprintGoal: '', estado: 'activo' as const, scrumMasterEmail: 'sm@test.com', totalMiembros: 2,
    createdAt: '2026-08-01T00:00:00Z', miRol
  };
}

function historia(id: string, prioridad: HistoriaUsuarioDto['prioridad'], estado: HistoriaUsuarioDto['estado']): HistoriaUsuarioDto {
  return {
    id, proyectoId: 'proj-1', sprintId: null, titulo: `Historia ${id}`, descripcion: null,
    criteriosAceptacion: null, prioridad, estado, creadoPor: 'po-1',
    createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-01T00:00:00Z'
  };
}

describe('BacklogComponent', () => {
  let component: BacklogComponent;
  let fixture: ComponentFixture<BacklogComponent>;
  let historiaService: jasmine.SpyObj<HistoriaUsuarioService>;
  let sprintService: jasmine.SpyObj<SprintService>;

  async function crearComponente(rol: string): Promise<void> {
    const historiaServiceSpy = jasmine.createSpyObj('HistoriaUsuarioService', [
      'listar', 'crear', 'actualizar', 'cambiarPrioridad', 'cambiarEstado', 'asignarSprint', 'eliminar'
    ]);
    const sprintServiceSpy = jasmine.createSpyObj('SprintService', ['listar']);
    sprintServiceSpy.listar.and.returnValue(of([] as SprintDto[]));

    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, FormsModule, CommonModule],
      providers: [
        { provide: HistoriaUsuarioService, useValue: historiaServiceSpy },
        { provide: SprintService, useValue: sprintServiceSpy },
      ]
    })
      .overrideComponent(BacklogComponent, {
        remove: { imports: [ShellComponent] },
        add: { imports: [MockShellComponent, BacklogComponent] }
      })
      .compileComponents();

    historiaService = TestBed.inject(HistoriaUsuarioService) as jasmine.SpyObj<HistoriaUsuarioService>;
    sprintService = TestBed.inject(SprintService) as jasmine.SpyObj<SprintService>;

    localStorage.setItem('mpdia_proyecto_activo', JSON.stringify(mockProyecto(rol)));

    fixture = TestBed.createComponent(BacklogComponent);
    component = fixture.componentInstance;
  }

  afterEach(() => localStorage.removeItem('mpdia_proyecto_activo'));

  describe('Product Owner', () => {
    beforeEach(async () => {
      await crearComponente('product_owner');
      historiaService.listar.and.returnValue(of([
        historia('h1', 'alta', 'pendiente'),
        historia('h2', 'media', 'en_progreso'),
        historia('h3', 'baja', 'completada'),
      ]));
      fixture.detectChanges();
    });

    it('se crea correctamente y carga el backlog', () => {
      expect(component).toBeTruthy();
      expect(component.historias.length).toBe(3);
    });

    it('esProductOwner es true y se muestra el botón de nueva historia', () => {
      expect(component.esProductOwner).toBeTrue();
      const compiled = fixture.nativeElement;
      const boton = Array.from(compiled.querySelectorAll('button')) as HTMLButtonElement[];
      expect(boton.some(b => b.textContent?.includes('Nueva historia'))).toBeTrue();
    });

    it('crear: llama al servicio con los datos del formulario y agrega la historia al listado', () => {
      const nueva = historia('h4', 'media', 'pendiente');
      historiaService.crear.and.returnValue(of(nueva));

      component.formCrear = { titulo: 'Nueva', descripcion: '', criteriosAceptacion: '', prioridad: 'media' };
      component.crear();

      expect(historiaService.crear).toHaveBeenCalledWith('proj-1', jasmine.objectContaining({ titulo: 'Nueva' }));
      expect(component.historias.find(h => h.id === 'h4')).toBeTruthy();
    });

    it('editar: abre el formulario de edición con los datos de la historia', () => {
      component.editar(component.historias[0]);
      expect(component.historiaEditando?.id).toBe('h1');
      expect(component.formEditar.titulo).toBe('Historia h1');
    });

    it('guardarEdicion: llama al servicio y actualiza la historia en el listado', () => {
      const editada = { ...historia('h1', 'alta', 'pendiente'), titulo: 'Editado' };
      historiaService.actualizar.and.returnValue(of(editada));

      component.editar(component.historias[0]);
      component.formEditar.titulo = 'Editado';
      component.guardarEdicion();

      expect(historiaService.actualizar).toHaveBeenCalledWith('h1', jasmine.objectContaining({ titulo: 'Editado' }));
      expect(component.historias.find(h => h.id === 'h1')?.titulo).toBe('Editado');
    });

    it('cambiarPrioridad: llama al servicio y refleja el cambio en el listado', () => {
      const actualizada = historia('h1', 'baja', 'pendiente');
      historiaService.cambiarPrioridad.and.returnValue(of(actualizada));

      component.cambiarPrioridad(component.historias[0], 'baja');

      expect(historiaService.cambiarPrioridad).toHaveBeenCalledWith('h1', 'baja');
      expect(component.historias.find(h => h.id === 'h1')?.prioridad).toBe('baja');
    });

    it('asignarSprint: llama al servicio con el sprintId elegido', () => {
      const actualizada = { ...historia('h1', 'alta', 'pendiente'), sprintId: 'sprint-1' };
      historiaService.asignarSprint.and.returnValue(of(actualizada));

      component.asignarSprint(component.historias[0], 'sprint-1');

      expect(historiaService.asignarSprint).toHaveBeenCalledWith('h1', 'sprint-1');
      expect(component.historias.find(h => h.id === 'h1')?.sprintId).toBe('sprint-1');
    });

    it('asignarSprint: con valor vacío desasigna (sprintId null)', () => {
      const actualizada = { ...historia('h1', 'alta', 'pendiente'), sprintId: null };
      historiaService.asignarSprint.and.returnValue(of(actualizada));
      component.historias[0].sprintId = 'sprint-1';

      component.asignarSprint(component.historias[0], '');

      expect(historiaService.asignarSprint).toHaveBeenCalledWith('h1', null);
    });

    it('cambiarPrioridad: si el backend rechaza el cambio, muestra alerta y no rompe el listado', () => {
      historiaService.cambiarPrioridad.and.returnValue(throwError(() => ({ error: { error: 'No autorizado' } })));

      component.cambiarPrioridad(component.historias[0], 'baja');

      expect(component.alertMsg).toContain('No autorizado');
      expect(component.historias.find(h => h.id === 'h1')?.prioridad).toBe('alta');
    });

    it('eliminar: pide confirmación y luego llama al servicio', () => {
      historiaService.eliminar.and.returnValue(of(undefined));
      component.pedirEliminar(component.historias[0]);
      expect(component.historiaAEliminar?.id).toBe('h1');

      component.confirmarEliminar();

      expect(historiaService.eliminar).toHaveBeenCalledWith('h1');
      expect(component.historias.find(h => h.id === 'h1')).toBeUndefined();
    });

    it('filtro por estado: cuenta y filtra correctamente', () => {
      component.filtroEstado = 'completada';
      expect(component.historiasFiltradas().length).toBe(1);
      expect(component.contarPorEstado('pendiente')).toBe(1);
      expect(component.contarPorEstado('todas')).toBe(3);
    });
  });

  describe('Scrum Member (solo lectura)', () => {
    beforeEach(async () => {
      await crearComponente('scrum_member');
      historiaService.listar.and.returnValue(of([historia('h1', 'alta', 'pendiente')]));
      fixture.detectChanges();
    });

    it('esProductOwner es false: no ve el botón de nueva historia ni los controles de edición', () => {
      expect(component.esProductOwner).toBeFalse();
      const compiled = fixture.nativeElement;
      const boton = Array.from(compiled.querySelectorAll('button')) as HTMLButtonElement[];
      expect(boton.some(b => b.textContent?.includes('Nueva historia'))).toBeFalse();
      expect(compiled.querySelector('select')).toBeFalsy();
    });

    it('igual puede consultar el backlog (lectura permitida a todo miembro)', () => {
      expect(component.historias.length).toBe(1);
    });
  });

  describe('Scrum Master (solo lectura del backlog, no gana permisos de PO)', () => {
    beforeEach(async () => {
      await crearComponente('scrum_master');
      historiaService.listar.and.returnValue(of([historia('h1', 'alta', 'pendiente')]));
      fixture.detectChanges();
    });

    it('esProductOwner es false para el Scrum Master (no obtiene automáticamente permisos de PO)', () => {
      expect(component.esProductOwner).toBeFalse();
    });
  });

  describe('Sin proyecto activo', () => {
    beforeEach(async () => {
      await crearComponente('product_owner');
      localStorage.removeItem('mpdia_proyecto_activo');
      fixture = TestBed.createComponent(BacklogComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('no intenta cargar historias y muestra el estado vacío', () => {
      expect(component.proyecto).toBeNull();
      expect(historiaService.listar).not.toHaveBeenCalled();
    });
  });
});
