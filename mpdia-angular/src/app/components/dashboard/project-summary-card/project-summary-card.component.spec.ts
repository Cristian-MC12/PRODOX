// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProjectSummaryCardComponent } from './project-summary-card.component';
import { ProjectOverview } from '../../../models/analytics.model';

describe('ProjectSummaryCardComponent', () => {
  let component: ProjectSummaryCardComponent;
  let fixture: ComponentFixture<ProjectSummaryCardComponent>;

  const mockOverviewComplete: ProjectOverview = {
    proyectoId: 'proyecto-123',
    proyectoNombre: 'Proyecto Test',
    totalSprints: 5,
    sprintsFinalizados: 3,
    sprintActualNumero: 4,
    promedioHistorico: { Calidad: 8.5 },
    mejorSprint: { numero: 2, scoreGeneral: 9.1, razon: 'Excelente calidad' },
    peorSprint: { numero: 1, scoreGeneral: 6.8, razon: 'Primer sprint' },
    datosDisponibles: true
  };

  const mockOverviewMinimal: ProjectOverview = {
    proyectoId: 'proyecto-123',
    proyectoNombre: 'Proyecto Test',
    totalSprints: 1,
    sprintsFinalizados: 0,
    sprintActualNumero: null,
    promedioHistorico: {},
    mejorSprint: null,
    peorSprint: null,
    datosDisponibles: false
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProjectSummaryCardComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(ProjectSummaryCardComponent);
    component = fixture.componentInstance;
  });

  it('debería crearse', () => {
    component.overview = mockOverviewComplete;
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('debería mostrar datos completos correctamente', () => {
    component.overview = mockOverviewComplete;
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.textContent).toContain('5');
    expect(compiled.textContent).toContain('3');
    expect(compiled.textContent).toContain('4');
    expect(compiled.textContent).toContain('2');
    expect(compiled.textContent).toContain('Excelente calidad');
  });

  it('debería manejar datos mínimos sin sprint actual', () => {
    component.overview = mockOverviewMinimal;
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.textContent).toContain('1');
    expect(compiled.textContent).toContain('0');
  });

  it('debería mostrar icono de trofeo para mejor sprint', () => {
    component.overview = mockOverviewComplete;
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const trophyIcon = compiled.querySelector('.bi-trophy-fill');
    expect(trophyIcon).toBeTruthy();
  });
});
