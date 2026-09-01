// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EvaluacionMetricChartComponent } from './metric-chart.component';
import { RegistroPuntoDto } from '../../../models/evaluacion-detalle.model';

function registro(overrides: Partial<RegistroPuntoDto> = {}): RegistroPuntoDto {
  return {
    id: 'r1', valor: 7, registradoAt: '2026-08-21T00:00:00Z',
    sprintId: 's1', sprintNumero: 1, userId: 'sm@test.com',
    ...overrides
  };
}

describe('EvaluacionMetricChartComponent', () => {
  let component: EvaluacionMetricChartComponent;
  let fixture: ComponentFixture<EvaluacionMetricChartComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EvaluacionMetricChartComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(EvaluacionMetricChartComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  // ── 0 registros ──────────────────────────────────────────────────────

  it('0 registros: muestra estado vacío y NO renderiza canvas', () => {
    component.registros = [];
    component.sprintEspecifico = true;
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.querySelector('canvas')).toBeFalsy();
    expect(compiled.textContent).toContain('Sin registros en este sprint.');
  });

  it('0 registros viendo histórico (todos los sprints): mensaje de tendencia, no de sprint', () => {
    component.registros = [];
    component.sprintEspecifico = false;
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No hay datos suficientes para mostrar una tendencia.');
  });

  // ── 1 registro ───────────────────────────────────────────────────────

  it('1 registro: NO renderiza canvas, muestra tarjeta de dato único con valor/fecha/frecuencia reales', () => {
    const r = registro({ valor: 42, registradoAt: '2026-08-21T10:30:00Z' });
    component.registros = [r];
    component.frecuenciaLabel = 'Por sprint';
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.querySelector('canvas')).toBeFalsy();
    expect(compiled.textContent).toContain('42'); // valor real, no inventado
    expect(compiled.textContent).toContain('1 registro');
    expect(compiled.textContent).toContain('Por sprint');
    // La fecha mostrada corresponde al registro real (21/08/2026, UTC).
    expect(compiled.textContent).toContain('21/08/2026');
  });

  // ── 2+ registros ─────────────────────────────────────────────────────

  it('2 registros: renderiza Chart.js con exactamente los 2 puntos reales (sin duplicar ni inventar)', () => {
    component.registros = [
      registro({ id: 'r1', valor: 5, registradoAt: '2026-08-21T00:00:00Z', sprintNumero: 1 }),
      registro({ id: 'r2', valor: 9, registradoAt: '2026-08-22T00:00:00Z', sprintNumero: 1 })
    ];
    component.ngOnChanges();
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.querySelector('canvas')).toBeTruthy();

    const chart = (component as any).chart;
    expect(chart).toBeTruthy();
    const datosGraficados: number[] = chart.data.datasets[0].data;
    expect(datosGraficados.length).toBe(2); // ni menos ni más que los registros reales
    expect(datosGraficados).toEqual([5, 9]); // valores reales, en el mismo orden cronológico
  });

  it('el tooltip usa fecha y valor reales (y sprint si está disponible) — no texto genérico', () => {
    component.registros = [
      registro({ id: 'r1', valor: 5, registradoAt: '2026-08-21T00:00:00Z', sprintNumero: 3 }),
      registro({ id: 'r2', valor: 9, registradoAt: '2026-08-22T00:00:00Z', sprintNumero: 3 })
    ];
    component.ngOnChanges();
    fixture.detectChanges();

    const chart = (component as any).chart;
    const tooltipCallbacks = chart.options.plugins.tooltip.callbacks;

    const tituloTooltip = tooltipCallbacks.title([{ dataIndex: 0 }]);
    expect(tituloTooltip).toContain('Fecha');
    expect(tituloTooltip).toContain('21/08/2026');

    const labelTooltip = tooltipCallbacks.label({ dataIndex: 1, formattedValue: '9' });
    expect(labelTooltip).toContain('Valor: 9');
    expect(labelTooltip).toContain('Sprint: 3');
  });

  it('no inventa puntos: cambiar de 2 a 3 registros reales actualiza el chart a exactamente 3 puntos', () => {
    component.registros = [
      registro({ id: 'r1', valor: 5, registradoAt: '2026-08-21T00:00:00Z' }),
      registro({ id: 'r2', valor: 9, registradoAt: '2026-08-22T00:00:00Z' })
    ];
    component.ngOnChanges();
    fixture.detectChanges();

    component.registros = [
      registro({ id: 'r1', valor: 5, registradoAt: '2026-08-21T00:00:00Z' }),
      registro({ id: 'r2', valor: 9, registradoAt: '2026-08-22T00:00:00Z' }),
      registro({ id: 'r3', valor: 3, registradoAt: '2026-08-23T00:00:00Z' })
    ];
    component.ngOnChanges();
    fixture.detectChanges();

    const chart = (component as any).chart;
    expect(chart.data.datasets[0].data).toEqual([5, 9, 3]);
  });

  it('el eje Y no fuerza una escala arbitraria (sin min/max/beginAtZero fijo)', () => {
    component.registros = [
      registro({ id: 'r1', valor: 500, registradoAt: '2026-08-21T00:00:00Z' }),
      registro({ id: 'r2', valor: 900, registradoAt: '2026-08-22T00:00:00Z' })
    ];
    component.ngOnChanges();
    fixture.detectChanges();

    const chart = (component as any).chart;
    const yScale = chart.options.scales.y;
    // min/max ausentes = Chart.js auto-escala al rango real de los datos, sin
    // ningún límite artificial impuesto por este componente. beginAtZero es
    // 'false' porque es el default nativo de Chart.js (nunca se sobreescribe).
    expect(yScale.min).toBeUndefined();
    expect(yScale.max).toBeUndefined();
    expect(yScale.beginAtZero).toBeFalse();
    expect(yScale.title.text).toBe('Valor');
  });

  it('al bajar de 2 a 1 registro, destruye el chart y no deja un canvas huérfano', () => {
    component.registros = [
      registro({ id: 'r1', valor: 5, registradoAt: '2026-08-21T00:00:00Z' }),
      registro({ id: 'r2', valor: 9, registradoAt: '2026-08-22T00:00:00Z' })
    ];
    component.ngOnChanges();
    fixture.detectChanges();
    expect((component as any).chart).toBeTruthy();

    component.registros = [registro({ id: 'r1', valor: 5, registradoAt: '2026-08-21T00:00:00Z' })];
    component.ngOnChanges();
    fixture.detectChanges();

    expect((component as any).chart).toBeUndefined();
    expect(fixture.nativeElement.querySelector('canvas')).toBeFalsy();
  });

  // ── Corrección Ejecución/Tendencias: agrupamiento por período según frecuencia ──

  describe('frecuenciaCaptura=por_sprint: cada punto es un sprint, no un registro', () => {
    it('2 registros del MISMO sprint se agrupan en 1 solo punto: no dibuja línea (G, A)', () => {
      component.frecuenciaCaptura = 'por_sprint';
      component.registros = [
        registro({ id: 'r1', valor: 5, registradoAt: '2026-08-21T00:00:00Z', sprintId: 's1', sprintNumero: 5 }),
        registro({ id: 'r2', valor: 7, registradoAt: '2026-08-22T00:00:00Z', sprintId: 's1', sprintNumero: 5 })
      ];
      component.ngOnChanges();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('canvas')).toBeFalsy();
      expect((component as any).chart).toBeUndefined();
      expect(fixture.nativeElement.textContent).toContain('1 registro');
      // valor representativo del período: el más reciente (7, no 5)
      expect(fixture.nativeElement.textContent).toContain('7');
    });

    it('2 registros de sprints DISTINTOS: sí dibuja línea, eje X con "Sprint N" (G, B)', () => {
      component.frecuenciaCaptura = 'por_sprint';
      component.registros = [
        registro({ id: 'r1', valor: 5, registradoAt: '2026-08-21T00:00:00Z', sprintId: 's1', sprintNumero: 1 }),
        registro({ id: 'r2', valor: 7, registradoAt: '2026-08-22T00:00:00Z', sprintId: 's2', sprintNumero: 2 })
      ];
      component.ngOnChanges();
      fixture.detectChanges();

      const chart = (component as any).chart;
      expect(chart).toBeTruthy();
      expect(chart.data.labels).toEqual(['Sprint 1', 'Sprint 2']);
      expect(chart.data.datasets[0].data).toEqual([5, 7]);
    });
  });

  describe('frecuenciaCaptura=semanal: cada punto es una semana ISO', () => {
    it('2 registros de la MISMA semana ISO se agrupan en 1 punto: no dibuja línea (G, C)', () => {
      component.frecuenciaCaptura = 'semanal';
      component.registros = [
        // 2026-08-03 (lunes) y 2026-08-05 (miércoles): misma semana ISO.
        registro({ id: 'r1', valor: 3, registradoAt: '2026-08-03T00:00:00Z' }),
        registro({ id: 'r2', valor: 6, registradoAt: '2026-08-05T00:00:00Z' })
      ];
      component.ngOnChanges();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('canvas')).toBeFalsy();
      expect((component as any).chart).toBeUndefined();
    });

    it('2 registros de semanas ISO DISTINTAS: sí dibuja línea, eje X con "Sem N · año" (G, D)', () => {
      component.frecuenciaCaptura = 'semanal';
      component.registros = [
        registro({ id: 'r1', valor: 3, registradoAt: '2026-08-03T00:00:00Z' }), // semana ISO 32
        registro({ id: 'r2', valor: 6, registradoAt: '2026-08-11T00:00:00Z' })  // semana ISO 33
      ];
      component.ngOnChanges();
      fixture.detectChanges();

      const chart = (component as any).chart;
      expect(chart).toBeTruthy();
      expect(chart.data.labels).toEqual(['Sem 32 · 2026', 'Sem 33 · 2026']);
      expect(chart.data.datasets[0].data).toEqual([3, 6]);
    });
  });

  describe('frecuenciaCaptura=diaria: cada punto es un día', () => {
    it('2 registros del MISMO día se agrupan en 1 punto: no dibuja línea (G, E)', () => {
      component.frecuenciaCaptura = 'diaria';
      component.registros = [
        registro({ id: 'r1', valor: 2, registradoAt: '2026-08-03T08:00:00Z' }),
        registro({ id: 'r2', valor: 5, registradoAt: '2026-08-03T18:00:00Z' })
      ];
      component.ngOnChanges();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('canvas')).toBeFalsy();
      expect((component as any).chart).toBeUndefined();
    });

    it('2 registros de días DISTINTOS: sí dibuja línea, comparando días (G, F)', () => {
      component.frecuenciaCaptura = 'diaria';
      component.registros = [
        registro({ id: 'r1', valor: 2, registradoAt: '2026-08-03T00:00:00Z' }),
        registro({ id: 'r2', valor: 5, registradoAt: '2026-08-04T00:00:00Z' })
      ];
      component.ngOnChanges();
      fixture.detectChanges();

      const chart = (component as any).chart;
      expect(chart).toBeTruthy();
      expect(chart.data.datasets[0].data).toEqual([2, 5]);
    });
  });

  it('sin frecuenciaCaptura (o "ilimitada"): NO agrupa, cada registro es su propio punto aunque compartan sprint/día', () => {
    component.registros = [
      registro({ id: 'r1', valor: 5, registradoAt: '2026-08-21T00:00:00Z', sprintId: 's1', sprintNumero: 1 }),
      registro({ id: 'r2', valor: 9, registradoAt: '2026-08-21T12:00:00Z', sprintId: 's1', sprintNumero: 1 })
    ];
    component.ngOnChanges();
    fixture.detectChanges();

    const chart = (component as any).chart;
    expect(chart).toBeTruthy();
    expect(chart.data.datasets[0].data).toEqual([5, 9]);
  });
});
