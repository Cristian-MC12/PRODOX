// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Risk } from '../../../models/analytics.model';

/**
 * Componente presentacional para mostrar riesgos detectados.
 * NO genera riesgos.
 * NO recalcula severidad.
 * Solo presenta datos del backend.
 */
@Component({
  selector: 'app-risks-alerts-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './risks-alerts-card.component.html',
  styleUrl: './risks-alerts-card.component.css'
})
export class RisksAlertsCardComponent {
  @Input({ required: true }) risks!: Risk[];

  /**
   * Obtiene clase CSS según severidad.
   * Accesibilidad: NO depende solo del color.
   */
  getSeverityClass(severidad: string): string {
    switch (severidad) {
      case 'CRITICAL':
      case 'HIGH':
        return 'alert-danger';
      case 'MEDIUM':
        return 'alert-warning';
      case 'LOW':
        return 'alert-info';
      default:
        return 'alert-secondary';
    }
  }

  /**
   * Obtiene badge class según severidad.
   */
  getSeverityBadgeClass(severidad: string): string {
    switch (severidad) {
      case 'CRITICAL':
      case 'HIGH':
        return 'bg-danger';
      case 'MEDIUM':
        return 'bg-warning text-dark';
      case 'LOW':
        return 'bg-info text-dark';
      default:
        return 'bg-secondary';
    }
  }

  /**
   * Obtiene icono según severidad.
   * Accesibilidad: Información visual adicional.
   */
  getSeverityIcon(severidad: string): string {
    switch (severidad) {
      case 'CRITICAL':
        return 'bi-exclamation-octagon-fill';
      case 'HIGH':
        return 'bi-exclamation-triangle-fill';
      case 'MEDIUM':
        return 'bi-exclamation-circle-fill';
      case 'LOW':
        return 'bi-info-circle-fill';
      default:
        return 'bi-info-circle';
    }
  }

  /**
   * DS.9 - Genera acción sugerida basada en el tipo de riesgo.
   * NO inventa acciones específicas, solo orientaciones generales.
   */
  getSuggestedAction(tipo: string): string {
    switch (tipo) {
      case 'DECLINING_METRIC':
        return 'Revisar causas del descenso en la retrospectiva del sprint';
      case 'HIGH_VARIABILITY':
        return 'Analizar factores de variabilidad en la planificación';
      case 'STAGNANT_PROGRESS':
        return 'Evaluar bloqueos y dependencias del equipo';
      case 'LOW_TEAM_SATISFACTION':
        return 'Discutir mejoras en la dinámica del equipo';
      case 'QUALITY_ISSUES':
        return 'Reforzar prácticas de calidad y revisión de código';
      case 'PRODUCTIVITY_DROP':
        return 'Identificar impedimentos y optimizar el flujo de trabajo';
      default:
        return 'Revisar métricas relacionadas y discutir en equipo';
    }
  }
}
