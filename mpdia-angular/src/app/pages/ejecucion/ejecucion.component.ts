// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, of, forkJoin } from 'rxjs';
import { ShellComponent } from '../../layout/shell/shell.component';
import { AuthService } from '../../services/auth.service';
import { PlaneacionService } from '../../services/planeacion.service';
import { EjecucionService } from '../../services/ejecucion.service';
import { SprintService } from '../../services/sprint.service';
import { ProyectoDto } from '../../models/proyecto.model';
import { SprintDto } from '../../models/sprint.model';
import { VariableDto } from '../../models/variable.model';
import { RegistroValorDto, RegistrarValorRequest } from '../../models/ejecucion.model';

interface FormValor {
  variableId:   string;
  valorNum:     number | null;
  valorTexto:   string;
  valorBool:    boolean | null;
  observacion:  string;
  yaRegistrado: boolean;
}

@Component({
  selector: 'app-ejecucion',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell title="Ejecución — Registro de Datos">

      @if (!proyecto) {
        <div class="text-center py-5 text-muted">
          <i class="bi bi-folder-x fs-1 d-block mb-3 opacity-25"></i>
          <p>Seleccioná un proyecto primero.</p>
          <button class="btn btn-primary btn-sm" (click)="router.navigate(['/proyectos'])">
            Ir a Proyectos
          </button>
        </div>
      } @else {

        @if (alertMsg()) {
          <div class="alert py-2 small mb-3" [class]="alertClass()">{{ alertMsg() }}</div>
        }

        <!-- Selector de sprint -->
        <div class="card mb-3">
          <div class="card-body py-2">
            <div class="d-flex flex-wrap gap-3 align-items-center">
              <div style="min-width:260px">
                <label class="form-label small fw-semibold mb-1">Sprint</label>
                <select class="form-select form-select-sm"
                        [(ngModel)]="sprintSeleccionadoId"
                        (ngModelChange)="onSprintChange()">
                  <option value="">Seleccionar sprint...</option>
                  @for (s of sprints; track s.id) {
                    <option [value]="s.id" [disabled]="s.estado === 'pendiente'">
                      Sprint {{ s.numero }} — {{ s.fechaInicio | date:'dd/MM' }}
                      al {{ s.fechaFin | date:'dd/MM/yyyy' }}
                      ({{ labelEstado(s.estado) }})
                    </option>
                  }
                </select>
              </div>
              @if (sprintActual) {
                <span class="badge" [class]="badgeSprint(sprintActual.estado)">
                  {{ labelEstado(sprintActual.estado) }}
                </span>
                <span class="small text-muted">
                  {{ sprintActual.fechaInicio | date:'dd/MM/yyyy' }} —
                  {{ sprintActual.fechaFin | date:'dd/MM/yyyy' }}
                </span>
              }
            </div>
          </div>
        </div>

        @if (!sprintActual) {
          <div class="text-center py-5 text-muted small">
            <i class="bi bi-calendar-check fs-2 d-block mb-2 opacity-25"></i>
            Seleccioná un sprint para registrar valores.
          </div>

        } @else if (sprintBloqueado) {
          <div class="alert alert-warning small">
            <i class="bi bi-lock me-1"></i>
            Sprint <strong>{{ labelEstado(sprintActual.estado) }}</strong>.
            No se pueden registrar nuevos valores.
          </div>
          <div class="card">
            <div class="card-header fw-semibold small py-2">
              <i class="bi bi-table me-1"></i>Valores registrados
            </div>
            <div class="card-body p-0">
              <ng-container [ngTemplateOutlet]="tablaHistorial"></ng-container>
            </div>
          </div>

        } @else if (cargando) {
          <div class="text-center py-4 text-muted small">
            <span class="spinner-border spinner-border-sm me-2"></span>Cargando...
          </div>

        } @else if (variables.length === 0) {
          <div class="text-center py-5 text-muted">
            <i class="bi bi-exclamation-triangle fs-1 d-block mb-3 opacity-25"></i>
            <p class="small">No hay variables configuradas.<br>Aprobá métricas en Planeación para generarlas.</p>
            <div class="d-flex gap-2 justify-content-center flex-wrap mt-3">
              <button class="btn btn-outline-primary btn-sm" [disabled]="sincronizando"
                      (click)="sincronizar()">
                @if (sincronizando) {
                  <span class="spinner-border spinner-border-sm me-1"></span>
                } @else {
                  <i class="bi bi-arrow-repeat me-1"></i>
                }
                Sincronizar variables
              </button>
              <button class="btn btn-primary btn-sm" (click)="router.navigate(['/planeacion'])">
                <i class="bi bi-layers me-1"></i>Ir a Planeación
              </button>
            </div>
          </div>

        } @else {

          <!-- Filtros -->
          <div class="d-flex flex-wrap gap-2 mb-3 align-items-center">
            <span class="small fw-semibold text-muted">Filtrar por factor:</span>
            <button class="btn btn-sm py-0 px-2"
                    [class]="categoriaFiltro === '' ? 'btn-primary' : 'btn-outline-secondary'"
                    (click)="categoriaFiltro = ''">Todos</button>
            @for (cat of categorias; track cat) {
              @if (tieneVariablesEnCategoria(cat)) {
                <button class="btn btn-sm py-0 px-2"
                        [class]="categoriaFiltro === cat ? badgeCat(cat) : 'btn-outline-secondary'"
                        (click)="categoriaFiltro = cat">
                  {{ cat }}
                  <span class="ms-1" style="font-size:0.65rem">({{ contarVariablesCategoria(cat) }})</span>
                </button>
              }
            }
          </div>

          <ul class="nav nav-tabs mb-3">
            <li class="nav-item">
              <button class="nav-link" [class.active]="tab === 'grupal'" (click)="tab = 'grupal'">
                <i class="bi bi-people me-1"></i>Grupales
                <span class="badge ms-1 bg-primary">{{ formsGrupalFiltrados.length }}</span>
              </button>
            </li>
            <li class="nav-item">
              <button class="nav-link" [class.active]="tab === 'individual'" (click)="tab = 'individual'">
                <i class="bi bi-person me-1"></i>Individuales
                <span class="badge ms-1 bg-warning text-dark">{{ formsIndividualFiltrados.length }}</span>
              </button>
            </li>
            <li class="nav-item">
              <button class="nav-link" [class.active]="tab === 'historial'" (click)="tab = 'historial'">
                <i class="bi bi-clock-history me-1"></i>Historial
              </button>
            </li>
          </ul>

          <!-- GRUPALES -->
          @if (tab === 'grupal') {
            @if (!esScrumMaster) {
              <div class="alert alert-info small">
                <i class="bi bi-info-circle me-1"></i>
                Las variables grupales las registra el Scrum Master.
              </div>
            } @else if (formsGrupalFiltrados.length === 0) {
              <div class="text-center py-4 text-muted small">Sin variables grupales
                {{ categoriaFiltro ? 'en la categoría ' + categoriaFiltro : '' }}.
              </div>
            } @else {
              <div class="card">
                <div class="card-header fw-semibold small py-2 d-flex justify-content-between align-items-center">
                  <span><i class="bi bi-people me-1"></i>Registro Grupal — Sprint {{ sprintActual.numero }}</span>
                  @if (categoriaFiltro) {
                    <span class="badge" [class]="badgeCat(categoriaFiltro)">{{ categoriaFiltro }}</span>
                  }
                </div>
                <form (ngSubmit)="guardarGrupal()">
                  <div class="table-responsive">
                    <table class="table table-sm mb-0 align-middle">
                      <thead class="table-light">
                        <tr>
                          <th class="ps-3">Variable</th>
                          <th>Factor</th>
                          <th>Tipo</th>
                          <th>Valor</th>
                          <th>Observación</th>
                          <th class="text-center">OK</th>
                        </tr>
                      </thead>
                      <tbody>
                        @for (fv of formsGrupalFiltrados; track fv.variableId) {
                          <tr [class.table-success]="fv.yaRegistrado">
                            <td class="ps-3 fw-semibold small">
                              <div class="d-flex align-items-center gap-2">
                                <span>{{ nombreVariable(fv.variableId) }}</span>
                                @if (tieneInfoContextual(fv.variableId)) {
                                  <button type="button" 
                                          class="btn btn-sm btn-link p-0" 
                                          (click)="toggleInfo(fv.variableId)"
                                          [title]="isInfoExpanded(fv.variableId) ? 'Ocultar' : 'Ver guía'">
                                    <i class="bi" 
                                       [class.bi-info-circle]="!isInfoExpanded(fv.variableId)"
                                       [class.bi-info-circle-fill]="isInfoExpanded(fv.variableId)"
                                       [class.text-primary]="!isInfoExpanded(fv.variableId)"
                                       [class.text-success]="isInfoExpanded(fv.variableId)"></i>
                                  </button>
                                }
                              </div>
                              @if (isInfoExpanded(fv.variableId)) {
                                <div class="alert alert-info py-2 px-2 small mt-2 mb-0" style="font-weight:normal">
                                  @if (getObjetivo(fv.variableId)) {
                                    <div class="mb-1"><strong>🎯 Objetivo:</strong> {{ getObjetivo(fv.variableId) }}</div>
                                  }
                                  @if (getProcedimiento(fv.variableId)) {
                                    <div class="mb-1"><strong>📏 Como medir:</strong> {{ getProcedimiento(fv.variableId) }}</div>
                                  }
                                  @if (getEscalaDefinicion(fv.variableId)) {
                                    <div class="mb-1"><strong>📊 Escala:</strong> {{ getEscalaDefinicion(fv.variableId) }}</div>
                                  }
                                  <div><strong>📅 Frecuencia:</strong> {{ getFrecuenciaTexto(fv.variableId) }} <span class="text-muted">(IA)</span></div>
                                </div>
                              }
                            </td>
                            <td>
                              <span class="badge" style="font-size:.6rem"
                                    [class]="badgeCat(categoriaVariable(fv.variableId))">
                                {{ categoriaVariable(fv.variableId) }}
                              </span>
                            </td>
                            <td class="small text-muted">{{ tipoVariable(fv.variableId) }}</td>
                            <td>
                              @if (fv.yaRegistrado) {
                                <strong class="text-success">{{ valorTextoMostrado(fv.variableId, fv) }}</strong>
                              } @else if (tipoVariable(fv.variableId) === 'numerico') {
                                <input type="number" class="form-control form-control-sm"
                                       style="width:90px" step="0.01"
                                       [(ngModel)]="fv.valorNum"
                                       [name]="'g-n-' + fv.variableId">
                              } @else if (tipoVariable(fv.variableId) === 'escala') {
                                <select class="form-select form-select-sm" style="width:75px"
                                        [(ngModel)]="fv.valorNum"
                                        [name]="'g-e-' + fv.variableId">
                                  <option [value]="null">—</option>
                                  @for (n of escalaVariable(fv.variableId); track n) {
                                    <option [value]="n">{{ n }}</option>
                                  }
                                </select>
                              } @else if (tipoVariable(fv.variableId) === 'texto') {
                                <input type="text" class="form-control form-control-sm"
                                       style="width:140px"
                                       [(ngModel)]="fv.valorTexto"
                                       [name]="'g-t-' + fv.variableId">
                              } @else {
                                <div class="form-check form-switch mb-0">
                                  <input class="form-check-input" type="checkbox"
                                         [(ngModel)]="fv.valorBool"
                                         [name]="'g-b-' + fv.variableId">
                                </div>
                              }
                            </td>
                            <td>
                              @if (!fv.yaRegistrado) {
                                <input type="text" class="form-control form-control-sm"
                                       placeholder="Opcional"
                                       [(ngModel)]="fv.observacion"
                                       [name]="'g-o-' + fv.variableId">
                              } @else {
                                <span class="text-muted small fst-italic">{{ fv.observacion || '—' }}</span>
                              }
                            </td>
                            <td class="text-center">
                              @if (fv.yaRegistrado) {
                                <i class="bi bi-check-circle-fill text-success"></i>
                              } @else {
                                <i class="bi bi-circle text-muted"></i>
                              }
                            </td>
                          </tr>
                        }
                      </tbody>
                    </table>
                  </div>
                  <div class="card-footer d-flex justify-content-end py-2">
                    <button type="submit" class="btn btn-primary btn-sm" [disabled]="guardando">
                      @if (guardando) {
                        <span class="spinner-border spinner-border-sm me-1"></span>
                      } @else {
                        <i class="bi bi-floppy me-1"></i>
                      }
                      Guardar grupales
                    </button>
                  </div>
                </form>
              </div>
            }
          }

          <!-- INDIVIDUALES -->
          @if (tab === 'individual') {
            @if (formsIndividualFiltrados.length === 0) {
              <div class="text-center py-4 text-muted small">Sin variables individuales
                {{ categoriaFiltro ? 'en la categoría ' + categoriaFiltro : '' }}.
              </div>
            } @else {
              <div class="card">
                <div class="card-header fw-semibold small py-2 d-flex justify-content-between align-items-center">
                  <span><i class="bi bi-person me-1"></i>Registro Individual — Sprint {{ sprintActual.numero }}</span>
                  @if (categoriaFiltro) {
                    <span class="badge" [class]="badgeCat(categoriaFiltro)">{{ categoriaFiltro }}</span>
                  }
                </div>
                <form (ngSubmit)="guardarIndividual()">
                  <div class="table-responsive">
                    <table class="table table-sm mb-0 align-middle">
                      <thead class="table-light">
                        <tr>
                          <th class="ps-3">Variable</th>
                          <th>Factor</th>
                          <th>Valor</th>
                          <th>Observación</th>
                          <th class="text-center">OK</th>
                        </tr>
                      </thead>
                      <tbody>
                        @for (fv of formsIndividualFiltrados; track fv.variableId) {
                          <tr [class.table-success]="fv.yaRegistrado">
                            <td class="ps-3 fw-semibold small">
                              <div class="d-flex align-items-center gap-2">
                                <span>{{ nombreVariable(fv.variableId) }}</span>
                                @if (tieneInfoContextual(fv.variableId)) {
                                  <button type="button" 
                                          class="btn btn-sm btn-link p-0" 
                                          (click)="toggleInfo(fv.variableId)"
                                          [title]="isInfoExpanded(fv.variableId) ? 'Ocultar' : 'Ver guía'">
                                    <i class="bi" 
                                       [class.bi-info-circle]="!isInfoExpanded(fv.variableId)"
                                       [class.bi-info-circle-fill]="isInfoExpanded(fv.variableId)"
                                       [class.text-primary]="!isInfoExpanded(fv.variableId)"
                                       [class.text-success]="isInfoExpanded(fv.variableId)"></i>
                                  </button>
                                }
                              </div>
                              @if (isInfoExpanded(fv.variableId)) {
                                <div class="alert alert-info py-2 px-2 small mt-2 mb-0" style="font-weight:normal">
                                  @if (getObjetivo(fv.variableId)) {
                                    <div class="mb-1"><strong>🎯 Objetivo:</strong> {{ getObjetivo(fv.variableId) }}</div>
                                  }
                                  @if (getProcedimiento(fv.variableId)) {
                                    <div class="mb-1"><strong>📏 Como medir:</strong> {{ getProcedimiento(fv.variableId) }}</div>
                                  }
                                  @if (getEscalaDefinicion(fv.variableId)) {
                                    <div class="mb-1"><strong>📊 Escala:</strong> {{ getEscalaDefinicion(fv.variableId) }}</div>
                                  }
                                  <div><strong>📅 Frecuencia:</strong> {{ getFrecuenciaTexto(fv.variableId) }} <span class="text-muted">(IA)</span></div>
                                </div>
                              }
                            </td>
                            <td>
                              <span class="badge" style="font-size:.6rem"
                                    [class]="badgeCat(categoriaVariable(fv.variableId))">
                                {{ categoriaVariable(fv.variableId) }}
                              </span>
                            </td>
                            <td>
                              @if (fv.yaRegistrado) {
                                <strong class="text-success">{{ valorTextoMostrado(fv.variableId, fv) }}</strong>
                              } @else if (tipoVariable(fv.variableId) === 'escala') {
                                <select class="form-select form-select-sm" style="width:75px"
                                        [(ngModel)]="fv.valorNum"
                                        [name]="'i-e-' + fv.variableId">
                                  <option [value]="null">—</option>
                                  @for (n of escalaVariable(fv.variableId); track n) {
                                    <option [value]="n">{{ n }}</option>
                                  }
                                </select>
                              } @else {
                                <input type="number" class="form-control form-control-sm"
                                       style="width:90px" step="0.01"
                                       [(ngModel)]="fv.valorNum"
                                       [name]="'i-n-' + fv.variableId">
                              }
                            </td>
                            <td>
                              @if (!fv.yaRegistrado) {
                                <input type="text" class="form-control form-control-sm"
                                       placeholder="Opcional"
                                       [(ngModel)]="fv.observacion"
                                       [name]="'i-o-' + fv.variableId">
                              } @else {
                                <span class="text-muted small fst-italic">{{ fv.observacion || '—' }}</span>
                              }
                            </td>
                            <td class="text-center">
                              @if (fv.yaRegistrado) {
                                <i class="bi bi-check-circle-fill text-success"></i>
                              } @else {
                                <i class="bi bi-circle text-muted"></i>
                              }
                            </td>
                          </tr>
                        }
                      </tbody>
                    </table>
                  </div>
                  <div class="card-footer d-flex justify-content-end py-2">
                    <button type="submit" class="btn btn-warning btn-sm text-dark" [disabled]="guardando">
                      @if (guardando) {
                        <span class="spinner-border spinner-border-sm me-1"></span>
                      } @else {
                        <i class="bi bi-floppy me-1"></i>
                      }
                      Guardar individuales
                    </button>
                  </div>
                </form>
              </div>
            }
          }

          <!-- HISTORIAL -->
          @if (tab === 'historial') {
            <div class="card">
              <div class="card-header fw-semibold small py-2">
                <i class="bi bi-clock-history me-1"></i>Historial — Sprint {{ sprintActual.numero }}
              </div>
              <div class="card-body p-0">
                <ng-container [ngTemplateOutlet]="tablaHistorial"></ng-container>
              </div>
            </div>
          }
        }
      }

      <!-- Template historial reutilizable -->
      <ng-template #tablaHistorial>
        @if (registros.length === 0) {
          <div class="text-center py-4 text-muted small">Sin registros.</div>
        } @else {
          <div class="table-responsive">
            <table class="table table-sm mb-0">
              <thead class="table-light">
                <tr>
                  <th class="ps-3">Variable</th>
                  <th>Valor</th>
                  <th>Usuario</th>
                  <th>Fecha</th>
                  <th>Obs.</th>
                </tr>
              </thead>
              <tbody>
                @for (r of registros; track r.id) {
                  <tr>
                    <td class="ps-3 small fw-semibold">{{ r.variableNombre }}</td>
                    <td class="fw-bold">
                      {{ r.valorNum !== null ? r.valorNum : (r.valorTexto ?? (r.valorBool ? 'Sí' : 'No')) }}
                    </td>
                    <td class="small text-muted">{{ r.userId }}</td>
                    <td class="small text-muted">{{ r.registradoAt | date:'dd/MM/yyyy HH:mm' }}</td>
                    <td class="small text-muted fst-italic">{{ r.observacion || '—' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </ng-template>

    </app-shell>
  `
})
export class EjecucionComponent implements OnInit {
  proyecto:  ProyectoDto | null  = null;
  sprints:   SprintDto[]         = [];
  variables: VariableDto[]       = [];
  registros: RegistroValorDto[]  = [];

  sprintSeleccionadoId = '';
  sprintActual: SprintDto | null = null;
  tab: 'grupal' | 'individual' | 'historial' = 'grupal';
  categoriaFiltro = '';

  formsGrupal:     FormValor[] = [];
  formsIndividual: FormValor[] = [];

  cargando  = false;
  guardando = false;
  sincronizando = false;
  expandedInfo: Set<string> = new Set(); // Control de info expandida

  alertMsg   = signal('');
  alertClass = signal('alert-success');

  readonly categorias = ['Calidad', 'Productividad', 'Cumplimiento', 'Flexibilidad', 'Sociohumano'];

  constructor(
    public  router: Router,
    public  auth: AuthService,
    private planeacionService: PlaneacionService,
    private ejecucionService: EjecucionService,
    private sprintService: SprintService
  ) {}

  get esScrumMaster()    { return this.auth.currentUser()?.role === 'scrum_master'; }
  get sprintBloqueado()  { return this.sprintActual?.estado === 'finalizado' || this.sprintActual?.estado === 'pendiente'; }
  get variablesGrupales()     { return this.variables.filter(v => v.tipoAlcance === 'grupal'); }
  get variablesIndividuales() { return this.variables.filter(v => v.tipoAlcance === 'individual'); }

  get formsGrupalFiltrados(): FormValor[] {
    if (!this.categoriaFiltro) return this.formsGrupal;
    return this.formsGrupal.filter(f => this.categoriaVariable(f.variableId) === this.categoriaFiltro);
  }

  get formsIndividualFiltrados(): FormValor[] {
    if (!this.categoriaFiltro) return this.formsIndividual;
    return this.formsIndividual.filter(f => this.categoriaVariable(f.variableId) === this.categoriaFiltro);
  }

  tieneVariablesEnCategoria(cat: string): boolean {
    return this.variables.some(v => v.metricaCategoria === cat);
  }

  contarVariablesCategoria(cat: string): number {
    return this.variables.filter(v => v.metricaCategoria === cat).length;
  }

  // ── helpers ──────────────────────────────────────────────────────────
  nombreVariable(id: string):    string { return this.variables.find(v => v.id === id)?.nombre          ?? id; }
  categoriaVariable(id: string): string { return this.variables.find(v => v.id === id)?.metricaCategoria ?? ''; }
  tipoVariable(id: string):      string { return this.variables.find(v => v.id === id)?.tipoDato         ?? 'numerico'; }

  escalaVariable(id: string): number[] {
    const v = this.variables.find(x => x.id === id);
    const min = v?.escalaMin ?? 1;
    const max = v?.escalaMax ?? 5;
    const arr: number[] = [];
    for (let i = min; i <= max; i++) arr.push(i);
    return arr;
  }

  valorTextoMostrado(id: string, fv: FormValor): string {
    const tipo = this.tipoVariable(id);
    if (tipo === 'booleano') return fv.valorBool ? 'Sí' : 'No';
    if (tipo === 'texto')    return fv.valorTexto;
    return fv.valorNum?.toString() ?? '—';
  }

  ngOnInit(): void {
    try {
      const p = localStorage.getItem('mpdia_proyecto_activo');
      this.proyecto = p ? JSON.parse(p) : null;
    } catch { /**/ }

    if (this.proyecto) {
      forkJoin({
        sprints:   this.sprintService.listar(this.proyecto.id).pipe(catchError(() => of([]))),
        variables: this.planeacionService.listarVariables(this.proyecto.id).pipe(catchError(() => of([])))
      }).subscribe(({ sprints, variables }) => {
        this.sprints   = [...sprints].sort((a, b) => a.numero - b.numero);
        this.variables = variables;
        const activo = this.sprints.find(s => s.estado === 'en_ejecucion');
        if (activo) { this.sprintSeleccionadoId = activo.id; this.onSprintChange(); }

        // Si no hay variables pero hay proyecto activo, intentar sincronizar automáticamente
        if (variables.length === 0) {
          this.sincronizar();
        }
      });
    }
  }

  /** Regenera variables faltantes para métricas ya aprobadas en este proyecto */
  sincronizar(): void {
    if (!this.proyecto || this.sincronizando) return;
    this.sincronizando = true;
    this.planeacionService.sincronizarVariables(this.proyecto.id).pipe(
      catchError(() => of([]))
    ).subscribe(variables => {
      this.sincronizando = false;
      if (variables.length > 0) {
        this.variables = variables;
        this.alert(`${variables.length} variable(s) sincronizada(s).`, 'alert-success');
        // Reconstruir formularios si hay sprint activo
        if (this.sprintActual) this.onSprintChange();
      }
    });
  }

  onSprintChange(): void {
    this.sprintActual = this.sprints.find(s => s.id === this.sprintSeleccionadoId) ?? null;
    if (!this.sprintActual) return;
    this.cargando = true;
    this.ejecucionService.listarPorSprint(this.sprintActual.id).pipe(
      catchError(() => of([]))
    ).subscribe(r => { this.registros = r; this.construirForms(r); this.cargando = false; });
  }

  private construirForms(registros: RegistroValorDto[]): void {
    const userId = this.auth.currentUser()?.userId ?? '';
    const mkForm = (v: VariableDto, filtro?: (r: RegistroValorDto) => boolean): FormValor => {
      const ex = registros.find(filtro ?? (r => r.variableId === v.id));
      return {
        variableId: v.id, valorNum: ex?.valorNum ?? null, valorTexto: ex?.valorTexto ?? '',
        valorBool: ex?.valorBool ?? null, observacion: ex?.observacion ?? '', yaRegistrado: !!ex
      };
    };
    this.formsGrupal     = this.variablesGrupales.map(v => mkForm(v));
    this.formsIndividual = this.variablesIndividuales.map(v =>
      mkForm(v, r => r.variableId === v.id && r.userId === userId));
  }

  guardarGrupal():     void { this.guardar(this.formsGrupal.filter(f => !f.yaRegistrado)); }
  guardarIndividual(): void { this.guardar(this.formsIndividual.filter(f => !f.yaRegistrado)); }

  private guardar(forms: FormValor[]): void {
    if (!this.sprintActual) return;
    const reqs: RegistrarValorRequest[] = forms
      .filter(f => f.valorNum !== null || f.valorTexto || f.valorBool !== null)
      .map(f => ({
        variableId: f.variableId,
        sprintId:   this.sprintActual!.id,
        valorNum:   f.valorNum   ?? undefined,
        valorTexto: f.valorTexto || undefined,
        valorBool:  f.valorBool  ?? undefined,
        observacion: f.observacion || undefined
      }));

    if (!reqs.length) { this.alert('Ingresá al menos un valor.', 'alert-warning'); return; }
    this.guardando = true;
    let done = 0;
    const next = (i: number) => {
      if (i >= reqs.length) {
        this.guardando = false;
        this.alert(`${done} valor(es) guardado(s).`, 'alert-success');
        this.onSprintChange();
        return;
      }
      this.ejecucionService.registrar(reqs[i]).pipe(
        catchError(e => { this.alert(e?.error?.error ?? 'Error.', 'alert-danger'); this.guardando = false; return of(null); })
      ).subscribe(r => { if (r) { done++; next(i + 1); } });
    };
    next(0);
  }

  labelEstado(e: string): string {
    return ({ 'en_ejecucion': 'En ejecución', 'pendiente': 'Pendiente', 'finalizado': 'Finalizado', 'reabierto': 'Reabierto' } as Record<string, string>)[e] ?? e;
  }

  badgeSprint(e: string): string {
    return ({ 'en_ejecucion': 'bg-success', 'pendiente': 'bg-warning text-dark', 'finalizado': 'bg-secondary', 'reabierto': 'bg-info text-dark' } as Record<string, string>)[e] ?? 'bg-secondary';
  }

  badgeCat(c: string): string {
    return ({
      'Calidad':       'bg-danger',
      'Productividad': 'bg-primary',
      'Cumplimiento':  'bg-success',
      'Flexibilidad':  'bg-warning text-dark',
      'Sociohumano':   'bg-info text-dark'
    } as Record<string, string>)[c] ?? 'bg-secondary';
  }

  private alert(msg: string, cls: string): void {
    this.alertMsg.set(msg); this.alertClass.set(cls);
    setTimeout(() => this.alertMsg.set(''), 4000);
  }

  // Métodos para información de parametrización (guía contextual)
  getObjetivo(id: string): string { return this.variables.find(v => v.id === id)?.objetivo ?? ''; }
  getProcedimiento(id: string): string { return this.variables.find(v => v.id === id)?.procedimiento ?? ''; }
  getEscalaDefinicion(id: string): string { return this.variables.find(v => v.id === id)?.escalaDefinicion ?? ''; }
  getFrecuenciaCaptura(id: string): string { return this.variables.find(v => v.id === id)?.frecuenciaCaptura ?? 'por_sprint'; }
  getFrecuenciaTexto(id: string): string { 
    const freq = this.getFrecuenciaCaptura(id);
    const map: Record<string, string> = {
      'por_sprint': 'Al finalizar sprint',
      'semanal': 'Una vez por semana',
      'diaria': 'Diariamente',
      'ilimitada': 'Cuando ocurra el evento'
    };
    return map[freq] || freq;
  }
  tieneInfoContextual(id: string): boolean { 
    const v = this.variables.find(x => x.id === id); 
    return !!(v?.objetivo || v?.procedimiento || v?.escalaDefinicion); 
  }
  getTooltipTexto(id: string): string {
    const partes: string[] = [];
    if (this.getObjetivo(id)) partes.push('Objetivo: ' + this.getObjetivo(id));
    if (this.getProcedimiento(id)) partes.push('Como medir: ' + this.getProcedimiento(id));
    if (this.getEscalaDefinicion(id)) partes.push('Escala: ' + this.getEscalaDefinicion(id));
    if (this.getFrecuenciaTexto(id)) partes.push('Frecuencia: ' + this.getFrecuenciaTexto(id));
    return partes.join('\\n');
  }

  toggleInfo(id: string): void {
    if (this.expandedInfo.has(id)) {
      this.expandedInfo.delete(id);
    } else {
      this.expandedInfo.add(id);
    }
  }

  isInfoExpanded(id: string): boolean {
    return this.expandedInfo.has(id);
  }
}
