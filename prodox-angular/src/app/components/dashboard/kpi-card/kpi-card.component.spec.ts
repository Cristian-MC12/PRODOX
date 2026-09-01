// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { KpiCardComponent } from './kpi-card.component';

describe('KpiCardComponent', () => {
  let component: KpiCardComponent;
  let fixture: ComponentFixture<KpiCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [KpiCardComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(KpiCardComponent);
    component = fixture.componentInstance;
    
    // Set required inputs
    component.title = 'Test KPI';
    component.value = 100;
    component.icon = 'bi-graph-up';
    
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display title and value', () => {
    const compiled = fixture.nativeElement;
    expect(compiled.querySelector('.kpi-title').textContent).toContain('Test KPI');
    expect(compiled.querySelector('.kpi-value').textContent).toContain('100');
  });

  it('should display icon', () => {
    const compiled = fixture.nativeElement;
    const icon = compiled.querySelector('.kpi-card-icon i');
    expect(icon.classList.contains('bi-graph-up')).toBeTruthy();
  });

  it('should display trend when provided', () => {
    component.trend = 'up';
    component.trendValue = '+5%';
    fixture.detectChanges();
    
    const compiled = fixture.nativeElement;
    const trend = compiled.querySelector('.kpi-trend');
    expect(trend).toBeTruthy();
    expect(trend.textContent).toContain('+5%');
  });
});
