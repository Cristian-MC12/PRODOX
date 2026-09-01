// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-kpi-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './kpi-card.component.html',
  styleUrl: './kpi-card.component.css'
})
export class KpiCardComponent {
  @Input({ required: true }) title!: string;
  @Input({ required: true }) value!: number | string;
  @Input({ required: true }) icon!: string;
  @Input() subtitle?: string;
  @Input() trend?: 'up' | 'down' | 'neutral';
  @Input() trendValue?: string;
  @Input() variant: 'primary' | 'danger' | 'success' | 'info' = 'primary';
}
