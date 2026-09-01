// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { SidebarComponent } from './sidebar.component';
import { AuthService } from '../../services/auth.service';
import { of } from 'rxjs';

describe('SidebarComponent', () => {
  let component: SidebarComponent;
  let fixture: ComponentFixture<SidebarComponent>;
  let mockAuthService: jasmine.SpyObj<AuthService>;

  const mockUser = {
    id: 'user-123',
    email: 'test@test.com',
    nombre: 'Test User',
    role: 'scrum_master' as const
  };

  beforeEach(async () => {
    mockAuthService = jasmine.createSpyObj('AuthService', ['logout'], {
      currentUser: jasmine.createSpy().and.returnValue(mockUser)
    });

    await TestBed.configureTestingModule({
      imports: [SidebarComponent, RouterTestingModule],
      providers: [
        { provide: AuthService, useValue: mockAuthService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SidebarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debería crearse', () => {
    expect(component).toBeTruthy();
  });

  it('debería mostrar enlace a Proyectos', () => {
    const compiled = fixture.nativeElement;
    const proyectosLink = compiled.querySelector('a[routerLink="/proyectos"]');
    expect(proyectosLink).toBeTruthy();
    expect(proyectosLink.textContent).toContain('Proyectos');
  });

  it('debería mostrar enlace a Dashboard cuando hay proyecto activo', () => {
    component.proyectoActivo.set({
      id: 'proyecto-123',
      nombre: 'Proyecto Test',
      descripcion: 'Test',
      metodo: 'scrum',
      timeBoxSemanas: 2,
      numeroSprints: 5,
      fechaInicio: '2026-07-01',
      productGoal: 'Goal',
      sprintGoal: 'Sprint',
      estado: 'activo',
      scrumMasterEmail: 'test@test.com',
      totalMiembros: 3,
      createdAt: '2026-07-01T00:00:00Z'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const dashboardLink = compiled.querySelector('a[routerLink="/dashboard"]');
    expect(dashboardLink).toBeTruthy();
    expect(dashboardLink.textContent).toContain('Dashboard');
  });

  it('debería tener icono en enlace Dashboard', () => {
    component.proyectoActivo.set({
      id: 'proyecto-123',
      nombre: 'Proyecto Test',
      descripcion: 'Test',
      metodo: 'scrum',
      timeBoxSemanas: 2,
      numeroSprints: 5,
      fechaInicio: '2026-07-01',
      productGoal: 'Goal',
      sprintGoal: 'Sprint',
      estado: 'activo',
      scrumMasterEmail: 'test@test.com',
      totalMiembros: 3,
      createdAt: '2026-07-01T00:00:00Z'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const dashboardLink = compiled.querySelector('a[routerLink="/dashboard"]');
    const icon = dashboardLink?.querySelector('i.bi-speedometer2');
    expect(icon).toBeTruthy();
  });

  it('NO debería mostrar AI Insights como entrada independiente (reorganización de navegación)', () => {
    component.proyectoActivo.set({
      id: 'proyecto-123',
      nombre: 'Proyecto Test',
      descripcion: 'Test',
      metodo: 'scrum',
      timeBoxSemanas: 2,
      numeroSprints: 5,
      fechaInicio: '2026-07-01',
      productGoal: 'Goal',
      sprintGoal: 'Sprint',
      estado: 'activo',
      scrumMasterEmail: 'test@test.com',
      totalMiembros: 3,
      createdAt: '2026-07-01T00:00:00Z'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const aiInsightsLink = compiled.querySelector('a[routerLink="/ai-insights"]');
    expect(aiInsightsLink).toBeFalsy();
  });

  it('debería mostrar Reportes en sección IA', () => {
    component.proyectoActivo.set({
      id: 'proyecto-123',
      nombre: 'Proyecto Test',
      descripcion: 'Test',
      metodo: 'scrum',
      timeBoxSemanas: 2,
      numeroSprints: 5,
      fechaInicio: '2026-07-01',
      productGoal: 'Goal',
      sprintGoal: 'Sprint',
      estado: 'activo',
      scrumMasterEmail: 'test@test.com',
      totalMiembros: 3,
      createdAt: '2026-07-01T00:00:00Z'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const reportLink = compiled.querySelector('a[routerLink="/ai-report"]');
    expect(reportLink).toBeTruthy();
    expect(reportLink.textContent).toContain('Reportes');
  });

  it('NO debería mostrar Retrospectivas como entrada independiente (reorganización de navegación)', () => {
    component.proyectoActivo.set({
      id: 'proyecto-123',
      nombre: 'Proyecto Test',
      descripcion: 'Test',
      metodo: 'scrum',
      timeBoxSemanas: 2,
      numeroSprints: 5,
      fechaInicio: '2026-07-01',
      productGoal: 'Goal',
      sprintGoal: 'Sprint',
      estado: 'activo',
      scrumMasterEmail: 'test@test.com',
      totalMiembros: 3,
      createdAt: '2026-07-01T00:00:00Z'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const retroLink = compiled.querySelector('a[routerLink="/ai-retrospective"]');
    expect(retroLink).toBeFalsy();
  });

  it('NO debería mostrar Copiloto en el menú (deshabilitado)', () => {
    fixture.detectChanges();
    const compiled = fixture.nativeElement;
    const copilotoLink = compiled.querySelector('a[routerLink="/configuracion"]');
    expect(copilotoLink).toBeFalsy();
  });

  it('debería mostrar Equipo inmediatamente debajo de Proyectos', () => {
    fixture.detectChanges();
    const compiled = fixture.nativeElement;
    const navLinks: NodeListOf<HTMLAnchorElement> = compiled.querySelectorAll('ul.nav > li.nav-item > a.nav-link');
    const labels = Array.from(navLinks).map(a => a.textContent?.trim());
    const proyectosIdx = labels.findIndex(l => l?.includes('Proyectos'));
    const equipoIdx = labels.findIndex(l => l?.includes('Equipo'));
    expect(proyectosIdx).toBeGreaterThan(-1);
    expect(equipoIdx).toBe(proyectosIdx + 1);
  });

  it('NO debería mostrar Dashboard sin proyecto activo', () => {
    component.proyectoActivo.set(null);
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const dashboardLink = compiled.querySelector('a[routerLink="/dashboard"]');
    expect(dashboardLink).toBeFalsy();
  });

  it('debería usar routerLinkActive para estado activo', () => {
    component.proyectoActivo.set({
      id: 'proyecto-123',
      nombre: 'Proyecto Test',
      descripcion: 'Test',
      metodo: 'scrum',
      timeBoxSemanas: 2,
      numeroSprints: 5,
      fechaInicio: '2026-07-01',
      productGoal: 'Goal',
      sprintGoal: 'Sprint',
      estado: 'activo',
      scrumMasterEmail: 'test@test.com',
      totalMiembros: 3,
      createdAt: '2026-07-01T00:00:00Z'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const dashboardLink = compiled.querySelector('a[routerLink="/dashboard"]');
    expect(dashboardLink?.getAttribute('routerlinkactive')).toBe('active');
  });

  // Corrección: el nombre y el rol en el pie del sidebar estaban hardcodeados
  // como "Cristian Martinez" / "Scrum Master" para cualquier usuario.
  it('debería mostrar el nombre real del usuario autenticado, no un nombre fijo', () => {
    fixture.detectChanges();
    const compiled = fixture.nativeElement;
    expect(compiled.textContent).toContain('Test User');
    expect(compiled.textContent).not.toContain('Cristian Martinez');
  });

  it('debería mostrar el rol real del usuario autenticado (Scrum Member), no asumir siempre Scrum Master', () => {
    (mockAuthService.currentUser as jasmine.Spy).and.returnValue({ ...mockUser, role: 'scrum_member' });
    // Corrección: esScrumMaster ya no depende del rol global de cuenta —
    // depende de si el email coincide con el scrumMasterEmail del proyecto
    // activo (aquí sigue siendo 'test@test.com' para ambos, así que sigue
    // reconocido como Scrum Master de ESTE proyecto aunque su rol de cuenta
    // ahora sea "scrum_member"). El caso real de un no-dueño se cubre abajo.
    component.proyectoActivo.set({
      id: 'proyecto-123', nombre: 'Proyecto Test', descripcion: 'Test', metodo: 'scrum',
      timeBoxSemanas: 2, numeroSprints: 5, fechaInicio: '2026-07-01', productGoal: 'Goal',
      sprintGoal: 'Sprint', estado: 'activo', scrumMasterEmail: 'test@test.com',
      totalMiembros: 3, createdAt: '2026-07-01T00:00:00Z'
    });
    fixture.detectChanges();

    expect(component.nombreMostrado()).toBe('Test User');
    expect(component.esScrumMaster()).toBeTrue();
  });

  it('un usuario cuyo email NO coincide con el scrumMasterEmail del proyecto activo no es reconocido como su Scrum Master, aunque su rol global de cuenta sea "scrum_master"', () => {
    (mockAuthService.currentUser as jasmine.Spy).and.returnValue({ ...mockUser, email: 'no-es-el-creador@test.com', role: 'scrum_master' });
    component.proyectoActivo.set({
      id: 'proyecto-123', nombre: 'Proyecto Test', descripcion: 'Test', metodo: 'scrum',
      timeBoxSemanas: 2, numeroSprints: 5, fechaInicio: '2026-07-01', productGoal: 'Goal',
      sprintGoal: 'Sprint', estado: 'activo', scrumMasterEmail: 'test@test.com',
      totalMiembros: 3, createdAt: '2026-07-01T00:00:00Z'
    });
    fixture.detectChanges();

    expect(component.esScrumMaster()).toBeFalse();
  });

  it('nombreMostrado: usa el correo como último recurso si el usuario no tiene nombre guardado', () => {
    (mockAuthService.currentUser as jasmine.Spy).and.returnValue({ ...mockUser, nombre: undefined, email: 'sinnombre@mpdia.com' });
    fixture.detectChanges();

    expect(component.nombreMostrado()).toBe('sinnombre');
  });

  it('debería cerrar sidebar al hacer clic en Dashboard', () => {
    component.proyectoActivo.set({
      id: 'proyecto-123',
      nombre: 'Proyecto Test',
      descripcion: 'Test',
      metodo: 'scrum',
      timeBoxSemanas: 2,
      numeroSprints: 5,
      fechaInicio: '2026-07-01',
      productGoal: 'Goal',
      sprintGoal: 'Sprint',
      estado: 'activo',
      scrumMasterEmail: 'test@test.com',
      totalMiembros: 3,
      createdAt: '2026-07-01T00:00:00Z'
    });
    component.open.set(true);
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const dashboardLink = compiled.querySelector('a[routerLink="/dashboard"]');
    
    spyOn(component, 'close');
    dashboardLink.click();
    
    expect(component.close).toHaveBeenCalled();
  });
});
