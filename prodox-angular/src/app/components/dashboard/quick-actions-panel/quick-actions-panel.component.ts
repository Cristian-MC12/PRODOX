// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Component, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Componente presentacional para acciones rápidas.
 * NO navega directamente.
 * Emite eventos que el container manejará.
 */
@Component({
  selector: 'app-quick-actions-panel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './quick-actions-panel.component.html',
  styleUrl: './quick-actions-panel.component.css'
})
export class QuickActionsPanelComponent {
  @Output() generateInsightsClick = new EventEmitter<void>();
  @Output() viewReportsClick = new EventEmitter<void>();
  @Output() viewRetrospectivesClick = new EventEmitter<void>();
  @Output() viewInsightsClick = new EventEmitter<void>();

  onGenerateInsights(): void {
    this.generateInsightsClick.emit();
  }

  onViewReports(): void {
    this.viewReportsClick.emit();
  }

  onViewRetrospectives(): void {
    this.viewRetrospectivesClick.emit();
  }

  onViewInsights(): void {
    this.viewInsightsClick.emit();
  }
}
