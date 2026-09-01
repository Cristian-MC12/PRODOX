// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { AIReportService } from '../../../services/ai-report.service';
import { SprintService } from '../../../services/sprint.service';
import { ProyectoDto } from '../../../models/proyecto.model';
import { SprintDto } from '../../../models/sprint.model';
import { AIRetrospective } from '../../../models/ai-reports.model';

/**
 * Panel de Retrospectivas embebido en Dashboard / Análisis IA.
 *
 * Reorganización de navegación: Retrospectivas dejó de tener entrada propia
 * en el menú lateral; este panel es el nuevo punto de acceso, integrado en
 * Dashboard. Reutiliza exactamente el mismo AIReportService.generateRetrospective()
 * y el mismo manejo de errores (400/403/429/503) que ya tenía la página
 * independiente /ai-retrospective (que se mantiene intacta y accesible por URL).
 *
 * NO genera nada automáticamente: la retrospectiva solo se genera cuando el
 * usuario presiona "Generar Retrospectiva" explícitamente.
 */
@Component({
  selector: 'app-retrospective-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './retrospective-panel.component.html',
  styleUrl: './retrospective-panel.component.css'
})
export class RetrospectivePanelComponent implements OnInit {

  proyecto: ProyectoDto | null = null;
  sprints: SprintDto[] = [];
  selectedSprintId = '';
  retrospective: AIRetrospective | null = null;

  loading = signal(false);
  generating = signal(false);
  alertMsg = signal('');
  alertClass = signal('alert-info');

  constructor(
    public router: Router,
    private reportService: AIReportService,
    private sprintService: SprintService
  ) {}

  ngOnInit(): void {
    const raw = localStorage.getItem('mpdia_proyecto_activo');
    this.proyecto = raw ? JSON.parse(raw) : null;

    if (this.proyecto) {
      this.loadSprints();
    }
  }

  loadSprints(): void {
    if (!this.proyecto) return;

    this.loading.set(true);

    this.sprintService.listar(this.proyecto.id)
      .pipe(
        catchError(err => {
          console.error('Error cargando sprints:', err);
          return of([]);
        })
      )
      .subscribe((data: SprintDto[]) => {
        // Solo sprints finalizados pueden tener retrospectiva.
        this.sprints = data.filter((s: SprintDto) => s.estado === 'finalizado');
        this.loading.set(false);
      });
  }

  generateRetrospective(): void {
    if (!this.selectedSprintId || this.generating()) return;

    this.generating.set(true);
    this.alertMsg.set('');
    this.retrospective = null;
    let hadError = false;

    this.reportService.generateRetrospective(this.selectedSprintId)
      .pipe(
        catchError(err => {
          console.error('Error generando retrospectiva:', err);
          hadError = true;

          if (err.status === 400) {
            this.showAlert('Sprint no encontrado', 'alert-danger');
          } else if (err.status === 403) {
            this.showAlert('No tienes permisos para generar retrospectivas en este proyecto', 'alert-danger');
          } else if (err.status === 429) {
            this.showAlert('Has alcanzado temporalmente el límite de generación. Intenta nuevamente más tarde.', 'alert-warning');
          } else if (err.status === 503) {
            // Mismo criterio que ai-retrospective.component.ts: Gemini no
            // disponible -> mensaje controlado, nunca contenido falso.
            this.showAlert(err.error?.error || 'El servicio de IA no está disponible en este momento. Intenta nuevamente en unos segundos.', 'alert-warning');
          } else {
            this.showAlert('Error al generar la retrospectiva. Intentá nuevamente.', 'alert-danger');
          }

          return of(null);
        })
      )
      .subscribe(data => {
        this.generating.set(false);

        if (!hadError && data) {
          this.retrospective = data;
          this.showAlert('Retrospectiva generada exitosamente', 'alert-success');
        }
      });
  }

  hasInsufficientData(): boolean {
    if (!this.retrospective) return false;
    return this.retrospective.whatWentWell.some(item =>
      item.includes('Datos insuficientes') || item.includes('datos insuficientes')
    );
  }

  isFirstSprint(): boolean {
    if (!this.retrospective) return false;
    return this.retrospective.whatWentWell.some(item =>
      item.toLowerCase().includes('primer sprint')
    ) || this.retrospective.whatCouldImprove.some(item =>
      item.toLowerCase().includes('primer sprint')
    );
  }

  private showAlert(message: string, cssClass: string): void {
    this.alertMsg.set(message);
    this.alertClass.set(cssClass);
    setTimeout(() => this.alertMsg.set(''), 5000);
  }
}
