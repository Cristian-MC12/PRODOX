// Autor: Cristian Santiago Martinez Cordoba â€” PRODOX
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
import { AISprintReport } from '../../models/ai-reports.model';
import { LimpiarMarkdownIAPipe } from '../../core/limpiar-markdown-ia.pipe';
import { construirSeccionesReporteEjecutivo } from '../../core/reporte-ejecutivo.builder';
import { generarYDescargarDocumento } from '../../core/word-report.util';

@Component({
  selector: 'app-ai-report',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent, LimpiarMarkdownIAPipe],
  templateUrl: './ai-report.component.html',
  styleUrl: './ai-report.component.css'
})
export class AIReportComponent implements OnInit {

  proyecto: ProyectoDto | null = null;
  sprints: SprintDto[] = [];
  selectedSprintId: string = '';
  report: AISprintReport | null = null;
  
  loading = signal(false);
  generating = signal(false);
  alertMsg = signal('');
  alertClass = signal('alert-info');
    generationStep = signal('');

  // Estado de edición
  editing = false;
  editedResumen = '';
  editedLogros = '';
  editedDesafios = '';
  editedRecomendaciones = '';

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
        this.sprints = data;
        this.loading.set(false);
        
        if (this.sprints.length === 0) {
          this.showAlert('No hay sprints disponibles en este proyecto', 'alert-warning');
        }
      });
  }

  generateReport(): void {
    if (!this.selectedSprintId || this.generating()) return;

    this.generating.set(true);
    this.updateGenerationProgress('Preparando reporte ejecutivo...');
    this.report = null;
    let hadError = false;

    // Simular pasos de generación
    setTimeout(() => this.updateGenerationProgress('Analizando datos del sprint...'), 1000);
    setTimeout(() => this.updateGenerationProgress('Procesando métricas...'), 3000);
    setTimeout(() => this.updateGenerationProgress('Generando análisis con IA...'), 5000);

    this.reportService.generateSprintReport(this.selectedSprintId)
      .pipe(
        catchError(err => {
          console.error('Error generando reporte:', err);
          hadError = true;
          this.generationStep.set('');
          
          if (err.status === 400) {
            this.showAlert('Sprint no encontrado', 'alert-danger');
          } else if (err.status === 403) {
            this.showAlert('No tienes permisos para generar reportes en este proyecto', 'alert-danger');
          } else if (err.status === 429) {
            this.showAlert('Has alcanzado temporalmente el límite de generación. Intenta nuevamente más tarde.', 'alert-warning');
          } else if (err.status === 503) {
            // FASE 23: antes esto caía en el "else" genérico y, peor, algunos
            // fallos de Gemini ni siquiera llegaban acá porque el backend
            // devolvía un HTTP 500 opaco (ver AIReportService.generateReport).
            // Ahora el backend distingue este caso y el mensaje del propio
            // error ya es claro para el usuario (ej. cuota de IA agotada).
            this.showAlert(err.error?.error || 'El servicio de IA no está disponible en este momento. Intenta nuevamente en unos segundos.', 'alert-warning');
          } else {
            this.showAlert('Error al generar el reporte. Intentá nuevamente.', 'alert-danger');
          }
          
          return of(null);
        })
      )
      .subscribe(data => {
        if (!hadError && data) {
          this.updateGenerationProgress('Finalizando reporte...');
          setTimeout(() => {
            this.report = data;
            this.generating.set(false);
            this.showAlert('Reporte generado exitosamente', 'alert-success');
          }, 500);
        } else {
          this.generating.set(false);
        }
      });
  }

  getSelectedSprint(): SprintDto | null {
    return this.sprints.find(s => s.id === this.selectedSprintId) || null;
  }

  hasInsufficientData(): boolean {
    if (!this.report) return false;
    return this.report.resumenEjecutivo?.includes('Datos insuficientes') ||
           this.report.resumenEjecutivo?.includes('datos insuficientes') ||
           Object.keys(this.report.metricas || {}).length === 0;
  }

  getMetricasArray(): Array<{categoria: string, valor: number}> {
    if (!this.report?.metricas) return [];
    return Object.entries(this.report.metricas).map(([categoria, valor]) => ({
      categoria,
      valor
    }));
  }

  private showAlert(message: string, cssClass: string): void {
    this.alertMsg.set(message);
    this.alertClass.set(cssClass);
    setTimeout(() => this.alertMsg.set(''), 5000);
  }

  private updateGenerationProgress(step: string): void {
    this.generationStep.set(step);
    this.alertMsg.set(step);
    this.alertClass.set('alert-info');
  }

  // ============ EXPORTACIÓN A WORD ============

  /**
   * Exporta el Reporte Ejecutivo ya generado a un documento Word (.docx),
   * 100% en el navegador — mismo patrón e infraestructura ya usados por AI
   * Insights, Retrospectiva y el Reporte General (ver word-report.util.ts).
   * Solo serializa this.report, ya generado y autorizado por
   * generateReport() (backend: validateScrumMasterAccess sobre el
   * proyecto del sprint) — no hace ninguna llamada nueva a IA ni a ningún
   * endpoint, no crea ni modifica ningún dato.
   */
  async exportarAWord(): Promise<void> {
    if (!this.report) {
      this.showAlert('No hay reporte generado para exportar', 'alert-warning');
      return;
    }

    const secciones = construirSeccionesReporteEjecutivo(this.report, this.proyecto?.nombre || 'Sin nombre');
    const fileName = `Reporte_Ejecutivo_Sprint${this.report.sprintNumero}_${this.proyecto?.nombre || 'Proyecto'}_${new Date().toISOString().split('T')[0]}.docx`;

    const exportado = await generarYDescargarDocumento('Reporte Ejecutivo de Sprint', secciones, fileName);

    if (exportado) {
      this.showAlert('Reporte exportado correctamente', 'alert-success');
    } else {
      this.showAlert('La exportación a Word no está disponible en este momento.', 'alert-warning');
    }
  }
}
