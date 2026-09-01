// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RisksAlertsCardComponent } from './risks-alerts-card.component';
import { Risk } from '../../../models/analytics.model';

describe('RisksAlertsCardComponent', () => {
  let component: RisksAlertsCardComponent;
  let fixture: ComponentFixture<RisksAlertsCardComponent>;

  const mockRisks: Risk[] = [
    {
      proyectoId: 'proyecto-123',
      tipo: 'DECLINING_METRIC',
      severidad: 'HIGH',
      titulo: 'Calidad en descenso',
      evidencia: 'Disminución de 15% en 3 sprints',
      categoriaAfectada: 'Calidad',
      detectedAt: '2026-08-11T10:00:00Z'
    },
    {
      proyectoId: 'proyecto-123',
      tipo: 'HIGH_VARIABILITY',
      severidad: 'MEDIUM',
      titulo: 'Variabilidad alta',
      evidencia: 'Desviación estándar de 2.5',
      categoriaAfectada: 'Productividad',
      detectedAt: '2026-08-11T10:00:00Z'
    }
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RisksAlertsCardComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(RisksAlertsCardComponent);
    component = fixture.componentInstance;
  });

  it('debería crearse', () => {
    component.risks = [];
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('debería mostrar lista de riesgos', () => {
    component.risks = mockRisks;
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.textContent).toContain('Calidad en descenso');
    expect(compiled.textContent).toContain('Variabilidad alta');
  });

  it('debería mostrar severidades correctamente', () => {
    component.risks = mockRisks;
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.textContent).toContain('HIGH');
    expect(compiled.textContent).toContain('MEDIUM');
  });

  it('debería aplicar clase danger para HIGH severity', () => {
    const highRisk: Risk = mockRisks[0];
    const cssClass = component.getSeverityClass(highRisk.severidad);
    expect(cssClass).toBe('alert-danger');
  });

  it('debería aplicar clase warning para MEDIUM severity', () => {
    const mediumRisk: Risk = mockRisks[1];
    const cssClass = component.getSeverityClass(mediumRisk.severidad);
    expect(cssClass).toBe('alert-warning');
  });

  it('debería mostrar estado vacío cuando no hay riesgos', () => {
    component.risks = [];
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.textContent).toContain('Sin riesgos detectados');
  });

  it('debería mostrar iconos según severidad', () => {
    expect(component.getSeverityIcon('CRITICAL')).toBe('bi-exclamation-octagon-fill');
    expect(component.getSeverityIcon('HIGH')).toBe('bi-exclamation-triangle-fill');
    expect(component.getSeverityIcon('MEDIUM')).toBe('bi-exclamation-circle-fill');
    expect(component.getSeverityIcon('LOW')).toBe('bi-info-circle-fill');
  });

  it('debería tener iconos para accesibilidad', () => {
    component.risks = mockRisks;
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const icons = compiled.querySelectorAll('i.bi');
    expect(icons.length).toBeGreaterThan(0);
  });

  // DS.9 — RIESGOS MÁS ACCIONABLES
  describe('DS.9 - Riesgos accionables', () => {
    it('debería generar acción sugerida para DECLINING_METRIC', () => {
      const action = component.getSuggestedAction('DECLINING_METRIC');
      expect(action).toBeTruthy();
      expect(action.toLowerCase()).toContain('causas');
    });

    it('debería generar acción sugerida para HIGH_VARIABILITY', () => {
      const action = component.getSuggestedAction('HIGH_VARIABILITY');
      expect(action).toBeTruthy();
      expect(action.toLowerCase()).toContain('variabilidad');
    });

    it('debería generar acción sugerida para STAGNANT_PROGRESS', () => {
      const action = component.getSuggestedAction('STAGNANT_PROGRESS');
      expect(action).toBeTruthy();
      expect(action.toLowerCase()).toContain('bloqueos');
    });

    it('debería generar acción sugerida para LOW_TEAM_SATISFACTION', () => {
      const action = component.getSuggestedAction('LOW_TEAM_SATISFACTION');
      expect(action).toBeTruthy();
      expect(action.toLowerCase()).toContain('equipo');
    });

    it('debería generar acción sugerida para QUALITY_ISSUES', () => {
      const action = component.getSuggestedAction('QUALITY_ISSUES');
      expect(action).toBeTruthy();
      expect(action.toLowerCase()).toContain('calidad');
    });

    it('debería generar acción sugerida para PRODUCTIVITY_DROP', () => {
      const action = component.getSuggestedAction('PRODUCTIVITY_DROP');
      expect(action).toBeTruthy();
      expect(action.toLowerCase()).toContain('impedimentos');
    });

    it('debería tener acción genérica para tipos desconocidos', () => {
      const action = component.getSuggestedAction('UNKNOWN_TYPE');
      expect(action).toBeTruthy();
      expect(action.toLowerCase()).toContain('revisar');
    });

    it('debería mostrar botón de acción sugerida en UI', () => {
      component.risks = mockRisks;
      fixture.detectChanges();
      const compiled = fixture.nativeElement;
      expect(compiled.textContent).toContain('Acción sugerida');
    });
  });
});
