// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetricsDistributionChartComponent } from './metrics-distribution-chart.component';

describe('MetricsDistributionChartComponent', () => {
  let component: MetricsDistributionChartComponent;
  let fixture: ComponentFixture<MetricsDistributionChartComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MetricsDistributionChartComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(MetricsDistributionChartComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show empty state when no segments', () => {
    component.segments = [];
    fixture.detectChanges();
    
    const compiled = fixture.nativeElement;
    expect(compiled.querySelector('.empty-chart')).toBeTruthy();
  });

  it('should calculate total correctly', () => {
    component.segments = [
      { category: 'Calidad', value: 5, color: '#0E7C86', percentage: 50 },
      { category: 'Productividad', value: 5, color: '#5A96C4', percentage: 50 }
    ];
    component.ngOnChanges();
    
    expect(component.total).toBe(10);
  });

  it('should render chart and center total when data is provided', () => {
    component.segments = [
      { category: 'Calidad', value: 3, color: '#0E7C86', percentage: 100 }
    ];
    component.ngOnChanges();
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.querySelector('canvas')).toBeTruthy();
    expect(compiled.querySelector('.donut-center-text').textContent).toContain('3');
  });
});
