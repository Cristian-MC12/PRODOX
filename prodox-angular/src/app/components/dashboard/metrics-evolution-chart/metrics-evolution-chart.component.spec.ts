// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SimpleChange } from '@angular/core';
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
    const newData = [
      { label: 'Sprint 1', value: 7.5 },
      { label: 'Sprint 2', value: 8.0 }
    ];
    component.dataPoints = newData;
    component.ngOnChanges({
      dataPoints: new SimpleChange(null, newData, false)
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.querySelector('canvas')).toBeTruthy();
    expect(compiled.querySelector('.empty-chart')).toBeFalsy();
  });
});
