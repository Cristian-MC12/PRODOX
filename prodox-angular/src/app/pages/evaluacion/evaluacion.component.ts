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
                        <div class="fw-semibold small">{{ m.variableNombre }}</div>
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
                        <td class="ps-3 small fw-semibold align-middle">{{ m.variableNombre }}</td>
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
                            <span class="small">{{ m.variableNombre }}</span>
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
                  {{ detalleAbierto.variableNombre }}
                  <span class="badge ms-2 prox-badge-sm" [class]="badgeCat(detalleAbierto.categoria)">
                    {{ detalleAbierto.categoria }}
                  </span>
                </h5>
                <button type="button" class="btn-close" (click)="cerrarDetalle()"></button>
              </div>
              <div class="modal-body">
                <!-- Descripción completa -->
                <div class="alert alert-light border-0 bg-light mb-3 small">
                  <i class="bi bi-info-circle me-2"></i>{{ detalleAbierto.variableDescripcion }}
                </div>
                
                <div class="small text-muted mb-2 d-flex align-items-center gap-2 flex-wrap">
                  <span>Frecuencia de captura: <strong>{{ frecuenciaLabel(detalleAbierto.frecuenciaCaptura) }}</strong></span>
                  <span class="badge bg-light text-dark border">
                    {{ sprintFiltro === null ? 'Mostrando: todos los sprints (comparación)' : 'Mostrando: Sprint ' + sprintFiltro }}
                  </span>
                </div>

                <!-- Contribuciones por integrante -->
                @if (calcularContribucionesPorIntegrante(detalleAbierto); as contribuciones) {
                  @if (contribuciones.length > 0) {
                    <div class="card border mb-3">
                      <div class="card-body py-2">
                        <div class="d-flex justify-content-between align-items-center mb-2">
                          <h6 class="mb-0 small fw-semibold">
                            <i class="bi bi-people me-1"></i>Contribuciones por Integrante
                            <span class="text-muted fw-normal" style="font-size: 0.7rem;">
                              ({{ sprintFiltro === null ? 'todos los sprints' : 'Sprint ' + sprintFiltro }})
                            </span>
                          </h6>
                          <span class="badge bg-primary">Total: {{ obtenerTotalGeneral(detalleAbierto) }}</span>
                        </div>
                        
                        @for (contrib of contribuciones; track contrib.userId) {
                          <div class="mb-2">
                            <div class="d-flex justify-content-between align-items-center mb-1">
                              <div>
                                <span class="small fw-medium">{{ contrib.userName }}</span>
                                <span class="text-muted" style="font-size: 0.7rem;">({{ contrib.registros }} registro{{ contrib.registros !== 1 ? 's' : '' }})</span>
                              </div>
                              <span class="small fw-bold">{{ contrib.total }} <span class="text-muted">({{ contrib.porcentaje }}%)</span></span>
                            </div>
                            <div class="progress" style="height: 20px;">
                              <div class="progress-bar" 
                                   [style.width.%]="contrib.porcentaje"
                                   [style.background]="'linear-gradient(90deg, #14B8A6 0%, #0D9488 100%)'">
                              </div>
                            </div>
                          </div>
                        }
                      </div>
                    </div>
                  }
                }

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

                <!-- Evaluación para el equipo Scrum -->
                @if (generarEvaluacionScrum(detalleAbierto); as evaluacion) {
                  <div class="card border mt-3" 
                       [class.border-success]="evaluacion.estado === 'favorable'"
                       [class.border-danger]="evaluacion.estado === 'atencion'"
                       [class.border-primary]="evaluacion.estado === 'observacion'"
                       [class.border-secondary]="evaluacion.estado === 'insuficiente'">
                    <div class="card-body py-3">
                      <div class="d-flex align-items-center gap-2 mb-3">
                        <span style="font-size: 1.5rem;">{{ evaluacion.icono }}</span>
                        <h6 class="mb-0 fw-bold">{{ evaluacion.titulo }}</h6>
                      </div>
                      
                      <div class="mb-3">
                        <strong class="small text-muted">Resumen:</strong>
                        <p class="mb-0">{{ evaluacion.resumen }}</p>
                      </div>
                      
                      @if (evaluacion.positivo) {
                        <div class="mb-3">
                          <div class="d-flex align-items-start gap-2">
                            <span class="text-success fw-bold">✓</span>
                            <div>
                              <strong class="text-success small">Lo positivo</strong>
                              <p class="mb-0 small">{{ evaluacion.positivo }}</p>
                            </div>
                          </div>
                        </div>
                      }
                      
                      @if (evaluacion.mejora) {
                        <div class="mb-3">
                          <div class="d-flex align-items-start gap-2">
                            <span class="text-warning fw-bold">⚠</span>
                            <div>
                              <strong class="text-warning small">Aspecto a mejorar</strong>
                              <p class="mb-0 small">{{ evaluacion.mejora }}</p>
                            </div>
                          </div>
                        </div>
                      }
                      
                      <div class="alert alert-info mb-0 py-2 small">
                        <div class="d-flex align-items-start gap-2">
                          <span>💡</span>
                          <div>
                            <strong>Recomendación</strong>
                            <p class="mb-0">{{ evaluacion.recomendacion }}</p>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                }

                <!-- Datos técnicos detallados (colapsables) -->
                <div class="mt-3">
                  <button class="btn btn-sm btn-outline-secondary w-100" 
                          type="button" 
                          (click)="mostrarDatosTecnicos = !mostrarDatosTecnicos">
                    <i class="bi" [class.bi-chevron-down]="!mostrarDatosTecnicos" [class.bi-chevron-up]="mostrarDatosTecnicos"></i>
                    {{ mostrarDatosTecnicos ? 'Ocultar' : 'Ver' }} datos técnicos detallados
                  </button>
                  
                  @if (mostrarDatosTecnicos) {
                    <div class="alert alert-light border mt-2 mb-0 small">
                      <div class="fw-semibold mb-1"><i class="bi bi-bar-chart me-1"></i>Análisis estadístico</div>
                      @for (p of analisisTexto(detalleAbierto); track $index) {
                        <p class="mb-1">{{ p }}</p>
                      }
                    </div>
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
  mostrarDatosTecnicos = false; // Control para mostrar/ocultar datos estadísticos detallados

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

  /**
   * Genera una evaluación orientada al equipo Scrum que responde:
   * "¿Cómo vamos? ¿Es positivo/negativo? ¿Qué significa? ¿Qué revisar? ¿Qué acción tomar?"
   * 
   * REGLA FUNDAMENTAL: Lenguaje práctico, NO técnico. Diferenciamos DATO → INTERPRETACIÓN → RECOMENDACIÓN
   * Evitar: "tendencia", "promedio", porcentajes en análisis principal
   * Lo positivo debe reflejar RESULTADOS, no solo "está registrando"
   */
  generarEvaluacionScrum(m: MetricaEvaluacionDetalleDto): {
    estado: 'favorable' | 'atencion' | 'observacion' | 'insuficiente';
    icono: string;
    titulo: string;
    resumen: string;
    positivo: string;
    mejora: string;
    recomendacion: string;
  } {
    const e = m.estadisticas;
    
    // ═══════════════════════════════════════════════════════════
    // CASO 1: Solo 1 registro (información insuficiente)
    // ═══════════════════════════════════════════════════════════
    if (e.totalRegistros === 1) {
      return {
        estado: 'insuficiente',
        icono: '⚪',
        titulo: 'Sin datos suficientes',
        resumen: `Solo hay un registro disponible con valor ${e.primerValor}. Se necesita al menos una medición adicional para evaluar si hay cambios.`,
        positivo: 'Se comenzó a medir esta métrica, lo cual permite empezar a hacer seguimiento.',
        mejora: '',
        recomendacion: `Registrar esta métrica en el próximo sprint para poder comparar y detectar si hay cambios que merezcan atención.`
      };
    }
    
    // ═══════════════════════════════════════════════════════════
    // CASO 2: Exactamente 2 registros (cambio observable, patrón incipiente)
    // ═══════════════════════════════════════════════════════════
    if (e.totalRegistros === 2) {
      // Detectar tipo de métrica
      const nombreLower = m.variableNombre.toLowerCase();
      const descripcionLower = (m.variableDescripcion || '').toLowerCase();
      const textoCompleto = nombreLower + ' ' + descripcionLower;
      
      const esMetricaDeProblema = textoCompleto.includes('defecto') || 
                                   textoCompleto.includes('error') || 
                                   textoCompleto.includes('fallo') ||
                                   textoCompleto.includes('incidencia') ||
                                   textoCompleto.includes('problema') ||
                                   textoCompleto.includes('retraso') ||
                                   textoCompleto.includes('registrado') && textoCompleto.includes('durante');
      
      const esMetricaDeTiempo = textoCompleto.includes('tiempo') || 
                                 textoCompleto.includes('duración') ||
                                 textoCompleto.includes('plazo');
      
      let resumen = '';
      let estado: 'favorable' | 'atencion' | 'observacion' | 'insuficiente' = 'observacion';
      let icono = '🔵';
      let titulo = 'Merece revisión';
      
      // Describir el cambio observado de forma clara
      if (e.cambio > 0) {
        const aumento = Math.abs(e.cambio);
        
        // Interpretación según tipo de métrica
        if (esMetricaDeProblema) {
          resumen = `Se registraron más defectos/problemas que en la medición anterior (de ${e.primerValor} a ${e.ultimoValor}). Esto merece atención porque puede indicar que el equipo está encontrando más problemas durante el desarrollo.`;
          estado = 'atencion';
          icono = '🔴';
          titulo = 'Mejora necesaria';
        } else if (esMetricaDeTiempo) {
          resumen = `El tiempo aumentó de ${e.primerValor} a ${e.ultimoValor}, lo que representa ${aumento} unidades más. Esto merece atención porque puede indicar que las tareas están tomando más tiempo de lo esperado.`;
          estado = 'atencion';
          icono = '🔴';
          titulo = 'Mejora necesaria';
        } else {
          resumen = `El valor pasó de ${e.primerValor} a ${e.ultimoValor}, aumentando ${aumento} unidades. Todavía hay pocos registros para confirmar si este comportamiento se mantendrá.`;
          icono = '🔵';
          titulo = 'Merece revisión';
        }
      } else if (e.cambio < 0) {
        const reduccion = Math.abs(e.cambio);
        
        if (esMetricaDeProblema) {
          resumen = `Se registraron menos defectos/problemas que en la medición anterior (de ${e.primerValor} a ${e.ultimoValor}). Esto puede ser una señal positiva si se mantiene en futuras mediciones.`;
          estado = 'observacion';
          icono = '🟢';
          titulo = 'Buen progreso';
        } else if (esMetricaDeTiempo) {
          resumen = `El tiempo disminuyó de ${e.primerValor} a ${e.ultimoValor}, lo que representa ${reduccion} unidades menos. Esto puede indicar mejora en la velocidad o eficiencia.`;
          estado = 'observacion';
          icono = '🟢';
          titulo = 'Buen progreso';
        } else {
          resumen = `El valor pasó de ${e.primerValor} a ${e.ultimoValor}, bajando ${reduccion} unidades. Todavía hay pocos registros para confirmar si este comportamiento se mantendrá.`;
          icono = '🔵';
          titulo = 'Merece revisión';
        }
      } else {
        resumen = `Ambos registros muestran el mismo valor: ${e.primerValor}. Se necesitan más mediciones para observar cambios.`;
        titulo = 'Sin cambios observables';
      }
      
      // Lo positivo: debe ser útil y relevante según el contexto
      let positivo = '';
      if (e.cambio < 0 && (esMetricaDeProblema || esMetricaDeTiempo)) {
        positivo = `El valor bajó respecto a la medición anterior, lo que puede indicar mejora si se sostiene.`;
      } else if (e.cambio === 0) {
        positivo = 'El resultado se mantuvo estable entre las dos mediciones.';
      } else if (estado === 'atencion' && esMetricaDeProblema) {
        // Ejemplo específico para defectos
        positivo = 'El equipo está registrando los defectos, lo que permite identificar dónde están apareciendo los problemas.';
      } else if (estado === 'atencion' && esMetricaDeTiempo) {
        positivo = 'El equipo está midiendo el tiempo, lo que permite identificar cuándo las tareas toman más de lo esperado.';
      } else if (estado === 'atencion') {
        positivo = 'El equipo está registrando esta métrica, lo que permite identificar cambios que merecen atención.';
      } else {
        positivo = 'El seguimiento de esta métrica permite detectar cambios entre mediciones.';
      }
      
      let mejora = '';
      if (estado === 'atencion') {
        if (esMetricaDeProblema) {
          mejora = 'Revisar qué tipos de problemas están aumentando y si se concentran en alguna funcionalidad o etapa del desarrollo.';
        } else if (esMetricaDeTiempo) {
          mejora = 'Identificar qué está causando el aumento del tiempo y si hay obstáculos que puedan eliminarse.';
        } else {
          mejora = 'Analizar qué cambios recientes pueden estar relacionados con este aumento.';
        }
      } else if (estado === 'observacion' && icono === '🟢') {
        mejora = 'Verificar qué acciones contribuyeron a esta reducción para mantenerlas en futuros sprints.';
      } else {
        mejora = 'Seguir registrando para poder identificar si hay patrones que merezcan atención.';
      }
      
      let recomendacion = '';
      if (estado === 'atencion') {
        if (esMetricaDeProblema) {
          recomendacion = `En el próximo sprint, clasificar los defectos por tipo o causa y revisar cuáles se repiten con mayor frecuencia. Con esa información, priorizar una o dos causas para intentar reducirlas.`;
        } else if (esMetricaDeTiempo) {
          recomendacion = `Analizar los casos donde el tiempo fue mayor y documentar qué los causó. En el próximo sprint, aplicar al menos una mejora concreta para reducir el tiempo en situaciones similares.`;
        } else {
          recomendacion = `Revisar con el equipo qué cambió entre la primera y segunda medición para entender el aumento.`;
        }
      } else {
        recomendacion = `Registrar esta métrica en al menos dos sprints más para confirmar si el comportamiento observado se mantiene o cambia.`;
      }
      
      return {
        estado,
        icono,
        titulo,
        resumen,
        positivo,
        mejora,
        recomendacion
      };
    }
    
    // ═══════════════════════════════════════════════════════════
    // CASO 3: 3 o más registros (patrón confirmado)
    // ═══════════════════════════════════════════════════════════
    
    // Detectar tipo de métrica con más contexto
    const nombreLower = m.variableNombre.toLowerCase();
    const descripcionLower = (m.variableDescripcion || '').toLowerCase();
    const textoCompleto = nombreLower + ' ' + descripcionLower;
    
    const esMetricaDeProblema = textoCompleto.includes('defecto') || 
                                 textoCompleto.includes('error') || 
                                 textoCompleto.includes('fallo') ||
                                 textoCompleto.includes('incidencia') ||
                                 textoCompleto.includes('problema') ||
                                 textoCompleto.includes('retraso') ||
                                 textoCompleto.includes('registrado') && textoCompleto.includes('durante');
    
    const esMetricaDeTiempo = textoCompleto.includes('tiempo') || 
                               textoCompleto.includes('duración') ||
                               textoCompleto.includes('plazo');
    
    const esMetricaDeResolucion = textoCompleto.includes('resuel') || 
                                   textoCompleto.includes('complet') ||
                                   textoCompleto.includes('cerrad');
    
    let estado: 'favorable' | 'atencion' | 'observacion' | 'insuficiente' = 'observacion';
    let icono = '🔵';
    let titulo = 'Merece revisión';
    
    // Determinar estado según comportamiento y tipo de métrica
    if (e.tendencia === 'ascendente') {
      if (esMetricaDeProblema || esMetricaDeTiempo) {
        estado = 'atencion';
        icono = '🔴';
        titulo = 'Atención necesaria';
      } else if (esMetricaDeResolucion) {
        estado = 'favorable';
        icono = '🟢';
        titulo = 'Va bien';
      } else {
        estado = 'observacion';
        icono = '🔵';
        titulo = 'Merece revisión';
      }
    } else if (e.tendencia === 'descendente') {
      if (esMetricaDeProblema || esMetricaDeTiempo) {
        estado = 'favorable';
        icono = '🟢';
        titulo = 'Va bien';
      } else if (esMetricaDeResolucion) {
        estado = 'atencion';
        icono = '🔴';
        titulo = 'Atención necesaria';
      } else {
        estado = 'observacion';
        icono = '🔵';
        titulo = 'Merece revisión';
      }
    } else if (e.tendencia === 'estable') {
      estado = 'observacion';
      icono = '🟡';
      titulo = 'Resultado estable';
    }
    
    // Generar resumen claro y orientado a la acción
    let resumen = '';
    const cambioAbsoluto = Math.abs(e.cambio);
    
    if (e.tendencia === 'ascendente') {
      if (estado === 'atencion' && esMetricaDeProblema) {
        resumen = `Los problemas aumentaron de ${e.primerValor} a ${e.ultimoValor} (${cambioAbsoluto} unidades más). Esto merece atención porque indica que están apareciendo más problemas durante el desarrollo.`;
      } else if (estado === 'atencion' && esMetricaDeTiempo) {
        resumen = `El tiempo aumentó de ${e.primerValor} a ${e.ultimoValor} (${cambioAbsoluto} unidades más). Esto merece atención porque puede indicar que las tareas están tomando más tiempo de lo esperado.`;
      } else if (estado === 'favorable') {
        resumen = `El valor aumentó de ${e.primerValor} a ${e.ultimoValor}, lo que muestra que el equipo está mejorando en esta área.`;
      } else {
        resumen = `El valor pasó de ${e.primerValor} a ${e.ultimoValor}, aumentando ${cambioAbsoluto} unidades. Conviene revisar si este cambio es positivo o requiere atención según el contexto del equipo.`;
      }
    } else if (e.tendencia === 'descendente') {
      if (estado === 'favorable' && esMetricaDeProblema) {
        resumen = `Los problemas disminuyeron de ${e.primerValor} a ${e.ultimoValor} (${cambioAbsoluto} unidades menos). Esto es un resultado positivo que indica mejora.`;
      } else if (estado === 'favorable' && esMetricaDeTiempo) {
        resumen = `El tiempo disminuyó de ${e.primerValor} a ${e.ultimoValor} (${cambioAbsoluto} unidades menos). Esto muestra mejora en la velocidad o eficiencia.`;
      } else if (estado === 'atencion') {
        resumen = `El valor disminuyó de ${e.primerValor} a ${e.ultimoValor}. Conviene revisar qué está provocando esta reducción y si representa un problema.`;
      } else {
        resumen = `El valor pasó de ${e.primerValor} a ${e.ultimoValor}, bajando ${cambioAbsoluto} unidades. Conviene analizar si este cambio es positivo o requiere atención según el contexto del equipo.`;
      }
    } else {
      resumen = `Los valores se mantienen estables, oscilando entre ${e.minimo} y ${e.maximo}. `;
      if (e.variabilidad === 'baja') {
        resumen += 'Los resultados son consistentes entre mediciones, lo que permite al equipo planear con mayor certeza.';
      } else if (e.variabilidad === 'alta') {
        resumen += 'Sin embargo, hay variaciones importantes entre mediciones, lo que sugiere factores inconsistentes que convendría revisar.';
      } else {
        resumen += 'No se observan cambios significativos que requieran acción inmediata.';
      }
    }
    
    // Generar "Lo positivo": debe ser útil y relevante
    let positivo = '';
    
    if (estado === 'favorable') {
      if (esMetricaDeProblema) {
        positivo = `Los problemas han disminuido respecto a mediciones anteriores, lo que muestra que las acciones del equipo están funcionando.`;
      } else if (esMetricaDeTiempo) {
        positivo = `El tiempo ha bajado respecto a mediciones anteriores, lo que indica mejora en la velocidad o eficiencia.`;
      } else if (esMetricaDeResolucion) {
        positivo = `El equipo ha logrado aumentar su capacidad de resolución comparado con mediciones anteriores.`;
      } else if (e.tendencia === 'descendente') {
        positivo = `El valor ha disminuido respecto a la primera medición, lo que puede ser un resultado positivo.`;
      } else if (e.tendencia === 'ascendente') {
        positivo = `El valor ha aumentado respecto a la primera medición, mostrando mejora.`;
      } else {
        positivo = `Los resultados se mantienen consistentes entre mediciones.`;
      }
      
      // Agregar consistencia si aplica
      if (e.variabilidad === 'baja') {
        positivo += ` Además, los valores son consistentes, lo que facilita la planeación.`;
      }
    } else if (estado === 'observacion') {
      if (e.variabilidad === 'baja' && e.tendencia === 'estable') {
        positivo = `Los resultados se mantienen estables y consistentes, lo que permite al equipo planear con mayor certeza.`;
      } else if (e.tendencia === 'estable') {
        positivo = `Los valores se mantienen en un rango conocido, sin cambios drásticos.`;
      } else {
        positivo = `El seguimiento de esta métrica permite identificar cambios y tomar decisiones informadas.`;
      }
    } else if (estado === 'atencion') {
      // En situaciones de atención, mencionar el valor del seguimiento
      if (esMetricaDeProblema) {
        positivo = `El equipo está registrando los problemas, lo que permite identificar dónde están apareciendo y con qué frecuencia.`;
      } else if (esMetricaDeTiempo) {
        positivo = `El equipo está midiendo el tiempo, lo que permite identificar cuándo las tareas toman más de lo esperado.`;
      } else if (e.variabilidad === 'baja') {
        positivo = `Los valores son consistentes, lo que permite identificar patrones y tomar decisiones fundamentadas.`;
      } else {
        positivo = `El equipo está midiendo esta métrica de forma consistente, lo que permite detectar cambios que merecen atención.`;
      }
    } else {
      positivo = `El seguimiento de esta métrica permite al equipo tomar decisiones informadas.`;
    }    
    // Aspecto a mejorar: directamente relacionado con la métrica
    let mejora = '';
    if (estado === 'atencion') {
      if (esMetricaDeProblema) {
        mejora = 'Revisar qué tipos de problemas están aumentando y si se concentran en alguna funcionalidad, etapa del desarrollo o miembro del equipo.';
      } else if (esMetricaDeTiempo) {
        mejora = 'Identificar qué está causando el aumento del tiempo: cuellos de botella, bloqueos, complejidad técnica o falta de claridad en requisitos.';
      } else if (esMetricaDeResolucion) {
        mejora = 'Analizar qué está afectando la capacidad de resolución: impedimentos, cambios en el equipo, mayor complejidad o sobrecarga de trabajo.';
      } else {
        mejora = 'Identificar qué factores están causando este cambio y determinar si requiere acción correctiva.';
      }
      
      if (e.variabilidad === 'alta') {
        mejora += ' Además, los valores varían mucho entre mediciones, lo que sugiere factores inconsistentes que convendría estabilizar.';
      }
    } else if (estado === 'observacion') {
      if (e.tendencia === 'estable') {
        mejora = 'Identificar si hay oportunidades de mejora que permitan conseguir resultados progresivamente mejores.';
      } else {
        mejora = 'Definir si el cambio observado representa algo positivo, negativo o neutral para el contexto del equipo.';
      }
      
      if (e.variabilidad === 'alta') {
        mejora += ' Los valores varían mucho entre mediciones, lo que indica factores inconsistentes que convendría revisar.';
      }
    } else if (estado === 'favorable') {
      mejora = 'Identificar qué prácticas o acciones específicas contribuyeron a este resultado para mantenerlas o fortalecerlas.';
      
      if (e.variabilidad === 'alta') {
        mejora += ' Los valores aún varían bastante entre mediciones, por lo que convendría buscar mayor consistencia.';
      }
    }
    
    // Recomendación: UNA acción concreta y realizable
    let recomendacion = '';
    if (estado === 'atencion') {
      if (esMetricaDeProblema) {
        recomendacion = `En el próximo sprint, clasificar cada problema registrado por tipo o causa raíz. Identificar los 2 o 3 más frecuentes y definir una acción concreta para reducirlos.`;
      } else if (esMetricaDeTiempo) {
        recomendacion = `Analizar los 3 casos donde el tiempo fue mayor y documentar qué los causó. En el próximo sprint, aplicar al menos una mejora concreta para reducir el tiempo en situaciones similares.`;
      } else if (esMetricaDeResolucion) {
        recomendacion = `En la próxima retrospectiva, revisar qué cambió desde las primeras mediciones. Identificar impedimentos actuales y definir una acción concreta para recuperar la capacidad anterior.`;
      } else {
        recomendacion = `En la próxima retrospectiva, discutir esta métrica con el equipo y decidir si el cambio observado requiere acción o solo seguimiento.`;
      }
    } else if (estado === 'observacion') {
      if (e.tendencia === 'estable') {
        recomendacion = `En la próxima retrospectiva, identificar al menos una pequeña mejora que el equipo pueda probar en el siguiente sprint para ver si impacta positivamente esta métrica.`;
      } else if (e.variabilidad === 'alta') {
        recomendacion = `Revisar qué factores causan las variaciones entre mediciones. En el próximo sprint, intentar estabilizar al menos uno de esos factores.`;
      } else {
        recomendacion = `Continuar midiendo durante los próximos 2 sprints. Si el cambio se mantiene en la misma dirección, revisarlo en retrospectiva para decidir si requiere acción.`;
      }
    } else if (estado === 'favorable') {
      recomendacion = `Documentar qué prácticas específicas contribuyeron a este resultado. Continuar midiéndola para verificar que la mejora se sostiene y compartir el aprendizaje con el equipo.`;
    }
    
    return {
      estado,
      icono,
      titulo,
      resumen,
      positivo,
      mejora,
      recomendacion
    };
  }

  /**
   * DEPRECATED: Análisis textual técnico original.
   * Se mantiene por compatibilidad pero ya no se usa en la UI principal.
   */
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

  abrirDetalle(m: MetricaEvaluacionDetalleDto): void { 
    this.detalleAbierto = m;
    this.mostrarDatosTecnicos = false; // Resetear al abrir
  }
  cerrarDetalle(): void { 
    this.detalleAbierto = null;
    this.mostrarDatosTecnicos = false; // Resetear al cerrar
  }

  // Calcular contribuciones por integrante
  calcularContribucionesPorIntegrante(m: MetricaEvaluacionDetalleDto): Array<{userId: string, userName: string, total: number, porcentaje: number, registros: number}> {
    const contribuciones = new Map<string, {total: number, registros: number}>();
    let totalGeneral = 0;
    
    // Filtrar registros según sprint seleccionado
    const registrosFiltrados = this.sprintFiltro !== null 
      ? m.registros.filter(r => r.sprintId === this.obtenerSprintIdPorNumero(this.sprintFiltro!))
      : m.registros;
    
    // Sumar valores por usuario
    for (const registro of registrosFiltrados) {
      if (registro.valor !== null && registro.userId) {
        const actual = contribuciones.get(registro.userId) || {total: 0, registros: 0};
        contribuciones.set(registro.userId, {
          total: actual.total + registro.valor,
          registros: actual.registros + 1
        });
        totalGeneral += registro.valor;
      }
    }
    
    // Convertir a array y calcular porcentajes
    const resultado = Array.from(contribuciones.entries()).map(([userId, data]) => ({
      userId,
      userName: this.obtenerNombreUsuario(userId),
      total: data.total,
      registros: data.registros,
      porcentaje: totalGeneral > 0 ? Math.round((data.total / totalGeneral) * 100) : 0
    }));
    
    // Ordenar por total descendente
    resultado.sort((a, b) => b.total - a.total);
    
    return resultado;
  }
  
  // Obtener total general de contribuciones
  obtenerTotalGeneral(m: MetricaEvaluacionDetalleDto): number {
    const registrosFiltrados = this.sprintFiltro !== null 
      ? m.registros.filter(r => r.sprintId === this.obtenerSprintIdPorNumero(this.sprintFiltro!))
      : m.registros;
      
    return registrosFiltrados
      .filter(r => r.valor !== null)
      .reduce((sum, r) => sum + (r.valor || 0), 0);
  }
  
  // Obtener nombre de usuario desde el email
  obtenerNombreUsuario(userId: string): string {
    // Extraer nombre del email (antes del @)
    const emailMatch = userId.match(/^([^@]+)/);
    if (emailMatch) {
      return emailMatch[1];
    }
    return userId;
  }
  
  // Obtener ID de sprint por número
  obtenerSprintIdPorNumero(numero: number): string | null {
    if (!this.detalleAbierto) return null;
    const registro = this.detalleAbierto.registros.find(r => r.sprintNumero === numero);
    return registro?.sprintId || null;
  }

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
