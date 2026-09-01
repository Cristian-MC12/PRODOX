// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { AfterViewChecked, Component, ElementRef, Input, OnChanges, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Chart, registerables, ChartConfiguration } from 'chart.js';

Chart.register(...registerables);

export interface DistributionSegment {
  category: string;
  value: number;
  color: string;
  percentage: number;
}

@Component({
  selector: 'app-metrics-distribution-chart',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './metrics-distribution-chart.component.html',
  styleUrl: './metrics-distribution-chart.component.css'
})
export class MetricsDistributionChartComponent implements OnChanges, AfterViewChecked, OnDestroy {
  @Input() segments: DistributionSegment[] = [];
  @Input() title: string = 'Distribución de métricas';
  @Input() subtitle?: string;

  @ViewChild('canvasRef') canvasRef?: ElementRef<HTMLCanvasElement>;

  total = 0;

  private chart?: Chart<'doughnut'>;
  private pendingRender = false;

  ngOnChanges(): void {
    this.total = this.segments.reduce((sum, s) => sum + s.value, 0);
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
    if (!this.segments || this.segments.length === 0) {
      this.chart?.destroy();
      this.chart = undefined;
      return;
    }

    const canvas = this.canvasRef?.nativeElement;
    if (!canvas) return;

    const config: ChartConfiguration<'doughnut'> = {
      type: 'doughnut',
      data: {
        labels: this.segments.map(s => s.category),
        datasets: [{
          data: this.segments.map(s => s.value),
          backgroundColor: this.segments.map(s => s.color),
          borderColor: '#FFFFFF',
          borderWidth: 2
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '68%',
        plugins: {
          legend: { display: false }, // se mantiene la leyenda HTML existente
          tooltip: {
            callbacks: {
              label: (item) => {
                const segment = this.segments[item.dataIndex];
                return `${segment.category}: ${segment.value} métrica(s) (${segment.percentage}%)`;
              }
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
