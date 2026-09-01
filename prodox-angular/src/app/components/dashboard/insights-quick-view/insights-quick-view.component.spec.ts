// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { InsightsQuickViewComponent } from './insights-quick-view.component';
import { AIInsight } from '../../../models/ai-insights.model';

describe('InsightsQuickViewComponent', () => {
  let component: InsightsQuickViewComponent;
  let fixture: ComponentFixture<InsightsQuickViewComponent>;

  const mockInsights: AIInsight[] = Array.from({ length: 7 }, (_, i) => ({
    id: `insight-${i}`,
    proyectoId: 'proyecto-123',
    sprintId: 'sprint-1',
    type: i % 2 === 0 ? 'TREND' : 'RISK',
    severity: i % 2 === 0 ? 'MEDIUM' : 'HIGH',
    title: `Insight ${i}`,
    description: `Descripción del insight ${i}`,
    evidence: [],
    recommendation: null,
    confidence: 'HIGH',
    dismissed: false,
    createdAt: new Date(Date.now() - i * 1000).toISOString(),
    dismissedAt: null
  }));

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InsightsQuickViewComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(InsightsQuickViewComponent);
    component = fixture.componentInstance;
  });

  it('debería crearse', () => {
    component.insights = [];
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('debería mostrar insights', () => {
    component.insights = mockInsights.slice(0, 3);
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.textContent).toContain('Insight 0');
    expect(compiled.textContent).toContain('Insight 1');
  });

  it('debería mostrar el título/descripción sin marcadores Markdown crudos (FASE 7C.1)', () => {
    component.insights = [{
      ...mockInsights[0],
      title: '**Alerta** de riesgo',
      description: 'Cambio del 20% --- revisar el sprint'
    }];
    fixture.detectChanges();

    const texto: string = fixture.nativeElement.textContent;
    expect(texto).toContain('Alerta de riesgo');
    expect(texto).not.toContain('**Alerta**');
    expect(texto).not.toMatch(/---/);
  });

  it('debería mostrar tipos correctamente', () => {
    component.insights = mockInsights.slice(0, 2);
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.textContent).toContain('Tendencia');
    expect(compiled.textContent).toContain('Riesgo');
  });

  it('debería emitir evento al hacer clic en ver todos', () => {
    component.insights = mockInsights.slice(0, 3);
    fixture.detectChanges();

    spyOn(component.viewAllClick, 'emit');

    const button = fixture.nativeElement.querySelector('button');
    button.click();

    expect(component.viewAllClick.emit).toHaveBeenCalled();
  });

  it('debería mostrar estado vacío cuando no hay insights', () => {
    component.insights = [];
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.textContent).toContain('No hay insights disponibles');
  });

  it('debería obtener clase correcta según tipo', () => {
    expect(component.getTypeClass('RISK')).toBe('border-danger');
    expect(component.getTypeClass('TREND')).toBe('border-info');
    expect(component.getTypeClass('ANOMALY')).toBe('border-warning');
  });

  it('debería obtener icono correcto según tipo', () => {
    expect(component.getTypeIcon('TREND')).toBe('bi-graph-up-arrow');
    expect(component.getTypeIcon('RISK')).toBe('bi-shield-exclamation');
    expect(component.getTypeIcon('ANOMALY')).toBe('bi-exclamation-triangle');
  });

  it('debería mostrar badge de confianza', () => {
    component.insights = mockInsights.slice(0, 1);
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.textContent).toContain('HIGH');
  });
});
