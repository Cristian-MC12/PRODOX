// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ProjectMemberService } from './project-member.service';
import { ProjectMemberDto } from '../models/project-member.model';
import { environment } from '../../environments/environment';

describe('ProjectMemberService', () => {
  let service: ProjectMemberService;
  let httpMock: HttpTestingController;

  const mockMiembro: ProjectMemberDto = {
    proyectoId: 'uuid-proyecto-1',
    userId:     'uuid-user-1',
    userEmail:  'member@mpdia.com',
    rol:        'scrum_member',
    joinedAt:   new Date().toISOString()
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ProjectMemberService]
    });
    service  = TestBed.inject(ProjectMemberService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // ── listar ───────────────────────────────────────────────────────────────

  it('listar: debe hacer GET a /project-members/{proyectoId}', () => {
    const proyectoId = 'uuid-proyecto-1';

    service.listar(proyectoId).subscribe(lista => {
      expect(lista.length).toBe(1);
      expect(lista[0].userEmail).toBe('member@mpdia.com');
    });

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/project-members/${proyectoId}`);
    expect(http.request.method).toBe('GET');
    http.flush([mockMiembro]);
  });

  it('listar: retorna lista vacía si no hay miembros', () => {
    service.listar('uuid-proyecto-1').subscribe(lista => {
      expect(lista).toEqual([]);
    });

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/project-members/uuid-proyecto-1`);
    http.flush([]);
  });

  // ── invitar ───────────────────────────────────────────────────────────────

  it('invitar: debe hacer POST con email y retornar código y estado de envío de correo', () => {
    const proyectoId = 'uuid-proyecto-1';
    const email = 'nuevo@mpdia.com';

    service.invitar(proyectoId, email).subscribe(res => {
      expect(res.codigo).toBe('PRJ-ABC123');
      expect(res.emailEnviado).toBeTrue();
    });

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/project-members/${proyectoId}/invitar`);
    expect(http.request.method).toBe('POST');
    expect(http.request.body).toEqual({ email });
    http.flush({ codigo: 'PRJ-ABC123', emailEnviado: true });
  });

  it('invitar: si el correo no pudo enviarse, igual retorna el código generado', () => {
    const proyectoId = 'uuid-proyecto-1';
    const email = 'nuevo@mpdia.com';

    service.invitar(proyectoId, email).subscribe(res => {
      expect(res.codigo).toBe('PRJ-ABC123');
      expect(res.emailEnviado).toBeFalse();
    });

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/project-members/${proyectoId}/invitar`);
    http.flush({ codigo: 'PRJ-ABC123', emailEnviado: false });
  });

  // ── unirse ────────────────────────────────────────────────────────────────

  it('unirse: debe hacer POST con código y retornar el nuevo miembro', () => {
    const codigo = 'PRJ-ABC123';

    service.unirse(codigo).subscribe(res => {
      expect(res.userEmail).toBe('member@mpdia.com');
      expect(res.rol).toBe('scrum_member');
    });

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/project-members/unirse`);
    expect(http.request.method).toBe('POST');
    expect(http.request.body).toEqual({ codigo });
    http.flush(mockMiembro);
  });

  // ── consultarInvitacion (estado público, sin sesión) ───────────────────

  it('consultarInvitacion: debe hacer GET a /project-members/invitacion/{codigo}', () => {
    const codigo = 'PRJ-ABC123';

    service.consultarInvitacion(codigo).subscribe(res => {
      expect(res.estado).toBe('valida');
      expect(res.proyectoNombre).toBe('Proyecto Demo');
    });

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/project-members/invitacion/${codigo}`);
    expect(http.request.method).toBe('GET');
    http.flush({ proyectoId: 'uuid-proyecto-1', proyectoNombre: 'Proyecto Demo', estado: 'valida' });
  });

  it('consultarInvitacion: propaga estados expirada/usada/no_existe tal cual los devuelve el backend', () => {
    service.consultarInvitacion('PRJ-VENCIDO').subscribe(res => {
      expect(res.estado).toBe('expirada');
    });

    const http = httpMock.expectOne(`${environment.apiBaseUrl}/project-members/invitacion/PRJ-VENCIDO`);
    http.flush({ proyectoId: 'uuid-proyecto-1', proyectoNombre: 'Proyecto Demo', estado: 'expirada' });
  });
});
