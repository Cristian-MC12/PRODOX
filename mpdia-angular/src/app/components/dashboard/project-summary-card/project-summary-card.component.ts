// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ProjectOverview } from '../../../models/analytics.model';

/**
 * Componente presentacional para mostrar resumen del proyecto.
 * NO hace HTTP requests.
 * NO calcula métricas.
 * Solo presenta datos recibidos vía Input.
 */
@Component({
  selector: 'app-project-summary-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './project-summary-card.component.html',
  styleUrl: './project-summary-card.component.css'
})
export class ProjectSummaryCardComponent {
  @Input({ required: true }) overview!: ProjectOverview;
}
