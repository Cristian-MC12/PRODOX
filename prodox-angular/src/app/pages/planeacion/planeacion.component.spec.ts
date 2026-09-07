// Autor: Cristian Santiago Martinez Cordoba — PRODOX
// FASE 4: tests del filtro de catálogo de métricas visibles
import { Component, Input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { of } from 'rxjs';
import { PlaneacionComponent } from './planeacion.component';
import { ShellComponent } from '../../layout/shell/shell.component';
import { PlaneacionService } from '../../services/planeacion.service';
import { SprintService } from '../../services/sprint.service';
import { ProyectoMetricaDto } from '../../models/planeacion.model';
import { ProyectoDto } from '../../models/proyecto.model';
import { SprintDto } from '../../models/sprint.model';
import { environment } from '../../../environments/environment';

// Mock de ShellComponent para aislar tests
@Component({
  selector: 'app-shell',
  standalone: true,
  template: '<ng-content></ng-content>'
})
class MockShellComponent {
  @Input() title?: string;
  @Input() showBanner?: boolean;
}

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

function mockProyecto(overrides: Partial<ProyectoDto> = {}): ProyectoDto {
  return {
    id: 'proj-1', nombre: 'sq', descripcion: null, metodo: 'scrum',
    timeBoxSemanas: 2, numeroSprints: 3, fechaInicio: '2026-07-01', productGoal: 'x',
    sprintGoal: '', estado: 'activo', scrumMasterEmail: 'sm@test.com', totalMiembros: 2,
    createdAt: '2026-07-01T00:00:00Z',
    ...overrides
  };
}

function mockSprint(numero: number, estado: SprintDto['estado']): SprintDto {
  return {
    id: `sprint-${numero}`, proyectoId: 'proj-1', proyectoNombre: 'sq', metodo: 'scrum',
    timeBoxSemanas: 2, numero, sprintGoal: `Sprint ${numero}`, estado,
    fechaInicio: '2026-07-01', fechaFin: '2026-07-14', cerradoPor: null, cerradoAt: null,
    createdAt: '2026-07-01T00:00:00Z'
  };
}

describe('PlaneacionComponent', () => {
  let component: PlaneacionComponent;
  let fixture: ComponentFixture<PlaneacionComponent>;
  let planeacionService: jasmine.SpyObj<PlaneacionService>;
  let sprintService: jasmine.SpyObj<SprintService>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    const planeacionServiceSpy = jasmine.createSpyObj('PlaneacionService', [
      'listarMetricas', 'listarSeleccionadas', 'seleccionar', 'deseleccionar',
      'aprobar', 'desaprobar', 'listarVariables', 'sincronizarVariables'
    ]);
    const sprintServiceSpy = jasmine.createSpyObj('SprintService', ['listar']);
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    // Defaults inofensivos para las pruebas existentes (que no ejercitan
    // ngOnInit()/el encabezado): un catálogo vacío y sin sprints.
    planeacionServiceSpy.listarMetricas.and.returnValue(of([]));
    planeacionServiceSpy.listarVariables.and.returnValue(of([]));
    sprintServiceSpy.listar.and.returnValue(of([]));

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
    sprintService = TestBed.inject(SprintService) as jasmine.SpyObj<SprintService>;
    httpMock = TestBed.inject(HttpTestingController);

    fixture = TestBed.createComponent(PlaneacionComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    localStorage.clear();
    httpMock.verify();
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

    // Revisión de Planeación: antes, una métrica de IA ya seleccionada en ESTE
    // proyecto seguía en el catálogo (solo se ocultaba el botón "Seleccionar").
    // Ahora se excluye directamente del catálogo — su estado ya se representa
    // en "Seleccionadas"/"Historial" (ver test 3), y ofrecerla también acá
    // generaba la confusión de "métrica duplicada" reportada.
    it('7. (FASE 15) una métrica creada con IA y ya seleccionada en este proyecto ya NO aparece en el catálogo', () => {
      const metricaIA = metrica('ia-metrica-1', 'Estado de ánimo del equipo', 'Socio-Humano FSH', {
        codigo: 'IA-001', seleccionada: true, aprobada: false
      });
      component.metricas = [...CATALOGO_MOCK, metricaIA];

      const todas = component.categorias.flatMap(cat => component.metricasFiltradas(cat));
      expect(todas.some(m => m.metricaId === 'ia-metrica-1')).toBe(false);
    });

    // Corrección post-implementación (V31, catálogo global de métricas): Metrica
    // es un catálogo GLOBAL — una métrica creada con IA por OTRO proyecto debe
    // seguir apareciendo como DISPONIBLE en este proyecto (para poder
    // reutilizarla vía ProyectoMetrica), nunca ocultarse. Antes ocurría lo
    // opuesto (ver historial de este test): eso era el bug que esta corrección
    // resuelve.
    it('8. una métrica creada con IA por OTRO proyecto aparece DISPONIBLE (reutilizable), no seleccionada', () => {
      const metricaIADeOtroProyecto = metrica('ia-metrica-2', 'Métrica de otro proyecto', 'Socio-Humano FSH', {
        codigo: 'IA-002', seleccionada: false, aprobada: false
      });
      component.metricas = [...CATALOGO_MOCK, metricaIADeOtroProyecto];

      const todas = component.categorias.flatMap(cat => component.metricasFiltradas(cat));
      const encontrada = todas.find(m => m.metricaId === 'ia-metrica-2');
      expect(encontrada).toBeTruthy();
      expect(component.estaSeleccionada(encontrada!)).toBe(false);
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

    // El componente nunca calcula "seleccionada" por su cuenta: refleja
    // exactamente lo que ya viene scopeado por proyecto desde el backend
    // (PlaneacionService.listarMetricasConEstado). Si el mismo metricaId
    // llega con seleccionada=true (como lo vería el Proyecto A) o
    // seleccionada=false (como lo vería el Proyecto B para la misma
    // métrica), el estado mostrado es exactamente ese — sin lógica cruzada.
    it('estaSeleccionada refleja exactamente el campo seleccionada del backend, sin lógica propia', () => {
      const vistaProyectoA = metrica('ia-metrica-3', 'Estado de ánimo', 'Socio-Humano FSH', {
        codigo: 'IA-003', seleccionada: true
      });
      const vistaProyectoB = metrica('ia-metrica-3', 'Estado de ánimo', 'Socio-Humano FSH', {
        codigo: 'IA-003', seleccionada: false
      });

      expect(component.estaSeleccionada(vistaProyectoA)).toBe(true);
      expect(component.estaSeleccionada(vistaProyectoB)).toBe(false);
    });

    it('no permite volver a seleccionar una métrica ya seleccionada (la fila ya no está en el catálogo)', () => {
      const yaSeleccionada = metrica('ia-metrica-4', 'Retrabajo', 'Impacto', {
        codigo: 'IA-004', seleccionada: true, aprobada: false
      });
      component.metricas = [...CATALOGO_MOCK, yaSeleccionada];

      const todas = component.categorias.flatMap(cat => component.metricasFiltradas(cat));
      // Ya no hace falta ocultar el botón "Seleccionar": la fila entera se
      // excluye del catálogo, así que no hay forma de intentar re-seleccionarla desde acá.
      expect(todas.some(m => m.metricaId === 'ia-metrica-4')).toBe(false);
    });

    // ══════════════════════════════════════════════════════════════════════
    // Revisión de Planeación — el catálogo (izquierda) deja de listar lo que
    // el proyecto ACTUAL ya seleccionó, sin afectar el resto de las reglas:
    // otro proyecto sigue viéndola disponible, y "Seleccionadas"/"Historial"
    // (derecha) no cambian en absoluto.
    // ══════════════════════════════════════════════════════════════════════

    it('una métrica seleccionada por el proyecto actual NO aparece en metricasFiltradas(), aunque esté en la whitelist', () => {
      const yaSeleccionadaEnEsteProyecto: ProyectoMetricaDto = {
        ...CATALOGO_MOCK.find(m => m.metricaId === 'ec0d74fe-0bf4-4970-af89-dcaa0736c8ed')!, // Defectos, whitelisteada
        seleccionada: true,
        aprobada: false
      };
      component.metricas = CATALOGO_MOCK.map(m =>
        m.metricaId === 'ec0d74fe-0bf4-4970-af89-dcaa0736c8ed' ? yaSeleccionadaEnEsteProyecto : m
      );

      const todas = component.categorias.flatMap(cat => component.metricasFiltradas(cat));
      expect(todas.some(m => m.metricaId === 'ec0d74fe-0bf4-4970-af89-dcaa0736c8ed')).toBe(false);
    });

    it('una métrica seleccionada por OTRO proyecto (seleccionada=false para este) SÍ aparece en metricasFiltradas()', () => {
      const deOtroProyecto = metrica('ia-metrica-5', 'Métrica usada por otro proyecto', 'Impacto', {
        codigo: 'IA-005', seleccionada: false, aprobada: false
      });
      component.metricas = [...CATALOGO_MOCK, deOtroProyecto];

      const todas = component.categorias.flatMap(cat => component.metricasFiltradas(cat));
      expect(todas.some(m => m.metricaId === 'ia-metrica-5')).toBe(true);
    });

    it('una métrica no seleccionada por nadie SÍ aparece en metricasFiltradas()', () => {
      const sinUsar = CATALOGO_MOCK.find(m => m.metricaId === 'beb22a94-0e1b-496a-8b9e-a08a8f6d77c3')!; // Aprendizaje organizacional (FAT)
      expect(sinUsar.seleccionada).toBe(false);

      const todas = component.categorias.flatMap(cat => component.metricasFiltradas(cat));
      expect(todas.some(m => m.metricaId === sinUsar.metricaId)).toBe(true);
    });

    it('el nuevo filtro no altera "Seleccionadas"/"Historial": siguen mostrando lo ya seleccionado en este proyecto', () => {
      // Mismo caso que el test 3, repetido explícitamente acá para dejar constancia
      // de que la exclusión en metricasFiltradas() no afecta a estos getters —
      // no la usan, filtran this.metricas directamente.
      expect(
        component.historialSeleccionadas.some(m => m.metricaId === '20bcc976-ede7-40bd-aa51-e56511f9e32d')
      ).toBe(true);
      expect(
        component.seleccionadasList.some(m => m.metricaId === '2345492b-16a3-464e-983d-0176c0f911c2')
      ).toBe(true);
    });
  });

  // ══════════════════════════════════════════════════════════════════════
  // Corrección de auditoría: el badge del encabezado mostraba el texto
  // LITERAL "Sprint 5 de 5" (hardcodeado, sin binding a ningún dato),
  // mientras el panel lateral y el banner de esta misma página mostraban
  // proyecto.numeroSprints (el valor real). Ninguno de los tests anteriores
  // de este archivo ejecuta ngOnInit()/renderiza el encabezado — por eso
  // esta inconsistencia nunca fue detectada. Estos tests SÍ ejercitan
  // ngOnInit() (vía fixture.detectChanges()) y verifican el texto renderizado.
  // ══════════════════════════════════════════════════════════════════════
  describe('Encabezado "Sprint N de M" (corrección de auditoría)', () => {
    function montarCon(sprints: SprintDto[], proyecto = mockProyecto()): void {
      localStorage.setItem('mpdia_proyecto_activo', JSON.stringify(proyecto));
      sprintService.listar.and.returnValue(of(sprints));
      fixture.detectChanges();
      // ngOnInit() dispara un forkJoin que incluye una llamada HTTP cruda
      // (no vía service) a /metric-ranking/pendientes — HttpClientTestingModule
      // no la resuelve sola, hay que flushearla para que el forkJoin complete
      // y this.sprints quede asignado.
      httpMock.expectOne(`${environment.apiBaseUrl}/metric-ranking/pendientes?proyectoId=${proyecto.id}`).flush([]);
      // El primer detectChanges() ya renderizó (con sprints todavía vacío);
      // hace falta un segundo ciclo para reflejar this.sprints tras el flush.
      fixture.detectChanges();
    }

    it('proyecto de 3 sprints (1 en ejecución): muestra "Sprint 2 de 3", nunca "Sprint 5 de 5"', () => {
      montarCon([
        mockSprint(1, 'finalizado'),
        mockSprint(2, 'en_ejecucion'),
        mockSprint(3, 'pendiente')
      ], mockProyecto({ numeroSprints: 3 }));

      expect(component.sprints.length).toBe(3);
      expect(component.sprintActual?.numero).toBe(2);

      const texto = fixture.nativeElement.textContent;
      expect(texto).toContain('Sprint 2 de 3');
      expect(texto).not.toContain('Sprint 5 de 5');
    });

    it('proyecto de 5 sprints (sprint 5 en ejecución): muestra "Sprint 5 de 5" — pero porque corresponde a los datos reales, no hardcodeado', () => {
      montarCon([
        mockSprint(1, 'finalizado'), mockSprint(2, 'finalizado'), mockSprint(3, 'finalizado'),
        mockSprint(4, 'finalizado'), mockSprint(5, 'en_ejecucion')
      ], mockProyecto({ numeroSprints: 5 }));

      expect(component.sprints.length).toBe(5);
      expect(component.sprintActual?.numero).toBe(5);
      expect(fixture.nativeElement.textContent).toContain('Sprint 5 de 5');
    });

    it('proyecto de 1 sprint: muestra "Sprint 1 de 1"', () => {
      montarCon([mockSprint(1, 'en_ejecucion')], mockProyecto({ numeroSprints: 1 }));

      expect(component.sprints.length).toBe(1);
      expect(component.sprintActual?.numero).toBe(1);
      expect(fixture.nativeElement.textContent).toContain('Sprint 1 de 1');
    });

    it('sin sprint en ejecución (ej. se cerró el último sin iniciar uno nuevo): sprintActual es null, no inventa un número', () => {
      montarCon([
        mockSprint(1, 'finalizado'), mockSprint(2, 'finalizado'), mockSprint(3, 'finalizado')
      ], mockProyecto({ numeroSprints: 3 }));

      expect(component.sprintActual).toBeNull();
      const texto = fixture.nativeElement.textContent;
      expect(texto).toContain('Sin sprint activo');
      expect(texto).toContain('3 sprint');
      expect(texto).not.toMatch(/Sprint \d+ de \d+/);
    });

    it('proyecto recién creado, sin sprints todavía: no muestra ningún badge de sprint ni división por cero', () => {
      montarCon([], mockProyecto({ numeroSprints: 0 }));

      expect(component.sprints.length).toBe(0);
      expect(component.sprintActual).toBeNull();
      expect(() => fixture.detectChanges()).not.toThrow();
      expect(fixture.nativeElement.textContent).not.toContain('Sprint 5 de 5');
    });

    it('cambiar de proyecto (nueva instancia del componente) NO arrastra el total de sprints del proyecto anterior', () => {
      // Proyecto A: 5 sprints.
      montarCon([
        mockSprint(1, 'finalizado'), mockSprint(2, 'finalizado'), mockSprint(3, 'finalizado'),
        mockSprint(4, 'finalizado'), mockSprint(5, 'en_ejecucion')
      ], mockProyecto({ id: 'proj-A', numeroSprints: 5 }));
      expect(fixture.nativeElement.textContent).toContain('Sprint 5 de 5');

      // Angular recrea el componente en cada navegación a la ruta (mismo
      // comportamiento que el resto de la app) — se simula creando una
      // instancia nueva, como ocurriría al volver a entrar a /planeacion
      // tras cambiar el proyecto activo.
      const fixtureB = TestBed.createComponent(PlaneacionComponent);
      sprintService.listar.and.returnValue(of([mockSprint(1, 'en_ejecucion'), mockSprint(2, 'pendiente'), mockSprint(3, 'pendiente')]));
      localStorage.setItem('mpdia_proyecto_activo', JSON.stringify(mockProyecto({ id: 'proj-B', numeroSprints: 3 })));
      fixtureB.detectChanges();
      httpMock.expectOne(`${environment.apiBaseUrl}/metric-ranking/pendientes?proyectoId=proj-B`).flush([]);
      fixtureB.detectChanges();

      const textoB = fixtureB.nativeElement.textContent;
      expect(textoB).toContain('Sprint 1 de 3');
      expect(textoB).not.toContain('Sprint 5 de 5');
      expect(textoB).not.toContain('5 de 5');
    });

    it('el sprint actual corresponde al proyecto activo (estado="en_ejecucion" del proyecto cargado, no de otro)', () => {
      montarCon([
        mockSprint(1, 'finalizado'),
        mockSprint(2, 'finalizado'),
        mockSprint(3, 'en_ejecucion')
      ], mockProyecto({ numeroSprints: 3 }));

      expect(component.sprintActual?.numero).toBe(3);
      expect(component.sprintActual?.estado).toBe('en_ejecucion');
    });
  });
});
