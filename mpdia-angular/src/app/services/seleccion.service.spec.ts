// Autor: Cristian Santiago Martinez Cordoba — MPDIA
// FASE 11, bloque 3: las selecciones deben estar aisladas por proyecto activo.
import { TestBed } from '@angular/core/testing';
import { SeleccionService } from './seleccion.service';

describe('SeleccionService — aislamiento por proyecto (FASE 11)', () => {
  let service: SeleccionService;

  const activarProyecto = (id: string) =>
    localStorage.setItem('mpdia_proyecto_activo', JSON.stringify({ id, nombre: 'Proyecto ' + id }));

  beforeEach(() => {
    localStorage.removeItem('mpdia_selecciones');
    localStorage.removeItem('mpdia_proyecto_activo');
    TestBed.configureTestingModule({});
    service = TestBed.inject(SeleccionService);
  });

  afterEach(() => {
    localStorage.removeItem('mpdia_selecciones');
    localStorage.removeItem('mpdia_proyecto_activo');
  });

  it('una selección del Proyecto A no aparece al entrar al Proyecto B', () => {
    activarProyecto('A');
    service.agregar({
      factorId: 'f1', factorNombre: 'Defectos', factorCategoria: 'Significado',
      metricaNombre: 'Defectos', metricaDescripcion: '', proyectoId: 'A'
    });
    expect(service.getSnapshot().length).toBe(1);

    activarProyecto('B');
    expect(service.getSnapshot().length).toBe(0);
  });

  it('al volver al Proyecto A, su selección sigue disponible', () => {
    activarProyecto('A');
    service.agregar({
      factorId: 'f1', factorNombre: 'Defectos', factorCategoria: 'Significado',
      metricaNombre: 'Defectos', metricaDescripcion: '', proyectoId: 'A'
    });

    activarProyecto('B');
    expect(service.getSnapshot().length).toBe(0);

    activarProyecto('A');
    expect(service.getSnapshot().length).toBe(1);
    expect(service.getSnapshot()[0].metricaNombre).toBe('Defectos');
  });

  it('limpiar() en el Proyecto B no borra la selección legítima del Proyecto A', () => {
    activarProyecto('A');
    service.agregar({
      factorId: 'f1', factorNombre: 'Defectos', factorCategoria: 'Significado',
      metricaNombre: 'Defectos', metricaDescripcion: '', proyectoId: 'A'
    });

    activarProyecto('B');
    service.agregar({
      factorId: 'f2', factorNombre: 'Impedimentos', factorCategoria: 'Impacto',
      metricaNombre: 'Impedimentos', metricaDescripcion: '', proyectoId: 'B'
    });
    service.limpiar();
    expect(service.getSnapshot().length).toBe(0);

    activarProyecto('A');
    expect(service.getSnapshot().length).toBe(1);
    expect(service.getSnapshot()[0].metricaNombre).toBe('Defectos');
  });
});
