// Autor: Cristian Santiago Martinez Cordoba â€” PRODOX
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { catchError, of } from 'rxjs';
import { ShellComponent } from '../../layout/shell/shell.component';
import { AuthService } from '../../services/auth.service';
import { SeleccionService } from '../../services/seleccion.service';
import { MetricRankingService } from '../../services/metric-ranking.service';
import { MetricaSeleccionada, Parametrizacion, PropuestaGenAI } from '../../models/seleccion.model';
import { MetricParametrizacionBase, TopParametrizacion } from '../../models/metric-ranking.model';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-parametrizacion',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell title="Parametrización de Métrica">

      @if (!metrica) {
        <div class="text-center py-5 text-muted">Cargando...</div>
      } @else {

        <!-- Breadcrumb de navegación -->
        <nav aria-label="breadcrumb" class="mb-3">
          <ol class="breadcrumb small mb-0">
            <li class="breadcrumb-item">
              <a href="#" (click)="$event.preventDefault(); router.navigate(['/planeacion'])">
                <i class="bi bi-layers me-1"></i>Planeación
              </a>
            </li>
            <li class="breadcrumb-item">
              <a href="#" (click)="$event.preventDefault(); volver()">Resumen de Selección</a>
            </li>
            <li class="breadcrumb-item active">{{ metrica.metricaNombre }}</li>
          </ol>
        </nav>

        <!-- Info de la métrica -->
        <div class="card mb-4 border-primary">
          <div class="card-body py-3">
            <div class="row align-items-center">
              <div class="col-md-6">
                <div class="text-muted small">Factor</div>
                <div class="fw-semibold">{{ metrica.factorNombre }}</div>
                <span class="badge prox-badge-sm" [class]="categoryBadge(metrica.factorCategoria)">
                  {{ metrica.factorCategoria }}
                </span>
              </div>
              <div class="col-md-6 mt-2 mt-md-0">
                <div class="text-muted small">Métrica</div>
                <div class="fw-semibold">{{ metrica.metricaNombre }}</div>
                <div class="text-muted small">{{ metrica.metricaDescripcion }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- Estado de parametrización (FASE 16.6) -->
        @if (estadoActual) {
          <div class="card mb-4" 
               [class.border-warning]="estadoActual === 'propuesta'" 
               [class.border-success]="estadoActual === 'aprobada'">
            <div class="card-body py-2">
              <div class="d-flex flex-wrap align-items-center justify-content-between gap-2">
                <div>
                  <span class="badge me-2 prox-badge-sm"
                        [class.bg-warning]="estadoActual === 'propuesta'"
                        [class.text-dark]="estadoActual === 'propuesta'"
                        [class.bg-success]="estadoActual === 'aprobada'">
                    @if (estadoActual === 'propuesta') {
                      <i class="bi bi-hourglass-split me-1"></i>Propuesta
                    } @else {
                      <i class="bi bi-check-circle me-1"></i>Aprobada
                    }
                  </span>
                  <span class="small text-muted">Versión {{ versionActual }}</span>
                  @if (estadoActual === 'propuesta' && ultimaVersionAprobadaInfo) {
                    <span class="small text-muted ms-2">
                      <i class="bi bi-info-circle me-1"></i>
                      Versión aprobada vigente: v{{ ultimaVersionAprobadaInfo.version }} (se mantiene hasta que apruebes esta propuesta nueva)
                    </span>
                  }
                </div>
                <div>
                  @if (estadoActual === 'propuesta') {
                    @if (esScrumMaster) {
                      <button class="btn btn-success btn-sm text-nowrap"
                              [disabled]="aprobando"
                              (click)="aprobarParametrizacion()">
                        @if (aprobando) {
                          <span class="spinner-border spinner-border-sm me-1"></span>
                          Aprobando...
                        } @else {
                          <i class="bi bi-check-lg me-1"></i>Aprobar parametrización
                        }
                      </button>
                    } @else {
                      <span class="text-muted small">
                        <i class="bi bi-hourglass-split me-1"></i>Pendiente de aprobación por el Scrum Master
                      </span>
                    }
                  } @else {
                    <span class="text-success small">
                      <i class="bi bi-check-circle-fill me-1"></i>parametrización lista para uso
                    </span>
                  }
                </div>
              </div>
              @if (errorAprobar) {
                <div class="alert alert-danger small mt-2 mb-0 py-1">
                  <i class="bi bi-exclamation-triangle me-1"></i>{{ errorAprobar }}
                </div>
              }
            </div>
          </div>
        }

        <!-- Ranking Top 3 de esta métrica -->
        @if (top3.length > 0) {
          <div class="card mb-4">
            <div class="card-header d-flex align-items-center gap-2">
              <i class="bi bi-trophy-fill text-warning"></i>
              <span class="fw-semibold small">Top {{ top3.length }} parametrizaciones más usadas para esta métrica</span>
            </div>
            <div class="card-body p-0">
              <div class="table-responsive">
                <table class="table table-sm table-hover mb-0">
                  <thead class="table-light">
                    <tr>
                      <th class="ps-3" style="width:40px">#</th>
                      <th style="min-width:220px">Objetivo</th>
                      <th style="min-width:150px">Autor</th>
                      <th class="text-center" style="width:80px">Usos</th>
                      <th style="width:120px"></th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (t of top3; track t.id; let i = $index) {
                      <tr>
                        <td class="ps-3 align-middle">
                          <span class="badge rounded-pill prox-badge-sm"
                                [class]="i === 0 ? 'bg-warning text-dark' : i === 1 ? 'bg-secondary' : 'bg-secondary'"
                                style="min-width:22px">
                            {{ i + 1 }}
                          </span>
                        </td>
                        <td class="align-middle">
                          <div class="small fw-semibold">{{ t.objetivo | slice:0:70 }}...</div>
                          <div class="text-muted" style="font-size:0.7rem">
                            Escala: {{ t.escala }}
                          </div>
                        </td>
                        <td class="align-middle small text-muted text-nowrap">{{ t.userEmail }}</td>
                        <td class="text-center align-middle">
                          <span class="badge bg-primary rounded-pill">{{ t.usos }}</span>
                        </td>
                        <td class="align-middle text-end pe-3">
                          <button class="btn btn-sm btn-outline-primary py-0 text-nowrap"
                                  (click)="usarDelTop(t)">
                            <i class="bi bi-clipboard-check me-1"></i>Usar
                          </button>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        }

        <!-- parametrización base de otro usuario (solo lectura) -->
        @if (parametrizacionBase && top3.length === 0) {
          <div class="card mb-4 border-info">
            <div class="card-header d-flex align-items-center gap-2 bg-info bg-opacity-10">
              <i class="bi bi-person-check text-info fs-5"></i>
              <div>
                <div class="fw-semibold small">parametrización de referencia</div>
                <div class="text-muted" style="font-size:0.75rem">
                  Creada por <strong>{{ parametrizacionBase.userEmail }}</strong>
                  el {{ parametrizacionBase.createdAt | date:'dd/MM/yyyy' }}.
                  Podés usarla como base o crear la tuya propia.
                </div>
              </div>
              <button class="btn btn-sm btn-outline-info ms-auto"
                      (click)="usarBase()">
                <i class="bi bi-clipboard-check me-1"></i>Usar como base
              </button>
            </div>
            <div class="card-body py-2">
              <dl class="row mb-0 small">
                <dt class="col-sm-3 text-muted">Objetivo</dt>
                <dd class="col-sm-9">{{ parametrizacionBase.objetivo }}</dd>
                <dt class="col-sm-3 text-muted">Procedimiento</dt>
                <dd class="col-sm-9">{{ parametrizacionBase.procedimiento }}</dd>
                <dt class="col-sm-3 text-muted">Indicador / Variables</dt>
                <dd class="col-sm-9">{{ parametrizacionBase.indicadorVariable }}</dd>
                <dt class="col-sm-3 text-muted">Escala</dt>
                <dd class="col-sm-9 mb-0">{{ parametrizacionBase.escala }}</dd>
              </dl>
            </div>
          </div>
        }

        <!-- Asistente GenAI -->
        <div class="card mb-4">
          <div class="card-header d-flex align-items-center gap-2">
            <i class="bi bi-robot text-primary fs-5"></i>
            <div class="flex-grow-1">
              <div class="fw-semibold small">AI â€” Asistente de parametrización</div>
              <div class="text-muted" style="font-size:0.75rem">
                La IA puede ayudarte a construir una parametrización para esta métrica
                a partir de su definición y buenas prácticas de Scrum.
              </div>
            </div>
          </div>
          <div class="card-body">
            <button class="btn btn-primary btn-sm"
                    [disabled]="generando"
                    (click)="generarPropuestas()">
              @if (generando) {
                <span class="spinner-border spinner-border-sm me-2"></span>
                Generando parametrización...
              } @else {
                <i class="bi bi-stars me-2"></i>
                Generar parametrización con GenAI
              }
            </button>
            @if (errorGenAI) {
              <div class="alert alert-danger small mt-3 mb-0 py-2">
                <i class="bi bi-exclamation-triangle me-1"></i>{{ errorGenAI }}
              </div>
            }
          </div>
        </div>

        <!-- Propuesta generada por IA -->
        @if (propuestas.length > 0 && propuestas[0]) {
          <div class="card mb-4 border-primary">
            <div class="card-header bg-primary bg-opacity-10 d-flex flex-wrap align-items-center justify-content-between gap-2 py-2">
              <div class="d-flex align-items-center gap-2">
                <i class="bi bi-stars text-primary fs-5"></i>
                <div>
                  <div class="fw-semibold small">Propuesta generada por IA</div>
                  <div class="text-muted" style="font-size:0.72rem">
                    <i class="bi bi-exclamation-circle me-1"></i>
                    <strong>Requiere validación humana</strong> â€” Revisá y ajustá según el contexto de tu equipo
                  </div>
                </div>
              </div>
              <button class="btn btn-sm btn-outline-primary text-nowrap"
                      [disabled]="generando"
                      (click)="generarPropuestas()"
                      title="Generar nueva propuesta">
                <i class="bi bi-arrow-clockwise me-1"></i>Regenerar
              </button>
            </div>
            <div class="card-body">
              <h6 class="text-primary fw-semibold mb-3">
                <i class="bi bi-tag me-1"></i>{{ propuestas[0].titulo }}
              </h6>
              <dl class="row mb-0 small">
                <dt class="col-sm-3 text-muted">
                  <i class="bi bi-bullseye me-1"></i>Objetivo
                </dt>
                <dd class="col-sm-9">{{ propuestas[0].objetivo }}</dd>

                <dt class="col-sm-3 text-muted">
                  <i class="bi bi-list-ol me-1"></i>Procedimiento
                </dt>
                <dd class="col-sm-9">{{ propuestas[0].procedimiento }}</dd>

                <dt class="col-sm-3 text-muted">
                  <i class="bi bi-speedometer2 me-1"></i>Indicador / Variables
                </dt>
                <dd class="col-sm-9">{{ propuestas[0].indicadorVariable }}</dd>

                <dt class="col-sm-3 text-muted">
                  <i class="bi bi-bar-chart-steps me-1"></i>Escala
                </dt>
                <dd class="col-sm-9">
                  {{ propuestas[0].escala }}
                  @if (propuestas[0].escalaTipo) {
                    <div class="text-muted" style="font-size:0.72rem">
                      {{ propuestas[0].escalaTipo === 'NUMERICA_ENTERA' ? 'Entera' : 'Decimal' }},
                      {{ propuestas[0].escalaMin }}
                      â€“
                      {{ propuestas[0].escalaSinLimite ? 'sin límite' : propuestas[0].escalaMax }},
                      paso {{ propuestas[0].escalaPaso }}
                    </div>
                  }
                </dd>

                @if (propuestas[0].formulaAcademica) {
                  <dt class="col-sm-3 text-muted">
                    <i class="bi bi-calculator me-1"></i>Fórmula académica
                  </dt>
                  <dd class="col-sm-9"><code>{{ propuestas[0].formulaAcademica }}</code></dd>
                }

                @if (propuestas[0].tipoOperacion) {
                  <dt class="col-sm-3 text-muted">
                    <i class="bi bi-gear me-1"></i>Tipo operación
                  </dt>
                  <dd class="col-sm-9"><span class="badge bg-secondary prox-badge-sm">{{ propuestas[0].tipoOperacion }}</span></dd>
                }

                @if (propuestas[0].unidadResultado) {
                  <dt class="col-sm-3 text-muted">
                    <i class="bi bi-rulers me-1"></i>Unidad resultado
                  </dt>
                  <dd class="col-sm-9">{{ propuestas[0].unidadResultado }}</dd>
                }

                @if (propuestas[0].fuenteAcademica) {
                  <dt class="col-sm-3 text-muted">
                    <i class="bi bi-book me-1"></i>Fuente académica
                  </dt>
                  <dd class="col-sm-9 small text-muted">{{ propuestas[0].fuenteAcademica }}</dd>
                }

                <dt class="col-sm-12 mt-2">
                  <div class="alert alert-info py-2 mb-0">
                    <i class="bi bi-info-circle me-1"></i>
                    <strong>Justificación:</strong> {{ propuestas[0].justificacion }}
                  </div>
                </dt>
              </dl>
            </div>
            <div class="card-footer py-2 d-flex flex-wrap justify-content-between align-items-center gap-2">
              <span class="small text-muted">
                <i class="bi bi-robot me-1"></i>Esta es una propuesta de IA, no una configuración oficial
              </span>
              <div class="d-flex flex-wrap gap-2">
                <button class="btn btn-outline-primary btn-sm text-nowrap"
                        (click)="usarPropuesta(propuestas[0])">
                  <i class="bi bi-clipboard-check me-1"></i>Copiar al formulario
                </button>
                @if (estadoActual !== 'propuesta') {
                  <button class="btn btn-success btn-sm text-nowrap"
                          [disabled]="guardando"
                          (click)="guardarPropuesta()">
                    @if (guardando) {
                      <span class="spinner-border spinner-border-sm me-1"></span>
                      Guardando...
                    } @else {
                      <i class="bi bi-floppy me-1"></i>
                      {{ estadoActual === 'aprobada' ? 'Guardar como nueva propuesta' : 'Guardar propuesta' }}
                    }
                  </button>
                }
              </div>
            </div>
            @if (errorGuardar) {
              <div class="card-footer pt-0 pb-2">
                <div class="alert alert-danger small mb-0 py-1">
                  <i class="bi bi-exclamation-triangle me-1"></i>{{ errorGuardar }}
                </div>
              </div>
            }
          </div>
        }

        <!-- Formulario de parametrización -->
        <div class="card mb-4">
          <div class="card-header fw-semibold small">
            <i class="bi bi-pencil me-1"></i>
            {{ propuestas.length > 0 ? 'Revisar y ajustar parametrización' : 'parametrización manual' }}
          </div>
          <div class="card-body">
            <div class="row g-3">
              <div class="col-12">
                <label class="form-label small fw-semibold">
                  Objetivo de medición <span class="text-danger">*</span>
                  <i class="bi bi-info-circle text-muted ms-1" 
                     style="cursor: help"
                     title="Define el propósito de esta métrica. ¿Qué insight o mejora esperas obtener al medirla?"></i>
                </label>
                <textarea class="form-control form-control-sm" rows="2"
                          placeholder="¿Qué se quiere lograr midiendo esta métrica?"
                          [(ngModel)]="form.objetivo"></textarea>
              </div>
              <div class="col-12">
                <label class="form-label small fw-semibold">
                  Procedimiento / Fórmula <span class="text-danger">*</span>
                  <i class="bi bi-info-circle text-muted ms-1" 
                     style="cursor: help"
                     title="Describe cómo se obtiene el valor de esta métrica. Incluye fórmulas matemáticas o pasos específicos para calcularla."></i>
                </label>
                <textarea class="form-control form-control-sm" rows="3"
                          placeholder="Fórmula o pasos para calcular el valor de la métrica..."
                          [(ngModel)]="form.procedimiento"></textarea>
              </div>
              <div class="col-md-6">
                <label class="form-label small fw-semibold">
                  Indicador y Variables <span class="text-danger">*</span>
                  <i class="bi bi-info-circle text-muted ms-1" 
                     style="cursor: help"
                     title="Define el nombre del indicador y las variables que intervienen en su cálculo. Ej: 'Velocidad del equipo' medida en story points completados por sprint."></i>
                </label>
                <input type="text" class="form-control form-control-sm"
                       placeholder="Ej: Velocidad = SP completados / SP planificados"
                       [(ngModel)]="form.indicadorVariable">
              </div>
              <div class="col-12">
                <label class="form-label small fw-semibold">
                  Escala de medición <span class="text-danger">*</span>
                  <i class="bi bi-info-circle text-muted ms-1" 
                     style="cursor: help"
                     title="Define el rango y tipo de valores permitidos para esta métrica. Establece límites claros para mantener consistencia en las mediciones."></i>
                </label>
                <div class="row g-2 p-2 rounded" style="background-color:var(--background)">
                  <div class="col-md-3">
                    <label class="form-label small text-muted mb-1">
                      Tipo
                      <i class="bi bi-info-circle-fill text-info ms-1" 
                         style="cursor: help; font-size: 0.7rem"
                         title="Numérica entera: solo números enteros (0, 1, 2, 5, 10). Numérica decimal: permite decimales (0.5, 1.25, 3.75)."></i>
                    </label>
                    <select class="form-select form-select-sm" [(ngModel)]="form.escalaTipo">
                      <option [ngValue]="undefined">Sin definir</option>
                      <option value="NUMERICA_ENTERA">Numérica entera</option>
                      <option value="NUMERICA_DECIMAL">Numérica decimal</option>
                    </select>
                  </div>
                  <div class="col-md-3">
                    <label class="form-label small text-muted mb-1">
                      Mínimo
                      <i class="bi bi-info-circle-fill text-info ms-1" 
                         style="cursor: help; font-size: 0.7rem"
                         title="El valor más bajo permitido para esta métrica. Ej: 0 para porcentajes, 1 para conteos que no pueden ser cero."></i>
                    </label>
                    <input type="number" class="form-control form-control-sm" [(ngModel)]="form.escalaMin">
                  </div>
                  <div class="col-md-3">
                    <label class="form-label small text-muted mb-1">
                      Máximo
                      <i class="bi bi-info-circle-fill text-info ms-1" 
                         style="cursor: help; font-size: 0.7rem"
                         title="El valor más alto permitido. Ej: 100 para porcentajes, 10 para escalas de satisfacción. Desactívalo si no hay límite superior."></i>
                    </label>
                    <input type="number" class="form-control form-control-sm"
                           [(ngModel)]="form.escalaMax" [disabled]="!!form.escalaSinLimite">
                  </div>
                  <div class="col-md-3">
                    <label class="form-label small text-muted mb-1">
                      Paso
                      <i class="bi bi-info-circle-fill text-info ms-1" 
                         style="cursor: help; font-size: 0.7rem"
                         title="Incremento mínimo entre valores. Ej: paso=1 permite 0,1,2,3... paso=5 permite 0,5,10,15... paso=0.5 permite 0, 0.5, 1.0, 1.5..."></i>
                    </label>
                    <input type="number" class="form-control form-control-sm" step="0.01" min="0.01"
                           [(ngModel)]="form.escalaPaso">
                  </div>
                  <div class="col-md-6 d-flex align-items-center">
                    <div class="form-check mt-3">
                      <input class="form-check-input" type="checkbox" id="escalaSinLimite"
                             [(ngModel)]="form.escalaSinLimite" (ngModelChange)="onEscalaSinLimiteChange()">
                      <label class="form-check-label small" for="escalaSinLimite">
                        Sin límite superior
                        <i class="bi bi-info-circle-fill text-info ms-1" 
                           style="cursor: help; font-size: 0.7rem"
                           title="Activa esto cuando la métrica no tiene un valor máximo definido. Ej: cantidad de bugs encontrados, tiempo de respuesta."></i>
                      </label>
                    </div>
                  </div>
                  <div class="col-md-6">
                    <label class="form-label small text-muted mb-1">
                      Descripción de los valores (opcional)
                      <i class="bi bi-info-circle-fill text-info ms-1" 
                         style="cursor: help; font-size: 0.7rem"
                         title="Ayuda a interpretar los valores. Ej: '0=Malo, 5=Regular, 10=Excelente' o '0-30=Bajo, 31-70=Medio, 71-100=Alto'"></i>
                    </label>
                    <input type="text" class="form-control form-control-sm"
                           placeholder="Ej: 0 = Muy malo; 10 = Excelente"
                           [(ngModel)]="form.escalaDescripcion">
                  </div>
                  @if (errorEscala) {
                    <div class="col-12"><div class="text-danger small mb-0">{{ errorEscala }}</div></div>
                  }
                </div>
              </div>
              <div class="col-md-6">
                <label class="form-label small fw-semibold">
                  ðŸ“… Frecuencia de captura <span class="text-muted">(recomendada por IA)</span>
                  <i class="bi bi-info-circle text-muted ms-1" 
                     style="cursor: help"
                     title="Define cuándo debe registrarse esta métrica. Al finalizar sprint es lo más común para métricas de equipo; diaria para métricas de seguimiento continuo; cuando ocurra el evento para métricas basadas en incidentes."></i>
                </label>
                <select class="form-select form-select-sm" [(ngModel)]="form.frecuenciaCaptura">
                  <option value="por_sprint">Al finalizar sprint</option>
                  <option value="semanal">Una vez por semana</option>
                  <option value="diaria">Diariamente</option>
                  <option value="ilimitada">Cuando ocurra el evento</option>
                </select>
              </div>

              <!-- revisión de captura por parametrización: alcance/responsable de
                   captura, independiente de la fórmula/operación de arriba. El
                   backend es la autoridad real (EjecucionService.validarPuedeRegistrar);
                   esta Selección solo decide qué queda guardado en la parametrización. -->
              <div class="col-md-6">
                <label class="form-label small fw-semibold">
                  👥 Responsable de captura
                  <i class="bi bi-info-circle text-muted ms-1" 
                     style="cursor: help"
                     title="Quién puede registrar valores para esta métrica. Equipo: cada miembro registra su propio valor (útil para métricas individuales como horas trabajadas). Scrum Master: solo él registra el valor (útil para métricas consolidadas del equipo)."></i>
                </label>
                <select class="form-select form-select-sm" [(ngModel)]="form.responsableCaptura">
                  <option value="EQUIPO">Equipo</option>
                  <option value="SCRUM_MASTER">Scrum Master</option>
                </select>
                <div class="form-hint text-muted small mt-1">
                  @if (form.responsableCaptura === 'EQUIPO') {
                    Cada integrante registra su propio valor.
                  } @else {
                    Solo el Scrum Master registra el valor.
                  }
                </div>
              </div>

              <!-- Campos académicos: Información PROPUESTA/GENERADA por IA, nunca
                   editable manualmente acá â€” se completan solo copiando una propuesta
                   de IA ("Copiar al formulario"), una entrada del ranking ("Usar") o
                   la parametrización base ("Usar como base"). Un campo que la IA no
                   generó se muestra explícitamente como "No definido", nunca como un
                   input vacío que invite a completarlo a mano. -->
              <div class="col-12 mt-3">
                <hr>
                <h6 class="text-muted small mb-3">
                  <i class="bi bi-mortarboard me-1"></i>
                  Campos académicos <span class="text-muted">(propuestos por IA â€” solo lectura)</span>
                </h6>
              </div>

              <div class="col-12">
                <dl class="row mb-0 small p-3 rounded" style="background-color:var(--background)">
                  <dt class="col-sm-3 text-muted">Fórmula académica</dt>
                  <dd class="col-sm-9">
                    @if (form.formulaAcademica) {
                      <code>{{ form.formulaAcademica }}</code>
                    } @else {
                      <span class="text-muted fst-italic">No definido</span>
                    }
                  </dd>

                  <dt class="col-sm-3 text-muted">Tipo de operación</dt>
                  <dd class="col-sm-9">
                    @if (form.tipoOperacion) {
                      <span class="badge bg-secondary prox-badge-sm">{{ form.tipoOperacion }}</span>
                    } @else {
                      <span class="text-muted fst-italic">No definido</span>
                    }
                  </dd>

                  <dt class="col-sm-3 text-muted">Unidad del resultado</dt>
                  <dd class="col-sm-9">
                    @if (form.unidadResultado) {
                      {{ form.unidadResultado }}
                    } @else {
                      <span class="text-muted fst-italic">No definido</span>
                    }
                  </dd>

                  <dt class="col-sm-3 text-muted">Fuente académica</dt>
                  <dd class="col-sm-9 mb-0">
                    @if (form.fuenteAcademica) {
                      {{ form.fuenteAcademica }}
                    } @else {
                      <span class="text-muted fst-italic">No definido</span>
                    }
                  </dd>
                </dl>
              </div>
            </div>
          </div>
          <div class="card-footer d-flex flex-wrap justify-content-between align-items-center gap-2">
            <button class="btn btn-outline-secondary btn-sm text-nowrap" (click)="volver()">
              <i class="bi bi-arrow-left me-1"></i>Volver
            </button>
            <div class="d-flex flex-wrap align-items-center gap-2 gap-sm-3">
              <span class="small">
                Estado: <span class="badge prox-badge-sm" [class]="estadoBadge()">{{ estadoLabel() }}</span>
              </span>
              <button class="btn btn-success btn-sm text-nowrap"
                      [disabled]="guardando"
                      (click)="guardar()">
                @if (guardando) {
                  <span class="spinner-border spinner-border-sm me-1"></span>
                } @else {
                  <i class="bi bi-floppy me-1"></i>
                }
                Guardar parametrización
              </button>
            </div>
          </div>
        </div>

      }
    </app-shell>
  `,
  styles: [``]
})
export class ParametrizacionComponent implements OnInit {
  metrica: MetricaSeleccionada | null       = null;
  parametrizacionBase: MetricParametrizacionBase | null = null;
  top3: TopParametrizacion[]                = [];
  propuestas: PropuestaGenAI[]              = [];
  propuestaElegida: number | null           = null;
  generando  = false;
  guardando  = false;
  errorGenAI = '';
  /** Corrección del manejo de escalas: error de validación de la escala estructurada. */
  errorEscala = '';
  
  // FASE 16.6: Aprobación y versionado
  parametrizacionId: string | null = null;
  estadoActual: 'propuesta' | 'aprobada' | null = null;
  versionActual: number = 1;
  aprobando = false;
  /**
   * FASE 16.10-D: respuesta completa de POST /guardar-propuesta (la parametrización
   * ya persistida). Es la fuente de verdad al aprobar â€” evita reconstruir el request
   * desde `this.form`, que puede desincronizarse si el usuario regenera con GenAI o
   * edita el formulario después de guardar. Si es null (p. ej. la propuesta pendiente
   * viene de una sesión anterior y no se guardó en este ciclo de vida del componente),
   * se conserva el comportamiento previo de usar `this.form` como respaldo.
   */
  propuestaPendiente: any = null;
  errorGuardar = '';
  errorAprobar = '';
  // FASE 16.10-C: info de la última versión aprobada, se conserva visible
  // aunque exista una propuesta nueva (aún sin aprobar) en curso.
  ultimaVersionAprobadaInfo: { version: number } | null = null;

  form: Parametrizacion = {
    objetivo: '', procedimiento: '', indicadorVariable: '', escala: '', frecuenciaCaptura: 'por_sprint',
    // Valor por defecto conservador: preserva el comportamiento previo a esta
    // revisión (todas las métricas quedaban "grupal"/solo Scrum Master) para
    // quien no cambie explícitamente la Selección.
    responsableCaptura: 'SCRUM_MASTER'
  };

  private readonly apiBase = environment.apiBaseUrl;

  constructor(
    private route: ActivatedRoute,
    public  router: Router,
    private auth: AuthService,
    private seleccionService: SeleccionService,
    private rankingService: MetricRankingService,
    private http: HttpClient
  ) {}

  /**
   * revisión de navegación (rol): la aprobación real ya está protegida en el
   * backend (ParametrizacionController exige Scrum Master del proyecto). Este
   * getter solo decide qué botón mostrar â€” nunca es la autorización real.
   *
   * Corrección: Scrum Master es siempre relativo al proyecto activo (su
   * scrumMasterEmail, fijado por el backend al crearlo), nunca el rol
   * global de cuenta (auth.currentUser()?.role, usado solo para decidir
   * quién puede CREAR un proyecto nuevo) â€” mismo patrón ya corregido en
   * dashboard.component.ts (esScrumMasterDelProyecto).
   */
  get esScrumMaster(): boolean {
    return this.leerScrumMasterEmailProyectoActivo() === this.auth.currentUser()?.email;
  }

  private leerScrumMasterEmailProyectoActivo(): string | null {
    try {
      const raw = localStorage.getItem('mpdia_proyecto_activo');
      return raw ? (JSON.parse(raw)?.scrumMasterEmail ?? null) : null;
    } catch { return null; }
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    this.seleccionService.getAll().subscribe(list => {
      this.metrica = list.find(s => s.id === id) ?? null;
      if (this.metrica?.parametrizacion) {
        this.form = { ...this.metrica.parametrizacion };
        this.propuestaElegida = this.metrica.parametrizacion.propuestaElegida ?? null;
      }
      // Cargar parametrización base del backend si existe
      if (this.metrica) {
        const metricaId = this.metrica.factorId;
        
        // FASE 16.6: Cargar estado de parametrización desde backend
        const proyectoActivo = localStorage.getItem('mpdia_proyecto_activo');
        const proyectoId = proyectoActivo ? JSON.parse(proyectoActivo)?.id : null;
        if (proyectoId) {
          this.cargarEstadoParametrizacion(metricaId, proyectoId);
        }
        
        // Buscar primero por metricaId (flujo Planeación), luego por factorId (flujo Selección)
        this.rankingService.getTop3ByMetricaId(metricaId).pipe(
          catchError(() => of([] as TopParametrizacion[]))
        ).subscribe(top => {
          if (top.length > 0) {
            this.top3 = top;
          } else {
            this.rankingService.getTop3(metricaId).pipe(
              catchError(() => of([] as TopParametrizacion[]))
            ).subscribe(t => this.top3 = t);
          }
        });

        this.rankingService.getBaseByMetricaId(metricaId).pipe(
          catchError(() => this.rankingService.getBase(metricaId).pipe(catchError(() => of(null))))
        ).subscribe(base => this.parametrizacionBase = base);
      }
    });
  }

  /**
   * Copiar una entrada del top 3 al formulario. Reutiliza la parametrización
   * COMPLETA (antes solo copiaba objetivo/procedimiento/indicadorVariable/
   * escala y descartaba el resto, incluidos los campos académicos y la
   * frecuencia de captura ya definidos â€” bug corregido acá). Un campo que
   * realmente está vacío en el original se conserva vacío/undefined, nunca
   * se inventa un valor.
   */
  usarDelTop(t: TopParametrizacion): void {
    console.log('ðŸ” Datos de TopParametrizacion:', t);
    
    this.form = {
      objetivo:          t.objetivo || '',
      procedimiento:     t.procedimiento || '',
      indicadorVariable: t.indicadorVariable || '',
      escala:            t.escala || '',
      frecuenciaCaptura: t.frecuenciaCaptura || 'por_sprint',
      fuenteAcademica:   t.fuenteAcademica ?? undefined,
      formulaAcademica:  t.formulaAcademica ?? undefined,
      tipoOperacion:     t.tipoOperacion ?? undefined,
      unidadResultado:   t.unidadResultado ?? undefined,
      escalaTipo:        t.escalaTipo ?? undefined,
      escalaMin:         t.escalaMin ?? undefined,
      escalaMax:         t.escalaMax ?? undefined,
      escalaPaso:        t.escalaPaso ?? undefined,
      escalaSinLimite:   t.escalaSinLimite ?? false,
      escalaDescripcion: t.escalaDescripcion ?? undefined,
      responsableCaptura: this.form.responsableCaptura || 'SCRUM_MASTER'
    };
    
    console.log('ðŸ“ Formulario después de copiar:', this.form);
    
    this.errorEscala = '';
    this.propuestaElegida = null;
    
    // Hacer scroll hacia el formulario para que el usuario vea los campos llenados
    setTimeout(() => {
      const formulario = document.querySelector('[style*="parametrización manual"]');
      if (formulario) {
        formulario.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    }, 100);
    
    // Incrementar el ranking de uso de esta parametrización
    if (this.metrica?.factorId) {
      this.rankingService.incrementarUso(this.metrica.factorId).pipe(catchError(() => of(null))).subscribe();
    }
  }

  /**
   * FASE 16.6: Cargar estado de parametrización desde backend
   * Obtiene la última versión aprobada para mostrar estado/versión/badge
   */
  cargarEstadoParametrizacion(metricaId: string, proyectoId: string): void {
    this.http.get<any>(
      `${this.apiBase}/parametrizacion/ultima-aprobada?metricaId=${metricaId}&proyectoId=${proyectoId}`
    ).pipe(
      catchError(() => of(null))
    ).subscribe(parametrizacion => {
      if (parametrizacion) {
        this.estadoActual = parametrizacion.status;
        this.versionActual = parametrizacion.version;
        this.parametrizacionId = parametrizacion.id;
        // GET /ultima-aprobada solo devuelve versiones con status "aprobada".
        // Se conserva esta info para mostrarla aunque luego se guarde una
        // propuesta nueva (siguiente versión, todavía sin aprobar).
        this.ultimaVersionAprobadaInfo = { version: parametrizacion.version };
        // Precargar el formulario con la parametrización aprobada vigente
        // para que el usuario pueda revisarla/modificarla antes de guardar
        // una nueva propuesta (no se sobreescribe si el usuario ya editó
        // el formulario manualmente después de esta carga).
        this.form = {
          objetivo: parametrizacion.objetivo,
          procedimiento: parametrizacion.procedimiento,
          indicadorVariable: parametrizacion.indicadorVariable,
          escala: parametrizacion.escala,
          frecuenciaCaptura: parametrizacion.frecuenciaCaptura || 'por_sprint',
          fuenteAcademica: parametrizacion.fuenteAcademica || '',
          formulaAcademica: parametrizacion.formulaAcademica || '',
          tipoOperacion: parametrizacion.tipoOperacion || '',
          unidadResultado: parametrizacion.unidadResultado || '',
          escalaTipo: parametrizacion.escalaTipo ?? undefined,
          escalaMin: parametrizacion.escalaMin ?? undefined,
          escalaMax: parametrizacion.escalaMax ?? undefined,
          escalaPaso: parametrizacion.escalaPaso ?? undefined,
          escalaSinLimite: parametrizacion.escalaSinLimite ?? undefined,
          escalaDescripcion: parametrizacion.escalaDescripcion ?? undefined,
          // Precarga el alcance/responsable REAL de la última versión aprobada
          // de esta métrica â€” a diferencia del Top 3/base, esta sí es la
          // parametrización vigente de la MISMA métrica+proyecto.
          responsableCaptura: parametrizacion.responsableCaptura || 'SCRUM_MASTER'
        };
      } else {
        // No existe parametrización aprobada aún
        this.estadoActual = null;
        this.versionActual = 1;
        this.parametrizacionId = null;
        this.ultimaVersionAprobadaInfo = null;
      }
    });
  }

  /**
   * Copiar la parametrización base al formulario para editarla. Reutiliza
   * la parametrización COMPLETA â€” mismo criterio que usarDelTop().
   */
  usarBase(): void {
    if (!this.parametrizacionBase) return;
    const b = this.parametrizacionBase;
    this.form = {
      objetivo:          b.objetivo,
      procedimiento:     b.procedimiento,
      indicadorVariable: b.indicadorVariable,
      escala:            b.escala,
      frecuenciaCaptura: b.frecuenciaCaptura || 'por_sprint',
      fuenteAcademica:   b.fuenteAcademica ?? undefined,
      formulaAcademica:  b.formulaAcademica ?? undefined,
      tipoOperacion:     b.tipoOperacion ?? undefined,
      unidadResultado:   b.unidadResultado ?? undefined,
      escalaTipo:        b.escalaTipo,
      escalaMin:         b.escalaMin,
      escalaMax:         b.escalaMax,
      escalaPaso:        b.escalaPaso,
      escalaSinLimite:   b.escalaSinLimite,
      escalaDescripcion: b.escalaDescripcion,
      // La base no transporta responsableCaptura â€” se conserva la Selección
      // que ya tenía el formulario (misma razón que usarDelTop()).
      responsableCaptura: this.form.responsableCaptura || 'SCRUM_MASTER'
    };
    this.propuestaElegida = null;
    this.errorEscala = '';
  }

  generarPropuestas(): void {
    if (!this.metrica) return;
    this.generando  = true;
    this.errorGenAI = '';
    this.propuestas = [];

    this.http.post<PropuestaGenAI[]>(`${this.apiBase}/parametrizacion/propuestas`, {
      factorNombre:       this.metrica.factorNombre,
      factorCategoria:    this.metrica.factorCategoria,
      metricaNombre:      this.metrica.metricaNombre,
      metricaDescripcion: this.metrica.metricaDescripcion
    }).subscribe({
      next:  p  => { 
        this.propuestas = p;
        this.generando = false;
        // Si la propuesta tiene solo 1 elemento, no auto-aplicar
        // Esperar confirmación explícita del usuario
      },
      error: () => { this.errorGenAI = 'Error al conectar con GenAI.'; this.generando = false; }
    });
  }

  /**
   * Usuario hace click en "Usar esta propuesta" - copia la propuesta al formulario
   */
  usarPropuesta(p: PropuestaGenAI): void {
    this.form = {
      objetivo:          p.objetivo,
      procedimiento:     p.procedimiento,
      indicadorVariable: p.indicadorVariable,
      escala:            p.escala,
      frecuenciaCaptura: p.frecuenciaCaptura || 'por_sprint',
      fuenteAcademica:   p.fuenteAcademica || '',
      formulaAcademica:  p.formulaAcademica || '',
      tipoOperacion:     p.tipoOperacion || '',
      unidadResultado:   p.unidadResultado || '',
      nombreVariable:    p.nombreVariable || '',
      propuestaElegida:  0,  // índice 0 ya que ahora solo hay 1 propuesta
      // Corrección del manejo de escalas: la IA propone una escala estructurada
      // coherente con la métrica (nunca 0-10 forzado) â€” el Scrum Master puede
      // ajustarla en el formulario antes de guardar/aprobar.
      escalaTipo:        p.escalaTipo,
      escalaMin:         p.escalaMin,
      escalaMax:         p.escalaMax,
      escalaPaso:        p.escalaPaso,
      escalaSinLimite:   p.escalaSinLimite,
      escalaDescripcion: p.escalaDescripcion,
      // GenAI no propone responsableCaptura (es una decisión organizacional
      // del Scrum Master, no algo que la IA deba inferir) â€” se conserva la
      // Selección que ya tenía el formulario.
      responsableCaptura: this.form.responsableCaptura || 'SCRUM_MASTER'
    };
    this.errorEscala = '';
    // Scroll al formulario para que el usuario vea los cambios
    setTimeout(() => {
      const formulario = document.querySelector('.card:last-of-type');
      formulario?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 100);
  }

  /**
   * DEPRECATED: Se mantiene por compatibilidad pero ya no se usa
   * (las 3 tarjetas fueron reemplazadas por 1 propuesta)
   */
  elegirPropuesta(idx: number, p: PropuestaGenAI): void {
    // Este método ya no se llama desde el template pero se mantiene
    // para no romper si hay referencias en otros lugares
    this.usarPropuesta(p);
  }

  calcularEstado(): 'sin_parametrizar' | 'parcial' | 'completa' {
    const completos = [this.form.objetivo, this.form.procedimiento,
                       this.form.indicadorVariable, this.form.escala]
      .filter(c => !!c?.trim()).length;
    if (completos === 0) return 'sin_parametrizar';
    if (completos === 4) return 'completa';
    return 'parcial';
  }

  estadoLabel(): string {
    return { sin_parametrizar: 'Sin parametrizar', parcial: 'Parcial', completa: 'Completa' }
      [this.calcularEstado()];
  }

  estadoBadge(): string {
    return { sin_parametrizar: 'bg-secondary', parcial: 'bg-warning text-dark', completa: 'bg-success' }
      [this.calcularEstado()];
  }

  /**
   * Corrección del manejo de escalas: si se marca "Sin límite superior", el
   * máximo deja de ser obligatorio/editable â€” se limpia para que nunca se
   * envíe un valor inconsistente con escalaSinLimite=true.
   */
  onEscalaSinLimiteChange(): void {
    if (this.form.escalaSinLimite) {
      this.form.escalaMax = undefined;
    }
  }

  /**
   * Espeja ParametrizacionService.validarEscalaEstructurada() en el backend:
   * misma autoridad, pero acá se ejecuta ANTES de enviar la petición para que
   * el Scrum Master vea el error de inmediato, sin esperar un 400 del servidor.
   * El backend sigue siendo la autoridad final â€” esta validación es una
   * comodidad de UI, nunca un sustituto de la del servidor.
   */
  private validarEscala(): boolean {
    this.errorEscala = '';
    const { escalaTipo, escalaMin, escalaMax, escalaPaso, escalaSinLimite } = this.form;
    const algunCampoInformado = escalaTipo != null || escalaMin != null || escalaMax != null
      || escalaPaso != null || escalaSinLimite != null;
    if (!algunCampoInformado) {
      return true; // no estructurada: compatibilidad, se permite (ver backend)
    }
    if (!escalaTipo) {
      this.errorEscala = 'Seleccioná el tipo de escala.';
      return false;
    }
    if (escalaMin == null) {
      this.errorEscala = 'El mínimo de la escala es obligatorio.';
      return false;
    }
    if (escalaPaso == null || escalaPaso <= 0) {
      this.errorEscala = 'El paso de la escala debe ser mayor que 0.';
      return false;
    }
    if (!escalaSinLimite) {
      if (escalaMax == null) {
        this.errorEscala = 'El máximo de la escala es obligatorio (o marcá "Sin límite superior").';
        return false;
      }
      if (escalaMax < escalaMin) {
        this.errorEscala = 'El máximo no puede ser menor que el mínimo.';
        return false;
      }
    }
    if (escalaTipo === 'NUMERICA_ENTERA') {
      const esEntero = (n: number) => Number.isInteger(n);
      if (!esEntero(escalaMin) || !esEntero(escalaPaso) || (!escalaSinLimite && !esEntero(escalaMax!))) {
        this.errorEscala = 'Para escala numérica entera, mínimo/máximo/paso deben ser números enteros.';
        return false;
      }
    }
    return true;
  }

  /** Resumen legible auto-generado desde la escala estructurada, para el campo de texto libre `escala`. */
  private escalaTexto(): string {
    const f = this.form;
    if (!f.escalaTipo || f.escalaMin == null) {
      return f.escala || 'Escala no definida';
    }
    const tipo = f.escalaTipo === 'NUMERICA_ENTERA' ? 'Numérica entera' : 'Numérica decimal';
    const rango = f.escalaSinLimite ? `${f.escalaMin} o más` : `${f.escalaMin} a ${f.escalaMax}`;
    const paso = f.escalaPaso != null ? `, paso ${f.escalaPaso}` : '';
    return `${tipo}, ${rango}${paso}`;
  }

  guardar(): void {
    if (!this.metrica) return;
    // Corrección de duplicados en Verificación: reentrada bloqueada sobre el estado
    // real del componente, no solo sobre [disabled]="guardando" del template â€” dos
    // clics muy rápidos pueden invocar guardar() dos veces antes de que Angular
    // refleje el atributo disabled en el DOM (mismo patrón ya aplicado en
    // resumen-seleccion.component.ts:aceptar()). La protección real y definitiva
    // contra duplicados sigue estando en el backend (MetricRankingService), esto
    // es solo una mejora de UX que evita una petición HTTP innecesaria.
    if (this.guardando) return;
    if (!this.validarEscala()) return;
    this.guardando = true;

    const escalaTexto = this.escalaTexto();

    // Guardar en localStorage (estado local del sprint)
    this.seleccionService.parametrizar(this.metrica.id, {
      ...this.form,
      escala: escalaTexto,
      propuestaElegida: this.propuestaElegida ?? undefined
    });

    // Guardar en el backend (persistencia compartida + ranking)
    const proyectoActivo = localStorage.getItem('mpdia_proyecto_activo');
    const proyectoId = proyectoActivo ? JSON.parse(proyectoActivo)?.id ?? null : null;

    this.rankingService.guardar({
      factorId:          null,
      objetivo:          this.form.objetivo,
      procedimiento:     this.form.procedimiento,
      indicadorVariable: this.form.indicadorVariable,
      escala:            escalaTexto,
      metricaBaseId:     this.parametrizacionBase?.id ?? null,
      proyectoId:        proyectoId,
      metricaId:         this.metrica.factorId,  // desde Planeación, factorId contiene el metricaId
      // FASE 11: propagar los campos académicos completados en este formulario â€” antes se
      // descartaban al llegar a MetricRankingService.guardar().
      tipoOperacion:     this.form.tipoOperacion ?? null,
      formulaAcademica:  this.form.formulaAcademica ?? null,
      unidadResultado:   this.form.unidadResultado ?? null,
      fuenteAcademica:   this.form.fuenteAcademica ?? null,
      // revisión de frecuencia de captura: antes no se enviaba acá, por lo que el
      // backend la persistía siempre como "por_sprint" sin importar lo elegido
      // en el selector de arriba (ver MetricRankingService.guardarPorMetrica()).
      frecuenciaCaptura: this.form.frecuenciaCaptura || 'por_sprint',
      // revisión de captura por parametrización: independiente de
      // tipoOperacion â€” decide QUIÉN captura, no CÃ“MO se calcula.
      responsableCaptura: this.form.responsableCaptura || 'SCRUM_MASTER',
      // Corrección del manejo de escalas: fuente de verdad estructurada que
      // Ejecución usará para mostrar y validar los valores.
      escalaTipo:        this.form.escalaTipo,
      escalaMin:         this.form.escalaMin,
      escalaMax:         this.form.escalaMax,
      escalaPaso:        this.form.escalaPaso,
      escalaSinLimite:   this.form.escalaSinLimite,
      escalaDescripcion: this.form.escalaDescripcion
    }).pipe(
      catchError(err => {
        this.guardando = false;
        this.errorEscala = err?.error?.mensaje || err?.error?.error || '';
        return of(null);
      })
    ).subscribe(resultado => {
      if (!resultado) return;
      this.guardando = false;
      this.router.navigate(['/resumen-seleccion']);
    });
  }

  volver(): void {
    this.router.navigate(['/resumen-seleccion']);
  }

  categoryBadge(cat: string): string {
    const map: Record<string, string> = {
      'Significado':      'bg-primary',
      'Flexibilidad':     'bg-warning text-dark',
      'Impacto':          'bg-danger',
      'Socio-Humano FSH': 'bg-info text-dark'
    };
    return map[cat] ?? 'bg-secondary';
  }
  
  // ========================================
  // FASE 16.6: APROBACIÃ“N Y VERSIONADO
  // ========================================
  
  /**
   * Guarda la propuesta generada por IA con estado "propuesta".
   * NO aprueba automáticamente - requiere acción explícita del usuario.
   */
  guardarPropuesta(): void {
    if (!this.metrica || !this.propuestas[0]) return;
    if (this.guardando) return; // ver comentario de reentrada en guardar()
    if (!this.validarEscala()) return;

    this.guardando = true;
    this.errorGuardar = '';

    const proyectoActivo = localStorage.getItem('mpdia_proyecto_activo');
    const proyectoId = proyectoActivo ? JSON.parse(proyectoActivo)?.id : null;

    if (!proyectoId) {
      this.errorGuardar = 'No se pudo identificar el proyecto activo';
      this.guardando = false;
      return;
    }

    const request = {
      metricaId: this.metrica.factorId,  // factorId contiene el metricaId en Planeación
      proyectoId: proyectoId,
      objetivo: this.form.objetivo,
      procedimiento: this.form.procedimiento,
      indicadorVariable: this.form.indicadorVariable,
      escala: this.escalaTexto(),
      frecuenciaCaptura: this.form.frecuenciaCaptura || 'por_sprint',
      fuenteAcademica: this.form.fuenteAcademica || null,
      formulaAcademica: this.form.formulaAcademica || null,
      tipoOperacion: this.form.tipoOperacion || null,
      unidadResultado: this.form.unidadResultado || null,
      // revisión de captura por parametrización: independiente de
      // tipoOperacion â€” decide QUIÉN captura, no CÃ“MO se calcula.
      responsableCaptura: this.form.responsableCaptura || 'SCRUM_MASTER',
      nombreVariable: this.form.nombreVariable || null,
      propuestaIAJson: JSON.stringify(this.propuestas[0]),
      escalaTipo: this.form.escalaTipo ?? null,
      escalaMin: this.form.escalaMin ?? null,
      escalaMax: this.form.escalaMax ?? null,
      escalaPaso: this.form.escalaPaso ?? null,
      escalaSinLimite: this.form.escalaSinLimite ?? null,
      escalaDescripcion: this.form.escalaDescripcion ?? null
    };
    
    this.http.post<any>(`${this.apiBase}/parametrizacion/guardar-propuesta`, request)
      .pipe(catchError((err) => {
        this.errorGuardar = err.status === 403 
          ? 'No tienes permiso para parametrizar en este proyecto'
          : 'Error al guardar la propuesta';
        this.guardando = false;
        return of(null);
      }))
      .subscribe(parametrizacion => {
        if (parametrizacion) {
          this.parametrizacionId = parametrizacion.id;
          this.estadoActual = parametrizacion.status;
          this.versionActual = parametrizacion.version;
          // FASE 16.10-D: la respuesta de guardar-propuesta es la parametrización
          // ya persistida (incluye todos los campos académicos) â€” se conserva como
          // fuente de verdad para el momento de aprobar.
          this.propuestaPendiente = parametrizacion;
          this.guardando = false;

          // Scroll al área de aprobación
          setTimeout(() => {
            const estadoCard = document.querySelector('.border-warning');
            estadoCard?.scrollIntoView({ behavior: 'smooth', block: 'center' });
          }, 100);
        }
      });
  }
  
  /**
   * Aprueba formalmente la parametrización.
   * Cambia el estado de "propuesta" a "aprobada".
   * Crea snapshot para reproducibilidad de cálculos futuros.
   */
  aprobarParametrizacion(): void {
    if (!this.parametrizacionId) return;
    if (this.aprobando) return; // ver comentario de reentrada en guardar()
    if (!this.validarEscala()) return;

    this.aprobando = true;
    this.errorAprobar = '';

    // FASE 16.10-D: usar la propuesta realmente persistida (guardar-propuesta),
    // NO el estado en memoria de this.form, que puede haberse desincronizado
    // por una regeneración GenAI o edición posterior sin volver a guardar.
    const fuente = this.propuestaPendiente ?? this.form;
    const request = {
      objetivo: fuente.objetivo,
      procedimiento: fuente.procedimiento,
      indicadorVariable: fuente.indicadorVariable,
      escala: fuente.escala,
      frecuenciaCaptura: fuente.frecuenciaCaptura || 'por_sprint',
      fuenteAcademica: fuente.fuenteAcademica || null,
      formulaAcademica: fuente.formulaAcademica || null,
      tipoOperacion: fuente.tipoOperacion || null,
      unidadResultado: fuente.unidadResultado || null,
      // revisión de captura por parametrización: prioriza lo que el Scrum
      // Master tiene seleccionado en el formulario ahora mismo (puede haber
      // cambiado el alcance antes de aprobar) sobre lo ya guardado como
      // propuesta â€” mismo criterio que la escala, justo abajo.
      responsableCaptura: this.form.responsableCaptura || fuente.responsableCaptura || 'SCRUM_MASTER',
      nombreVariable: fuente.nombreVariable || null,
      // Corrección del manejo de escalas: prioriza lo que el Scrum Master tiene
      // en el formulario ahora mismo (puede haber ajustado la escala propuesta
      // por la IA antes de aprobar) sobre lo que ya se guardó como propuesta.
      escalaTipo: this.form.escalaTipo ?? fuente.escalaTipo ?? null,
      escalaMin: this.form.escalaMin ?? fuente.escalaMin ?? null,
      escalaMax: this.form.escalaMax ?? fuente.escalaMax ?? null,
      escalaPaso: this.form.escalaPaso ?? fuente.escalaPaso ?? null,
      escalaSinLimite: this.form.escalaSinLimite ?? fuente.escalaSinLimite ?? null,
      escalaDescripcion: this.form.escalaDescripcion ?? fuente.escalaDescripcion ?? null
    };
    
    this.http.post<any>(`${this.apiBase}/parametrizacion/${this.parametrizacionId}/aprobar`, request)
      .pipe(catchError((err) => {
        this.errorAprobar = err.status === 403 
          ? 'No tienes permiso para aprobar parametrizaciones en este proyecto'
          : err.status === 404
          ? 'parametrización no encontrada'
          : 'Error al aprobar la parametrización';
        this.aprobando = false;
        return of(null);
      }))
      .subscribe(parametrizacion => {
        if (parametrizacion) {
          this.estadoActual = parametrizacion.status;
          this.versionActual = parametrizacion.version;
          this.aprobando = false;
          
          // Guardar también en localStorage para compatibilidad con Fase 16.5
          if (this.metrica) {
            this.seleccionService.parametrizar(this.metrica.id, {
              ...this.form,
              propuestaElegida: this.propuestaElegida ?? undefined
            });
          }
        }
      });
  }
}
