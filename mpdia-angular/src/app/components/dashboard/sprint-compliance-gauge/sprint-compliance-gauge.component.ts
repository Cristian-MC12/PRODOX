// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { AfterViewChecked, Component, ElementRef, Input, OnChanges, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Chart, registerables, ChartConfiguration } from 'chart.js';

Chart.register(...registerables);

@Component({
  selector: 'app-sprint-compliance-gauge',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './sprint-compliance-gauge.component.html',
  styleUrl: './sprint-compliance-gauge.component.css'
})
export class SprintComplianceGaugeComponent implements OnChanges, AfterViewChecked, OnDestroy {
  @Input() percentage: number = 0;
  @Input() label: string = 'Cumplimiento';
  @Input() subtitle?: string;

  @ViewChild('canvasRef') canvasRef?: ElementRef<HTMLCanvasElement>;

  private chart?: Chart<'doughnut'>;
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

  /**
   * Porcentaje seguro para mostrar: nunca NaN/Infinity/negativo/>100,
   * independientemente de lo que llegue por @Input (defensa adicional —
   * el cálculo real vive en DashboardComponent.getSprintCompliance()).
   */
  get displayPercentage(): number {
    if (!Number.isFinite(this.percentage)) return 0;
    return Math.max(0, Math.min(100, this.percentage));
  }

  getStatusColor(): string {
    const p = this.displayPercentage;
    if (p >= 85) return '#1E8E5A';
    if (p >= 70) return '#5A96C4';
    if (p >= 50) return '#D99A2B';
    return '#C23B34';
  }

  getStatusLabel(): string {
    const p = this.displayPercentage;
    if (p >= 85) return 'Excelente';
    if (p >= 70) return 'Bueno';
    if (p >= 50) return 'Regular';
    return 'Requiere atención';
  }

  private renderChart(): void {
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas) return;

    const p = this.displayPercentage;
    const color = this.getStatusColor();
    const statusLabel = this.getStatusLabel();

    const config: ChartConfiguration<'doughnut'> = {
      type: 'doughnut',
      data: {
        labels: [this.label || 'Cumplimiento', ''],
        datasets: [{
          data: [p, 100 - p],
          backgroundColor: [color, '#E3F0F0'],
          borderWidth: 0,
          borderRadius: p > 0 && p < 100 ? 8 : 0
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '82%',
        rotation: -90,
        circumference: 360,
        plugins: {
          legend: { display: false },
          tooltip: {
            // Solo la porción real (índice 0) tiene información útil — el
            // resto del anillo (100-p) es relleno visual, no un dato.
            filter: (item) => item.dataIndex === 0,
            callbacks: {
              label: () => `${this.label || 'Cumplimiento'}: ${p}% — ${statusLabel}`
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
}
