// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ProyectoService } from './proyecto.service';
import { ProyectoDto, CrearProyectoRequest } from '../models/proyecto.model';
import { environment } from '../../environments/environment';

describe('ProyectoService', () => {
  let service: ProyectoService;
  let httpMock: HttpTestingController;

  const mockProyecto: ProyectoDto = {
    id: 'uuid-proyecto-1',
    nombre: 'Sistema MPDIA',
    descripcion: 'Descripción del proyecto',
    metodo: 'scrum',
    timeBoxSemanas: 2,
    numeroSprints: 3,
    fechaInicio: '2026-06-01',
    productGoal: 'Medir productividad',
    sprintGoal: 'Sprint 1 goal',
    estado: 'activo',
    scrumMasterEmail: 'sm@mpdia.com',
    totalMiembros: 1,
    createdAt: new Date().toISOString()
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ProyectoService]
    });
    service  = TestBed.inject(ProyectoService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // ── crear ────────────────────────────────────────────────────────────────

  it('crear: debe hacer POST con los datos del proyecto', () => {
    const req: CrearProyectoRequest = {
      nombre: 'Sistema MPDIA',
      descripcion: 'Desc',
      metodo: 'scrum',
      timeBoxSemanas: 2,
      numeroSprints: 3,
      fechaInicio: '2026-06-01',
      productGoal: 'Goal producto'
    };

    service.crear(req).subscribe(res => {
      expect(res.nombre).toBe('Sistema MPDIA');
      expect(res.metodo).toBe('scrum');
      expect(res.estado).toBe('activo');
    });

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/proyectos`);
    expect(http.request.method).toBe('POST');
    expect(http.request.body).toEqual(req);
    http.flush(mockProyecto);
  });

  // ── getMisProyectos ──────────────────────────────────────────────────────

  it('getMisProyectos: debe hacer GET a /proyectos/mios', () => {
    service.getMisProyectos().subscribe(lista => {
      expect(lista.length).toBe(1);
      expect(lista[0].nombre).toBe('Sistema MPDIA');
    });

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/proyectos/mios`);
    expect(http.request.method).toBe('GET');
    http.flush([mockProyecto]);
  });

  it('getMisProyectos: retorna lista vacía si el usuario no tiene proyectos', () => {
    service.getMisProyectos().subscribe(lista => {
      expect(lista).toEqual([]);
    });

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/proyectos/mios`);
    http.flush([]);
  });

  // ── getById ───────────────────────────────────────────────────────────────

  it('getById: debe hacer GET a /proyectos/{id}', () => {
    const id = 'uuid-proyecto-1';

    service.getById(id).subscribe(res => {
      expect(res.id).toBe(id);
      expect(res.nombre).toBe('Sistema MPDIA');
    });

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/proyectos/${id}`);
    expect(http.request.method).toBe('GET');
    http.flush(mockProyecto);
  });

  // ── finalizar ─────────────────────────────────────────────────────────────

  it('finalizar: debe hacer PATCH a /proyectos/{id}/finalizar', () => {
    const id = 'uuid-proyecto-1';
    const finalizado: ProyectoDto = { ...mockProyecto, estado: 'finalizado' };

    service.finalizar(id).subscribe(res => {
      expect(res.estado).toBe('finalizado');
    });

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/proyectos/${id}/finalizar`);
    expect(http.request.method).toBe('PATCH');
    http.flush(finalizado);
  });
});
