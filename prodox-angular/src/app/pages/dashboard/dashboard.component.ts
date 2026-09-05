// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { AfterViewChecked, Component, ElementRef, OnDestroy, OnInit, ViewChild, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { forkJoin, catchError, of } from 'rxjs';
import { Chart, registerables, ChartConfiguration, BarElement } from 'chart.js';

import { ShellComponent } from '../../layout/shell/shell.component';
import { KpiCardComponent } from '../../components/dashboard/kpi-card/kpi-card.component';
import { SprintComplianceGaugeComponent } from '../../components/dashboard/sprint-compliance-gauge/sprint-compliance-gauge.component';
import { MetricsEvolutionChartComponent } from '../../components/dashboard/metrics-evolution-chart/metrics-evolution-chart.component';
import { MetricsDistributionChartComponent, DistributionSegment } from '../../components/dashboard/metrics-distribution-chart/metrics-distribution-chart.component';
import { ActivityFeedComponent } from '../../components/dashboard/activity-feed/activity-feed.component';

import { AnalyticsService } from '../../services/analytics.service';
import { SprintService } from '../../services/sprint.service';
import { ProjectMemberService } from '../../services/project-member.service';
import { PlaneacionService } from '../../services/planeacion.service';
import { AuthService } from '../../services/auth.service';

import { ProyectoDto } from '../../models/proyecto.model';
import { ProjectOverview, Risk, TrendAnalysis } from '../../models/analytics.model';
import { SprintDto, EstadoSprint } from '../../models/sprint.model';
import { ProyectoMetricaDto } from '../../models/planeacion.model';

Chart.register(...registerables);

type DashboardState = 'loading' | 'success' | 'empty' | 'insufficient-data' | 'error';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    ShellComponent,
    KpiCardComponent,
    SprintComplianceGaugeComponent,
    MetricsEvolutionChartComponent,
    MetricsDistributionChartComponent,
    ActivityFeedComponent
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit, AfterViewChecked, OnDestroy {

  @ViewChild('sprintsStatusCanvas') sprintsStatusCanvas?: ElementRef<HTMLCanvasElement>;
  private sprintsChart?: Chart<'bar'>;
  private pendingSprintsChartRender = false;

  proyecto: ProyectoDto | null = null;
  sprintActivo: SprintDto | null = null;

  // Datos del dashboard
  projectOverview: ProjectOverview | null = null;
  risks: Risk[] = [];
  trends: TrendAnalysis[] = [];
  totalMiembros = 0;
  metricas: ProyectoMetricaDto[] = [];
  sprints: SprintDto[] = [];

  // Arrays para los gráficos Chart.js, calculados UNA VEZ cuando sus datos
  // fuente cambian (no en el template): [dataPoints]="getEvolutionData()"
  // invocaba el método en cada ciclo de detección de cambios de Angular
  // (cualquier mousemove/hover lo dispara), devolviendo un array nuevo cada
  // vez — Chart.js interpretaba esa nueva referencia como "cambiaron los
  // datos" y reiniciaba su animación sin parar, lo que además interrumpía
  // el propio tooltip que se intentaba mostrar con el mouse quieto.
  evolutionData: Array<{ label: string; value: number }> = [];
  distributionSegments: DistributionSegment[] = [];

  // Estado y control
  state = signal<DashboardState>('loading');
  alertMsg = signal('');
  alertClass = signal('alert-info');

  // Colores para distribución de métricas — deben coincidir EXACTAMENTE con
  // los nombres reales de metrica_categorias (Significado, Flexibilidad,
  // Impacto, Socio-Humano FSH). Cualquier categoría que no matchee cae en
  // 'Otros' (gris), por eso los nombres tienen que ser exactos.
  private categoryColors: Record<string, string> = {
    'Significado': '#0E7C86',
    'Impacto': '#5A96C4',
    'Flexibilidad': '#1E8E5A',
    'Socio-Humano FSH': '#C23B34',
    'Otros': '#5B6B70'
  };

  private readonly estadoSprintLabels: Record<EstadoSprint, string> = {
    pendiente: 'Pendiente',
    en_ejecucion: 'En ejecución',
    finalizado: 'Finalizado',
    reabierto: 'Reabierto'
  };

  // Mismos hex que ya se usan en este archivo (categoryColors / estados del
  // gauge) — Chart.js necesita un color real, no la palabra clave ('blue',
  // 'cyan'...) que usaba el CSS anterior (ese atributo data-color, de hecho,
  // no tenía ninguna regla CSS que lo consumiera).
  private readonly estadoSprintColors: Record<EstadoSprint, string> = {
    pendiente: '#5A96C4',
    en_ejecucion: '#0E7C86',
    finalizado: '#1E8E5A',
    reabierto: '#D99A2B'
  };

  constructor(
    private router: Router,
    private analyticsService: AnalyticsService,
    private sprintService: SprintService,
    private memberService: ProjectMemberService,
    private planeacionService: PlaneacionService,
    private authService: AuthService
  ) {}

  /**
   * Acciones de generación IA (Insights, Retrospectiva, Reporte) son
   * exclusivas del Scrum Master DEL PROYECTO ACTIVO, no de un rol global de
   * cuenta: un mismo usuario puede ser Scrum Master de un proyecto y
   * miembro normal de otro. Se compara contra ProyectoDto.scrumMasterEmail
   * (ya cargado con el proyecto activo, viene de Proyecto.scrumMasterId en
   * el backend) en vez de auth.currentUser()?.role, que es el rol de cuenta
   * global usado para decidir quién puede CREAR proyectos, no quién lidera
   * este proyecto en particular.
   */
  get esScrumMasterDelProyecto(): boolean {
    return !!this.proyecto
      && this.proyecto.scrumMasterEmail === this.authService.currentUser()?.email;
  }

  ngOnInit(): void {
    this.loadProyectoActivo();
  }

  ngAfterViewChecked(): void {
    if (this.pendingSprintsChartRender) {
      this.pendingSprintsChartRender = false;
      this.renderSprintsStatusChart();
    }
  }

  ngOnDestroy(): void {
    this.sprintsChart?.destroy();
  }

  private loadProyectoActivo(): void {
    const raw = localStorage.getItem('mpdia_proyecto_activo');
    this.proyecto = raw ? JSON.parse(raw) : null;

    if (this.proyecto) {
      this.loadDashboardData();
    } else {
      this.handleError('No hay proyecto activo seleccionado');
    }
  }

  private loadDashboardData(): void {
    if (!this.proyecto) return;

    this.state.set('loading');
    this.alertMsg.set('');

    // Limpiar datos del proyecto/sprint anterior: evita mostrar en pantalla
    // información de otro proyecto mientras llegan los datos nuevos.
    this.projectOverview = null;
    this.risks = [];
    this.trends = [];
    this.totalMiembros = 0;
    this.metricas = [];
    this.sprints = [];
    this.sprintActivo = null;
    this.evolutionData = [];
    this.distributionSegments = [];
    this.pendingSprintsChartRender = true; // destruye el gráfico viejo en el próximo ciclo

    const proyectoId = this.proyecto.id;

    const requests = {
      overview: this.analyticsService.getProjectOverview(proyectoId).pipe(
        catchError(err => {
          console.error('Error cargando overview:', err);
          return of(null);
        })
      ),
      risks: this.analyticsService.identifyRisks(proyectoId).pipe(
        catchError(err => {
          console.error('Error cargando risks:', err);
          return of([]);
        })
      ),
      sprintActivo: this.sprintService.getActivo(proyectoId).pipe(
        catchError(() => of(null))
      ),
      sprints: this.sprintService.listar(proyectoId).pipe(
        catchError(err => {
          console.error('Error cargando sprints:', err);
          return of([]);
        })
      ),
      miembros: this.memberService.listar(proyectoId).pipe(
        catchError(err => {
          console.error('Error cargando miembros:', err);
          return of([]);
        })
      ),
      metricas: this.planeacionService.listarMetricas(proyectoId).pipe(
        catchError(err => {
          console.error('Error cargando métricas:', err);
          return of([]);
        })
      )
    };

    forkJoin(requests).subscribe({
      next: (results) => {
        // Si el usuario cambió de proyecto mientras esta petición estaba en
        // vuelo, descartar la respuesta: pertenece al proyecto anterior.
        if (!this.proyecto || this.proyecto.id !== proyectoId) return;

        this.projectOverview = results.overview;
        this.risks = results.risks;
        this.sprintActivo = results.sprintActivo;
        this.sprints = results.sprints;
        this.totalMiembros = results.miembros.length;
        this.metricas = results.metricas;
        this.distributionSegments = this.getDistributionSegments();
        this.pendingSprintsChartRender = true;

        // Cargar tendencias si hay suficientes sprints
        this.loadTrendsIfAvailable(proyectoId);

        this.processResults();
      },
      error: (err) => {
        console.error('Error en forkJoin:', err);
        this.handleError('Error cargando datos del dashboard');
      }
    });
  }

  private loadTrendsIfAvailable(proyectoId: string): void {
    const sprintsFinalizados = this.projectOverview?.sprintsFinalizados || 0;

    if (sprintsFinalizados >= 2) {
      this.analyticsService.getSprintTrends(proyectoId, null, 6).pipe(
        catchError(err => {
          console.error('Error cargando trends:', err);
          return of([]);
        })
      ).subscribe(trends => {
        if (!this.proyecto || this.proyecto.id !== proyectoId) return;
        this.trends = trends.filter(t => t.datosDisponibles && t.dataPoints.length > 0);
        this.evolutionData = this.getEvolutionData();
      });
    } else {
      this.trends = [];
      this.evolutionData = [];
    }
  }

  private processResults(): void {
    if (!this.projectOverview) {
      this.handleError('No se pudo obtener información del proyecto');
      return;
    }

    if (!this.projectOverview.datosDisponibles) {
      this.state.set('insufficient-data');
      this.showAlert(
        'Completa al menos un sprint para ver métricas del proyecto',
        'alert-warning'
      );
      return;
    }

    if (this.projectOverview.sprintsFinalizados === 0) {
      this.state.set('empty');
      this.showAlert(
        'No hay sprints finalizados. Completa tu primer sprint para ver el dashboard.',
        'alert-info'
      );
      return;
    }

    this.state.set('success');
  }

  private handleError(message: string): void {
    this.state.set('error');
    this.showAlert(message, 'alert-danger');
  }

  private showAlert(message: string, cssClass: string): void {
    this.alertMsg.set(message);
    this.alertClass.set(cssClass);
    setTimeout(() => this.alertMsg.set(''), 8000);
  }

  retry(): void {
    this.loadDashboardData();
  }

  // ─────────────────────────────────────────────────────────────
  // KPI Cards - Helpers
  // ─────────────────────────────────────────────────────────────

  /**
   * "Métricas activas" = métricas del proyecto actual con aprobada=true
   * (proyecto_metricas.aprobada), la misma fuente de verdad que usa
   * Planeación/Ejecución para considerar una métrica oficialmente parte
   * del proyecto. NO cuenta el catálogo global (34 métricas): listarMetricas
   * devuelve el catálogo completo con el estado de ESTE proyecto por fila,
   * así que se filtra explícitamente por aprobada.
   */
  getMetricasActivas(): ProyectoMetricaDto[] {
    return this.metricas.filter(m => m.aprobada);
  }

  getTotalMetricas(): number {
    return this.getMetricasActivas().length;
  }

  // No existe una fuente de datos histórica (snapshot de métricas aprobadas
  // en el sprint anterior) para calcular una variación real — mostrar
  // "Sin cambios" es la respuesta honesta, no una variación inventada.
  getMetricasTrendValue(): string {
    return 'Sin comparación disponible';
  }

  getRisksTrendValue(): string {
    // No hay snapshot histórico de riesgos por sprint (identifyRisks es un
    // cálculo en vivo sobre los últimos 3 sprints, no un valor guardado por
    // sprint) — no hay base real para "vs sprint anterior".
    return 'Sin comparación disponible';
  }

  getMiembrosTrendValue(): string {
    // No hay un corte por sprint para "miembros nuevos vs sprint anterior";
    // solo tenemos joinedAt (fecha de alta), no asociado a un sprint.
    return 'Sin comparación disponible';
  }

  getRisksSeverityText(): string {
    if (this.risks.length === 0) return 'Sin riesgos';

    const critical = this.risks.filter(r => r.severidad === 'CRITICAL').length;
    const high = this.risks.filter(r => r.severidad === 'HIGH').length;

    if (critical > 0) return `${critical} críticos`;
    if (high > 0) return `${high} altos`;
    return 'Riesgos menores';
  }

  // ─────────────────────────────────────────────────────────────
  // Sprint activo
  // ─────────────────────────────────────────────────────────────

  getSprintActivoLabel(): string {
    if (!this.sprintActivo) return 'Sin sprint activo';
    return `Sprint ${this.sprintActivo.numero}`;
  }

  getSprintActivoEstadoLabel(): string {
    if (!this.sprintActivo) return 'No hay sprint en ejecución';
    return this.estadoSprintLabels[this.sprintActivo.estado] ?? this.sprintActivo.estado;
  }

  // ─────────────────────────────────────────────────────────────
  // Gauge - Sprint Compliance
  // ─────────────────────────────────────────────────────────────

  /**
   * Corrección de auditoría (Dashboard/Evaluación): promedioHistorico trae UN
   * valor por categoría (ej. "Significado", "Flexibilidad"...), cada uno el
   * promedio crudo de RegistroValor.valorNum de las variables de esa
   * categoría, en la escala que cada variable haya definido. El backend NO
   * propaga unidad/escala/tipoOperacion hasta este nivel — dos categorías
   * pueden representar magnitudes completamente distintas (ej. Velocidad en
   * Story Points vs. Satisfacción en %, caso real verificado en auditoría).
   *
   * Promediar 2+ categorías entre sí (lo que hacía este método antes) mezcla
   * esas escalas/unidades heterogéneas sin ninguna normalización — no tiene
   * sentido matemático, sin importar qué tan "razonable" luzca el resultado.
   * Como no existe una forma de normalizar esas categorías con la
   * información disponible (ver AgileAnalyticsService.getProjectOverview()),
   * el único caso en el que "el promedio general" es matemáticamente válido
   * es cuando hay EXACTAMENTE una categoría — ahí no hay mezcla, el "general"
   * es, literalmente, ese único valor. Con 0 o 2+ categorías se prefiere no
   * mostrar un número (hasComplianceData()=false, ver plantilla) en vez de
   * fabricar una clasificación Bueno/Regular/Malo sin respaldo matemático.
   *
   * Se mantiene el clamp a [0,100] y la protección contra NaN/Infinity como
   * defensa ante datos fuera de rango — nunca como sustituto de esta regla.
   */
  getSprintCompliance(): number {
    if (!this.hasComplianceData()) {
      return 0;
    }

    const promedio = Object.values(this.projectOverview!.promedioHistorico)[0];

    if (!Number.isFinite(promedio)) return 0;

    return Math.max(0, Math.min(100, Math.round(promedio)));
  }

  hasComplianceData(): boolean {
    if (!this.projectOverview?.promedioHistorico) return false;
    // Exactamente una categoría: única combinación verificable como "sin
    // mezcla de unidades" con la información que expone el backend hoy.
    return Object.keys(this.projectOverview.promedioHistorico).length === 1;
  }

  // ─────────────────────────────────────────────────────────────
  // Evolution Chart - Data
  // ─────────────────────────────────────────────────────────────

  /**
   * Solo devuelve puntos reales (TrendAnalysis.dataPoints, calculados por
   * AgileAnalyticsService.getSprintTrends contra registros reales). Si no
   * hay suficientes sprints finalizados o el backend no devolvió tendencias,
   * se devuelve un array vacío — el componente hijo ya maneja ese caso
   * mostrando "No hay datos suficientes" en vez de una gráfica engañosa.
   * (Antes acá se simulaba una variación con Math.sin() cuando no había
   * tendencias reales — eso generaba puntos artificiales y se elimina.)
   */
  getEvolutionData(): Array<{ label: string; value: number }> {
    if (this.trends && this.trends.length > 0) {
      const firstTrend = this.trends[0];
      if (firstTrend.dataPoints && firstTrend.dataPoints.length > 0) {
        return firstTrend.dataPoints.map(point => ({
          label: `S${point.sprintNumero}`,
          value: point.valor
        }));
      }
    }
    return [];
  }

  // ─────────────────────────────────────────────────────────────
  // Distribution Chart - Data
  // ─────────────────────────────────────────────────────────────

  /**
   * Distribución de las métricas ACTIVAS (aprobadas) del proyecto por
   * categoría — mismo universo que getTotalMetricas(), así que el "Total"
   * del donut coincide con el KPI "Métricas activas". Antes esto se
   * calculaba sumando promedioHistorico (promedios de evaluación por
   * categoría, no conteos de métricas), lo que producía un total sin
   * relación con la cantidad real de métricas (ej: 151 vs 3 activas).
   */
  getDistributionSegments(): DistributionSegment[] {
    const activas = this.getMetricasActivas();
    if (activas.length === 0) return [];

    const porCategoria = new Map<string, number>();
    for (const m of activas) {
      porCategoria.set(m.categoria, (porCategoria.get(m.categoria) ?? 0) + 1);
    }

    const total = activas.length;

    return Array.from(porCategoria.entries()).map(([categoria, count]) => ({
      category: categoria,
      value: count,
      color: this.categoryColors[categoria] || this.categoryColors['Otros'],
      percentage: Math.round((count / total) * 100)
    }));
  }

  // ─────────────────────────────────────────────────────────────
  // Utilities
  // ─────────────────────────────────────────────────────────────

  formatDate(dateString: string): string {
    const date = new Date(dateString);
    const now = new Date();
    const diff = now.getTime() - date.getTime();

    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (hours < 1) return 'Hace un momento';
    if (hours < 24) return `Hace ${hours}h`;
    if (days === 1) return 'Ayer';
    if (days < 7) return `Hace ${days}d`;

    return date.toLocaleDateString('es-ES', {
      day: 'numeric',
      month: 'short'
    });
  }

  formatDateCompact(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleDateString('es-ES', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric'
    });
  }

  getSeverityLabel(severity: string): string {
    const labels: Record<string, string> = {
      'CRITICAL': 'Alto',
      'HIGH': 'Medio',
      'MEDIUM': 'Medio',
      'LOW': 'Bajo'
    };
    return labels[severity] || severity;
  }

  /**
   * Cuenta los sprints REALES del proyecto actual agrupados por su estado
   * real (SprintDto.estado: pendiente | en_ejecucion | finalizado |
   * reabierto). Antes esto aproximaba "Planificación/Ejecución/Evaluación/
   * Cierre" a partir de projectOverview (totalSprints - finalizados -
   * activos), con un bucket "Cierre" que siempre era 0 — estados que no
   * existen en el modelo real y un conteo derivado, no observado.
   */
  getSprintsByStatus(): Array<{label: string; value: number; percentage: number; color: string}> {
    const conteos: Record<EstadoSprint, number> = {
      pendiente: 0,
      en_ejecucion: 0,
      finalizado: 0,
      reabierto: 0
    };

    for (const s of this.sprints) {
      if (s.estado in conteos) conteos[s.estado]++;
    }

    const max = Math.max(...Object.values(conteos), 1);

    return (Object.keys(conteos) as EstadoSprint[]).map(estado => ({
      label: this.estadoSprintLabels[estado],
      value: conteos[estado],
      percentage: (conteos[estado] / max) * 100,
      color: this.estadoSprintColors[estado]
    }));
  }

  /**
   * Chart.js Bar de "Sprints por estado". El <canvas> vive dentro del
   * @if (state()==='success') del template, así que @ViewChild solo queda
   * disponible DESPUÉS de que la vista se revisa — por eso el render real
   * se dispara desde ngAfterViewChecked (ver pendingSprintsChartRender),
   * nunca directamente al asignar this.sprints.
   */
  private renderSprintsStatusChart(): void {
    const canvas = this.sprintsStatusCanvas?.nativeElement;
    if (!canvas || this.state() !== 'success') {
      this.sprintsChart?.destroy();
      this.sprintsChart = undefined;
      return;
    }

    const datos = this.getSprintsByStatus();

    // Plugin nativo de Chart.js (sin librerías nuevas) que dibuja la cantidad
    // real sobre cada barra — el diseño original (SVG) mostraba el número
    // directamente en la barra (.bar-value-compact); el eje Y queda oculto
    // por diseño (barras compactas), así que sin esto el conteo solo sería
    // visible al pasar el mouse.
    const valueLabelsPlugin = {
      id: 'sprintsValueLabels',
      afterDatasetsDraw(chart: Chart<'bar'>) {
        const { ctx } = chart;
        const meta = chart.getDatasetMeta(0);
        ctx.save();
        ctx.font = '600 12px sans-serif';
        ctx.textAlign = 'center';
        (meta.data as BarElement[]).forEach((bar, index) => {
          const value = datos[index].value;
          const pos = bar.getCenterPoint();
          const x = pos.x ?? 0;
          const barY = bar.y ?? pos.y ?? 0;
          if (value > 0) {
            // Con barra real debajo: número en blanco, dentro de la barra.
            ctx.fillStyle = '#FFFFFF';
            ctx.textBaseline = 'top';
            ctx.fillText(String(value), x, barY + 4);
          } else {
            // Sin barra (0 real, no relleno artificial): número flotando
            // sobre la línea base, en color oscuro para que sea legible.
            ctx.fillStyle = '#5B6B70';
            ctx.textBaseline = 'bottom';
            ctx.fillText('0', x, barY - 4);
          }
        });
        ctx.restore();
      }
    };

    const config: ChartConfiguration<'bar'> = {
      type: 'bar',
      data: {
        labels: datos.map(d => d.label),
        datasets: [{
          data: datos.map(d => d.value),
          backgroundColor: datos.map(d => d.color),
          borderRadius: 6,
          maxBarThickness: 40
        }]
      },
      plugins: [valueLabelsPlugin],
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          x: { grid: { display: false } },
          y: { display: false, beginAtZero: true }
        },
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              title: (items) => `Estado: ${items[0]?.label ?? ''}`,
              label: (item) => `${item.formattedValue} sprint(s)`
            }
          }
        }
      }
    };

    if (this.sprintsChart) {
      this.sprintsChart.data = config.data;
      this.sprintsChart.update();
    } else {
      this.sprintsChart = new Chart(canvas, config);
    }
  }

  getEmptyStateTitle(): string {
    if (this.state() === 'error') return 'Error cargando dashboard';
    if (this.state() === 'empty') return 'Proyecto recién creado';
    return 'Datos insuficientes';
  }

  getEmptyStateMessage(): string {
    if (this.state() === 'error') return 'No se pudo obtener la información del proyecto.';
    if (this.state() === 'empty') return 'No hay sprints finalizados todavía. Completa tu primer sprint para comenzar a ver métricas.';
    return 'Completa al menos un sprint para comenzar a ver métricas y análisis.';
  }

  // Navegación a funcionalidades de IA
  navigateToInsights(): void {
    this.router.navigate(['/ai-insights']);
  }

  navigateToRetrospective(): void {
    this.router.navigate(['/ai-retrospective']);
  }

  navigateToReport(): void {
    this.router.navigate(['/ai-report']);
  }
}
