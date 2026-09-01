// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { catchError, of } from 'rxjs';
import { ShellComponent } from '../../layout/shell/shell.component';
import { AuthService } from '../../services/auth.service';
import { MetricRankingService } from '../../services/metric-ranking.service';
import { SeleccionService } from '../../services/seleccion.service';
import { MetricParametrizacionBase } from '../../models/metric-ranking.model';
import { environment } from '../../../environments/environment';

interface Pendiente {
  id:                string;
  factorId:          string;
  factorNombre:      string;
  factorCategoria:   string;
  userEmail:         string;
  objetivo:          string;
  procedimiento:     string;
  indicadorVariable: string;
  escala:            string;
  status:            string;
  revisadoPor:       string | null;
  motivoRechazo:     string | null;
  createdAt:         string;
  /** Snapshot crudo (jsonb) que puede traer un nombreVariable técnico ya fijado. */
  configuracionAprobadaJson?: string | null;
  /**
   * Solo se usa en el modal de edición: identificador técnico opcional. Si se deja
   * vacío o excede 120 caracteres, el backend genera uno automáticamente — nunca
   * bloquea guardar ni aprobar por este motivo.
   */
  nombreVariable?:   string;
}

const NOMBRE_VARIABLE_MAX = 120;

@Component({
  selector: 'app-verificacion',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell title="Verificación del Scrum Master">

      <!-- Acceso denegado si no es Scrum Master -->
      @if (!esScrumMaster) {
        <div class="alert alert-danger d-flex align-items-center gap-3">
          <i class="bi bi-shield-x fs-4"></i>
          <div>
            <strong>Acceso restringido.</strong><br>
            <small>Solo el Scrum Master puede acceder a esta pantalla.</small>
          </div>
        </div>
      } @else {

        <!-- Breadcrumb -->
        <nav aria-label="breadcrumb" class="mb-3">
          <ol class="breadcrumb small mb-0">
            <li class="breadcrumb-item">
              <a href="#" (click)="$event.preventDefault(); router.navigate(['/planeacion'])">
                <i class="bi bi-layers me-1"></i>Planeación
              </a>
            </li>
            <li class="breadcrumb-item">
              <a href="#" (click)="$event.preventDefault(); router.navigate(['/resumen-seleccion'])">Resumen de Selección</a>
            </li>
            <li class="breadcrumb-item active">Verificación</li>
          </ol>
        </nav>

        <p class="text-muted small mb-4">
          Revisá las parametrizaciones enviadas por el equipo y aprobá o rechazá cada una.
        </p>

        <!-- FASE 14 — BLOQUE 1: documentación del comportamiento real de versionado,
             para que el Scrum Master entienda las consecuencias de aprobar una nueva versión. -->
        <div class="alert alert-info small mb-4" role="note">
          <i class="bi bi-info-circle me-1"></i>
          <strong>Sobre las versiones de parametrización:</strong> al aprobar una nueva versión,
          la versión anterior deja de estar vigente automáticamente y Ejecución pasa a usar
          exclusivamente la nueva. Como cada versión aprobada genera su propia variable de
          medición, las tendencias y comparaciones entre sprints de esa métrica comienzan a
          acumular historial desde cero a partir de la nueva versión — los resultados ya
          calculados con la versión anterior no se pierden, pero dejan de sumarse a las
          tendencias futuras de esa métrica.
        </div>

        <!-- KPIs -->
        <div class="row g-3 mb-4">
          <div class="col-md-4">
            <div class="card text-center kpi-card">
              <div class="card-body py-3">
                <div class="kpi-label">Pendientes</div>
                <div class="kpi-value text-warning">{{ pendientes.length }}</div>
              </div>
            </div>
          </div>
          <div class="col-md-4">
            <div class="card text-center kpi-card">
              <div class="card-body py-3">
                <div class="kpi-label">Aprobadas</div>
                <div class="kpi-value text-success">{{ aprobadas }}</div>
              </div>
            </div>
          </div>
          <div class="col-md-4">
            <div class="card text-center kpi-card">
              <div class="card-body py-3">
                <div class="kpi-label">Rechazadas</div>
                <div class="kpi-value text-danger">{{ rechazadas }}</div>
              </div>
            </div>
          </div>
        </div>

        @if (alertMsg) {
          <div class="alert py-2 small" [class]="alertClass">{{ alertMsg }}</div>
        }

        <!-- Acciones rápidas cuando todo está verificado -->
        @if (pendientes.length === 0 && aprobadas > 0) {
          <div class="alert alert-success d-flex align-items-center justify-content-between flex-wrap gap-2 mb-3">
            <div>
              <i class="bi bi-check-circle-fill me-2"></i>
              <strong>{{ aprobadas }} parametrización(es) aprobada(s).</strong>
              Las variables están listas para registrar valores en Ejecución.
            </div>
            <button class="btn btn-success btn-sm" (click)="irAEjecucion()">
              <i class="bi bi-pencil-square me-1"></i>Ir a Ejecución
            </button>
          </div>
        }
        <div class="card">
          <div class="card-header fw-semibold small d-flex justify-content-between align-items-center">
            <span><i class="bi bi-clipboard-check me-1"></i>Parametrizaciones pendientes de revisión</span>
            <button class="btn btn-sm btn-outline-secondary" (click)="cargar()">
              <i class="bi bi-arrow-clockwise me-1"></i>Actualizar
            </button>
          </div>
          <div class="card-body p-0">
            @if (cargando) {
              <div class="text-center py-4 text-muted small">
                <span class="spinner-border spinner-border-sm me-2"></span>Cargando...
              </div>
            } @else if (pendientes.length === 0) {
              <div class="text-center py-5 text-muted small">
                <i class="bi bi-check-all fs-3 d-block mb-2 text-success opacity-75"></i>
                No hay parametrizaciones pendientes de revisión.
              </div>
            } @else {
              <div class="table-responsive">
                <table class="table table-hover mb-0">
                  <thead class="table-light">
                    <tr>
                      <th>Factor</th>
                      <th>Enviado por</th>
                      <th>Objetivo</th>
                      <th>Escala</th>
                      <th class="text-center">Acciones</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (p of pendientes; track p.id) {
                      <tr>
                        <td>
                          <span class="badge prox-badge-sm mb-1"
                                [class]="categoryBadge(factorCategoriaNombre(p))">
                            {{ factorCategoriaNombre(p) }}
                          </span>
                          <div class="small fw-semibold">{{ p.factorNombre }}</div>
                        </td>
                        <td class="small text-muted align-middle">{{ p.userEmail }}</td>
                        <td class="align-middle">
                          <div class="small">{{ p.objetivo | slice:0:80 }}...</div>
                          <div class="text-muted" style="font-size:0.7rem">
                            {{ p.procedimiento | slice:0:60 }}...
                          </div>
                        </td>
                        <td class="small align-middle">{{ p.escala }}</td>
                        <td class="text-center align-middle">
                          <div class="d-flex gap-1 justify-content-center">
                            <button class="btn btn-sm btn-outline-info py-0 px-2"
                                    (click)="verDetalle(p)" title="Ver detalle">
                              <i class="bi bi-eye"></i>
                            </button>
                            <button class="btn btn-sm btn-outline-secondary py-0 px-2"
                                    (click)="abrirEdicion(p)" title="Editar">
                              <i class="bi bi-pencil"></i>
                            </button>
                            <button class="btn btn-sm btn-success py-0 px-2"
                                    [disabled]="procesando === p.id"
                                    (click)="aprobar(p)" title="Aprobar">
                              <i class="bi bi-check-lg"></i>
                            </button>
                            <button class="btn btn-sm btn-danger py-0 px-2"
                                    [disabled]="procesando === p.id"
                                    (click)="abrirRechazo(p)" title="Rechazar">
                              <i class="bi bi-x-lg"></i>
                            </button>
                          </div>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>
        </div>

        <!-- Modal: ver detalle -->
        @if (detalle) {
          <div class="modal d-block" tabindex="-1" style="background:rgba(0,0,0,.4)">
            <div class="modal-dialog modal-lg modal-dialog-centered">
              <div class="modal-content">
                <div class="modal-header">
                  <h6 class="modal-title">
                    <i class="bi bi-eye me-2"></i>Detalle de parametrización
                  </h6>
                  <button type="button" class="btn-close" (click)="detalle = null"></button>
                </div>
                <div class="modal-body">
                  <div class="mb-2">
                    <span class="badge" [class]="categoryBadge(factorCategoriaNombre(detalle))">
                      {{ factorCategoriaNombre(detalle) }}
                    </span>
                    <strong class="ms-2">{{ detalle.factorNombre }}</strong>
                    <span class="text-muted small ms-2">— por {{ detalle.userEmail }}</span>
                  </div>
                  <dl class="row small mb-0">
                    <dt class="col-sm-3 text-muted">Objetivo</dt>
                    <dd class="col-sm-9">{{ detalle.objetivo }}</dd>
                    <dt class="col-sm-3 text-muted">Procedimiento</dt>
                    <dd class="col-sm-9">{{ detalle.procedimiento }}</dd>
                    <dt class="col-sm-3 text-muted">Indicador / Variables</dt>
                    <dd class="col-sm-9">{{ detalle.indicadorVariable }}</dd>
                    <dt class="col-sm-3 text-muted">Escala</dt>
                    <dd class="col-sm-9 mb-0">{{ detalle.escala }}</dd>
                  </dl>
                </div>
                <div class="modal-footer gap-2">
                  <button class="btn btn-success btn-sm" (click)="aprobar(detalle!); detalle = null">
                    <i class="bi bi-check-lg me-1"></i>Aprobar
                  </button>
                  <button class="btn btn-danger btn-sm" (click)="abrirRechazo(detalle!); detalle = null">
                    <i class="bi bi-x-lg me-1"></i>Rechazar
                  </button>
                  <button class="btn btn-secondary btn-sm" (click)="detalle = null">Cerrar</button>
                </div>
              </div>
            </div>
          </div>
        }

        <!-- Modal: editar parametrización -->
        @if (editando) {
          <div class="modal d-block" tabindex="-1" style="background:rgba(0,0,0,.4)">
            <div class="modal-dialog modal-lg modal-dialog-centered">
              <div class="modal-content">
                <div class="modal-header">
                  <h6 class="modal-title">
                    <i class="bi bi-pencil text-primary me-2"></i>Editar parametrización
                  </h6>
                  <button type="button" class="btn-close" (click)="editando = null"></button>
                </div>
                <div class="modal-body">
                  <div class="mb-3">
                    <span class="badge" [class]="categoryBadge(factorCategoriaNombre(editando))">
                      {{ factorCategoriaNombre(editando) }}
                    </span>
                    <strong class="ms-2">{{ editando.factorNombre }}</strong>
                    <span class="text-muted small ms-2">— por {{ editando.userEmail }}</span>
                  </div>

                  <div class="mb-3">
                    <label class="form-label small fw-semibold">Objetivo</label>
                    <textarea class="form-control form-control-sm" rows="2"
                              [(ngModel)]="editando.objetivo"></textarea>
                  </div>

                  <div class="mb-3">
                    <label class="form-label small fw-semibold">Procedimiento</label>
                    <textarea class="form-control form-control-sm" rows="3"
                              [(ngModel)]="editando.procedimiento"></textarea>
                  </div>

                  <div class="mb-3">
                    <label class="form-label small fw-semibold">Indicador / Variables</label>
                    <input type="text" class="form-control form-control-sm"
                           [(ngModel)]="editando.indicadorVariable">
                  </div>

                  <div class="mb-3">
                    <label class="form-label small fw-semibold">Escala</label>
                    <input type="text" class="form-control form-control-sm"
                           [(ngModel)]="editando.escala">
                  </div>

                  <div class="mb-0">
                    <label class="form-label small fw-semibold">
                      Identificador técnico (opcional)
                    </label>
                    <input type="text" class="form-control form-control-sm" placeholder="ej: pbi_aceptados"
                           maxlength="120" [attr.aria-describedby]="'nombreVariableHelp'"
                           [(ngModel)]="editando.nombreVariable">
                    <div class="d-flex justify-content-between align-items-start mt-1">
                      <div id="nombreVariableHelp" class="form-text small mb-0 me-2">
                        Nombre interno de la variable (independiente del texto descriptivo de
                        Objetivo/Procedimiento/Indicador), snake_case, máximo 120 caracteres.
                        Ejemplo válido: <code>pbi_aceptados</code>. Si lo dejás vacío, o escribís
                        algo que no cumple el formato o supera los 120 caracteres, no pasa nada:
                        el sistema genera uno automáticamente a partir del Indicador al aprobar —
                        nunca se rechaza la parametrización ni se recorta el Indicador por esto.
                      </div>
                      <div class="form-text small text-nowrap"
                           [class.text-danger]="(editando.nombreVariable ?? '').length >= nombreVariableMax">
                        {{ (editando.nombreVariable ?? '').length }}/{{ nombreVariableMax }}
                      </div>
                    </div>
                  </div>
                </div>
                <div class="modal-footer">
                  <button class="btn btn-secondary btn-sm" (click)="editando = null">Cancelar</button>
                  <button class="btn btn-primary btn-sm" (click)="guardarEdicion()">
                    <i class="bi bi-save me-1"></i>Guardar cambios
                  </button>
                </div>
              </div>
            </div>
          </div>
        }

        <!-- Modal: motivo de rechazo -->
        @if (rechazando) {
          <div class="modal d-block" tabindex="-1" style="background:rgba(0,0,0,.4)">
            <div class="modal-dialog modal-dialog-centered">
              <div class="modal-content">
                <div class="modal-header">
                  <h6 class="modal-title">
                    <i class="bi bi-x-circle text-danger me-2"></i>Rechazar parametrización
                  </h6>
                  <button type="button" class="btn-close" (click)="rechazando = null"></button>
                </div>
                <div class="modal-body">
                  <p class="small text-muted mb-2">
                    Factor: <strong>{{ rechazando.factorNombre }}</strong> —
                    enviado por <strong>{{ rechazando.userEmail }}</strong>
                  </p>
                  <label class="form-label small fw-semibold">
                    Motivo del rechazo <span class="text-danger">*</span>
                  </label>
                  <textarea class="form-control form-control-sm" rows="3"
                            placeholder="Explicá por qué se rechaza esta parametrización..."
                            [(ngModel)]="motivoRechazo"></textarea>
                  @if (errorRechazo) {
                    <div class="text-danger small mt-1">{{ errorRechazo }}</div>
                  }
                </div>
                <div class="modal-footer">
                  <button class="btn btn-secondary btn-sm" (click)="rechazando = null">Cancelar</button>
                  <button class="btn btn-danger btn-sm"
                          [disabled]="!motivoRechazo.trim()"
                          (click)="confirmarRechazo()">
                    <i class="bi bi-x-circle me-1"></i>Confirmar rechazo
                  </button>
                </div>
              </div>
            </div>
          </div>
        }

      }
    </app-shell>
  `
})
export class VerificacionComponent implements OnInit {
  pendientes: Pendiente[] = [];
  cargando    = true;
  procesando  = '';
  detalle: Pendiente | null  = null;
  rechazando: Pendiente | null = null;
  editando: Pendiente | null = null;
  motivoRechazo = '';
  errorRechazo  = '';
  alertMsg   = '';
  alertClass = 'alert-success';
  aprobadas  = 0;
  rechazadas = 0;
  readonly nombreVariableMax = NOMBRE_VARIABLE_MAX;

  private readonly apiBase = environment.apiBaseUrl;

  constructor(
    public  auth: AuthService,
    public  router: Router,
    private http: HttpClient,
    private seleccionService: SeleccionService,
    private rankingService: MetricRankingService
  ) {}

  /**
   * Corrección: Scrum Master es siempre relativo al proyecto activo (su
   * scrumMasterEmail, fijado por el backend al crearlo), nunca el rol
   * global de cuenta — mismo patrón ya corregido en dashboard.component.ts
   * (esScrumMasterDelProyecto). La autorización real ya la exige el backend
   * (MetricRankingService.validarScrumMaster) — este getter solo decide qué
   * mostrar en esta pantalla.
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

  irAEjecucion(): void {
    this.seleccionService.limpiar();
    this.router.navigate(['/ejecucion']);
  }

  ngOnInit(): void {
    if (this.esScrumMaster) this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    const proyectoActivo = localStorage.getItem('mpdia_proyecto_activo');
    const proyectoId = proyectoActivo ? JSON.parse(proyectoActivo)?.id ?? null : null;
    const url = proyectoId
      ? `${this.apiBase}/metric-ranking/pendientes?proyectoId=${proyectoId}`
      : `${this.apiBase}/metric-ranking/pendientes`;
    this.http.get<Pendiente[]>(url).pipe(
      catchError(() => of([]))
    ).subscribe(list => {
      this.pendientes = list;
      this.cargando   = false;
    });

    // FASE 10: los contadores de Aprobadas/Rechazadas reflejan el estado real en BD
    // (aislado por proyecto activo), no solo lo acumulado en memoria durante la sesión
    // (ver diagnóstico FASE 9, bloque 4).
    if (proyectoId) {
      this.rankingService.getResumen(proyectoId).pipe(
        catchError(() => of(null))
      ).subscribe(resumen => {
        if (resumen) {
          this.aprobadas  = resumen.aprobadas;
          this.rechazadas = resumen.rechazadas;
        }
      });
    }
  }

  verDetalle(p: Pendiente): void {
    this.detalle = p;
  }

  abrirEdicion(p: Pendiente): void {
    // Clonar para no modificar el original hasta guardar
    this.editando = { ...p, nombreVariable: this.extraerNombreVariableGuardado(p) };
  }

  /**
   * Lee el nombreVariable técnico ya fijado (si lo hay) desde el snapshot
   * configuracionAprobadaJson, para prellenar el campo de edición en vez de
   * dejarlo vacío cada vez que se reabre el modal.
   */
  private extraerNombreVariableGuardado(p: Pendiente): string {
    if (!p.configuracionAprobadaJson) return '';
    try {
      const snapshot = JSON.parse(p.configuracionAprobadaJson);
      return typeof snapshot?.nombreVariable === 'string' ? snapshot.nombreVariable : '';
    } catch {
      return '';
    }
  }

  /**
   * Distingue "la parametrización ya no existe" (eliminada, ej. limpieza de datos
   * QA) de un error de validación real (ej. indicador de más de 120 caracteres).
   * El backend puede responder 404 o, para /verificar y /parametrizacion/{id},
   * 400 con mensaje "Parametrización no encontrada." — ambos se tratan igual acá.
   */
  private esNoEncontrado(err: any): boolean {
    if (err?.status === 404) return true;
    const mensaje = err?.error?.error;
    return err?.status === 400 && typeof mensaje === 'string' && /no encontrad/i.test(mensaje);
  }

  /**
   * Corrección del defecto documentado: al eliminar datos de QA, un pendiente
   * podía quedar visible en pantalla apuntando a un ID inexistente. Ahora, ante
   * "no encontrada", se avisa con un mensaje claro, se quita la fila y se cierra
   * cualquier modal que la referenciara, y se refresca el listado desde el
   * backend para no dejar la interfaz mostrando una entidad que ya no existe.
   */
  private manejarNoEncontrado(p: Pendiente): void {
    this.procesando = '';
    this.pendientes = this.pendientes.filter(x => x.id !== p.id);
    if (this.editando?.id === p.id)  this.editando  = null;
    if (this.detalle?.id === p.id)   this.detalle   = null;
    if (this.rechazando?.id === p.id) this.rechazando = null;
    this.showAlert(
      `La parametrización de "${p.factorNombre}" ya no existe (fue eliminada). Actualizando el listado...`,
      'alert-warning'
    );
    this.cargar();
  }

  guardarEdicion(): void {
    if (!this.editando) return;

    const p = this.editando;
    this.procesando = p.id;

    this.http.put<any>(`${this.apiBase}/metric-ranking/parametrizacion/${p.id}`, {
      objetivo: p.objetivo,
      procedimiento: p.procedimiento,
      indicadorVariable: p.indicadorVariable,
      escala: p.escala,
      nombreVariable: (p.nombreVariable ?? '').trim()
    }).subscribe({
      next: (updated) => {
        this.procesando = '';
        // Actualizar en la lista local
        const idx = this.pendientes.findIndex(x => x.id === p.id);
        if (idx >= 0) {
          this.pendientes[idx] = { ...p, configuracionAprobadaJson: updated?.configuracionAprobadaJson ?? null };
        }
        this.editando = null;
        this.showAlert(`Parametrización de "${p.factorNombre}" actualizada.`, 'alert-success');
      },
      error: (err) => {
        if (this.esNoEncontrado(err)) {
          this.editando = null;
          this.manejarNoEncontrado(p);
          return;
        }
        this.procesando = '';
        const mensaje = err?.error?.error || 'No se pudo guardar la edición. Intentá de nuevo.';
        this.showAlert(mensaje, 'alert-danger');
      }
    });
  }

  aprobar(p: Pendiente): void {
    this.procesando = p.id;
    // FASE 17 (corrección del defecto documentado): antes, cualquier error del
    // backend (ej. indicador demasiado largo) se descartaba con catchError(() =>
    // of(null)) y este mismo subscribe mostraba igual el mensaje de éxito y
    // quitaba la fila de pendientes — el Scrum Master nunca se enteraba de que
    // la aprobación había fallado. Ahora se distingue éxito de error real.
    this.http.post(`${this.apiBase}/metric-ranking/verificar`, {
      parametrizacionId: p.id,
      accion: 'aprobar'
    }).subscribe({
      next: () => {
        this.procesando = '';
        this.aprobadas++;
        this.pendientes = this.pendientes.filter(x => x.id !== p.id);
        this.showAlert(`Parametrización de "${p.factorNombre}" aprobada.`, 'alert-success');
      },
      error: (err) => {
        if (this.esNoEncontrado(err)) {
          this.manejarNoEncontrado(p);
          return;
        }
        this.procesando = '';
        const mensaje = err?.error?.error || 'No se pudo aprobar la parametrización. Intentá de nuevo.';
        this.showAlert(mensaje, 'alert-danger');
      }
    });
  }

  abrirRechazo(p: Pendiente): void {
    this.rechazando   = p;
    this.motivoRechazo = '';
    this.errorRechazo  = '';
  }

  confirmarRechazo(): void {
    if (!this.motivoRechazo.trim()) {
      this.errorRechazo = 'El motivo es obligatorio.';
      return;
    }
    const p = this.rechazando!;
    this.procesando = p.id;
    this.http.post(`${this.apiBase}/metric-ranking/verificar`, {
      parametrizacionId: p.id,
      accion: 'rechazar',
      motivoRechazo: this.motivoRechazo
    }).subscribe({
      next: () => {
        this.procesando  = '';
        this.rechazadas++;
        this.rechazando  = null;
        this.pendientes  = this.pendientes.filter(x => x.id !== p.id);
        this.showAlert(`Parametrización de "${p.factorNombre}" rechazada.`, 'alert-warning');
      },
      error: (err) => {
        if (this.esNoEncontrado(err)) {
          this.rechazando = null;
          this.manejarNoEncontrado(p);
          return;
        }
        // Comportamiento previo preservado para otros errores (fuera del alcance
        // de esta corrección): no bloquea al usuario con un mensaje de error.
        this.procesando  = '';
        this.rechazadas++;
        this.rechazando  = null;
        this.pendientes  = this.pendientes.filter(x => x.id !== p.id);
        this.showAlert(`Parametrización de "${p.factorNombre}" rechazada.`, 'alert-warning');
      }
    });
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

  /** Devuelve la categoría del factor, infiriéndola del nombre si el backend retorna "—" */
  factorCategoriaNombre(p: Pendiente): string {
    if (p.factorCategoria && p.factorCategoria !== '—') return p.factorCategoria;
    const nombre = (p.factorNombre ?? '').toLowerCase();
    if (['defectos','errores','calidad','twq','impedimentos','problemas'].some(k => nombre.includes(k))) return 'Impacto';
    if (['velocidad','capacidad','satisfacción cliente','comprensión','roles'].some(k => nombre.includes(k))) return 'Significado';
    if (['metas','requisitos','cumplimiento','alcance'].some(k => nombre.includes(k))) return 'Impacto';
    if (['proceso','aprendizaje','fracasos','flexibilidad','nmp','fat','gae'].some(k => nombre.includes(k))) return 'Flexibilidad';
    if (['bienestar','ánimo','satisfacción equipo','atmósfera','confianza','compromiso','motivación','comunicación','liderazgo','orgullo','habilidades','poder','configuración'].some(k => nombre.includes(k))) return 'Socio-Humano FSH';
    return p.factorCategoria ?? '—';
  }

  private showAlert(msg: string, cls: string): void {
    this.alertMsg = msg; this.alertClass = cls;
    setTimeout(() => this.alertMsg = '', 4000);
  }
}
