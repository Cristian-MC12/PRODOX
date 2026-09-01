// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { catchError, of, forkJoin } from 'rxjs';
import { ShellComponent } from '../../layout/shell/shell.component';
import { AuthService } from '../../services/auth.service';
import { PlaneacionService } from '../../services/planeacion.service';
import { SprintService } from '../../services/sprint.service';
import { SeleccionService } from '../../services/seleccion.service';
import { ProyectoDto } from '../../models/proyecto.model';
import { SprintDto } from '../../models/sprint.model';
import { ProyectoMetricaDto } from '../../models/planeacion.model';
import { VariableDto } from '../../models/variable.model';
import { MetricaSeleccionada } from '../../models/seleccion.model';
import { environment } from '../../../environments/environment';

type Paso = 'metricas' | 'variables' | 'sprints';

@Component({
  selector: 'app-planeacion',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell title="" [showBanner]="false">
      <!-- Header personalizado con slot -->
      <div slot="header" class="d-flex align-items-center justify-content-between flex-grow-1 gap-3">
        <div class="d-flex align-items-center gap-3">
          <h1 style="font-size: 24px; font-weight: 600; color: #1F2937; margin: 0;">Planeación</h1>
          <div class="d-flex align-items-center gap-2">
            <i class="bi bi-folder" style="color: #3B82F6; font-size: 16px;"></i>
            <span style="color: #1F2937; font-weight: 500; font-size: 14px;">{{ proyecto?.nombre || 'Sin proyecto' }}</span>
            @if (proyecto) {
              <span class="badge" style="background: #8B5CF6; font-size: 11px; padding: 4px 10px; border-radius: 6px; font-weight: 600;">
                {{ proyecto.metodo | uppercase }}
              </span>
              <span class="badge" style="background: #10B981; font-size: 11px; padding: 4px 10px; border-radius: 6px; font-weight: 600;">
                Sprint 5 de 5
              </span>
            }
          </div>
        </div>
        @if (proyecto) {
          <div class="d-flex align-items-center gap-2">
            <button class="btn d-flex align-items-center gap-2"
                    style="background: #8B5CF6; color: white; border: none; border-radius: 8px; padding: 10px 18px; font-size: 14px; font-weight: 500; box-shadow: 0 2px 8px rgba(139, 92, 246, 0.3);"
                    (click)="router.navigate(['/crear-metrica-ia'])"
                    title="Crear métrica con Inteligencia Artificial">
              <i class="bi bi-stars" style="font-size: 18px;"></i>
              Crear métrica con IA
            </button>
          </div>
        }
      </div>

      @if (!proyecto) {
        <div class="prox-empty-state">
          <i class="bi bi-folder-x"></i>
          <p>Seleccioná un proyecto primero.</p>
          <button class="btn btn-primary btn-sm mt-3" (click)="router.navigate(['/proyectos'])">
            Ir a Proyectos
          </button>
        </div>
      } @else {

        @if (alertMsg()) {
          <div class="alert py-2 small mb-3" [class]="alertClass()">{{ alertMsg() }}</div>
        }
        <div class="card mb-3" style="background: linear-gradient(135deg, #F5F3FF 0%, #EDE9FE 100%); border: 1px solid #E9D5FF; border-radius: 10px; box-shadow: 0 1px 3px rgba(0,0,0,0.05);">
          <div class="card-body d-flex align-items-center gap-3 py-2 px-3">
            <!-- Icono principal -->
            <div class="d-flex align-items-center justify-content-center flex-shrink-0" 
                 style="width: 44px; height: 44px; background: linear-gradient(135deg, #A78BFA 0%, #8B5CF6 100%); border-radius: 10px; box-shadow: 0 2px 6px rgba(139, 92, 246, 0.25);">
              <i class="bi bi-bullseye" style="font-size: 22px; color: white;"></i>
            </div>
            
            <!-- Título y descripción -->
            <div class="flex-grow-1" style="min-width: 0; max-width: 420px;">
              <h5 class="mb-0 fw-bold" style="font-size: 13px; color: #1F2937; line-height: 1.3;">
                Validar FASE 21 — Dashboard y AI Insights
              </h5>
              <p class="mb-0" style="font-size: 10px; color: #6B7280; line-height: 1.4; word-wrap: break-word; white-space: normal;">
                Definir y validar las métricas que permitirán analizar el rendimiento del equipo
                y obtener insights con inteligencia artificial.
              </p>
            </div>

            <!-- Divisor vertical -->
            <div style="width: 1px; height: 40px; background: linear-gradient(to bottom, transparent, #E9D5FF 20%, #E9D5FF 80%, transparent); flex-shrink: 0;"></div>

            <!-- Métricas en línea con espacio distribuido -->
            <div class="d-flex align-items-center justify-content-between flex-grow-1" style="padding-left: 12px;">
              <!-- Sprints -->
              <div class="text-center" style="min-width: 70px;">
                <div class="d-flex align-items-center justify-content-center mb-1" 
                     style="width: 36px; height: 36px; background: rgba(99, 102, 241, 0.1); border-radius: 50%; margin: 0 auto;">
                  <i class="bi bi-calendar3" style="font-size: 16px; color: #6366F1;"></i>
                </div>
                <div class="fw-bold" style="font-size: 16px; color: #1F2937; line-height: 1.2;">{{ proyecto.numeroSprints }}</div>
                <div style="font-size: 10px; color: #9CA3AF; line-height: 1.2;">Sprints</div>
              </div>

              <!-- Semanas -->
              <div class="text-center" style="min-width: 75px;">
                <div class="d-flex align-items-center justify-content-center mb-1" 
                     style="width: 36px; height: 36px; background: rgba(20, 184, 166, 0.1); border-radius: 50%; margin: 0 auto;">
                  <i class="bi bi-clock" style="font-size: 16px; color: #14B8A6;"></i>
                </div>
                <div class="fw-bold" style="font-size: 16px; color: #1F2937; line-height: 1.2;">{{ proyecto.timeBoxSemanas }} sem</div>
                <div style="font-size: 10px; color: #9CA3AF; line-height: 1.2;">por iteración</div>
              </div>

              <!-- Fecha inicio -->
              @if (proyecto.fechaInicio) {
                <div class="text-center" style="min-width: 95px;">
                  <div class="d-flex align-items-center justify-content-center mb-1" 
                       style="width: 36px; height: 36px; background: rgba(139, 92, 246, 0.1); border-radius: 50%; margin: 0 auto;">
                    <i class="bi bi-calendar-check" style="font-size: 16px; color: #8B5CF6;"></i>
                  </div>
                  <div class="fw-bold" style="font-size: 16px; color: #1F2937; line-height: 1.2;">{{ proyecto.fechaInicio | date:'dd/MM/yyyy' }}</div>
                  <div style="font-size: 10px; color: #9CA3AF; line-height: 1.2;">Fecha de inicio</div>
                </div>
              }

              <!-- Progreso -->
              <div class="text-center" style="min-width: 95px;">
                <div class="d-flex align-items-center justify-content-center mb-1" 
                     style="width: 36px; height: 36px; background: rgba(16, 185, 129, 0.1); border-radius: 50%; margin: 0 auto;">
                  <i class="bi bi-check-circle" style="font-size: 16px; color: #10B981;"></i>
                </div>
                <div class="fw-bold" style="font-size: 16px; color: #1F2937; line-height: 1.2;">{{ progresoPlaneacion }}%</div>
                <div style="font-size: 10px; color: #9CA3AF; line-height: 1.2;">Progreso de planeación</div>
              </div>
            </div>
          </div>
        </div>


        <!-- Tabs de fase -->
        <ul class="nav nav-tabs mb-3">
          <li class="nav-item">
            <button class="nav-link" [class.active]="paso === 'metricas'" (click)="paso = 'metricas'">
              <i class="bi bi-bar-chart-steps me-1"></i>
              Métricas
              <span class="badge ms-1" [class]="totalSeleccionadas > 0 ? 'bg-primary' : 'bg-secondary'">
                {{ totalSeleccionadas }}
              </span>
            </button>
          </li>
          <li class="nav-item">
            <button class="nav-link" [class.active]="paso === 'variables'" (click)="cargarVariables(); paso = 'variables'">
              <i class="bi bi-list-check me-1"></i>
              Variables
              <span class="badge ms-1" [class]="variables.length > 0 ? 'bg-success' : 'bg-secondary'">
                {{ variables.length }}
              </span>
            </button>
          </li>
          <li class="nav-item">
            <button class="nav-link" [class.active]="paso === 'sprints'" (click)="paso = 'sprints'">
              <i class="bi bi-calendar3 me-1"></i>
              Calendario de Sprints
            </button>
          </li>
        </ul>

        <!-- ── TAB: MÉTRICAS ──────────────────────────────────────────── -->
        @if (paso === 'metricas') {
          <div class="row g-3">

            <!-- Panel izquierdo: catálogo agrupado -->
            <div class="col-lg-7">
              <div class="card" style="height: calc(100vh - 340px); min-height: 500px; display: flex; flex-direction: column;">
                <div class="card-header d-flex align-items-center justify-content-between gap-2 py-2" style="flex-shrink: 0; flex-wrap: nowrap;">
                  <span class="fw-semibold small text-nowrap">
                    <i class="bi bi-grid me-1"></i>Catálogo de Métricas
                  </span>
                  <div class="d-flex gap-2 align-items-center" style="flex-wrap: nowrap;">
                    <button class="btn btn-outline-primary btn-sm"
                            style="white-space: nowrap; font-size: 12px; padding: 4px 8px;"
                            (click)="router.navigate(['/crear-metrica-ia'])"
                            title="Crear métrica con IA">
                      <i class="bi bi-robot me-1"></i>Crear con IA
                    </button>
                    <select class="form-select form-select-sm"
                            style="width: 140px; font-size: 12px;"
                            [(ngModel)]="categoriaFiltro">
                      <option value="">Todas</option>
                      @for (cat of categorias; track cat) {
                        <option [value]="cat">{{ cat }}</option>
                      }
                    </select>
                    <input type="text" class="form-control form-control-sm"
                           style="width: 120px; font-size: 12px;"
                           placeholder="Buscar..."
                           [(ngModel)]="busqueda">
                  </div>
                </div>
                <div class="card-body p-0 flex-grow-1" style="overflow-y:auto">
                  @if (cargando) {
                    <div class="text-center py-4 text-muted small">
                      <span class="spinner-border spinner-border-sm me-2"></span>Cargando...
                    </div>
                  } @else {
                    @for (cat of categorias; track cat) {
                      @if (metricasFiltradas(cat).length > 0) {
                        <!-- Categoría principal -->
                        <div class="px-3 pt-2 pb-1 d-flex align-items-center gap-2">
                          <span class="badge" [class]="badgeCategoria(cat)">{{ cat }}</span>
                        </div>
                        <!-- Factores dentro de la categoría -->
                        @for (factor of factoresDeCat(cat); track factor) {
                          @if (metricasDeFactor(cat, factor).length > 0) {
                            <div class="px-3 py-1 bg-light border-bottom">
                              <span class="small text-muted fw-semibold">
                                <i class="bi bi-diagram-3 me-1"></i>{{ factor }}
                              </span>
                            </div>
                            @for (m of metricasDeFactor(cat, factor); track m.metricaId) {
                              <div class="d-flex align-items-start gap-2 px-3 py-2 border-bottom metrica-row"
                                   [class.bg-success-subtle]="m.aprobada"
                                   [class.bg-primary-subtle]="estaSeleccionada(m) && !m.aprobada">
                                <div class="flex-grow-1">
                                  <div class="fw-semibold small">{{ m.nombre }}</div>
                                  <div class="text-muted" style="font-size:0.72rem">{{ m.descripcion }}</div>
                                  <div class="mt-1 d-flex gap-1 flex-wrap">
                                    <span class="badge bg-light text-dark border prox-badge-sm">
                                      {{ m.codigo }}
                                    </span>
                                    @if (m.aprobada) {
                                      <span class="badge bg-success prox-badge-sm">
                                        <i class="bi bi-check me-1"></i>Aprobada
                                      </span>
                                      @if (m.tieneVariable) {
                                        <span class="badge bg-info text-dark prox-badge-sm">
                                          <i class="bi bi-lightning me-1"></i>Variable generada
                                        </span>
                                      }
                                    } @else if (estaSeleccionada(m)) {
                                      <span class="badge bg-primary prox-badge-sm">
                                        <i class="bi bi-check2 me-1"></i>Seleccionada
                                      </span>
                                      <span class="badge bg-warning text-dark prox-badge-sm">
                                        Pendiente aprobación
                                      </span>
                                    } @else {
                                      <span class="badge bg-light text-dark border prox-badge-sm">
                                        Disponible
                                      </span>
                                    }
                                  </div>
                                </div>
                                <div class="d-flex gap-1 flex-shrink-0 pt-1">
                                  @if (!estaSeleccionada(m)) {
                                    <button class="btn btn-sm btn-outline-primary btn-icon"
                                            (click)="seleccionar(m)" title="Seleccionar">
                                      <i class="bi bi-plus-lg"></i>
                                    </button>
                                  } @else if (!m.aprobada) {
                                    <button class="btn btn-sm btn-success btn-icon"
                                            (click)="aprobar(m)" title="Aprobar y generar variable"
                                            [disabled]="aprobando === m.metricaId">
                                      @if (aprobando === m.metricaId) {
                                        <span class="spinner-border spinner-border-sm"></span>
                                      } @else {
                                        <i class="bi bi-check-lg"></i>
                                      }
                                    </button>
                                    <button class="btn btn-sm btn-outline-danger btn-icon"
                                            (click)="deseleccionar(m)" title="Quitar">
                                      <i class="bi bi-x-lg"></i>
                                    </button>
                                  } @else {
                                    <button class="btn btn-sm btn-outline-warning btn-icon"
                                            (click)="desaprobar(m)" title="Desaprobar">
                                      <i class="bi bi-arrow-counterclockwise"></i>
                                    </button>
                                  }
                                </div>
                              </div>
                            }
                          }
                        }
                      }
                    }
                  }
                </div>
              </div>
            </div>

            <!-- Panel derecho: Plan de medición -->
            <div class="col-lg-5">
              <div class="card" style="height: calc(100vh - 340px); min-height: 500px; display: flex; flex-direction: column;">
                <div class="card-body p-3" style="display: flex; flex-direction: column; overflow: hidden;">
                  <!-- Header: Plan de medición -->
                  <div class="d-flex align-items-center justify-content-between mb-3" style="flex-shrink: 0;">
                    <h6 class="mb-0" style="font-weight: 600; font-size: 15px; color: #1F2937;">Plan de medición</h6>
                    <div class="d-flex align-items-center gap-2">
                      <span class="badge" style="background: rgba(139, 92, 246, 0.1); color: #8B5CF6; font-size: 11px; padding: 4px 10px; border-radius: 12px; font-weight: 600;">
                        {{ totalSeleccionadas }} seleccionadas
                      </span>
                      <span class="badge" style="background: rgba(16, 185, 129, 0.1); color: #10B981; font-size: 11px; padding: 4px 10px; border-radius: 12px; font-weight: 600;">
                        <i class="bi bi-check-circle me-1"></i>{{ totalAprobadas }} aprobadas
                      </span>
                    </div>
                  </div>

                  <!-- Contenedor scrolleable -->
                  <div style="flex-grow: 1; overflow-y: auto; overflow-x: hidden;">
                    @if (seleccionadasList.length === 0 && historialAprobadas.length === 0) {
                      <div class="text-center text-muted py-4" style="font-size: 13px;">
                        Seleccioná métricas del catálogo para comenzar tu plan.
                      </div>
                    } @else {
                      <!-- Métricas pendientes (no aprobadas) -->
                      @for (m of seleccionadasList; track m.metricaId) {
                        <div class="d-flex align-items-center justify-content-between py-2 border-bottom">
                          <div class="d-flex align-items-center gap-2 flex-grow-1" style="min-width: 0;">
                            <i class="bi bi-grip-vertical" style="color: #D1D5DB; font-size: 14px;"></i>
                            <span style="font-size: 13px; font-weight: 500; color: #1F2937; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
                              {{ m.nombre }}
                            </span>
                          </div>
                          <div class="d-flex align-items-center gap-2 flex-shrink-0">
                            <!-- Botón parametrizar -->
                            <button class="btn btn-sm d-flex align-items-center justify-content-center" 
                                    style="width: 28px; height: 28px; background: rgba(139, 92, 246, 0.1); border: 1px solid #E9D5FF; border-radius: 6px; padding: 0;"
                                    (click)="irAParametrizar(m)"
                                    title="Parametrizar con GenAI">
                              <i class="bi bi-stars" style="color: #8B5CF6; font-size: 13px;"></i>
                            </button>
                            
                            <!-- Botón ver parametrización (solo si existe) -->
                            @if (estadoParametrizacion(m) !== 'sin_parametrizar') {
                              <button class="btn btn-sm d-flex align-items-center justify-content-center" 
                                      style="width: 28px; height: 28px; background: rgba(59, 130, 246, 0.1); border: 1px solid #DBEAFE; border-radius: 6px; padding: 0;"
                                      (click)="verParametrizacion(m)"
                                      title="Ver parametrización">
                                <i class="bi bi-eye" style="color: #3B82F6; font-size: 13px;"></i>
                              </button>
                            }
                            
                            <!-- Botón eliminar -->
                            <button class="btn btn-sm d-flex align-items-center justify-content-center" 
                                    style="width: 28px; height: 28px; background: rgba(239, 68, 68, 0.1); border: 1px solid #FEE2E2; border-radius: 6px; padding: 0;"
                                    (click)="deseleccionar(m)" 
                                    title="Quitar de seleccionadas">
                              <i class="bi bi-x-lg" style="color: #EF4444; font-size: 13px;"></i>
                            </button>
                          </div>
                        </div>
                      }
                      
                      <!-- Historial desplegable de métricas aprobadas -->
                      @if (historialAprobadas.length > 0) {
                        <div class="mt-2">
                          <button class="btn btn-sm w-100 d-flex align-items-center justify-content-between py-2 border-0"
                                  style="background: rgba(16, 185, 129, 0.05); border-radius: 6px;"
                                  (click)="historialDesplegado = !historialDesplegado">
                            <div class="d-flex align-items-center gap-2">
                              <i class="bi bi-clock-history" style="color: #10B981; font-size: 14px;"></i>
                              <span style="font-size: 13px; font-weight: 500; color: #1F2937;">Historial de aprobadas</span>
                              <span class="badge" style="background: #10B981; font-size: 10px; padding: 2px 6px; border-radius: 8px; color: white;">
                                {{ historialAprobadas.length }}
                              </span>
                            </div>
                            <i class="bi" [class]="historialDesplegado ? 'bi-chevron-up' : 'bi-chevron-down'" 
                               style="color: #6B7280; font-size: 12px;"></i>
                          </button>
                          
                          @if (historialDesplegado) {
                            <div class="mt-2">
                              @for (m of historialAprobadas; track m.metricaId) {
                                <div class="d-flex align-items-center justify-content-between py-2 px-2 border-bottom" 
                                     style="background: rgba(16, 185, 129, 0.02);">
                                  <div class="flex-grow-1" style="min-width: 0;">
                                    <div style="font-size: 13px; font-weight: 500; color: #1F2937; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
                                      {{ m.nombre }}
                                    </div>
                                    @if (m.seleccionadaAt) {
                                      <div style="font-size: 10px; color: #9CA3AF;">
                                        Aprobada: {{ m.seleccionadaAt | date:'dd/MM/yyyy HH:mm' }}
                                      </div>
                                    }
                                  </div>
                                  <div class="d-flex align-items-center gap-2 flex-shrink-0">
                                    <i class="bi bi-check-circle-fill" style="color: #10B981; font-size: 16px;" title="Aprobada"></i>
                                  </div>
                                </div>
                              }
                            </div>
                          }
                        </div>
                      }
                    }
                  </div>
                  </div>
                  <!-- Fin contenedor scrolleable -->

                  <!-- Botón compacto (fijo) -->
                  @if (historialSeleccionadas.length > 0) {
                    <button class="btn w-100 d-flex align-items-center justify-content-center gap-2"
                            style="background: #0891B2; color: white; border: none; border-radius: 8px; padding: 8px 12px; font-size: 13px; font-weight: 500; box-shadow: 0 1px 3px rgba(8, 145, 178, 0.2); flex-shrink: 0; margin-top: 12px;"
                            (click)="router.navigate(['/resumen-seleccion'])">
                      Revisar y enviar al Scrum Master
                      <i class="bi bi-arrow-right" style="font-size: 14px;"></i>
                    </button>
                  }
                  <!-- Fin card-body scrolleable interno -->
                </div>
              </div>
            </div>
        }

        <!-- ── TAB: VARIABLES ─────────────────────────────────────────── -->
        @if (paso === 'variables') {
          <div class="card">
            <div class="card-header fw-semibold small py-2">
              <i class="bi bi-list-check me-1"></i>Variables generadas automáticamente
            </div>
            @if (variables.length === 0) {
              <div class="prox-empty-state">
                <i class="bi bi-lightning"></i>
                <p>No hay variables generadas. Aprobá métricas en la pestaña anterior.</p>
              </div>
            } @else {
              <div class="table-responsive">
                <table class="table table-sm table-hover mb-0">
                  <thead class="table-light">
                    <tr>
                      <th class="ps-3">Variable</th>
                      <th>Categoría</th>
                      <th>Alcance</th>
                      <th>Tipo dato</th>
                      <th>Frecuencia</th>
                      <th>Cardinalidad</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (v of variables; track v.id) {
                      <tr>
                        <td class="ps-3">
                          <div class="fw-semibold small">{{ v.nombre }}</div>
                          <div class="text-muted" style="font-size:0.7rem">{{ v.descripcion }}</div>
                        </td>
                        <td>
                          <span class="badge prox-badge-sm" [class]="badgeCategoria(v.metricaCategoria)">
                            {{ v.metricaCategoria }}
                          </span>
                        </td>
                        <td>
                          <span class="badge prox-badge-sm"
                                [class]="v.tipoAlcance === 'grupal' ? 'bg-primary' : 'bg-warning text-dark'">
                            <i class="bi me-1"
                               [class]="v.tipoAlcance === 'grupal' ? 'bi-people' : 'bi-person'"></i>
                            {{ v.tipoAlcance | titlecase }}
                          </span>
                        </td>
                        <td class="small text-muted">{{ v.tipoDato }}</td>
                        <td class="small text-muted">{{ v.frecuencia | titlecase }}</td>
                        <td class="small text-muted">{{ v.cardinalidad | titlecase }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>
        }

        <!-- ── TAB: CALENDARIO SPRINTS ───────────────────────────────── -->
        @if (paso === 'sprints') {
          <div class="card">
            <div class="card-header fw-semibold small py-2">
              <i class="bi bi-calendar3 me-1"></i>Calendario de Sprints
            </div>
            @if (sprints.length === 0) {
              <div class="text-center py-4 text-muted small">
                <span class="spinner-border spinner-border-sm me-2"></span>Cargando...
              </div>
            } @else {
              <div class="table-responsive">
                <table class="table table-sm mb-0">
                  <thead class="table-light">
                    <tr>
                      <th class="ps-3" style="width:60px">#</th>
                      <th>Sprint Goal</th>
                      <th style="width:120px">Inicio</th>
                      <th style="width:120px">Fin</th>
                      <th style="width:110px">Estado</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (s of sprints; track s.id) {
                      <tr [class.table-success]="s.estado === 'en_ejecucion'">
                        <td class="ps-3 fw-bold align-middle">{{ s.numero }}</td>
                        <td class="small align-middle">{{ s.sprintGoal || ('Sprint ' + s.numero) }}</td>
                        <td class="small text-muted align-middle">
                          {{ s.fechaInicio | date:'dd/MM/yyyy' }}
                        </td>
                        <td class="small text-muted align-middle">
                          {{ s.fechaFin ? (s.fechaFin | date:'dd/MM/yyyy') : '—' }}
                        </td>
                        <td class="align-middle">
                          <span class="badge prox-badge-sm"
                                [class]="badgeSprint(s.estado)">
                            {{ labelSprint(s.estado) }}
                          </span>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
              <!-- Barra visual de timeline -->
              <div class="card-footer py-3">
                <div class="small text-muted mb-2">Timeline del proyecto</div>
                <div class="d-flex gap-1 flex-wrap">
                  @for (s of sprints; track s.id) {
                    <div class="rounded px-2 py-1 text-white text-center small"
                         [class]="badgeSprint(s.estado)"
                         style="min-width:60px;font-size:0.7rem">
                      S{{ s.numero }}
                    </div>
                  }
                </div>
              </div>
            }
          </div>
        }

      }

      <!-- Modal para ver parametrización -->
      @if (parametrizacionVista) {
        <div class="modal d-block" style="background-color: rgba(0,0,0,0.5)" (click)="cerrarModalParametrizacion()">
          <div class="modal-dialog modal-lg" (click)="$event.stopPropagation()">
            <div class="modal-content">
              <div class="modal-header">
                <h5 class="modal-title">
                  <i class="bi bi-eye me-2"></i>Parametrización: {{ parametrizacionVista.nombre }}
                </h5>
                <button type="button" class="btn-close" (click)="cerrarModalParametrizacion()"></button>
              </div>
              <div class="modal-body">
                <div class="mb-3">
                  <strong class="text-primary"><i class="bi bi-bullseye me-1"></i>Objetivo:</strong>
                  <p class="mt-1">{{ parametrizacionVista.objetivo }}</p>
                </div>
                <div class="mb-3">
                  <strong class="text-primary"><i class="bi bi-list-ol me-1"></i>Procedimiento:</strong>
                  <p class="mt-1">{{ parametrizacionVista.procedimiento }}</p>
                </div>
                @if (parametrizacionVista.indicadorVariable) {
                  <div class="mb-3">
                    <strong class="text-primary"><i class="bi bi-speedometer2 me-1"></i>Indicador/Variable:</strong>
                    <p class="mt-1">{{ parametrizacionVista.indicadorVariable }}</p>
                  </div>
                }
                <div class="mb-3">
                  <strong class="text-primary"><i class="bi bi-bar-chart-steps me-1"></i>Escala:</strong>
                  <p class="mt-1">{{ parametrizacionVista.escala }}</p>
                </div>
                @if (parametrizacionVista.frecuenciaCaptura) {
                  <div>
                    <strong class="text-primary"><i class="bi bi-calendar me-1"></i>Frecuencia de captura:</strong>
                    <p class="mt-1">{{ parametrizacionVista.frecuenciaCaptura }}</p>
                  </div>
                }
              </div>
              <div class="modal-footer">
                <button class="btn btn-secondary btn-sm" (click)="cerrarModalParametrizacion()">Cerrar</button>
              </div>
            </div>
          </div>
        </div>
      }
    </app-shell>
  `,
  styles: [`
    .metrica-row:hover { background-color: var(--bs-light); cursor: default; }
  `]
})
export class PlaneacionComponent implements OnInit {
  proyecto: ProyectoDto | null = null;
  metricas: ProyectoMetricaDto[] = [];
  variables: VariableDto[] = [];
  sprints: SprintDto[] = [];
  paso: Paso = 'metricas';
  cargando = true;
  busqueda = '';
  categoriaFiltro = '';
  aprobando: string | null = null;
  historialDesplegado = false; // Control del accordion del historial

  alertMsg   = signal('');
  alertClass = signal('alert-success');

  private selecciones: MetricaSeleccionada[] = [];
  parametrizacionesBackend: Map<string, any> = new Map(); // metricaId -> parametrizacion
  parametrizacionVista: any = null; // Para el modal

  readonly categorias = ['Significado', 'Flexibilidad', 'Impacto', 'Socio-Humano FSH'];

  // FASE 4: catálogo de NUEVAS selecciones limitado a estas 5 métricas.
  // Las demás siguen existiendo en BD sin ningún cambio; si un proyecto ya
  // las tiene seleccionadas/aprobadas, siguen apareciendo normalmente en
  // "Seleccionadas"/"Historial" (esos paneles no usan metricasFiltradas(),
  // filtran this.metricas directamente) — este filtro solo aplica al
  // panel de catálogo donde se eligen métricas nuevas.
  readonly METRICAS_VISIBLES = new Set<string>([
    'd0a56045-a0f5-47bf-9e96-a2056a99c709', // Defectos (SIG-CE-02)
    '9dd2745a-63ee-42db-8f00-ec6ef279532d', // Deuda técnica gestionada (FLX-GAE-02)
    '7e73e324-c4ef-44f2-9111-7c64e1226c1f', // Aprendizaje organizacional (FAT) (FLX-FAT-01)
    'c0fbef4a-6103-4d57-8b5a-b7c7e55c7fd8', // Defectos encontrados (IMP-CAL-01)
    'a0327bb1-362c-4f3e-9bc0-a3c34aad9bf0', // Errores en producción (IMP-CAL-02)
  ]);

  constructor(
    public  router: Router,
    public  auth: AuthService,
    private planeacionService: PlaneacionService,
    private sprintService: SprintService,
    private seleccionService: SeleccionService,
    private http: HttpClient
  ) {}

  // "Seleccionadas" representa únicamente las métricas que están dentro del
  // estado pendiente de Planeación: seleccionadas pero todavía NO aprobadas.
  // Una vez aprobada, la métrica sale de este panel y queda disponible en Ejecución.
  get totalSeleccionadas() { return this.seleccionadasList.length; }
  get totalAprobadas()     { return this.metricas.filter(m => m.aprobada).length; }
  get seleccionadasList()  { return this.metricas.filter(m => this.estaSeleccionada(m) && !m.aprobada); }

  // "Historial de seleccionadas": vista puramente informativa con TODAS las métricas
  // que alguna vez fueron seleccionadas en este proyecto, hayan sido aprobadas o no.
  // A diferencia de seleccionadasList, no se filtra por `aprobada`, así que una métrica
  // que ya pasó a Ejecución permanece aquí. Ordenado por fecha de selección (persistida
  // en ProyectoMetrica.createdAt), más reciente primero.
  get historialSeleccionadas(): ProyectoMetricaDto[] {
    return this.metricas
      .filter(m => m.seleccionada)
      .slice()
      .sort((a, b) => new Date(b.seleccionadaAt ?? 0).getTime() - new Date(a.seleccionadaAt ?? 0).getTime());
  }
  get parametrizacionesCompletas() {
    return this.seleccionadasList.filter(m => this.estadoParametrizacion(m) === 'completa').length;
  }

  /** Calcula el progreso de planeación basado en métricas aprobadas y parametrizadas */
  get progresoPlaneacion(): number {
    if (this.metricas.length === 0) return 0;
    
    const totalMetricas = this.metricas.filter(m => m.seleccionada).length;
    if (totalMetricas === 0) return 0;
    
    const metricasAprobadas = this.metricas.filter(m => m.aprobada).length;
    const metricasParametrizadas = this.seleccionadasList.filter(m => 
      this.estadoParametrizacion(m) === 'completa'
    ).length;
    
    // Progreso = (aprobadas + parametrizadas completas) / total seleccionadas
    const progreso = ((metricasAprobadas + metricasParametrizadas) / totalMetricas) * 100;
    return Math.round(progreso);
  }

  /** Obtiene solo las métricas aprobadas del historial */
  get historialAprobadas(): ProyectoMetricaDto[] {
    return this.historialSeleccionadas.filter(m => m.aprobada);
  }

  ngOnInit(): void {
    try {
      const p = localStorage.getItem('mpdia_proyecto_activo');
      this.proyecto = p ? JSON.parse(p) : null;
    } catch { /* ignore */ }

    // Suscribirse a cambios de parametrización
    this.seleccionService.getAll().subscribe(s => this.selecciones = s);

    if (this.proyecto) {
      forkJoin({
        metricas: this.planeacionService.listarMetricas(this.proyecto.id).pipe(catchError(() => of([]))),
        sprints:  this.sprintService.listar(this.proyecto.id).pipe(catchError(() => of([]))),
        parametrizaciones: this.http.get<any[]>(`${environment.apiBaseUrl}/metric-ranking/pendientes?proyectoId=${this.proyecto.id}`).pipe(catchError(() => of([]))),
        variables: this.planeacionService.listarVariables(this.proyecto.id).pipe(catchError(() => of([])))
      }).subscribe(({ metricas, sprints, parametrizaciones, variables }) => {
        this.metricas = metricas;
        this.sprints  = [...sprints].sort((a, b) => a.numero - b.numero);
        this.variables = variables;
        
        // Indexar parametrizaciones por metricaId
        parametrizaciones.forEach(p => {
          if (p.metricaId) {
            this.parametrizacionesBackend.set(p.metricaId, p);
          }
        });
        
        this.cargando = false;
        // Sincronizar métricas seleccionadas con SeleccionService
        this.sincronizarSelecciones();
      });
    } else {
      this.cargando = false;
    }
  }

  /** Sincroniza las métricas seleccionadas del backend con el SeleccionService (localStorage) */
  private sincronizarSelecciones(): void {
    if (!this.proyecto) return;
    const proyectoId = this.proyecto.id;

    // Quitar del SeleccionService las métricas que ya están aprobadas en el backend
    this.metricas.filter(m => m.aprobada).forEach(m => {
      const sel = this.seleccionService.getSnapshot().find(
        s => s.factorId === m.metricaId || s.metricaNombre === m.nombre
      );
      if (sel) this.seleccionService.quitar(sel.id);
    });

    // Agregar métricas seleccionadas del backend que NO estén ya en SeleccionService
    this.seleccionadasList.filter(m => !m.aprobada).forEach(m => {
      const yaExiste = this.seleccionService.getSnapshot().find(
        s => s.factorId === m.metricaId || s.metricaNombre === m.nombre
      );
      
      // Solo agregar si NO existe ya (para no sobrescribir parametrizaciones)
      if (!yaExiste) {
        this.seleccionService.agregar({
          factorId:           m.metricaId,
          factorNombre:       m.nombre,
          factorCategoria:    m.categoria,
          metricaNombre:      m.nombre,
          metricaDescripcion: m.descripcion ?? '',
          proyectoId
        });
      }
    });
  }

  estadoParametrizacion(m: ProyectoMetricaDto): 'sin_parametrizar' | 'parcial' | 'completa' {
    // Primero verificar en el backend
    const paramBackend = this.parametrizacionesBackend.get(m.metricaId);
    if (paramBackend && paramBackend.objetivo && paramBackend.procedimiento && paramBackend.escala) {
      return 'completa';
    }
    
    // Si no está en backend, verificar en localStorage
    const sel = this.selecciones.find(
      s => s.factorId === m.metricaId || s.metricaNombre === m.nombre
    );
    return sel?.estadoParametrizacion ?? 'sin_parametrizar';
  }

  verParametrizacion(m: ProyectoMetricaDto): void {
    const param = this.parametrizacionesBackend.get(m.metricaId);
    if (param) {
      this.parametrizacionVista = {
        nombre: m.nombre,
        ...param
      };
    }
  }

  cerrarModalParametrizacion(): void {
    this.parametrizacionVista = null;
  }

  irAParametrizar(m: ProyectoMetricaDto): void {
    if (!this.proyecto) return;
    // Asegurar que esté en el SeleccionService antes de navegar
    this.seleccionService.agregar({
      factorId:          m.metricaId,
      factorNombre:      m.nombre,
      factorCategoria:   m.categoria,
      metricaNombre:     m.nombre,
      metricaDescripcion: m.descripcion ?? '',
      proyectoId: this.proyecto.id
    });
    const sel = this.seleccionService.getSnapshot().find(
      s => s.factorId === m.metricaId || s.metricaNombre === m.nombre
    );
    if (sel) {
      this.router.navigate(['/parametrizacion', sel.id]);
    }
  }

  cargarVariables(): void {
    if (!this.proyecto) return;
    this.planeacionService.listarVariables(this.proyecto.id).pipe(
      catchError(() => of([]))
    ).subscribe(v => this.variables = v);
  }

  /** FASE 15: prefijo único (V27__metrica_ia_secuencia_codigo.sql) de las métricas
   *  creadas mediante "Crear métrica con IA" — nunca lo usa ninguna métrica
   *  preexistente del catálogo sembrado. */
  private esMetricaCreadaConIA(m: ProyectoMetricaDto): boolean {
    return !!m.codigo?.startsWith('IA-');
  }

  metricasFiltradas(categoria: string): ProyectoMetricaDto[] {
    if (this.categoriaFiltro && this.categoriaFiltro !== categoria) return [];
    return this.metricas.filter(m =>
      // Además de las 5 oficiales, el catálogo muestra TODAS las métricas
      // creadas con IA (Metrica es un catálogo GLOBAL desde V31: cualquier
      // proyecto puede reutilizar una métrica creada por otro). Antes, una
      // métrica de IA solo aparecía en el catálogo de un proyecto una vez
      // que ESE proyecto ya la había seleccionado — así, el Proyecto B nunca
      // veía como "Disponible" una métrica creada por el Proyecto A, aunque
      // el catálogo global ya la tuviera. m.seleccionada sigue viniendo
      // scopeada por proyecto desde el backend (PlaneacionService.
      // listarMetricasConEstado), así que la distinción Disponible/
      // Seleccionada se sigue calculando correctamente por proyecto — solo
      // cambió QUÉ filas se listan, no de quién es cada selección. Las demás
      // métricas del catálogo no-whitelisteadas (ej. las ocultas de FASE 4,
      // que no son de IA) permanecen exactamente igual que antes: fuera del
      // panel de catálogo aunque ya estén seleccionadas/aprobadas.
      (this.METRICAS_VISIBLES.has(m.metricaId) || this.esMetricaCreadaConIA(m)) &&
      m.categoria === categoria &&
      // Revisión de Planeación: una métrica ya seleccionada por el proyecto
      // ACTUAL no debe seguir ofreciéndose como opción en el catálogo — su
      // estado ya se representa en "Seleccionadas"/"Historial" (que no usan
      // este método, ver sus getters). m.seleccionada ya viene scopeada por
      // proyecto desde el backend, así que esto no afecta en absoluto a otro
      // proyecto que también use esta misma Metrica global.
      !this.estaSeleccionada(m) &&
      (!this.busqueda || m.nombre.toLowerCase().includes(this.busqueda.toLowerCase()) ||
       m.codigo?.toLowerCase().includes(this.busqueda.toLowerCase()))
    );
  }

  /** Factores únicos dentro de una categoría (respetando búsqueda) */
  factoresDeCat(categoria: string): string[] {
    return [...new Set(
      this.metricasFiltradas(categoria).map(m => m.factor ?? 'Sin factor')
    )];
  }

  /** Métricas de un factor específico dentro de una categoría */
  metricasDeFactor(categoria: string, factor: string): ProyectoMetricaDto[] {
    return this.metricasFiltradas(categoria).filter(
      m => (m.factor ?? 'Sin factor') === factor
    );
  }

  estaSeleccionada(m: ProyectoMetricaDto): boolean {
    return m.seleccionada;
  }

  seleccionar(m: ProyectoMetricaDto): void {
    if (!this.proyecto) return;
    this.planeacionService.seleccionar(this.proyecto.id, m.metricaId).pipe(
      catchError(err => { this.alert(err?.error?.error ?? 'Error', 'alert-danger'); return of(null); })
    ).subscribe(() => {
      this.recargar();
      // Agregar al SeleccionService para parametrización
      this.seleccionService.agregar({
        factorId:          m.metricaId,
        factorNombre:      m.nombre,
        factorCategoria:   m.categoria,
        metricaNombre:     m.nombre,
        metricaDescripcion: m.descripcion ?? '',
        proyectoId: this.proyecto!.id
      });
    });
  }

  deseleccionar(m: ProyectoMetricaDto): void {
    if (!this.proyecto) return;
    this.planeacionService.deseleccionar(this.proyecto.id, m.metricaId).pipe(
      catchError(err => { this.alert(err?.error?.error ?? 'Error', 'alert-danger'); return of(null); })
    ).subscribe(() => {
      this.recargar();
      // Quitar del SeleccionService
      const sel = this.selecciones.find(
        s => s.factorId === m.metricaId || s.metricaNombre === m.nombre
      );
      if (sel) this.seleccionService.quitar(sel.id);
    });
  }

  aprobar(m: ProyectoMetricaDto): void {
    if (!this.proyecto) return;
    this.aprobando = m.metricaId;
    this.planeacionService.aprobar(this.proyecto.id, m.metricaId).pipe(
      catchError(err => { this.alert(err?.error?.error ?? 'Error', 'alert-danger'); return of(null); })
    ).subscribe(v => {
      if (v) this.alert(`Variable "${v.nombre}" generada automáticamente.`, 'alert-success');
      this.aprobando = null;
      // Quitar del SeleccionService ya que pasó a ejecución
      const sel = this.selecciones.find(
        s => s.factorId === m.metricaId || s.metricaNombre === m.nombre
      );
      if (sel) this.seleccionService.quitar(sel.id);
      this.recargar();
    });
  }

  desaprobar(m: ProyectoMetricaDto): void {
    if (!this.proyecto) return;
    this.planeacionService.desaprobar(this.proyecto.id, m.metricaId).pipe(
      catchError(err => { this.alert(err?.error?.error ?? 'Error', 'alert-danger'); return of(null); })
    ).subscribe(() => this.recargar());
  }

  private recargar(): void {
    if (!this.proyecto) return;
    this.planeacionService.listarMetricas(this.proyecto.id).pipe(
      catchError(() => of([]))
    ).subscribe(m => this.metricas = m);
  }

  badgeCategoria(cat: string): string {
    const map: Record<string, string> = {
      'Significado':      'bg-primary',
      'Flexibilidad':     'bg-warning text-dark',
      'Impacto':          'bg-danger',
      'Socio-Humano FSH': 'bg-info text-dark'
    };
    return map[cat] ?? 'bg-secondary';
  }

  badgeSprint(estado: string): string {
    const map: Record<string, string> = {
      'en_ejecucion': 'bg-success',
      'pendiente':    'bg-warning text-dark',
      'finalizado':   'bg-secondary',
      'reabierto':    'bg-info text-dark'
    };
    return map[estado] ?? 'bg-secondary';
  }

  labelSprint(estado: string): string {
    const map: Record<string, string> = {
      'en_ejecucion': 'En ejecución',
      'pendiente':    'Pendiente',
      'finalizado':   'Finalizado',
      'reabierto':    'Reabierto'
    };
    return map[estado] ?? estado;
  }

  private alert(msg: string, cls: string): void {
    this.alertMsg.set(msg);
    this.alertClass.set(cls);
    setTimeout(() => this.alertMsg.set(''), 4000);
  }
}
