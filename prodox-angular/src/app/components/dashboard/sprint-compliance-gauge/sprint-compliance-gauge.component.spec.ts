// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SprintComplianceGaugeComponent } from './sprint-compliance-gauge.component';

describe('SprintComplianceGaugeComponent', () => {
  let component: SprintComplianceGaugeComponent;
  let fixture: ComponentFixture<SprintComplianceGaugeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SprintComplianceGaugeComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(SprintComplianceGaugeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display percentage', () => {
    component.percentage = 75;
    fixture.detectChanges();
    
    const compiled = fixture.nativeElement;
    expect(compiled.querySelector('.gauge-percentage').textContent).toContain('75');
  });

  it('should calculate correct status color for high percentage', () => {
    component.percentage = 90;
    expect(component.getStatusColor()).toBe('#1E8E5A');
  });

  it('should calculate correct status color for low percentage', () => {
    component.percentage = 30;
    expect(component.getStatusColor()).toBe('#C23B34');
  });

  it('should display correct status label', () => {
    component.percentage = 85;
    expect(component.getStatusLabel()).toBe('Excelente');
    
    component.percentage = 50;
    expect(component.getStatusLabel()).toBe('Regular');
  });
});
