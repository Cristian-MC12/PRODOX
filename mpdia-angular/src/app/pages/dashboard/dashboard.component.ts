// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { forkJoin, catchError, of } from 'rxjs';
import { ShellComponent } from '../../layout/shell/shell.component';
import { ProjectSummaryCardComponent } from '../../components/dashboard/project-summary-card/project-summary-card.component';
import { MetricsOverviewCardComponent } from '../../components/dashboard/metrics-overview-card/metrics-overview-card.component';
import { RisksAlertsCardComponent } from '../../components/dashboard/risks-alerts-card/risks-alerts-card.component';
import { InsightsQuickViewComponent } from '../../components/dashboard/insights-quick-view/insights-quick-view.component';
import { QuickActionsPanelComponent } from '../../components/dashboard/quick-actions-panel/quick-actions-panel.component';
import { AnalyticsService } from '../../services/analytics.service';
import { AIInsightsService } from '../../services/ai-insights.service';
import { SprintService } from '../../services/sprint.service';
import { ProyectoDto } from '../../models/proyecto.model';
import { ProjectOverview, Risk, TrendAnalysis } from '../../models/analytics.model';
import { AIInsight } from '../../models/ai-insights.model';
import { SprintDto } from '../../models/sprint.model';

type DashboardState = 'loading' | 'success' | 'empty' | 'insufficient-data' | 'error';

/**
 * Dashboard Inteligente - Componente Orquestador
 * 
 * Responsabilidades:
 * - Obtener proyecto activo desde localStorage
 * - Coordinar carga paralela de datos desde múltiples servicios
 * - Manejar estados: loading, success, empty, insufficient-data, error
 * - NO llamar Gemini automáticamente
 * - Delegar presentación a componentes hijos (futura implementación)
 * 
 * NO implementa lógica de negocio, solo orquestación.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    ShellComponent,
    ProjectSummaryCardComponent,
    MetricsOverviewCardComponent,
    RisksAlertsCardComponent,
    InsightsQuickViewComponent,
    QuickActionsPanelComponent
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit {

  proyecto: ProyectoDto | null = null;
  sprintActivo: SprintDto | null = null;
  
  // Datos del dashboard
  projectOverview: ProjectOverview | null = null;
  recentInsights: AIInsight[] = [];
  risks: Risk[] = [];
  trends: TrendAnalysis[] = [];
  
  // Estado y control
  state = signal<DashboardState>('loading');
  alertMsg = signal('');
  alertClass = signal('alert-info');
  
  // Flags para datos opcionales
  hasInsufficientData = signal(false);
  hasTrends = signal(false);
  
  constructor(
    private router: Router,
    private analyticsService: AnalyticsService,
    private insightsService: AIInsightsService,
    private sprintService: SprintService
  ) {}

  ngOnInit(): void {
    this.loadProyectoActivo();
  }

  /**
   * Carga proyecto activo desde localStorage.
   * Contexto únicamente, backend valida ProjectMember.
   */
  private loadProyectoActivo(): void {
    const raw = localStorage.getItem('mpdia_proyecto_activo');
    this.proyecto = raw ? JSON.parse(raw) : null;
    
    if (this.proyecto) {
      this.loadDashboardData();
    } else {
      this.handleError('No hay proyecto activo seleccionado');
    }
  }

  /**
   * Carga todos los datos del dashboard en paralelo usando forkJoin.
   * NO llama a Gemini automáticamente.
   * 
   * Requests paralelos:
   * 1. getProjectOverview
   * 2. getProjectInsights
   * 3. identifyRisks
   * 4. getActivo (sprint)
   * 
   * Después de forkJoin, opcionalmente carga trends si hay suficientes sprints.
   */
  private loadDashboardData(): void {
    if (!this.proyecto) return;

    this.state.set('loading');
    this.alertMsg.set('');
    
    const proyectoId = this.proyecto.id;

    // Preparar requests en paralelo
    const requests = {
      overview: this.analyticsService.getProjectOverview(proyectoId).pipe(
        catchError(err => {
          console.error('Error cargando overview:', err);
          return of(null);
        })
      ),
      insights: this.insightsService.getProjectInsights(proyectoId).pipe(
        catchError(err => {
          console.error('Error cargando insights:', err);
          return of([]);
        })
      ),
      risks: this.analyticsService.identifyRisks(proyectoId).pipe(
        catchError(err => {
          console.error('Error cargando risks:', err);
          return of([]);
        })
      ),
      sprintActivo: this.sprintService.getActivo(proyectoId).pipe(
        catchError(err => {
          console.error('Error cargando sprint activo:', err);
          return of(null);
        })
      )
    };

    // Ejecutar requests en paralelo
    forkJoin(requests).subscribe({
      next: (results) => {
        this.projectOverview = results.overview;
        this.recentInsights = this.limitInsights(results.insights);
        this.risks = results.risks;
        this.sprintActivo = results.sprintActivo;
        
        // Cargar tendencias solo si hay suficientes sprints (>=3)
        this.loadTrendsIfAvailable(proyectoId);
        
        this.processResults();
      },
      error: (err) => {
        console.error('Error en forkJoin:', err);
        this.handleError('Error cargando datos del dashboard');
      }
    });
  }

  /**
   * Carga tendencias solo si hay suficientes sprints (>=3).
   * Este request NO está en el forkJoin principal porque es condicional.
   */
  private loadTrendsIfAvailable(proyectoId: string): void {
    const sprintsFinalizados = this.projectOverview?.sprintsFinalizados || 0;
    
    if (sprintsFinalizados >= 3) {
      this.analyticsService.getSprintTrends(proyectoId, null, 5).pipe(
        catchError(err => {
          console.error('Error cargando trends:', err);
          return of([]);
        })
      ).subscribe(trends => {
        this.trends = trends.filter(t => t.datosDisponibles);
        this.hasTrends.set(this.trends.length > 0);
      });
    } else {
      this.hasTrends.set(false);
    }
  }

  /**
   * Procesa los resultados y determina el estado del dashboard.
   * 
   * Estados posibles:
   * - error: no se pudo obtener overview
   * - insufficient-data: overview existe pero datosDisponibles=false
   * - empty: 0 sprints finalizados
   * - success: datos suficientes disponibles
   */
  private processResults(): void {
    if (!this.projectOverview) {
      this.handleError('No se pudo obtener información del proyecto');
      return;
    }

    if (!this.projectOverview.datosDisponibles) {
      this.hasInsufficientData.set(true);
      this.state.set('insufficient-data');
      this.showAlert(
        'Completa al menos un sprint para ver métricas del proyecto',
        'alert-warning'
      );
      return;
    }

    if (this.projectOverview.sprintsFinalizados === 0) {
      this.hasInsufficientData.set(true);
      this.state.set('empty');
      this.showAlert(
        'No hay sprints finalizados. Completa tu primer sprint para ver el dashboard.',
        'alert-info'
      );
      return;
    }

    // Datos suficientes disponibles
    this.hasInsufficientData.set(false);
    this.state.set('success');
  }

  /**
   * Limita insights a los últimos 5 para evitar sobrecargar la vista.
   */
  private limitInsights(insights: AIInsight[]): AIInsight[] {
    return insights
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
      .slice(0, 5);
  }

  /**
   * Maneja errores y actualiza el estado.
   */
  private handleError(message: string): void {
    this.state.set('error');
    this.showAlert(message, 'alert-danger');
  }

  private showAlert(message: string, cssClass: string): void {
    this.alertMsg.set(message);
    this.alertClass.set(cssClass);
    setTimeout(() => this.alertMsg.set(''), 8000);
  }

  /**
   * Permite al usuario reintentar la carga en caso de error.
   */
  retry(): void {
    this.loadDashboardData();
  }

  /**
   * Navegación rápida a funcionalidades existentes.
   */
  navigateToInsights(): void {
    this.router.navigate(['/ai-insights']);
  }

  navigateToReports(): void {
    this.router.navigate(['/ai-report']);
  }

  navigateToRetrospectives(): void {
    this.router.navigate(['/ai-retrospective']);
  }

  /**
   * Genera insights explícitamente (NO automático).
   * Navega a la página de insights donde el usuario puede generar.
   */
  generateInsights(): void {
    this.navigateToInsights();
  }

  // Helper methods para template (próxima fase)
  getMetricasArray(): Array<{categoria: string, valor: number}> {
    if (!this.projectOverview?.promedioHistorico) return [];
    return Object.entries(this.projectOverview.promedioHistorico).map(([categoria, valor]) => ({
      categoria,
      valor
    }));
  }

  Object = Object; // Helper para usar Object.entries en template
}
