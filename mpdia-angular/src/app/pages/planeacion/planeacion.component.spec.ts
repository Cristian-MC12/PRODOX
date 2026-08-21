// Autor: Cristian Santiago Martinez Cordoba — MPDIA
// FASE 4: tests del filtro de catálogo de métricas visibles
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { of } from 'rxjs';
import { PlaneacionComponent } from './planeacion.component';
import { ShellComponent } from '../../layout/shell/shell.component';
import { PlaneacionService } from '../../services/planeacion.service';
import { SprintService } from '../../services/sprint.service';
import { ProyectoMetricaDto } from '../../models/planeacion.model';

// Mock de ShellComponent para aislar tests
@Component({
  selector: 'app-shell',
  standalone: true,
  template: '<ng-content></ng-content>'
})
class MockShellComponent {}

function metrica(id: string, nombre: string, categoria: string, opts: Partial<ProyectoMetricaDto> = {}): ProyectoMetricaDto {
  return {
    metricaId: id,
    codigo: nombre.substring(0, 6).toUpperCase(),
    nombre,
    descripcion: null,
    categoria,
    factor: null,
    seleccionada: false,
    seleccionadaAt: null,
    aprobada: false,
    aprobadaPor: null,
    aprobadaAt: null,
    tieneVariable: false,
    ...opts
  };
}

// Catálogo simulado: las 5 métricas autorizadas (IDs reales verificados en BD)
// + varias ocultas, una de ellas ya seleccionada/aprobada en un proyecto real
// (reproduce el caso real de "Calidad (TWQ)"/"Motivación intrínseca" en el
// proyecto "Trabajo 1", que ya las tenía activas antes de esta fase).
const CATALOGO_MOCK: ProyectoMetricaDto[] = [
  metrica('dde97e2b-1b25-493e-9273-a6b59564b053', 'Impedimentos por sprint', 'Significado'),
  metrica('2ba0cf34-0bec-4e7d-8dc5-40795f050ec9', 'Problemas reportados por el cliente', 'Significado'),
  metrica('40beffdf-13f4-4772-8820-4df93fae525c', 'Deuda técnica gestionada', 'Flexibilidad'),
  metrica('beb22a94-0e1b-496a-8b9e-a08a8f6d77c3', 'Aprendizaje organizacional (FAT)', 'Flexibilidad'),
  metrica('ec0d74fe-0bf4-4970-af89-dcaa0736c8ed', 'Defectos', 'Significado'),
  // Ocultas
  metrica('20bcc976-ede7-40bd-aa51-e56511f9e32d', 'Calidad (TWQ)', 'Significado', {
    seleccionada: true, aprobada: true, seleccionadaAt: '2026-08-02T00:00:00Z'
  }),
  metrica('2345492b-16a3-464e-983d-0176c0f911c2', 'Motivación intrínseca', 'Socio-Humano FSH', {
    seleccionada: true, aprobada: false, seleccionadaAt: '2026-08-02T00:00:00Z'
  }),
  metrica('2ffdb8da-71d0-4bda-9925-982a112ea65a', 'Cambios de alcance por sprint', 'Impacto'),
];

describe('PlaneacionComponent', () => {
  let component: PlaneacionComponent;
  let fixture: ComponentFixture<PlaneacionComponent>;
  let planeacionService: jasmine.SpyObj<PlaneacionService>;

  beforeEach(async () => {
    const planeacionServiceSpy = jasmine.createSpyObj('PlaneacionService', [
      'listarMetricas', 'listarSeleccionadas', 'seleccionar', 'deseleccionar',
      'aprobar', 'desaprobar', 'listarVariables', 'sincronizarVariables'
    ]);
    const sprintServiceSpy = jasmine.createSpyObj('SprintService', ['listar']);
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, FormsModule, CommonModule],
      providers: [
        { provide: PlaneacionService, useValue: planeacionServiceSpy },
        { provide: SprintService, useValue: sprintServiceSpy },
        { provide: Router, useValue: routerSpy },
      ]
    })
    .overrideComponent(PlaneacionComponent, {
      remove: { imports: [ShellComponent] },
      add: { imports: [MockShellComponent, PlaneacionComponent] }
    })
    .compileComponents();

    planeacionService = TestBed.inject(PlaneacionService) as jasmine.SpyObj<PlaneacionService>;

    fixture = TestBed.createComponent(PlaneacionComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Catálogo de métricas visibles (FASE 4)', () => {
    beforeEach(() => {
      component.metricas = CATALOGO_MOCK;
    });

    it('1. con el catálogo original, el catálogo visible devuelve exactamente las 5 autorizadas', () => {
      const todas = component.categorias.flatMap(cat => component.metricasFiltradas(cat));
      const ids = todas.map(m => m.metricaId).sort();
      expect(ids).toEqual([...component.METRICAS_VISIBLES].sort());
      expect(todas.length).toBe(5);
    });

    it('2. las métricas ocultas no aparecen en el catálogo de nuevas selecciones', () => {
      const todas = component.categorias.flatMap(cat => component.metricasFiltradas(cat));
      const idsOcultos = [
        '20bcc976-ede7-40bd-aa51-e56511f9e32d', // Calidad (TWQ)
        '2345492b-16a3-464e-983d-0176c0f911c2', // Motivación intrínseca
        '2ffdb8da-71d0-4bda-9925-982a112ea65a', // Cambios de alcance por sprint
      ];
      for (const id of idsOcultos) {
        expect(todas.some(m => m.metricaId === id)).toBe(false);
      }
    });

    it('3. una métrica oculta ya seleccionada/aprobada en un proyecto existente sigue apareciendo en "Seleccionadas"/"Historial"', () => {
      // 'Calidad (TWQ)': oculta del catálogo, pero seleccionada+aprobada -> debe seguir en el historial.
      expect(
        component.historialSeleccionadas.some(m => m.metricaId === '20bcc976-ede7-40bd-aa51-e56511f9e32d')
      ).toBe(true);

      // 'Motivación intrínseca': oculta, seleccionada pero NO aprobada -> debe seguir en seleccionadasList.
      expect(
        component.seleccionadasList.some(m => m.metricaId === '2345492b-16a3-464e-983d-0176c0f911c2')
      ).toBe(true);
    });

    it('4. el filtro por categoría sigue funcionando sobre las 5 visibles', () => {
      component.categoriaFiltro = 'Flexibilidad';
      const resultado = component.metricasFiltradas('Flexibilidad');
      expect(resultado.map(m => m.nombre).sort()).toEqual(
        ['Aprendizaje organizacional (FAT)', 'Deuda técnica gestionada'].sort()
      );
      // otra categoría debe devolver vacío mientras el filtro de categoría esté activo
      expect(component.metricasFiltradas('Significado')).toEqual([]);
    });

    it('5. la búsqueda sigue funcionando sobre las 5 visibles', () => {
      component.busqueda = 'defectos';
      const resultado = component.categorias.flatMap(cat => component.metricasFiltradas(cat));
      expect(resultado.length).toBe(1);
      expect(resultado[0].nombre).toBe('Defectos');
    });

    it('5b. la búsqueda NO debe hacer visible una métrica oculta aunque coincida el texto', () => {
      component.busqueda = 'calidad'; // coincide con "Calidad (TWQ)", que está oculta
      const resultado = component.categorias.flatMap(cat => component.metricasFiltradas(cat));
      expect(resultado.length).toBe(0);
    });

    it('6. las 5 métricas permitidas pueden seguir seleccionándose normalmente', () => {
      component.proyecto = { id: 'proj-1' } as any;
      planeacionService.seleccionar.and.returnValue(of(undefined as any));
      planeacionService.listarMetricas.and.returnValue(of(CATALOGO_MOCK));

      const defectos = CATALOGO_MOCK.find(m => m.metricaId === 'ec0d74fe-0bf4-4970-af89-dcaa0736c8ed')!;
      component.seleccionar(defectos);

      expect(planeacionService.seleccionar).toHaveBeenCalledWith(
        'proj-1', 'ec0d74fe-0bf4-4970-af89-dcaa0736c8ed'
      );
    });
  });
});
