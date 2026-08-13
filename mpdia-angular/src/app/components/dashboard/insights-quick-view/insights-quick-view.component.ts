// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AIInsight } from '../../../models/ai-insights.model';

/**
 * Componente presentacional para mostrar insights recientes.
 * Muestra máximo 5 insights.
 * NO genera insights.
 * NO llama Gemini.
 * Emite evento para navegación.
 */
@Component({
  selector: 'app-insights-quick-view',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './insights-quick-view.component.html',
  styleUrl: './insights-quick-view.component.css'
})
export class InsightsQuickViewComponent {
  @Input({ required: true }) insights!: AIInsight[];
  @Output() viewAllClick = new EventEmitter<void>();

  /**
   * Obtiene clase según tipo de insight.
   */
  getTypeClass(type: string): string {
    switch (type) {
      case 'RISK':
        return 'border-danger';
      case 'ANOMALY':
        return 'border-warning';
      case 'TREND':
        return 'border-info';
      case 'COMPARISON':
        return 'border-primary';
      default:
        return 'border-secondary';
    }
  }

  /**
   * Obtiene badge class según severidad.
   */
  getSeverityBadge(severity: string): string {
    switch (severity) {
      case 'CRITICAL':
        return 'bg-danger';
      case 'HIGH':
        return 'bg-warning text-dark';
      case 'MEDIUM':
        return 'bg-info text-dark';
      case 'LOW':
        return 'bg-secondary';
      default:
        return 'bg-secondary';
    }
  }

  /**
   * Obtiene icono según tipo.
   */
  getTypeIcon(type: string): string {
    switch (type) {
      case 'TREND':
        return 'bi-graph-up-arrow';
      case 'ANOMALY':
        return 'bi-exclamation-triangle';
      case 'RISK':
        return 'bi-shield-exclamation';
      case 'COMPARISON':
        return 'bi-arrow-left-right';
      default:
        return 'bi-lightbulb';
    }
  }

  /**
   * Obtiene label traducido según tipo.
   */
  getTypeLabel(type: string): string {
    switch (type) {
      case 'TREND':
        return 'Tendencia';
      case 'ANOMALY':
        return 'Anomalía';
      case 'RISK':
        return 'Riesgo';
      case 'COMPARISON':
        return 'Comparación';
      default:
        return type;
    }
  }

  onViewAllClick(): void {
    this.viewAllClick.emit();
  }
}
