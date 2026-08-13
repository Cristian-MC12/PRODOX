// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { QuickActionsPanelComponent } from './quick-actions-panel.component';

describe('QuickActionsPanelComponent', () => {
  let component: QuickActionsPanelComponent;
  let fixture: ComponentFixture<QuickActionsPanelComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [QuickActionsPanelComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(QuickActionsPanelComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debería crearse', () => {
    expect(component).toBeTruthy();
  });

  it('debería emitir evento al hacer clic en Generar Insights', () => {
    spyOn(component.generateInsightsClick, 'emit');

    const buttons = fixture.nativeElement.querySelectorAll('button');
    const generateButton = Array.from(buttons).find((btn: any) => 
      btn.textContent.includes('Generar Insights')
    ) as HTMLButtonElement;

    generateButton.click();

    expect(component.generateInsightsClick.emit).toHaveBeenCalled();
  });

  it('debería emitir evento al hacer clic en Ver Reportes', () => {
    spyOn(component.viewReportsClick, 'emit');

    const buttons = fixture.nativeElement.querySelectorAll('button');
    const reportButton = Array.from(buttons).find((btn: any) => 
      btn.textContent.includes('Ver Reportes')
    ) as HTMLButtonElement;

    reportButton.click();

    expect(component.viewReportsClick.emit).toHaveBeenCalled();
  });

  it('debería emitir evento al hacer clic en Ver Retrospectivas', () => {
    spyOn(component.viewRetrospectivesClick, 'emit');

    const buttons = fixture.nativeElement.querySelectorAll('button');
    const retroButton = Array.from(buttons).find((btn: any) => 
      btn.textContent.includes('Ver Retrospectivas')
    ) as HTMLButtonElement;

    retroButton.click();

    expect(component.viewRetrospectivesClick.emit).toHaveBeenCalled();
  });

  it('debería emitir evento al hacer clic en Ver Todos los Insights', () => {
    spyOn(component.viewInsightsClick, 'emit');

    const buttons = fixture.nativeElement.querySelectorAll('button');
    const viewAllButton = Array.from(buttons).find((btn: any) => 
      btn.textContent.includes('Ver Todos los Insights')
    ) as HTMLButtonElement;

    viewAllButton.click();

    expect(component.viewInsightsClick.emit).toHaveBeenCalled();
  });

  it('debería tener 4 botones de acción', () => {
    const buttons = fixture.nativeElement.querySelectorAll('button');
    expect(buttons.length).toBe(4);
  });

  it('debería tener iconos en todos los botones', () => {
    const buttonIcons = fixture.nativeElement.querySelectorAll('button i.bi');
    expect(buttonIcons.length).toBe(4);
  });

  it('debería tener aria-labels para accesibilidad', () => {
    const buttons = fixture.nativeElement.querySelectorAll('button[aria-label]');
    expect(buttons.length).toBe(4);
  });
});
