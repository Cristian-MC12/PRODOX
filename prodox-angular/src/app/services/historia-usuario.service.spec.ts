// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HistoriaUsuarioService } from './historia-usuario.service';
import { HistoriaUsuarioDto } from '../models/historia-usuario.model';
import { environment } from '../../environments/environment';

describe('HistoriaUsuarioService', () => {
  let service: HistoriaUsuarioService;
  let httpMock: HttpTestingController;

  const mockHistoria: HistoriaUsuarioDto = {
    id: 'historia-1',
    proyectoId: 'proyecto-1',
    sprintId: null,
    titulo: 'Como usuario quiero X',
    descripcion: 'Descripción',
    criteriosAceptacion: 'Criterio 1',
    prioridad: 'media',
    estado: 'pendiente',
    creadoPor: 'po-1',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [HistoriaUsuarioService]
    });
    service = TestBed.inject(HistoriaUsuarioService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listar: hace GET a /historias/{proyectoId}', () => {
    service.listar('proyecto-1').subscribe(lista => {
      expect(lista.length).toBe(1);
      expect(lista[0].titulo).toBe('Como usuario quiero X');
    });

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/historias/proyecto-1`);
    expect(http.request.method).toBe('GET');
    http.flush([mockHistoria]);
  });

  it('detalle: hace GET a /historias/detalle/{historiaId}', () => {
    service.detalle('historia-1').subscribe(h => expect(h.id).toBe('historia-1'));

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/historias/detalle/historia-1`);
    expect(http.request.method).toBe('GET');
    http.flush(mockHistoria);
  });

  it('crear: hace POST a /historias/{proyectoId} con el body de la historia', () => {
    service.crear('proyecto-1', { titulo: 'Nueva historia', prioridad: 'alta' }).subscribe(h => {
      expect(h.titulo).toBe('Como usuario quiero X');
    });

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/historias/proyecto-1`);
    expect(http.request.method).toBe('POST');
    expect(http.request.body).toEqual({ titulo: 'Nueva historia', prioridad: 'alta' });
    http.flush(mockHistoria);
  });

  it('actualizar: hace PATCH a /historias/{historiaId}', () => {
    service.actualizar('historia-1', { titulo: 'Editado' }).subscribe();

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/historias/historia-1`);
    expect(http.request.method).toBe('PATCH');
    http.flush(mockHistoria);
  });

  it('cambiarPrioridad: hace PATCH a /historias/{historiaId}/prioridad con el body correcto', () => {
    service.cambiarPrioridad('historia-1', 'alta').subscribe();

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/historias/historia-1/prioridad`);
    expect(http.request.method).toBe('PATCH');
    expect(http.request.body).toEqual({ prioridad: 'alta' });
    http.flush({ ...mockHistoria, prioridad: 'alta' });
  });

  it('cambiarEstado: hace PATCH a /historias/{historiaId}/estado con el body correcto', () => {
    service.cambiarEstado('historia-1', 'completada').subscribe();

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/historias/historia-1/estado`);
    expect(http.request.method).toBe('PATCH');
    expect(http.request.body).toEqual({ estado: 'completada' });
    http.flush({ ...mockHistoria, estado: 'completada' });
  });

  it('asignarSprint: hace PATCH a /historias/{historiaId}/sprint con el sprintId', () => {
    service.asignarSprint('historia-1', 'sprint-1').subscribe();

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/historias/historia-1/sprint`);
    expect(http.request.method).toBe('PATCH');
    expect(http.request.body).toEqual({ sprintId: 'sprint-1' });
    http.flush({ ...mockHistoria, sprintId: 'sprint-1' });
  });

  it('asignarSprint: con null desasigna (vuelve al backlog)', () => {
    service.asignarSprint('historia-1', null).subscribe();

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/historias/historia-1/sprint`);
    expect(http.request.body).toEqual({ sprintId: null });
    http.flush(mockHistoria);
  });

  it('eliminar: hace DELETE a /historias/{historiaId}', () => {
    service.eliminar('historia-1').subscribe();

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/historias/historia-1`);
    expect(http.request.method).toBe('DELETE');
    http.flush(null);
  });
});
