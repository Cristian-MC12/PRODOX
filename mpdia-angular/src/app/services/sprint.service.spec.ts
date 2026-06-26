// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { SprintService } from './sprint.service';
import { SprintDto } from '../models/sprint.model';
import { environment } from '../../environments/environment';

describe('SprintService', () => {
  let service: SprintService;
  let httpMock: HttpTestingController;

  const proyectoId = 'uuid-proyecto-1';

  const mockSprint: SprintDto = {
    id:             'uuid-sprint-1',
    proyectoId:     proyectoId,
    proyectoNombre: 'Sistema MPDIA',
    metodo:         'scrum',
    timeBoxSemanas: 2,
    numero:         1,
    sprintGoal:     'Completar módulo de autenticación',
    estado:         'en_ejecucion',
    fechaInicio:    '2026-06-01',
    fechaFin:       '2026-06-15',
    cerradoPor:     null,
    cerradoAt:      null,
    createdAt:      new Date().toISOString()
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [SprintService]
    });
    service  = TestBed.inject(SprintService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // ── getActivo ────────────────────────────────────────────────────────────

  it('getActivo: debe hacer GET a /sprints/{proyectoId}/activo', () => {
    service.getActivo(proyectoId).subscribe(sprint => {
      expect(sprint.estado).toBe('en_ejecucion');
      expect(sprint.numero).toBe(1);
      expect(sprint.proyectoId).toBe(proyectoId);
    });

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/sprints/${proyectoId}/activo`);
    expect(http.request.method).toBe('GET');
    http.flush(mockSprint);
  });

  // ── listar ────────────────────────────────────────────────────────────────

  it('listar: debe hacer GET a /sprints/{proyectoId}', () => {
    const sprint2: SprintDto = { ...mockSprint, id: 'uuid-sprint-2', numero: 2, estado: 'finalizado' };

    service.listar(proyectoId).subscribe(lista => {
      expect(lista.length).toBe(2);
      expect(lista[0].numero).toBe(1);
      expect(lista[1].numero).toBe(2);
    });

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/sprints/${proyectoId}`);
    expect(http.request.method).toBe('GET');
    http.flush([mockSprint, sprint2]);
  });

  it('listar: retorna lista vacía si no hay sprints', () => {
    service.listar(proyectoId).subscribe(lista => {
      expect(lista).toEqual([]);
    });

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/sprints/${proyectoId}`);
    http.flush([]);
  });

  // ── cerrarEIniciarSiguiente ───────────────────────────────────────────────

  it('cerrarEIniciarSiguiente: debe hacer POST con el nuevo sprintGoal', () => {
    const nuevoSprint: SprintDto = {
      ...mockSprint,
      id:         'uuid-sprint-2',
      numero:     2,
      sprintGoal: 'Completar módulo de proyectos',
      estado:     'en_ejecucion'
    };

    service.cerrarEIniciarSiguiente(proyectoId, 'Completar módulo de proyectos')
      .subscribe(sprint => {
        expect(sprint.numero).toBe(2);
        expect(sprint.sprintGoal).toBe('Completar módulo de proyectos');
        expect(sprint.estado).toBe('en_ejecucion');
      });

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/sprints/${proyectoId}/siguiente`);
    expect(http.request.method).toBe('POST');
    expect(http.request.body).toEqual({ sprintGoal: 'Completar módulo de proyectos' });
    http.flush(nuevoSprint);
  });
});
