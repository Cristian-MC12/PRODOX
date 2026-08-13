// Autor: Cristian Santiago Martinez Cordoba — MPDIA
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

  it('debería mostrar AI Insights en sección IA', () => {
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
    expect(aiInsightsLink).toBeTruthy();
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

  it('debería mostrar Retrospectivas en sección IA', () => {
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
    expect(retroLink).toBeTruthy();
    expect(retroLink.textContent).toContain('Retrospectivas');
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
