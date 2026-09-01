// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetricsEvolutionChartComponent } from './metrics-evolution-chart.component';

describe('MetricsEvolutionChartComponent', () => {
  let component: MetricsEvolutionChartComponent;
  let fixture: ComponentFixture<MetricsEvolutionChartComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MetricsEvolutionChartComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(MetricsEvolutionChartComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show empty state when no data', () => {
    component.dataPoints = [];
    fixture.detectChanges();
    
    const compiled = fixture.nativeElement;
    expect(compiled.querySelector('.empty-chart')).toBeTruthy();
  });

  it('should render chart when data is provided', () => {
    component.dataPoints = [
      { label: 'Sprint 1', value: 7.5 },
      { label: 'Sprint 2', value: 8.0 }
    ];
    component.ngOnChanges();
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.querySelector('canvas')).toBeTruthy();
    expect(compiled.querySelector('.empty-chart')).toBeFalsy();
  });
});
