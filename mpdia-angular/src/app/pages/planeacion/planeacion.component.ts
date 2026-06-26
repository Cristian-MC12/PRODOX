// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
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

type Paso = 'metricas' | 'variables' | 'sprints';

@Component({
  selector: 'app-planeacion',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell title="Planeación">

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

        <!-- Info del proyecto -->
        <div class="card mb-3 border-primary">
          <div class="card-body py-2">
            <div class="d-flex flex-wrap gap-3 align-items-center">
              <div>
                <div class="text-muted small">Proyecto</div>
                <div class="fw-semibold">{{ proyecto.nombre }}</div>
              </div>
              <div class="vr"></div>
              <span class="badge" [class]="proyecto.metodo === 'scrum' ? 'bg-primary' : 'bg-info text-dark'">
                {{ proyecto.metodo | uppercase }}
              </span>
              <div class="vr"></div>
              <div>
                <div class="text-muted small">Sprints</div>
                <div class="fw-semibold">{{ proyecto.numeroSprints }} × {{ proyecto.timeBoxSemanas }} sem</div>
              </div>
              @if (proyecto.fechaInicio) {
                <div class="vr"></div>
                <div>
                  <div class="text-muted small">Inicio</div>
                  <div class="fw-semibold">{{ proyecto.fechaInicio | date:'dd/MM/yyyy' }}</div>
                </div>
              }
              <div class="vr"></div>
              <div>
                <div class="text-muted small">Product Goal</div>
                <div class="small">{{ proyecto.productGoal | slice:0:80 }}</div>
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
              <div class="card h-100">
                <div class="card-header d-flex align-items-center justify-content-between py-2">
                  <span class="fw-semibold small">
                    <i class="bi bi-grid me-1"></i>Catálogo de Métricas
                  </span>
                  <div class="d-flex gap-2 ms-2">
                    <select class="form-select form-select-sm"
                            style="max-width:150px"
                            [(ngModel)]="categoriaFiltro">
                      <option value="">Todas las categorías</option>
                      @for (cat of categorias; track cat) {
                        <option [value]="cat">{{ cat }}</option>
                      }
                    </select>
                    <input type="text" class="form-control form-control-sm"
                           style="max-width:160px"
                           placeholder="Buscar..."
                           [(ngModel)]="busqueda">
                  </div>
                </div>
                <div class="card-body p-0" style="max-height:500px;overflow-y:auto">
                  @if (cargando) {
                    <div class="text-center py-4 text-muted small">
                      <span class="spinner-border spinner-border-sm me-2"></span>Cargando...
                    </div>
                  } @else {
                    @for (cat of categorias; track cat) {
                      @if (metricasFiltradas(cat).length > 0) {
                        <div class="px-3 pt-2 pb-1">
                          <span class="badge" [class]="badgeCategoria(cat)">{{ cat }}</span>
                        </div>
                        @for (m of metricasFiltradas(cat); track m.metricaId) {
                          <div class="d-flex align-items-start gap-2 px-3 py-2 border-bottom metrica-row"
                               [class.bg-success-subtle]="m.aprobada"
                               [class.bg-primary-subtle]="estaSeleccionada(m) && !m.aprobada">
                            <div class="flex-grow-1">
                              <div class="fw-semibold small">{{ m.nombre }}</div>
                              <div class="text-muted" style="font-size:0.72rem">{{ m.descripcion }}</div>
                              <div class="mt-1 d-flex gap-1 flex-wrap">
                                <span class="badge bg-light text-dark border" style="font-size:0.62rem">
                                  {{ m.codigo }}
                                </span>
                                @if (m.aprobada) {
                                  <span class="badge bg-success" style="font-size:0.62rem">
                                    <i class="bi bi-check me-1"></i>Aprobada
                                  </span>
                                  @if (m.tieneVariable) {
                                    <span class="badge bg-info text-dark" style="font-size:0.62rem">
                                      <i class="bi bi-lightning me-1"></i>Variable generada
                                    </span>
                                  }
                                } @else if (estaSeleccionada(m)) {
                                  <span class="badge bg-warning text-dark" style="font-size:0.62rem">
                                    Pendiente aprobación
                                  </span>
                                }
                              </div>
                            </div>
                            <div class="d-flex gap-1 flex-shrink-0 pt-1">
                              @if (!estaSeleccionada(m)) {
                                <button class="btn btn-sm btn-outline-primary py-0 px-2"
                                        (click)="seleccionar(m)" title="Agregar">
                                  <i class="bi bi-plus-lg"></i>
                                </button>
                              } @else if (!m.aprobada) {
                                <button class="btn btn-sm btn-success py-0 px-2"
                                        (click)="aprobar(m)" title="Aprobar y generar variable"
                                        [disabled]="aprobando === m.metricaId">
                                  @if (aprobando === m.metricaId) {
                                    <span class="spinner-border spinner-border-sm"></span>
                                  } @else {
                                    <i class="bi bi-check-lg"></i>
                                  }
                                </button>
                                <button class="btn btn-sm btn-outline-danger py-0 px-2"
                                        (click)="deseleccionar(m)" title="Quitar">
                                  <i class="bi bi-x-lg"></i>
                                </button>
                              } @else {
                                <button class="btn btn-sm btn-outline-warning py-0 px-2"
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
                </div>
              </div>
            </div>

            <!-- Panel derecho: seleccionadas + parametrización -->
            <div class="col-lg-5">
              <div class="card mb-3">
                <div class="card-header fw-semibold small py-2">
                  <i class="bi bi-check2-square me-1"></i>Seleccionadas
                  <span class="badge bg-primary ms-1">{{ totalSeleccionadas }}</span>
                  <span class="badge bg-success ms-1">{{ totalAprobadas }} aprobadas</span>
                </div>
                <div style="max-height:200px;overflow-y:auto">
                  @if (seleccionadasList.length === 0) {
                    <div class="text-center text-muted py-3 small">
                      Hacé click en <i class="bi bi-plus-lg"></i> para agregar métricas.
                    </div>
                  } @else {
                    <table class="table table-sm mb-0">
                      <tbody>
                        @for (m of seleccionadasList; track m.metricaId) {
                            <tr>
                              <td class="ps-3 small">{{ m.nombre }}</td>
                              <td>
                                <span class="badge" style="font-size:0.6rem"
                                      [class]="badgeCategoria(m.categoria)">
                                  {{ m.categoria }}
                                </span>
                              </td>
                              <td class="text-end pe-2">
                                @if (m.aprobada) {
                                  <i class="bi bi-check-circle-fill text-success"></i>
                                } @else {
                                  <i class="bi bi-clock text-warning"></i>
                                }
                              </td>
                            </tr>
                        }
                      </tbody>
                    </table>
                  }
                </div>
              </div>

              <!-- Panel de parametrización con GenAI -->
              @if (seleccionadasList.length > 0) {
                <div class="card mb-3">
                  <div class="card-header d-flex align-items-center justify-content-between py-2">
                    <span class="fw-semibold small">
                      <i class="bi bi-robot me-1 text-primary"></i>Parametrización GenAI
                    </span>
                    <span class="badge bg-success">
                      {{ parametrizacionesCompletas }} / {{ seleccionadasList.length }}
                    </span>
                  </div>
                  <div class="card-body p-0">
                    @for (m of seleccionadasList; track m.metricaId) {
                      <div class="d-flex align-items-center justify-content-between px-3 py-2 border-bottom">
                        <div class="flex-grow-1 me-2" style="min-width:0">
                          <div class="small fw-semibold text-truncate">{{ m.nombre }}</div>
                          <div class="mt-1">
                            @switch (estadoParametrizacion(m)) {
                              @case ('completa') {
                                <span class="badge bg-success" style="font-size:0.62rem">
                                  <i class="bi bi-check-circle me-1"></i>Completa
                                </span>
                              }
                              @case ('parcial') {
                                <span class="badge bg-warning text-dark" style="font-size:0.62rem">
                                  <i class="bi bi-exclamation-circle me-1"></i>Parcial
                                </span>
                              }
                              @default {
                                <span class="badge bg-secondary" style="font-size:0.62rem">
                                  <i class="bi bi-dash-circle me-1"></i>Sin parametrizar
                                </span>
                              }
                            }
                          </div>
                        </div>
                        <button class="btn btn-sm py-0 px-2 flex-shrink-0"
                                [class]="estadoParametrizacion(m) === 'sin_parametrizar' ? 'btn-outline-primary' : 'btn-outline-success'"
                                (click)="irAParametrizar(m)"
                                title="Parametrizar con GenAI">
                          <i class="bi bi-stars me-1"></i>
                          {{ estadoParametrizacion(m) === 'sin_parametrizar' ? 'Parametrizar' : 'Editar' }}
                        </button>
                      </div>
                    }
                  </div>
                  <div class="card-footer py-2 text-center">
                    <button class="btn btn-sm w-100"
                            [class]="parametrizacionesCompletas === seleccionadasList.length ? 'btn-success' : 'btn-outline-primary'"
                            (click)="router.navigate(['/resumen-seleccion'])">
                      <i class="bi bi-list-check me-1"></i>
                      Ver resumen de parametrización
                      @if (parametrizacionesCompletas < seleccionadasList.length) {
                        <span class="badge bg-light text-dark ms-1">
                          {{ parametrizacionesCompletas }}/{{ seleccionadasList.length }}
                        </span>
                      }
                    </button>
                  </div>
                </div>
              }

              <!-- Guía de estados -->
              <div class="card">
                <div class="card-header fw-semibold small py-2">
                  <i class="bi bi-info-circle me-1"></i>Flujo de planeación
                </div>
                <div class="card-body py-2 small text-muted">
                  <div class="d-flex gap-2 align-items-center mb-2">
                    <i class="bi bi-plus-circle text-primary fs-5"></i>
                    <div><strong>Seleccionar</strong> — agrega la métrica al proyecto.</div>
                  </div>
                  <div class="d-flex gap-2 align-items-center mb-2">
                    <i class="bi bi-check-circle text-success fs-5"></i>
                    <div><strong>Aprobar</strong> — genera automáticamente la variable con sus metadatos.</div>
                  </div>
                  <div class="d-flex gap-2 align-items-center">
                    <i class="bi bi-lightning text-info fs-5"></i>
                    <div><strong>Variable generada</strong> — lista para registrar valores en Ejecución.</div>
                  </div>
                </div>
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
              <div class="text-center py-5 text-muted small">
                <i class="bi bi-lightning fs-2 d-block mb-2 opacity-25"></i>
                No hay variables generadas. Aprobá métricas en la pestaña anterior.
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
                          <span class="badge" [class]="badgeCategoria(v.metricaCategoria)"
                                style="font-size:0.65rem">
                            {{ v.metricaCategoria }}
                          </span>
                        </td>
                        <td>
                          <span class="badge" style="font-size:0.65rem"
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
                          <span class="badge" style="font-size:0.65rem"
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

  alertMsg   = signal('');
  alertClass = signal('alert-success');

  private selecciones: MetricaSeleccionada[] = [];

  readonly categorias = ['Calidad', 'Productividad', 'Cumplimiento', 'Flexibilidad', 'Sociohumano'];

  constructor(
    public  router: Router,
    public  auth: AuthService,
    private planeacionService: PlaneacionService,
    private sprintService: SprintService,
    private seleccionService: SeleccionService
  ) {}

  get totalSeleccionadas() { return this.metricas.filter(m => this.estaSeleccionada(m)).length; }
  get totalAprobadas()     { return this.metricas.filter(m => m.aprobada).length; }
  get seleccionadasList()  { return this.metricas.filter(m => this.estaSeleccionada(m)); }
  get parametrizacionesCompletas() {
    return this.seleccionadasList.filter(m => this.estadoParametrizacion(m) === 'completa').length;
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
        sprints:  this.sprintService.listar(this.proyecto.id).pipe(catchError(() => of([])))
      }).subscribe(({ metricas, sprints }) => {
        this.metricas = metricas;
        this.sprints  = [...sprints].sort((a, b) => a.numero - b.numero);
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

    // Agregar solo las seleccionadas que NO estén aprobadas aún
    this.seleccionadasList.filter(m => !m.aprobada).forEach(m => {
      this.seleccionService.agregar({
        factorId:           m.metricaId,
        factorNombre:       m.nombre,
        factorCategoria:    m.categoria,
        metricaNombre:      m.nombre,
        metricaDescripcion: m.descripcion ?? '',
        proyectoId
      });
    });
  }

  estadoParametrizacion(m: ProyectoMetricaDto): 'sin_parametrizar' | 'parcial' | 'completa' {
    const sel = this.selecciones.find(
      s => s.factorId === m.metricaId || s.metricaNombre === m.nombre
    );
    return sel?.estadoParametrizacion ?? 'sin_parametrizar';
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

  metricasFiltradas(categoria: string): ProyectoMetricaDto[] {
    if (this.categoriaFiltro && this.categoriaFiltro !== categoria) return [];
    return this.metricas.filter(m =>
      m.categoria === categoria &&
      (!this.busqueda || m.nombre.toLowerCase().includes(this.busqueda.toLowerCase()) ||
       m.codigo?.toLowerCase().includes(this.busqueda.toLowerCase()))
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
      'Calidad':       'bg-danger',
      'Productividad': 'bg-primary',
      'Cumplimiento':  'bg-success',
      'Flexibilidad':  'bg-warning text-dark',
      'Sociohumano':   'bg-info text-dark'
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
