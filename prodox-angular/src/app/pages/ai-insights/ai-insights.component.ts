// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ShellComponent } from '../../layout/shell/shell.component';
import { AIInsightsService } from '../../services/ai-insights.service';
import { ProyectoDto } from '../../models/proyecto.model';
import { AIInsight, InsightEvidence } from '../../models/ai-insights.model';
import { LimpiarMarkdownIAPipe } from '../../core/limpiar-markdown-ia.pipe';

@Component({
  selector: 'app-ai-insights',
  standalone: true,
  imports: [CommonModule, ShellComponent, LimpiarMarkdownIAPipe],
  templateUrl: './ai-insights.component.html',
  styleUrl: './ai-insights.component.css'
})
export class AIInsightsComponent implements OnInit {

  proyecto: ProyectoDto | null = null;
  insights: AIInsight[] = [];
  
  loading = signal(false);
  generating = signal(false);
  alertMsg = signal('');
  alertClass = signal('alert-info');
  generationStep = signal('');
  
  // IN.5 — Priorización
  sortBy = signal<'fecha' | 'severidad'>('severidad');
  sortDirection = signal<'asc' | 'desc'>('desc');
  typeFilter = signal<string>('ALL');

  constructor(
    public router: Router,
    private insightsService: AIInsightsService
  ) {}

  ngOnInit(): void {
    const raw = localStorage.getItem('mpdia_proyecto_activo');
    this.proyecto = raw ? JSON.parse(raw) : null;
    
    if (this.proyecto) {
      this.loadInsights();
    }
  }

  loadInsights(): void {
    if (!this.proyecto) return;

    this.loading.set(true);
    this.alertMsg.set('');

    this.insightsService.getProjectInsights(this.proyecto.id)
      .pipe(
        catchError(err => {
          console.error('Error cargando insights:', err);
          this.showAlert('Error al cargar insights', 'alert-danger');
          return of([]);
        })
      )
      .subscribe(data => {
        this.insights = data;
        this.loading.set(false);
      });
  }

  generateInsights(): void {
    if (!this.proyecto || this.generating()) return;

    this.generating.set(true);
    this.updateGenerationProgress('Preparando análisis...');
    let hadError = false;

    // Simular pasos de generación para mejor UX
    setTimeout(() => this.updateGenerationProgress('Analizando métricas del proyecto...'), 1000);
    setTimeout(() => this.updateGenerationProgress('Detectando patrones y anomalías...'), 3000);
    setTimeout(() => this.updateGenerationProgress('Generando insights con IA...'), 5000);

    this.insightsService.generateInsights(this.proyecto.id)
      .pipe(
        catchError(err => {
          console.error('Error generando insights:', err);
          hadError = true;
          this.generationStep.set('');

          if (err.status === 403) {
            this.showAlert('No tienes permisos para generar insights en este proyecto', 'alert-danger');
          } else {
            this.showAlert('Error al generar insights. Intentá nuevamente.', 'alert-danger');
          }

          return of(null);
        })
      )
      .subscribe(resultado => {
        if (!hadError && resultado) {
          this.updateGenerationProgress('Finalizando...');
          setTimeout(() => {
            this.generating.set(false);
            // Recarga la lista completa (persistida) en vez de reemplazarla
            // solo con la tanda nueva, para que se vea el estado real acumulado.
            // IMPORTANTE: loadInsights() limpia alertMsg al iniciar, así que
            // debe llamarse ANTES de mostrar el mensaje de estado de esta
            // generación — si se llamara después, borraría el mensaje recién
            // mostrado apenas se disparara.
            this.loadInsights();
            // FASE 23: antes de esto la respuesta era solo AIInsight[] y un
            // fallo parcial de Gemini se veía exactamente igual que una
            // corrida completa con pocos resultados. Ahora el backend
            // distingue el estado real de la corrida (ver
            // GenerateInsightsResultDto) y se lo comunicamos al usuario.
            switch (resultado.status) {
              case 'SIN_DATOS':
                this.showAlert('No se generaron insights. El proyecto todavía no tiene sprints finalizados.', 'alert-warning');
                break;
              case 'SIN_SENALES':
                this.showAlert('No se generaron insights. El proyecto puede no tener suficientes datos históricos o señales significativas.', 'alert-warning');
                break;
              case 'FAILED':
                this.showAlert('No se pudo generar ningún insight: el servicio de IA no respondió correctamente. Intentá nuevamente en unos segundos.', 'alert-danger');
                break;
              case 'PARTIAL':
                this.showAlert(
                  `Se generaron ${resultado.senalesNuevas} insight(s), pero ${resultado.errores.length} señal(es) no pudieron procesarse (el servicio de IA no respondió para todas). Podés reintentar más tarde.`,
                  'alert-warning');
                break;
              default: { // COMPLETE
                const nuevosTexto = resultado.senalesNuevas > 0
                  ? `${resultado.senalesNuevas} insight(s) nuevo(s) generado(s)`
                  : 'Sin novedades';
                const omitidosTexto = resultado.senalesOmitidasPorDuplicado > 0
                  ? ` (${resultado.senalesOmitidasPorDuplicado} señal(es) ya estaban cubiertas, no se duplicaron)`
                  : '';
                this.showAlert(`${nuevosTexto}${omitidosTexto}`, 'alert-success');
              }
            }
          }, 500);
        } else {
          this.generating.set(false);
        }
      });
  }

  dismissInsight(insight: AIInsight): void {
    if (!confirm('¿Estás seguro de que querés descartar este insight?')) {
      return;
    }

    let hadError = false;

    this.insightsService.dismissInsight(insight.id)
      .pipe(
        catchError(err => {
          console.error('Error descartando insight:', err);
          hadError = true;
          this.showAlert('Error al descartar el insight', 'alert-danger');
          return of(null);
        })
      )
      .subscribe(() => {
        if (!hadError) {
          this.showAlert('Insight descartado', 'alert-success');
          this.insights = this.insights.filter(i => i.id !== insight.id);
        }
      });
  }

  getSeverityBadgeClass(severity: string): string {
    switch (severity) {
      case 'CRITICAL': return 'bg-danger';
      case 'HIGH': return 'bg-warning';
      case 'MEDIUM': return 'bg-info';
      case 'LOW': return 'bg-secondary';
      default: return 'bg-secondary';
    }
  }

  getSeverityLabel(severity: string): string {
    switch (severity) {
      case 'CRITICAL': return 'Crítico';
      case 'HIGH': return 'Alto';
      case 'MEDIUM': return 'Medio';
      case 'LOW': return 'Bajo';
      default: return severity;
    }
  }

  getTypeIcon(type: string): string {
    switch (type) {
      case 'TREND': return 'bi-graph-up-arrow';
      case 'ANOMALY': return 'bi-exclamation-triangle';
      case 'RISK': return 'bi-shield-exclamation';
      case 'COMPARISON': return 'bi-arrow-left-right';
      default: return 'bi-info-circle';
    }
  }

  getTypeLabel(type: string): string {
    switch (type) {
      case 'TREND': return 'Tendencia';
      case 'ANOMALY': return 'Anomalía';
      case 'RISK': return 'Riesgo';
      case 'COMPARISON': return 'Comparación';
      default: return type;
    }
  }

  getConfidenceBadgeClass(confidence: string): string {
    switch (confidence) {
      case 'HIGH': return 'badge-success';
      case 'MEDIUM': return 'badge-warning';
      case 'LOW': return 'badge-secondary';
      default: return 'badge-secondary';
    }
  }

  getConfidenceLabel(confidence: string): string {
    switch (confidence) {
      case 'HIGH': return 'Alta';
      case 'MEDIUM': return 'Media';
      case 'LOW': return 'Baja';
      default: return confidence;
    }
  }

  formatEvidence(evidence: InsightEvidence[]): string {
    if (!evidence || evidence.length === 0) return 'Sin datos de evidencia';

    return evidence.map(e => {
      const parts: string[] = [];
      parts.push(`Categoría: ${e.categoria}`);
      
      if (e.valorActual !== null) {
        parts.push(`Valor actual: ${e.valorActual.toFixed(2)}`);
      }
      if (e.valorAnterior !== null) {
        parts.push(`Valor anterior: ${e.valorAnterior.toFixed(2)}`);
      }
      if (e.promedioHistorico !== null) {
        parts.push(`Promedio histórico: ${e.promedioHistorico.toFixed(2)}`);
      }
      if (e.variacionPorcentual !== null) {
        parts.push(`Variación: ${e.variacionPorcentual.toFixed(1)}%`);
      }
      if (e.tendencia) {
        parts.push(`Tendencia: ${e.tendencia}`);
      }
      if (e.numeroSprints !== null) {
        parts.push(`Sprints analizados: ${e.numeroSprints}`);
      }
      
      return parts.join(' | ');
    }).join('\n');
  }

  private showAlert(message: string, cssClass: string): void {
    this.alertMsg.set(message);
    this.alertClass.set(cssClass);
    setTimeout(() => this.alertMsg.set(''), 5000);
  }

  private updateGenerationProgress(step: string): void {
    this.generationStep.set(step);
    this.showAlert(step, 'alert-info');
  }

  /**
   * IN.5 - Filtra y ordena insights según criterios actuales.
   */
  get filteredInsights(): AIInsight[] {
    let filtered = [...this.insights];

    // Filtrar por tipo si está seleccionado
    if (this.typeFilter() !== 'ALL') {
      filtered = filtered.filter(insight => insight.type === this.typeFilter());
    }

    // Ordenar
    filtered.sort((a, b) => {
      let comparison = 0;

      if (this.sortBy() === 'fecha') {
        const dateA = new Date(a.createdAt).getTime();
        const dateB = new Date(b.createdAt).getTime();
        comparison = dateA - dateB;
      } else if (this.sortBy() === 'severidad') {
        // Severidad: CRITICAL > HIGH > MEDIUM > LOW
        const severityOrder = { 'CRITICAL': 4, 'HIGH': 3, 'MEDIUM': 2, 'LOW': 1 };
        const severityA = severityOrder[a.severity as keyof typeof severityOrder] || 0;
        const severityB = severityOrder[b.severity as keyof typeof severityOrder] || 0;
        comparison = severityA - severityB;
      }

      return this.sortDirection() === 'asc' ? comparison : -comparison;
    });

    return filtered;
  }

  toggleSortDirection(): void {
    this.sortDirection.update(dir => dir === 'asc' ? 'desc' : 'asc');
  }
}
