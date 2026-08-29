// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { AfterViewChecked, Component, ElementRef, Input, OnChanges, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Chart, registerables, ChartConfiguration } from 'chart.js';
import { RegistroPuntoDto } from '../../../models/evaluacion-detalle.model';

Chart.register(...registerables);

/** Frecuencias con un período bien definido, sobre las que tiene sentido agrupar. */
const FRECUENCIAS_CON_PERIODO = new Set(['por_sprint', 'semanal', 'diaria']);

/** Un punto ya agrupado por período (o el registro individual, si la frecuencia no agrupa). */
interface PuntoDeGrafica {
  label:        string;
  valor:        number;
  registradoAt: string;
  sprintNumero: number | null;
}

/**
 * FASE — Evaluación: representación correcta de "datos insuficientes".
 *
 * Antes, tanto la tarjeta de tendencias como el modal de detalle usaban
 * <app-mini-chart> incluso con 1 solo registro: ese componente dibujaba un
 * único punto en el centro de un eje Y artificialmente ensanchado (min-1 /
 * max+1) para evitar dividir por cero — una escala que no representa nada
 * real y no comunica una tendencia (porque no la hay con 1 solo dato).
 *
 * Este componente reemplaza esa gráfica ÚNICAMENTE dentro de Evaluación
 * (no toca shared/mini-chart, que Ejecución también usa) con 3 estados
 * basados exclusivamente en la cantidad real de registros recibidos:
 *   0 registros → estado vacío (ningún gráfico).
 *   1 registro  → tarjeta de dato único (ningún gráfico).
 *   2+ registros → Chart.js Line, con eje Y auto-escalado a los valores
 *                   reales (Chart.js decide el rango, nunca un padding
 *                   arbitrario) y tooltip con fecha + valor + sprint reales.
 * Nunca se inventan, duplican ni interpolan puntos.
 *
 * Corrección Ejecución/Tendencias: los "2+/1/0 registros" de arriba ahora se
 * cuentan sobre PUNTOS AGRUPADOS POR PERÍODO (según frecuenciaCaptura), no
 * sobre registros crudos — dos capturas del mismo sprint/semana/día nunca se
 * dibujan como 2 puntos de una línea de tendencia. El eje X también respeta
 * la frecuencia: "Sprint N" para por_sprint, "Sem N · año" para semanal, y
 * fecha (como antes) para diaria/ilimitada. Ver puntosAgrupados().
 */
@Component({
  selector: 'app-evaluacion-metric-chart',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './metric-chart.component.html',
  styleUrl: './metric-chart.component.css'
})
export class EvaluacionMetricChartComponent implements OnChanges, AfterViewChecked, OnDestroy {
  @Input() registros: RegistroPuntoDto[] = [];
  @Input() frecuenciaLabel = '';
  /** Código crudo de Variable.frecuenciaCaptura (diaria|semanal|por_sprint|ilimitada). */
  @Input() frecuenciaCaptura = '';
  @Input() height = 150;
  /** true = se está viendo un sprint concreto; false = "todos los sprints" (histórico). */
  @Input() sprintEspecifico = true;

  @ViewChild('canvasRef') canvasRef?: ElementRef<HTMLCanvasElement>;

  private chart?: Chart<'line'>;
  private pendingRender = false;

  get registrosOrdenados(): RegistroPuntoDto[] {
    return [...this.registros].sort(
      (a, b) => new Date(a.registradoAt).getTime() - new Date(b.registradoAt).getTime()
    );
  }

  /**
   * Un punto por período (según frecuenciaCaptura), en orden cronológico. Con
   * frecuencia sin período definido (vacía/ilimitada/desconocida) devuelve un
   * punto por registro, igual que antes de esta corrección. Con dos registros
   * del MISMO período, se queda con el más reciente (mismo criterio "vigente"
   * que ya usa el resto del sistema) — nunca duplica el período en el eje X.
   */
  get puntosAgrupados(): PuntoDeGrafica[] {
    const ordenados = this.registrosOrdenados;
    if (!FRECUENCIAS_CON_PERIODO.has(this.frecuenciaCaptura)) {
      return ordenados.map(r => ({
        label: this.formatearFecha(r.registradoAt),
        valor: r.valor,
        registradoAt: r.registradoAt,
        sprintNumero: r.sprintNumero
      }));
    }

    const ultimoPorPeriodo = new Map<string, RegistroPuntoDto>();
    for (const r of ordenados) {
      ultimoPorPeriodo.set(this.clavePeriodo(r), r);
    }
    return [...ultimoPorPeriodo.values()].map(r => ({
      label: this.etiquetaPeriodo(r),
      valor: r.valor,
      registradoAt: r.registradoAt,
      sprintNumero: r.sprintNumero
    }));
  }

  get registroUnico(): PuntoDeGrafica | null {
    const puntos = this.puntosAgrupados;
    return puntos.length === 1 ? puntos[0] : null;
  }

  private clavePeriodo(r: RegistroPuntoDto): string {
    if (this.frecuenciaCaptura === 'por_sprint') return `sprint-${r.sprintId}`;
    if (this.frecuenciaCaptura === 'semanal') return `semana-${this.claveSemanaIso(new Date(r.registradoAt))}`;
    return `dia-${this.claveDiaUtc(new Date(r.registradoAt))}`; // 'diaria'
  }

  private etiquetaPeriodo(r: RegistroPuntoDto): string {
    if (this.frecuenciaCaptura === 'por_sprint') {
      return (r.sprintNumero !== null && r.sprintNumero !== undefined)
        ? `Sprint ${r.sprintNumero}`
        : this.formatearFecha(r.registradoAt);
    }
    if (this.frecuenciaCaptura === 'semanal') {
      const [anio, semana] = this.claveSemanaIso(new Date(r.registradoAt)).split('-W');
      return `Sem ${semana} · ${anio}`;
    }
    return this.formatearFecha(r.registradoAt); // 'diaria'
  }

  private claveDiaUtc(fecha: Date): string {
    return `${fecha.getUTCFullYear()}-${fecha.getUTCMonth()}-${fecha.getUTCDate()}`;
  }

  /** Semana ISO 8601 (jueves de esa semana decide el año), calculada en UTC. */
  private claveSemanaIso(fecha: Date): string {
    const d = new Date(Date.UTC(fecha.getUTCFullYear(), fecha.getUTCMonth(), fecha.getUTCDate()));
    const diaIso = d.getUTCDay() || 7; // domingo (0) -> 7
    d.setUTCDate(d.getUTCDate() + 4 - diaIso);
    const inicioAnio = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
    const semana = Math.ceil((((d.getTime() - inicioAnio.getTime()) / 86400000) + 1) / 7);
    return `${d.getUTCFullYear()}-W${semana}`;
  }

  get mensajeVacio(): string {
    return this.sprintEspecifico
      ? 'Sin registros en este sprint.'
      : 'No hay datos suficientes para mostrar una tendencia.';
  }

  ngOnChanges(): void {
    this.pendingRender = true;
  }

  ngAfterViewChecked(): void {
    if (this.pendingRender) {
      this.pendingRender = false;
      this.renderChart();
    }
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  /** Igual que MiniChartComponent: fechaCaptura se construye en UTC — formatear en UTC evita un día de corrimiento. */
  formatearFecha(fecha: string, incluirHora = false): string {
    const d = new Date(fecha);
    if (Number.isNaN(d.getTime())) return fecha;
    const opciones: Intl.DateTimeFormatOptions = incluirHora
      ? { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: 'UTC' }
      : { day: '2-digit', month: '2-digit', timeZone: 'UTC' };
    return d.toLocaleDateString('es-CO', opciones);
  }

  private renderChart(): void {
    const puntos = this.puntosAgrupados;
    if (puntos.length < 2) {
      this.chart?.destroy();
      this.chart = undefined;
      return;
    }

    const canvas = this.canvasRef?.nativeElement;
    if (!canvas) return;

    const brand = this.cssVar('--bs-primary', '#0E7C86');

    const config: ChartConfiguration<'line'> = {
      type: 'line',
      data: {
        labels: puntos.map(p => p.label),
        datasets: [{
          label: 'Valor',
          data: puntos.map(p => p.valor),
          borderColor: brand,
          backgroundColor: `${brand}26`,
          borderWidth: 2,
          pointRadius: 4,
          pointHoverRadius: 6,
          pointBackgroundColor: '#FFFFFF',
          pointBorderColor: brand,
          pointBorderWidth: 2,
          fill: true,
          tension: 0.2
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'nearest', intersect: false },
        scales: {
          x: {
            ticks: { font: { size: 9 } }
          },
          y: {
            title: { display: true, text: 'Valor', font: { size: 10 } },
            ticks: { font: { size: 9 } }
            // Sin min/max/beginAtZero forzado: Chart.js auto-escala al rango
            // real de los valores — nunca se asume una escala 0-10, 0-100, etc.
          }
        },
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              title: (items) => `Fecha: ${this.formatearFecha(puntos[items[0].dataIndex].registradoAt, true)}`,
              label: (item) => {
                const p = puntos[item.dataIndex];
                const partes = [`Valor: ${item.formattedValue}`];
                if (p.sprintNumero !== null && p.sprintNumero !== undefined) {
                  partes.push(`Sprint: ${p.sprintNumero}`);
                }
                return partes;
              }
            }
          }
        }
      }
    };

    if (this.chart) {
      this.chart.data = config.data;
      this.chart.options = config.options!;
      this.chart.update();
    } else {
      this.chart = new Chart(canvas, config);
    }
  }

  private cssVar(name: string, fallback: string): string {
    const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    return value || fallback;
  }
}
