// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Componente presentacional para mostrar métricas promedio.
 * NO asume categorías fijas.
 * Acepta cualquier Map<string, number> dinámicamente.
 * NO recalcula valores.
 */
@Component({
  selector: 'app-metrics-overview-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './metrics-overview-card.component.html',
  styleUrl: './metrics-overview-card.component.css'
})
export class MetricsOverviewCardComponent {
  @Input({ required: true }) metricas!: Record<string, number>;

  /**
   * Convierte el objeto a array para usar en ngFor.
   */
  getMetricasArray(): Array<{categoria: string, valor: number}> {
    return Object.entries(this.metricas).map(([categoria, valor]) => ({
      categoria,
      valor
    }));
  }

  /**
   * DS.5 - Interpreta el valor numérico de la métrica.
   * NOTA: Los umbrales son orientativos y genéricos. 
   * No hay una escala oficial definida en MPDIA.
   */
  getMetricInterpretation(valor: number): string {
    if (valor >= 8.5) {
      return '🟢 Excelente';
    } else if (valor >= 7.0) {
      return '🔵 Bueno';
    } else if (valor >= 5.5) {
      return '🟡 Regular';
    } else if (valor >= 4.0) {
      return '🟠 Necesita atención';
    } else {
      return '🔴 Crítico';
    }
  }
}
