// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { AfterViewChecked, Component, ElementRef, Input, OnChanges, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Chart, registerables, ChartConfiguration } from 'chart.js';

Chart.register(...registerables);

interface DataPoint {
  label: string;
  value: number;
}

/** Lee una variable CSS ya definida en styles.scss, con fallback si no está disponible. */
function cssVar(name: string, fallback: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value || fallback;
}

@Component({
  selector: 'app-metrics-evolution-chart',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './metrics-evolution-chart.component.html',
  styleUrl: './metrics-evolution-chart.component.css'
})
export class MetricsEvolutionChartComponent implements OnChanges, AfterViewChecked, OnDestroy {
  @Input() dataPoints: DataPoint[] = [];
  @Input() title: string = 'Evolución de métricas';
  @Input() subtitle?: string;

  @ViewChild('canvasRef') canvasRef?: ElementRef<HTMLCanvasElement>;

  private chart?: Chart<'line'>;
  // El @if del template puede mostrar/ocultar el <canvas> según dataPoints;
  // @ViewChild solo queda actualizado DESPUÉS de que la vista se revisa, así
  // que el render real se pospone a ngAfterViewChecked (nunca a ngOnChanges,
  // donde el canvas todavía podría no existir en el DOM).
  private pendingRender = false;

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

  private renderChart(): void {
    if (!this.dataPoints || this.dataPoints.length === 0) {
      this.chart?.destroy();
      this.chart = undefined;
      return;
    }

    const canvas = this.canvasRef?.nativeElement;
    if (!canvas) return;

    const brand = cssVar('--brand', '#0E7C86');
    const textSecondary = cssVar('--text-secondary', '#5B6B70');
    const border = cssVar('--border', '#D9DEDC');

    const config: ChartConfiguration<'line'> = {
      type: 'line',
      data: {
        labels: this.dataPoints.map(d => d.label),
        datasets: [{
          label: 'Valor',
          data: this.dataPoints.map(d => d.value),
          borderColor: brand,
          backgroundColor: `${brand}26`, // ~15% opacidad, mismo tono que el área SVG anterior
          borderWidth: 2.5,
          pointBackgroundColor: '#FFFFFF',
          pointBorderColor: brand,
          pointBorderWidth: 2.5,
          pointRadius: 4,
          pointHoverRadius: 6,
          fill: true,
          tension: 0.3
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'nearest', intersect: false },
        scales: {
          x: {
            grid: { display: false },
            ticks: { color: textSecondary, font: { size: 11 } }
          },
          y: {
            grid: { color: border },
            ticks: { color: textSecondary, font: { size: 11 } }
          }
        },
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              title: (items) => `Sprint: ${items[0]?.label ?? ''}`,
              label: (item) => `Valor real: ${item.formattedValue}`
            }
          }
        }
      }
    };

    if (this.chart) {
      this.chart.data = config.data;
      this.chart.update();
    } else {
      this.chart = new Chart(canvas, config);
    }
  }
}
