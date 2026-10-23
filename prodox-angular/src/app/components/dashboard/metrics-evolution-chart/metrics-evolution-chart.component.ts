// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { AfterViewInit, Component, ElementRef, Input, OnChanges, OnDestroy, SimpleChanges, ViewChild } from '@angular/core';
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
export class MetricsEvolutionChartComponent implements OnChanges, AfterViewInit, OnDestroy {
  @Input() dataPoints: DataPoint[] = [];
  @Input() title: string = 'Evolución de métricas';
  @Input() subtitle?: string;

  @ViewChild('canvasRef') canvasRef?: ElementRef<HTMLCanvasElement>;

  private chart?: Chart<'line'>;
  private viewInitialized = false;

  ngAfterViewInit(): void {
    this.viewInitialized = true;
    this.renderChart();
  }

  ngOnChanges(changes: SimpleChanges): void {
    // Solo re-renderizar si ya se inicializó la vista Y los datos cambiaron
    if (this.viewInitialized && changes['dataPoints']) {
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
        animation: false, // Deshabilitado para evitar saltos visuales en updates
        interaction: { mode: 'nearest', intersect: false },
        layout: {
          padding: {
            top: 10,
            right: 10,
            bottom: 10,
            left: 10
          }
        },
        scales: {
          x: {
            grid: { display: false },
            ticks: { 
              color: textSecondary, 
              font: { size: 11 },
              maxRotation: 0,
              autoSkip: true,
              autoSkipPadding: 10
            }
          },
          y: {
            grid: { color: border },
            ticks: { 
              color: textSecondary, 
              font: { size: 11 },
              padding: 5
            }
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
