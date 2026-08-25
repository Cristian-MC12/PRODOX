// Autor: Cristian Santiago Martinez Cordoba — MPDIA
// FASE 16 — Tests de MiniChartComponent
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MiniChartComponent, PuntoMiniChart } from './mini-chart.component';

describe('MiniChartComponent', () => {
  let component: MiniChartComponent;
  let fixture: ComponentFixture<MiniChartComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MiniChartComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(MiniChartComponent);
    component = fixture.componentInstance;
  });

  /**
   * Los tests asignan `puntos` directamente sobre la instancia (sin pasar por
   * un binding de plantilla del padre), así que Angular no dispara
   * ngOnChanges() por sí solo — hay que invocarlo explícitamente, igual que
   * ocurriría al reasignar el @Input desde un componente contenedor real.
   */
  function setPuntos(puntos: PuntoMiniChart[]): void {
    component.puntos = puntos;
    component.ngOnChanges({ puntos: {} as any });
  }

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('sin puntos, muestra el mensaje "Sin registros todavía"', () => {
    setPuntos([]);
    fixture.detectChanges();
    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Sin registros todavía');
  });

  it('con puntos reales, dibuja exactamente un círculo por registro', () => {
    setPuntos([
      { fecha: '2026-08-21T00:00:00Z', valor: 7 },
      { fecha: '2026-08-22T00:00:00Z', valor: 8 },
      { fecha: '2026-08-23T00:00:00Z', valor: 6 }
    ]);
    fixture.detectChanges();

    const circulos = fixture.nativeElement.querySelectorAll('circle');
    expect(circulos.length).toBe(3);
  });

  it('dibuja los ejes X e Y con al menos una línea cada uno', () => {
    setPuntos([{ fecha: '2026-08-21T00:00:00Z', valor: 5 }]);
    fixture.detectChanges();

    const lineas = fixture.nativeElement.querySelectorAll('line');
    expect(lineas.length).toBeGreaterThanOrEqual(2); // eje X + eje Y (más ticks)
  });

  it('muestra numeración visible en el eje Y (ticks con valores)', () => {
    setPuntos([
      { fecha: '2026-08-21T00:00:00Z', valor: 10 },
      { fecha: '2026-08-22T00:00:00Z', valor: 20 }
    ]);
    fixture.detectChanges();

    expect(component.ticksY.length).toBeGreaterThan(0);
    const textos = Array.from(fixture.nativeElement.querySelectorAll('text')) as HTMLElement[];
    expect(textos.length).toBeGreaterThan(0);
  });

  it('los puntos se ordenan cronológicamente aunque lleguen desordenados', () => {
    setPuntos([
      { fecha: '2026-08-23T00:00:00Z', valor: 6 },
      { fecha: '2026-08-21T00:00:00Z', valor: 7 },
      { fecha: '2026-08-22T00:00:00Z', valor: 8 }
    ]);
    fixture.detectChanges();

    expect(component.puntosDibujados.map(p => p.valor)).toEqual([7, 8, 6]);
  });

  it('al agregar un nuevo punto, la gráfica se actualiza (más círculos)', () => {
    setPuntos([{ fecha: '2026-08-21T00:00:00Z', valor: 7 }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('circle').length).toBe(1);

    setPuntos([
      { fecha: '2026-08-21T00:00:00Z', valor: 7 },
      { fecha: '2026-08-22T00:00:00Z', valor: 8 }
    ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('circle').length).toBe(2);
  });
});
