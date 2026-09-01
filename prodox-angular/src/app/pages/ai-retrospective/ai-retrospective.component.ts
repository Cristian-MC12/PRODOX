// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ShellComponent } from '../../layout/shell/shell.component';
import { AIReportService } from '../../services/ai-report.service';
import { SprintService } from '../../services/sprint.service';
import { ProyectoDto } from '../../models/proyecto.model';
import { SprintDto } from '../../models/sprint.model';
import { AIRetrospective } from '../../models/ai-reports.model';

@Component({
  selector: 'app-ai-retrospective',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  templateUrl: './ai-retrospective.component.html',
  styleUrl: './ai-retrospective.component.css'
})
export class AIRetrospectiveComponent implements OnInit {

  proyecto: ProyectoDto | null = null;
  sprints: SprintDto[] = [];
  selectedSprintId: string = '';
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
    this.alertMsg.set('');

    this.sprintService.listar(this.proyecto.id)
      .pipe(
        catchError(err => {
          console.error('Error cargando sprints:', err);
          this.showAlert('Error al cargar los sprints', 'alert-danger');
          return of([]);
        })
      )
      .subscribe((data: SprintDto[]) => {
        // Filtrar solo sprints finalizados para retrospectiva
        this.sprints = data.filter((s: SprintDto) => s.estado === 'finalizado');
        this.loading.set(false);
        
        if (this.sprints.length === 0) {
          this.showAlert('No hay sprints finalizados en este proyecto', 'alert-warning');
        }
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
            // FASE 24: antes esto caía en el "else" genérico y, peor, algunos
            // fallos de Gemini ni siquiera llegaban acá porque el backend
            // devolvía un HTTP 500 opaco (ver AIRetrospectiveService.generateRetrospective,
            // mismo defecto que AIReportService tenía antes de FASE 23). Ahora
            // el backend distingue este caso y el mensaje del propio error ya
            // es claro para el usuario (ej. cuota de IA agotada).
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

  getSelectedSprint(): SprintDto | null {
    return this.sprints.find(s => s.id === this.selectedSprintId) || null;
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
