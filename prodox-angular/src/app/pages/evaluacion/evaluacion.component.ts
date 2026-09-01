// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ShellComponent } from '../../layout/shell/shell.component';
import { EvaluacionService } from '../../services/evaluacion.service';
import { ProyectoDto } from '../../models/proyecto.model';
import {
  MetricaEvaluacionDetalleDto, RegistroPuntoDto, SprintStatsDto, Tendencia, Variabilidad
} from '../../models/evaluacion-detalle.model';
import { EvaluacionMetricChartComponent } from './metric-chart/metric-chart.component';

interface CajaBigotes {
  min: number; q1: number; median: number; q3: number; max: number;
}

const FRECUENCIA_LABEL: Record<string, string> = {
  diaria:      'Diaria',
  semanal:     'Semanal',
  por_sprint:  'Por sprint',
  ilimitada:   'Ilimitada'
};

@Component({
  selector: 'app-evaluacion',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent, EvaluacionMetricChartComponent],
  template: `
    <app-shell title="Evaluación">

      @if (!proyecto) {
        <div class="prox-empty-state">
          <i class="bi bi-folder-x"></i>
          <p>Seleccioná un proyecto primero.</p>
          <button class="btn btn-primary btn-sm mt-3" (click)="router.navigate(['/proyectos'])">
            Ir a Proyectos
          </button>
        </div>
      } @else {

        <!-- Revisión de navegación: breadcrumb del flujo completo -->
        <nav aria-label="breadcrumb" class="mb-2">
          <ol class="breadcrumb small mb-0">
            <li class="breadcrumb-item">
              <a href="#" (click)="$event.preventDefault(); router.navigate(['/planeacion'])">
                <i class="bi bi-layers me-1"></i>Planeación
              </a>
            </li>
            <li class="breadcrumb-item">
              <a href="#" (click)="$event.preventDefault(); router.navigate(['/ejecucion'])">
                <i class="bi bi-pencil-square me-1"></i>Ejecución
              </a>
            </li>
            <li class="breadcrumb-item active">Evaluación</li>
          </ol>
        </nav>

        <!-- Info del proyecto -->
        <div class="d-flex align-items-center gap-3 mb-3 flex-wrap">
          <div class="fw-semibold">{{ proyecto.nombre }}</div>
          <span class="badge" [class]="proyecto.metodo === 'scrum' ? 'bg-primary' : 'bg-info text-dark'">
            {{ proyecto.metodo | uppercase }}
          </span>
          <span class="text-muted small">{{ sprintsDisponibles.length }} sprint(s) con datos</span>
          <div class="ms-auto d-flex gap-2">
            <button class="btn btn-sm btn-outline-primary" (click)="cargar()" [disabled]="cargando">
              <i class="bi bi-arrow-clockwise me-1"></i>Actualizar
            </button>
            @if (datos.length > 0) {
              <button class="btn btn-sm btn-success" (click)="router.navigate(['/ai-retrospective'])"
                      title="Interpretar estos resultados y preparar la mejora del siguiente sprint">
                <i class="bi bi-arrow-repeat me-1"></i>Continuar con Retrospectiva
              </button>
            }
          </div>
        </div>

        <!-- Tabs -->
        <ul class="nav nav-tabs mb-3">
          <li class="nav-item">
            <button class="nav-link" [class.active]="tab === 'tendencias'" (click)="tab = 'tendencias'">
              <i class="bi bi-graph-up me-1"></i>Tendencias
            </button>
          </li>
          <li class="nav-item">
            <button class="nav-link" [class.active]="tab === 'comparacion'" (click)="tab = 'comparacion'">
              <i class="bi bi-table me-1"></i>Comparación entre Sprints
            </button>
          </li>
          <li class="nav-item">
            <button class="nav-link" [class.active]="tab === 'estadisticas'" (click)="tab = 'estadisticas'">
              <i class="bi bi-bar-chart me-1"></i>Estadísticas
            </button>
          </li>
        </ul>

        @if (cargando) {
          <div class="text-center py-5 text-muted small">
            <span class="spinner-border spinner-border-sm me-2"></span>Calculando evaluación...
          </div>
        } @else if (datos.length === 0) {
          <div class="prox-empty-state">
            <i class="bi bi-bar-chart"></i>
            <p>No hay datos registrados aún. Completá la fase de Ejecución primero.</p>
          </div>
        } @else {

          <!-- Filtros -->
          <div class="d-flex flex-wrap gap-2 mb-3 align-items-center">
            <span class="small fw-semibold text-muted">Filtrar por:</span>
            <select class="form-select form-select-sm" style="max-width:150px" [(ngModel)]="categoriaFiltro">
              <option value="">Todas las categorías</option>
              @for (c of categoriasDisponibles; track c) { <option [value]="c">{{ c }}</option> }
            </select>
            <select class="form-select form-select-sm" style="max-width:150px" [(ngModel)]="frecuenciaFiltro">
              <option value="">Todas las frecuencias</option>
              @for (f of frecuenciasDisponibles; track f) {
                <option [value]="f">{{ frecuenciaLabel(f) }}</option>
              }
            </select>
            @if (tab === 'tendencias') {
              <select class="form-select form-select-sm" style="max-width:150px" [(ngModel)]="sprintFiltro">
                <option [ngValue]="null">Todos los sprints</option>
                @for (s of sprintsDisponibles; track s) { <option [ngValue]="s">Sprint {{ s }}</option> }
              </select>
            }
          </div>

          @if (metricasFiltradas.length === 0) {
            <div class="text-center py-4 text-muted small">Ninguna métrica coincide con el filtro.</div>
          }

          <!-- ── TENDENCIAS: evolución real por métrica ─────────────────── -->
          @if (tab === 'tendencias') {
            <div class="row g-3">
              @for (m of metricasFiltradas; track m.variableId) {
                <div class="col-lg-6">
                  <div class="card h-100 metrica-card" (click)="abrirDetalle(m)" role="button">
                    <div class="card-header py-2 d-flex justify-content-between align-items-start">
                      <div>
                        <div class="fw-semibold small">{{ m.variableDescripcion || m.variableNombre }}</div>
                        <div class="text-muted" style="font-size:0.68rem">
                          Frecuencia: {{ frecuenciaLabel(m.frecuenciaCaptura) }}
                        </div>
                      </div>
                      <span class="badge prox-badge-sm" [class]="badgeCat(m.categoria)">
                        {{ m.categoria }}
                      </span>
                    </div>
                    <div class="card-body py-2">
                      <app-evaluacion-metric-chart
                        [registros]="registrosParaVista(m)"
                        [frecuenciaLabel]="frecuenciaLabel(m.frecuenciaCaptura)"
                        [frecuenciaCaptura]="m.frecuenciaCaptura"
                        [sprintEspecifico]="sprintFiltro !== null"
                        [height]="110">
                      </app-evaluacion-metric-chart>
                    </div>
                    <div class="card-footer py-2">
                      @if (statsParaVista(m); as stats) {
                        <div class="row g-1 small">
                          <div class="col-4"><span class="text-muted">Registros</span><br>
                            <strong>{{ stats.totalRegistros }}</strong></div>
                          <div class="col-4"><span class="text-muted">Promedio</span><br>
                            <strong>{{ stats.promedio }}</strong></div>
                          <div class="col-4"><span class="text-muted">Rango</span><br>
                            <strong>{{ stats.minimo }} – {{ stats.maximo }}</strong></div>
                        </div>
                      }
                      @if (!sprintFiltro) {
                        <div class="d-flex justify-content-between align-items-center mt-2">
                          <span [class]="'small ' + colorTendenciaTexto(m.estadisticas.tendencia)">
                            <i class="bi me-1" [class]="iconoTendencia(m.estadisticas.tendencia)"></i>
                            {{ labelTendencia(m.estadisticas.tendencia) }}
                          </span>
                          @if (m.estadisticas.variabilidad) {
                            <span class="badge prox-badge-sm" [class]="badgeVariabilidad(m.estadisticas.variabilidad)">
                              Variabilidad {{ m.estadisticas.variabilidad }}
                            </span>
                          }
                        </div>
                      }
                    </div>
                  </div>
                </div>
              }
            </div>
          }

          <!-- ── COMPARACIÓN ENTRE SPRINTS ──────────────────────────────── -->
          @if (tab === 'comparacion') {
            <div class="card">
              <div class="card-header fw-semibold small py-2">
                <i class="bi bi-table me-1"></i>Promedios por Variable y Sprint
                <span class="text-muted fw-normal">(entre paréntesis, cantidad de registros)</span>
              </div>
              <div class="table-responsive">
                <table class="table table-sm table-bordered mb-0">
                  <thead class="table-light">
                    <tr>
                      <th class="ps-3" style="min-width:160px">Variable</th>
                      <th style="min-width:80px">Categoría</th>
                      @for (s of sprintsDisponibles; track s) {
                        <th class="text-center">Sprint {{ s }}</th>
                      }
                      <th class="text-center" style="min-width:160px">Cambio entre sprints</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (m of metricasFiltradas; track m.variableId) {
                      <tr>
                        <td class="ps-3 small fw-semibold align-middle">{{ m.variableDescripcion || m.variableNombre }}</td>
                        <td class="align-middle">
                          <span class="badge prox-badge-sm" [class]="badgeCat(m.categoria)">
                            {{ m.categoria }}
                          </span>
                        </td>
                        @for (s of sprintsDisponibles; track s) {
                          <td class="text-center align-middle">
                            @if (sprintStat(m, s); as ss) {
                              <div class="fw-semibold">{{ ss.promedio }}</div>
                              <div class="text-muted" style="font-size:0.62rem">({{ ss.totalRegistros }} reg.)</div>
                            } @else {
                              <span class="text-muted">—</span>
                            }
                          </td>
                        }
                        <td class="align-middle" style="font-size:0.7rem">
                          @for (c of cambiosEntreSprints(m); track c.desde) {
                            <div [class]="c.pct === null ? 'text-muted' : (c.pct >= 0 ? 'text-success' : 'text-danger')">
                              S{{ c.desde }}→S{{ c.hasta }}:
                              {{ c.pct !== null ? (c.pct >= 0 ? '+' : '') + c.pct + '%' : (c.abs >= 0 ? '+' : '') + c.abs }}
                            </div>
                          }
                          @if (cambiosEntreSprints(m).length === 0) {
                            <span class="text-muted">—</span>
                          }
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </div>
          }

          <!-- ── ESTADÍSTICAS ────────────────────────────────────────── -->
          @if (tab === 'estadisticas') {
            <div class="row g-3">
              <!-- KPIs globales -->
              <div class="col-12">
                <div class="row g-2">
                  <div class="col-6 col-md-3">
                    <div class="card text-center border-primary">
                      <div class="card-body py-2 prox-stat">
                        <div class="prox-stat-label">Variables medidas</div>
                        <div class="prox-stat-value text-primary">{{ metricasFiltradas.length }}</div>
                      </div>
                    </div>
                  </div>
                  <div class="col-6 col-md-3">
                    <div class="card text-center border-success">
                      <div class="card-body py-2 prox-stat">
                        <div class="prox-stat-label">Sprints con datos</div>
                        <div class="prox-stat-value text-success">{{ sprintsDisponibles.length }}</div>
                      </div>
                    </div>
                  </div>
                  <div class="col-6 col-md-3">
                    <div class="card text-center border-info">
                      <div class="card-body py-2 prox-stat">
                        <div class="prox-stat-label">Total de registros</div>
                        <div class="prox-stat-value text-info">{{ totalRegistrosGlobal }}</div>
                      </div>
                    </div>
                  </div>
                  <div class="col-6 col-md-3">
                    <div class="card text-center border-secondary">
                      <div class="card-body py-2 prox-stat">
                        <div class="prox-stat-label">Promedio general*</div>
                        <div class="prox-stat-value text-secondary">{{ promedioGeneral }}</div>
                      </div>
                    </div>
                  </div>
                  <div class="col-6 col-md-3">
                    <div class="card text-center border-success">
                      <div class="card-body py-2 prox-stat">
                        <div class="prox-stat-label">Tendencia ascendente</div>
                        <div class="prox-stat-value text-success">{{ contarTendencia('ascendente') }}</div>
                      </div>
                    </div>
                  </div>
                  <div class="col-6 col-md-3">
                    <div class="card text-center border-danger">
                      <div class="card-body py-2 prox-stat">
                        <div class="prox-stat-label">Tendencia descendente</div>
                        <div class="prox-stat-value text-danger">{{ contarTendencia('descendente') }}</div>
                      </div>
                    </div>
                  </div>
                  <div class="col-6 col-md-3">
                    <div class="card text-center border-warning">
                      <div class="card-body py-2 prox-stat">
                        <div class="prox-stat-label">Métricas estables</div>
                        <div class="prox-stat-value text-warning">{{ contarTendencia('estable') }}</div>
                      </div>
                    </div>
                  </div>
                  <div class="col-6 col-md-3">
                    <div class="card text-center border-dark">
                      <div class="card-body py-2 prox-stat">
                        <div class="prox-stat-label">Alta variabilidad</div>
                        <div class="prox-stat-value text-dark">{{ contarVariabilidad('alta') }}</div>
                      </div>
                    </div>
                  </div>
                </div>
                <div class="text-muted mt-1" style="font-size:0.68rem">
                  * Promedio de los promedios de cada métrica (métricas con distinta escala no se combinan en un único valor ponderado).
                </div>
              </div>

              <!-- Por categoría -->
              @for (cat of categoriasDisponibles; track cat) {
                @if (filasPorCategoria(cat).length > 0) {
                  <div class="col-md-6">
                    <div class="card h-100">
                      <div class="card-header py-2 small d-flex align-items-center gap-2">
                        <span class="badge prox-badge-sm" [class]="badgeCat(cat)">{{ cat }}</span>
                        <span class="fw-semibold">{{ filasPorCategoria(cat).length }} variable(s)</span>
                      </div>
                      <div class="card-body py-2">
                        @for (m of filasPorCategoria(cat); track m.variableId) {
                          <div class="d-flex justify-content-between align-items-center mb-2">
                            <span class="small">{{ m.variableDescripcion || m.variableNombre }}</span>
                            <div class="d-flex align-items-center gap-2">
                              <span class="small fw-semibold">{{ m.estadisticas.ultimoValor }}</span>
                              <span [class]="colorTendenciaTexto(m.estadisticas.tendencia)">
                                <i class="bi" [class]="iconoTendencia(m.estadisticas.tendencia)"></i>
                              </span>
                            </div>
                          </div>
                        }
                      </div>
                    </div>
                  </div>
                }
              }
            </div>
          }

        }
      }

      <!-- Modal de detalle de métrica -->
      @if (detalleAbierto) {
        <div class="modal d-block" style="background-color: rgba(0,0,0,0.5)" (click)="cerrarDetalle()">
          <div class="modal-dialog modal-lg" (click)="$event.stopPropagation()">
            <div class="modal-content">
              <div class="modal-header">
                <h5 class="modal-title">
                  {{ detalleAbierto.variableDescripcion || detalleAbierto.variableNombre }}
                  <span class="badge ms-2 prox-badge-sm" [class]="badgeCat(detalleAbierto.categoria)">
                    {{ detalleAbierto.categoria }}
                  </span>
                </h5>
                <button type="button" class="btn-close" (click)="cerrarDetalle()"></button>
              </div>
              <div class="modal-body">
                <div class="small text-muted mb-2 d-flex align-items-center gap-2 flex-wrap">
                  <span>Frecuencia de captura: <strong>{{ frecuenciaLabel(detalleAbierto.frecuenciaCaptura) }}</strong></span>
                  <span class="badge bg-light text-dark border">
                    {{ sprintFiltro === null ? 'Mostrando: todos los sprints (comparación)' : 'Mostrando: Sprint ' + sprintFiltro }}
                  </span>
                </div>

                <app-evaluacion-metric-chart
                  [registros]="registrosParaVista(detalleAbierto)"
                  [frecuenciaLabel]="frecuenciaLabel(detalleAbierto.frecuenciaCaptura)"
                  [frecuenciaCaptura]="detalleAbierto.frecuenciaCaptura"
                  [sprintEspecifico]="sprintFiltro !== null"
                  [height]="220">
                </app-evaluacion-metric-chart>

                <!-- Diagrama de caja (distribución) -->
                @if (registrosParaVista(detalleAbierto).length >= 4 && calcularCaja(registrosParaVista(detalleAbierto)); as caja) {
                  <div class="mt-3">
                    <div class="small fw-semibold mb-1">Distribución de valores</div>
                    @if (escalaCaja(caja, 640); as esc) {
                      <svg viewBox="0 0 640 60" style="width:100%;height:60px">
                        <line [attr.x1]="esc(caja.min)" y1="30" [attr.x2]="esc(caja.max)" y2="30"
                              stroke="var(--bs-secondary)" stroke-width="1"/>
                        <rect [attr.x]="esc(caja.q1)" y="12" [attr.width]="esc(caja.q3) - esc(caja.q1)" height="36"
                              fill="var(--bs-primary-bg-subtle)" stroke="var(--bs-primary)" stroke-width="1.5"/>
                        <line [attr.x1]="esc(caja.median)" y1="12" [attr.x2]="esc(caja.median)" y2="48"
                              stroke="var(--bs-primary)" stroke-width="2"/>
                        <line [attr.x1]="esc(caja.min)" y1="18" [attr.x2]="esc(caja.min)" y2="42" stroke="var(--bs-secondary)"/>
                        <line [attr.x1]="esc(caja.max)" y1="18" [attr.x2]="esc(caja.max)" y2="42" stroke="var(--bs-secondary)"/>
                      </svg>
                    }
                    <div class="d-flex flex-wrap gap-2 justify-content-between text-muted" style="font-size:0.65rem">
                      <span>Mín: {{ caja.min }}</span>
                      <span>Q1: {{ caja.q1 }}</span>
                      <span>Mediana: {{ caja.median }}</span>
                      <span>Q3: {{ caja.q3 }}</span>
                      <span>Máx: {{ caja.max }}</span>
                    </div>
                  </div>
                }

                <!-- Tarjeta de resumen: Registros/Promedio/Mínimo-Máximo respetan el sprint
                     seleccionado arriba; el resto (primer/último valor, cambio, tendencia,
                     variabilidad) son conceptos de secuencia y se calculan siempre sobre el
                     histórico completo, marcados explícitamente como tales para no
                     confundirlos con la gráfica de un único sprint. -->
                <div class="row g-2 mt-3 small">
                  @if (statsParaVista(detalleAbierto); as stats) {
                    <div class="col-4"><span class="text-muted">Registros</span><br>
                      <strong>{{ stats.totalRegistros }}</strong></div>
                    <div class="col-4"><span class="text-muted">Promedio</span><br>
                      <strong>{{ stats.promedio }}</strong></div>
                    <div class="col-4"><span class="text-muted">Mínimo / Máximo</span><br>
                      <strong>{{ stats.minimo }} / {{ stats.maximo }}</strong></div>
                  }
                  <div class="col-12"><hr class="my-1">
                    <span class="text-muted" style="font-size:0.68rem">Histórico completo (todos los sprints):</span>
                  </div>
                  <div class="col-4"><span class="text-muted">Primer valor</span><br>
                    <strong>{{ detalleAbierto.estadisticas.primerValor }}</strong></div>
                  <div class="col-4"><span class="text-muted">Último valor</span><br>
                    <strong>{{ detalleAbierto.estadisticas.ultimoValor }}</strong></div>
                  <div class="col-4"><span class="text-muted">Cambio</span><br>
                    <strong>{{ detalleAbierto.estadisticas.cambio >= 0 ? '+' : '' }}{{ detalleAbierto.estadisticas.cambio }}
                      @if (detalleAbierto.estadisticas.cambioPct !== null) {
                        ({{ detalleAbierto.estadisticas.cambioPct >= 0 ? '+' : '' }}{{ detalleAbierto.estadisticas.cambioPct }}%)
                      }
                    </strong>
                  </div>
                  <div class="col-6">
                    <span class="text-muted">Tendencia</span><br>
                    <span [class]="colorTendenciaTexto(detalleAbierto.estadisticas.tendencia)">
                      <i class="bi me-1" [class]="iconoTendencia(detalleAbierto.estadisticas.tendencia)"></i>
                      {{ labelTendencia(detalleAbierto.estadisticas.tendencia) }}
                    </span>
                  </div>
                  <div class="col-6">
                    <span class="text-muted">Variabilidad</span><br>
                    @if (detalleAbierto.estadisticas.variabilidad) {
                      <span class="badge prox-badge-sm" [class]="badgeVariabilidad(detalleAbierto.estadisticas.variabilidad)">
                        {{ detalleAbierto.estadisticas.variabilidad }}
                        ({{ detalleAbierto.estadisticas.coeficienteVariacion }}% CV)
                      </span>
                    } @else {
                      <span class="text-muted small">Insuficientes registros</span>
                    }
                  </div>
                </div>

                <!-- Análisis textual determinístico -->
                <div class="alert alert-light border mt-3 mb-0 small">
                  <div class="fw-semibold mb-1"><i class="bi bi-file-text me-1"></i>Análisis</div>
                  @for (p of analisisTexto(detalleAbierto); track $index) {
                    <p class="mb-1">{{ p }}</p>
                  }
                </div>

                <!-- Tabla de registros -->
                <div class="mt-3">
                  <div class="small fw-semibold mb-1">Registros ({{ detalleAbierto.registros.length }})</div>
                  <div class="table-responsive" style="max-height:220px;overflow-y:auto">
                    <table class="table table-sm mb-0">
                      <thead class="table-light">
                        <tr><th>Fecha</th><th>Sprint</th><th>Valor</th></tr>
                      </thead>
                      <tbody>
                        @for (r of registrosDesc(detalleAbierto.registros); track r.id) {
                          <tr>
                            <td class="small text-muted">{{ r.registradoAt | date:'dd/MM/yyyy HH:mm' }}</td>
                            <td class="small text-muted">Sprint {{ r.sprintNumero }}</td>
                            <td class="fw-semibold">{{ r.valor }}</td>
                          </tr>
                        }
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
              <div class="modal-footer">
                <button class="btn btn-secondary btn-sm" (click)="cerrarDetalle()">Cerrar</button>
              </div>
            </div>
          </div>
        </div>
      }
    </app-shell>
  `,
  styles: [`
    .metrica-card { cursor: pointer; transition: box-shadow .15s; }
    .metrica-card:hover { box-shadow: 0 0 0 2px var(--bs-primary); }
  `]
})
export class EvaluacionComponent implements OnInit {
  proyecto: ProyectoDto | null = null;
  datos:    MetricaEvaluacionDetalleDto[] = [];
  tab: 'tendencias' | 'comparacion' | 'estadisticas' = 'tendencias';
  cargando = true;

  categoriaFiltro  = '';
  frecuenciaFiltro = '';
  sprintFiltro: number | null = null;

  detalleAbierto: MetricaEvaluacionDetalleDto | null = null;

  constructor(
    public  router: Router,
    private evaluacionService: EvaluacionService
  ) {}

  // ── filtros / listas derivadas ──────────────────────────────────────

  get sprintsDisponibles(): number[] {
    const set = new Set<number>();
    this.datos.forEach(m => m.porSprint.forEach(s => set.add(s.sprintNumero)));
    return [...set].sort((a, b) => a - b);
  }

  get categoriasDisponibles(): string[] {
    return [...new Set(this.datos.map(d => d.categoria))];
  }

  get frecuenciasDisponibles(): string[] {
    return [...new Set(this.datos.map(d => d.frecuenciaCaptura))];
  }

  get metricasFiltradas(): MetricaEvaluacionDetalleDto[] {
    return this.datos.filter(m =>
      (!this.categoriaFiltro || m.categoria === this.categoriaFiltro) &&
      (!this.frecuenciaFiltro || m.frecuenciaCaptura === this.frecuenciaFiltro)
    );
  }

  filasPorCategoria(cat: string): MetricaEvaluacionDetalleDto[] {
    return this.metricasFiltradas.filter(m => m.categoria === cat);
  }

  get totalRegistrosGlobal(): number {
    return this.metricasFiltradas.reduce((acc, m) => acc + m.estadisticas.totalRegistros, 0);
  }

  /** Promedio de los promedios de cada métrica (no combina escalas distintas en un único total). */
  get promedioGeneral(): number {
    const ms = this.metricasFiltradas;
    if (!ms.length) return 0;
    const suma = ms.reduce((acc, m) => acc + m.estadisticas.promedio, 0);
    return Math.round((suma / ms.length) * 100) / 100;
  }

  contarTendencia(t: Tendencia): number {
    return this.metricasFiltradas.filter(m => m.estadisticas.tendencia === t).length;
  }

  contarVariabilidad(v: Variabilidad): number {
    return this.metricasFiltradas.filter(m => m.estadisticas.variabilidad === v).length;
  }

  ngOnInit(): void {
    try {
      const p = localStorage.getItem('mpdia_proyecto_activo');
      this.proyecto = p ? JSON.parse(p) : null;
    } catch { /* ignore */ }
    if (this.proyecto) this.cargar();
  }

  /** true una vez que sprintFiltro ya fue inicializado (con el sprint más reciente) al menos una vez. */
  private sprintFiltroInicializado = false;

  cargar(): void {
    if (!this.proyecto) return;
    this.cargando = true;
    this.evaluacionService.detalle(this.proyecto.id).pipe(
      catchError(() => of([]))
    ).subscribe(d => {
      this.datos = d;
      this.cargando = false;

      // Por defecto se muestra el sprint más reciente (nunca se mezclan series
      // de distintos sprints en una misma gráfica sin que el usuario lo pida
      // explícitamente eligiendo "Todos los sprints"). Solo se fija una vez:
      // refrescar ("Actualizar") no debe pisar una selección manual del usuario.
      if (!this.sprintFiltroInicializado && this.sprintsDisponibles.length > 0) {
        this.sprintFiltro = Math.max(...this.sprintsDisponibles);
        this.sprintFiltroInicializado = true;
      }
    });
  }

  // ── datos filtrados por sprint (solo afecta a la vista de Tendencias) ─

  /**
   * Revisión de Evaluación: si ya existe al menos un resultado calculado del
   * equipo para esta métrica (ResultadoMetrica vigente, ver
   * resultadosCalculados), la gráfica usa ESE valor por sprint en vez del
   * último RegistroValor individual crudo — así "Sprint 1 → 72, Sprint 2 → 81"
   * representa el resultado real del equipo, no el dato de un solo miembro.
   * Sin resultados calculados todavía (métrica recién parametrizada, o
   * frecuencia semanal/diaria sin granularidad equivalente en ResultadoMetrica),
   * cae de vuelta al comportamiento preexistente sobre 'registros'.
   */
  registrosParaVista(m: MetricaEvaluacionDetalleDto): RegistroPuntoDto[] {
    if (m.resultadosCalculados && m.resultadosCalculados.length > 0) {
      const puntos: RegistroPuntoDto[] = m.resultadosCalculados.map(r => ({
        id: r.resultadoId,
        valor: r.resultado,
        registradoAt: r.calculadoAt,
        sprintId: r.sprintId,
        sprintNumero: r.sprintNumero,
        userId: '' // resultado del equipo, no de un miembro individual
      }));
      if (this.sprintFiltro === null) return puntos;
      return puntos.filter(r => r.sprintNumero === this.sprintFiltro);
    }
    if (this.sprintFiltro === null) return m.registros;
    return m.registros.filter(r => r.sprintNumero === this.sprintFiltro);
  }

  /** Estadísticas a mostrar: globales si no hay filtro de sprint, o las del sprint seleccionado. */
  statsParaVista(m: MetricaEvaluacionDetalleDto): { totalRegistros: number; promedio: number; minimo: number; maximo: number } {
    if (this.sprintFiltro === null) return m.estadisticas;
    const s = m.porSprint.find(x => x.sprintNumero === this.sprintFiltro);
    return s ?? { totalRegistros: 0, promedio: 0, minimo: 0, maximo: 0 };
  }

  sprintStat(m: MetricaEvaluacionDetalleDto, sprintNumero: number): SprintStatsDto | undefined {
    return m.porSprint.find(s => s.sprintNumero === sprintNumero);
  }

  /** % (o cambio absoluto si el promedio previo es 0) entre cada par de sprints consecutivos con datos. */
  cambiosEntreSprints(m: MetricaEvaluacionDetalleDto): { desde: number; hasta: number; pct: number | null; abs: number }[] {
    const serie = m.porSprint;
    const cambios: { desde: number; hasta: number; pct: number | null; abs: number }[] = [];
    for (let i = 1; i < serie.length; i++) {
      const prev = serie[i - 1], cur = serie[i];
      const abs = Math.round((cur.promedio - prev.promedio) * 100) / 100;
      const pct = prev.promedio !== 0 ? Math.round((abs / Math.abs(prev.promedio)) * 1000) / 10 : null;
      cambios.push({ desde: prev.sprintNumero, hasta: cur.sprintNumero, pct, abs });
    }
    return cambios;
  }

  registrosDesc(registros: RegistroPuntoDto[]): RegistroPuntoDto[] {
    return [...registros].sort((a, b) => new Date(b.registradoAt).getTime() - new Date(a.registradoAt).getTime());
  }

  // ── diagrama de caja (distribución) ─────────────────────────────────

  calcularCaja(registros: RegistroPuntoDto[]): CajaBigotes {
    const s = registros.map(r => r.valor).sort((a, b) => a - b);
    const q = (p: number) => {
      const idx = (s.length - 1) * p;
      const lo = Math.floor(idx), hi = Math.ceil(idx);
      if (lo === hi) return s[lo];
      return Math.round((s[lo] + (s[hi] - s[lo]) * (idx - lo)) * 100) / 100;
    };
    return { min: s[0], q1: q(0.25), median: q(0.5), q3: q(0.75), max: s[s.length - 1] };
  }

  escalaCaja(caja: CajaBigotes, width: number): (v: number) => number {
    const pad = 20;
    const rango = caja.max - caja.min || 1;
    return (v: number) => pad + ((v - caja.min) / rango) * (width - pad * 2);
  }

  // ── análisis textual determinístico ─────────────────────────────────

  analisisTexto(m: MetricaEvaluacionDetalleDto): string[] {
    const e = m.estadisticas;
    const parrafos: string[] = [];

    if (e.totalRegistros < 2) {
      parrafos.push(
        `Solo se cuenta con ${e.totalRegistros} valor para esta métrica. Se requiere al menos ` +
        `un segundo período de medición para identificar una tendencia.`
      );
      return parrafos;
    }

    const tendenciaTxt = e.tendencia === 'ascendente' ? 'una tendencia ascendente'
      : e.tendencia === 'descendente' ? 'una tendencia descendente'
      : 'una tendencia estable';
    parrafos.push(`La métrica presenta ${tendenciaTxt} a lo largo de ${e.totalRegistros} registros, con un promedio de ${e.promedio}.`);

    const signoCambio = e.cambio >= 0 ? '+' : '';
    const cambioTxt = e.cambioPct !== null
      ? `un cambio de ${signoCambio}${e.cambio} (${e.cambioPct >= 0 ? '+' : ''}${e.cambioPct}%)`
      : `un cambio absoluto de ${signoCambio}${e.cambio}`;
    parrafos.push(`El valor pasó de ${e.primerValor} a ${e.ultimoValor}, representando ${cambioTxt}.`);

    if (e.variabilidad) {
      parrafos.push(`La variabilidad es ${e.variabilidad} (coeficiente de variación de ${e.coeficienteVariacion}%), con valores entre ${e.minimo} y ${e.maximo}.`);
    } else {
      parrafos.push(`No hay registros suficientes (mínimo 3) para clasificar la variabilidad de esta métrica.`);
    }

    return parrafos;
  }

  // ── detalle (modal) ─────────────────────────────────────────────────

  abrirDetalle(m: MetricaEvaluacionDetalleDto): void { this.detalleAbierto = m; }
  cerrarDetalle(): void { this.detalleAbierto = null; }

  // ── helpers visuales ─────────────────────────────────────────────────

  frecuenciaLabel(f: string): string {
    return FRECUENCIA_LABEL[f] ?? f;
  }

  labelTendencia(t: Tendencia): string {
    if (t === 'ascendente')  return 'Ascendente';
    if (t === 'descendente') return 'Descendente';
    if (t === 'estable')     return 'Estable';
    return 'Insuficiente';
  }

  iconoTendencia(t: Tendencia): string {
    if (t === 'ascendente')  return 'bi-arrow-up-right';
    if (t === 'descendente') return 'bi-arrow-down-right';
    if (t === 'estable')     return 'bi-arrow-right';
    return 'bi-dash';
  }

  colorTendenciaTexto(t: Tendencia): string {
    if (t === 'ascendente')  return 'text-success';
    if (t === 'descendente') return 'text-danger';
    if (t === 'estable')     return 'text-warning';
    return 'text-muted';
  }

  badgeVariabilidad(v: Variabilidad): string {
    if (v === 'baja')  return 'bg-success';
    if (v === 'media') return 'bg-warning text-dark';
    if (v === 'alta')  return 'bg-danger';
    return 'bg-secondary';
  }

  badgeCat(cat: string): string {
    const map: Record<string, string> = {
      'Significado':      'bg-primary',
      'Flexibilidad':     'bg-warning text-dark',
      'Impacto':          'bg-danger',
      'Socio-Humano FSH': 'bg-info text-dark'
    };
    return map[cat] ?? 'bg-secondary';
  }
}
