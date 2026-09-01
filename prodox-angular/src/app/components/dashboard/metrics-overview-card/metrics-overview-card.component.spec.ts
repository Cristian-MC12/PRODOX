// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetricsOverviewCardComponent } from './metrics-overview-card.component';

describe('MetricsOverviewCardComponent', () => {
  let component: MetricsOverviewCardComponent;
  let fixture: ComponentFixture<MetricsOverviewCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MetricsOverviewCardComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(MetricsOverviewCardComponent);
    component = fixture.componentInstance;
  });

  it('debería crearse', () => {
    component.metricas = {};
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('debería manejar categorías dinámicas', () => {
    component.metricas = {
      'Calidad': 8.5,
      'Productividad': 7.2,
      'Colaboración': 9.1
    };
    fixture.detectChanges();

    const array = component.getMetricasArray();
    expect(array.length).toBe(3);
    expect(array[0].categoria).toBe('Calidad');
    expect(array[0].valor).toBe(8.5);
  });

  it('debería mostrar valores correctamente', () => {
    component.metricas = {
      'Calidad': 8.5,
      'Productividad': 7.2
    };
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.textContent).toContain('Calidad');
    expect(compiled.textContent).toContain('8.5');
    expect(compiled.textContent).toContain('Productividad');
    expect(compiled.textContent).toContain('7.2');
  });

  it('debería mostrar estado vacío cuando no hay métricas', () => {
    component.metricas = {};
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.textContent).toContain('No hay métricas disponibles');
  });

  it('debería manejar categorías personalizadas', () => {
    component.metricas = {
      'Mi Métrica Custom': 5.0,
      'Otra Métrica': 3.5
    };
    fixture.detectChanges();

    const array = component.getMetricasArray();
    expect(array.length).toBe(2);
    expect(array.some(m => m.categoria === 'Mi Métrica Custom')).toBe(true);
  });

  // DS.5 — CONTEXTO DE MÉTRICAS
  describe('DS.5 - Contexto de métricas', () => {
    it('debería interpretar valor excelente (>= 8.5)', () => {
      const interpretacion = component.getMetricInterpretation(9.0);
      expect(interpretacion).toContain('Excelente');
      expect(interpretacion).toContain('🟢');
    });

    it('debería interpretar valor bueno (>= 7.0)', () => {
      const interpretacion = component.getMetricInterpretation(7.5);
      expect(interpretacion).toContain('Bueno');
      expect(interpretacion).toContain('🔵');
    });

    it('debería interpretar valor regular (>= 5.5)', () => {
      const interpretacion = component.getMetricInterpretation(6.0);
      expect(interpretacion).toContain('Regular');
      expect(interpretacion).toContain('🟡');
    });

    it('debería interpretar valor que necesita atención (>= 4.0)', () => {
      const interpretacion = component.getMetricInterpretation(4.5);
      expect(interpretacion).toContain('Necesita atención');
      expect(interpretacion).toContain('🟠');
    });

    it('debería interpretar valor crítico (< 4.0)', () => {
      const interpretacion = component.getMetricInterpretation(3.0);
      expect(interpretacion).toContain('Crítico');
      expect(interpretacion).toContain('🔴');
    });
  });
});
