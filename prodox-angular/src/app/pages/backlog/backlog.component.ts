// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { catchError, of } from 'rxjs';
import { ShellComponent } from '../../layout/shell/shell.component';
import { HistoriaUsuarioService } from '../../services/historia-usuario.service';
import { SprintService } from '../../services/sprint.service';
import { ProyectoDto } from '../../models/proyecto.model';
import { SprintDto } from '../../models/sprint.model';
import { EstadoHistoria, HistoriaUsuarioDto, PrioridadHistoria } from '../../models/historia-usuario.model';
import { ROL_PRODUCT_OWNER } from '../../models/project-role.model';

type FiltroEstado = 'todas' | EstadoHistoria;

interface FormularioHistoria {
  titulo: string;
  descripcion: string;
  criteriosAceptacion: string;
  prioridad: PrioridadHistoria;
}

const FORM_VACIO: FormularioHistoria = { titulo: '', descripcion: '', criteriosAceptacion: '', prioridad: 'media' };

@Component({
  selector: 'app-backlog',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell title="Backlog del producto">

      @if (alertMsg) {
        <div class="alert py-2 small" [class]="alertClass">{{ alertMsg }}</div>
      }

      @if (!proyecto) {
        <div class="prox-empty-state">
          <p>No hay proyecto activo. Seleccioná un proyecto para ver su backlog.</p>
        </div>
      } @else {

        <!-- Encabezado + acción crear (solo Product Owner) -->
        <div class="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
          <div>
            <p class="text-muted small mb-0">
              Historias de usuario de <strong>{{ proyecto.nombre }}</strong>.
              @if (!esProductOwner) {
                Vista de solo lectura — la gestión del backlog es responsabilidad del Product Owner.
              }
            </p>
          </div>
          @if (esProductOwner) {
            <button class="btn btn-primary btn-sm" (click)="mostrarFormulario = !mostrarFormulario">
              <i class="bi bi-plus-lg me-1"></i>Nueva historia
            </button>
          }
        </div>

        <!-- Formulario crear historia (solo PO) -->
        @if (esProductOwner && mostrarFormulario) {
          <div class="card mb-4 border-primary">
            <div class="card-header fw-semibold small bg-primary text-white">
              <i class="bi bi-file-earmark-plus me-1"></i>Nueva historia de usuario
            </div>
            <div class="card-body">
              <div class="row g-3">
                <div class="col-md-8">
                  <label class="form-label small fw-semibold">Título <span class="text-danger">*</span></label>
                  <input type="text" class="form-control form-control-sm"
                         placeholder="Como [rol], quiero [funcionalidad] para [beneficio]"
                         [(ngModel)]="formCrear.titulo">
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold">Prioridad</label>
                  <select class="form-select form-select-sm" [(ngModel)]="formCrear.prioridad">
                    <option value="alta">Alta</option>
                    <option value="media">Media</option>
                    <option value="baja">Baja</option>
                  </select>
                </div>
                <div class="col-12">
                  <label class="form-label small fw-semibold">Descripción</label>
                  <textarea class="form-control form-control-sm" rows="2"
                            [(ngModel)]="formCrear.descripcion"></textarea>
                </div>
                <div class="col-12">
                  <label class="form-label small fw-semibold">Criterios de aceptación</label>
                  <textarea class="form-control form-control-sm" rows="2"
                            placeholder="Uno por línea"
                            [(ngModel)]="formCrear.criteriosAceptacion"></textarea>
                </div>
              </div>
              <div class="d-flex gap-2 mt-3">
                <button class="btn btn-primary btn-sm" [disabled]="!formCrear.titulo.trim() || guardando" (click)="crear()">
                  @if (guardando) { <span class="spinner-border spinner-border-sm me-1"></span> }
                  @else { <i class="bi bi-floppy me-1"></i> }
                  Crear historia
                </button>
                <button class="btn btn-outline-secondary btn-sm" (click)="cancelarCrear()">Cancelar</button>
              </div>
            </div>
          </div>
        }

        <!-- Filtros -->
        <div class="d-flex gap-2 mb-3 flex-wrap">
          @for (f of filtros; track f.valor) {
            <button class="btn btn-sm"
                    [class.btn-primary]="filtroEstado === f.valor"
                    [class.btn-outline-secondary]="filtroEstado !== f.valor"
                    (click)="filtroEstado = f.valor">
              {{ f.label }} ({{ contarPorEstado(f.valor) }})
            </button>
          }
        </div>

        @if (cargando) {
          <div class="text-center py-4 text-muted small">
            <span class="spinner-border spinner-border-sm me-2"></span>Cargando backlog...
          </div>
        } @else if (historiasFiltradas().length === 0) {
          <div class="prox-empty-state">
            <i class="bi bi-card-list"></i>
            <p>No hay historias {{ filtroEstado === 'todas' ? 'en el backlog' : 'en este estado' }}.</p>
          </div>
        } @else {
          <div class="row g-3">
            @for (h of historiasFiltradas(); track h.id) {
              <div class="col-12">
                <div class="card" [class.border-danger]="h.prioridad === 'alta'">
                  <div class="card-body">
                    <div class="d-flex justify-content-between align-items-start flex-wrap gap-2">
                      <div class="flex-grow-1">
                        <div class="d-flex align-items-center gap-2 flex-wrap mb-1">
                          <span class="badge" [class]="badgePrioridad(h.prioridad)">{{ h.prioridad | uppercase }}</span>
                          <span class="badge" [class]="badgeEstado(h.estado)">{{ etiquetaEstado(h.estado) }}</span>
                          @if (h.sprintId) {
                            <span class="badge bg-light text-dark border">
                              <i class="bi bi-calendar3-range me-1"></i>{{ nombreSprint(h.sprintId) }}
                            </span>
                          } @else {
                            <span class="badge bg-light text-dark border">Sin sprint</span>
                          }
                        </div>
                        <h6 class="fw-semibold mb-1">{{ h.titulo }}</h6>
                        @if (h.descripcion) {
                          <p class="small text-muted mb-1">{{ h.descripcion }}</p>
                        }
                        @if (h.criteriosAceptacion) {
                          <div class="small">
                            <strong class="text-muted">Criterios de aceptación:</strong>
                            <div class="text-muted" style="white-space: pre-line">{{ h.criteriosAceptacion }}</div>
                          </div>
                        }
                      </div>

                      @if (esProductOwner) {
                        <div class="d-flex flex-column gap-1" style="min-width:180px">
                          <select class="form-select form-select-sm" [ngModel]="h.prioridad"
                                  (ngModelChange)="cambiarPrioridad(h, $event)">
                            <option value="alta">Prioridad: Alta</option>
                            <option value="media">Prioridad: Media</option>
                            <option value="baja">Prioridad: Baja</option>
                          </select>
                          <select class="form-select form-select-sm" [ngModel]="h.estado"
                                  (ngModelChange)="cambiarEstado(h, $event)">
                            <option value="pendiente">Estado: Pendiente</option>
                            <option value="en_progreso">Estado: En progreso</option>
                            <option value="completada">Estado: Completada</option>
                          </select>
                          <select class="form-select form-select-sm" [ngModel]="h.sprintId ?? ''"
                                  (ngModelChange)="asignarSprint(h, $event)">
                            <option value="">Sin sprint (backlog)</option>
                            @for (s of sprints; track s.id) {
                              <option [value]="s.id">Sprint {{ s.numero }}</option>
                            }
                          </select>
                          <div class="d-flex gap-1">
                            <button class="btn btn-outline-secondary btn-sm flex-grow-1" (click)="editar(h)">
                              <i class="bi bi-pencil"></i>
                            </button>
                            <button class="btn btn-outline-danger btn-sm" (click)="pedirEliminar(h)">
                              <i class="bi bi-trash"></i>
                            </button>
                          </div>
                        </div>
                      }
                    </div>
                  </div>
                </div>
              </div>
            }
          </div>
        }
      }

      <!-- Modal edición (solo PO) -->
      @if (historiaEditando) {
        <div class="modal d-block" style="background-color: rgba(0,0,0,0.5)" (click)="cancelarEdicion()">
          <div class="modal-dialog" (click)="$event.stopPropagation()">
            <div class="modal-content">
              <div class="modal-header">
                <h5 class="modal-title">Editar historia</h5>
                <button type="button" class="btn-close" (click)="cancelarEdicion()"></button>
              </div>
              <div class="modal-body">
                <div class="mb-2">
                  <label class="form-label small fw-semibold">Título</label>
                  <input type="text" class="form-control form-control-sm" [(ngModel)]="formEditar.titulo">
                </div>
                <div class="mb-2">
                  <label class="form-label small fw-semibold">Descripción</label>
                  <textarea class="form-control form-control-sm" rows="2" [(ngModel)]="formEditar.descripcion"></textarea>
                </div>
                <div class="mb-2">
                  <label class="form-label small fw-semibold">Criterios de aceptación</label>
                  <textarea class="form-control form-control-sm" rows="2" [(ngModel)]="formEditar.criteriosAceptacion"></textarea>
                </div>
              </div>
              <div class="modal-footer">
                <button class="btn btn-outline-secondary btn-sm" (click)="cancelarEdicion()">Cancelar</button>
                <button class="btn btn-primary btn-sm" [disabled]="!formEditar.titulo.trim() || guardando" (click)="guardarEdicion()">
                  Guardar
                </button>
              </div>
            </div>
          </div>
        </div>
      }

      <!-- Modal confirmación eliminar (solo PO) -->
      @if (historiaAEliminar) {
        <div class="modal d-block" style="background-color: rgba(0,0,0,0.5)" (click)="cancelarEliminar()">
          <div class="modal-dialog" (click)="$event.stopPropagation()">
            <div class="modal-content">
              <div class="modal-header">
                <h5 class="modal-title text-danger">Eliminar historia</h5>
                <button type="button" class="btn-close" (click)="cancelarEliminar()"></button>
              </div>
              <div class="modal-body">
                <p>¿Seguro que querés eliminar <strong>{{ historiaAEliminar.titulo }}</strong>?</p>
              </div>
              <div class="modal-footer">
                <button class="btn btn-outline-secondary btn-sm" (click)="cancelarEliminar()">Cancelar</button>
                <button class="btn btn-danger btn-sm" (click)="confirmarEliminar()">Sí, eliminar</button>
              </div>
            </div>
          </div>
        </div>
      }

    </app-shell>
  `
})
export class BacklogComponent implements OnInit {
  proyecto: ProyectoDto | null = null;
  historias: HistoriaUsuarioDto[] = [];
  sprints: SprintDto[] = [];

  cargando = true;
  guardando = false;
  mostrarFormulario = false;
  formCrear: FormularioHistoria = { ...FORM_VACIO };

  historiaEditando: HistoriaUsuarioDto | null = null;
  formEditar: FormularioHistoria = { ...FORM_VACIO };

  historiaAEliminar: HistoriaUsuarioDto | null = null;

  filtroEstado: FiltroEstado = 'todas';
  readonly filtros: { valor: FiltroEstado; label: string }[] = [
    { valor: 'todas', label: 'Todas' },
    { valor: 'pendiente', label: 'Pendientes' },
    { valor: 'en_progreso', label: 'En progreso' },
    { valor: 'completada', label: 'Completadas' },
  ];

  alertMsg = '';
  alertClass = 'alert-success';

  constructor(
    private historiaService: HistoriaUsuarioService,
    private sprintService: SprintService
  ) {}

  /** V39: el backlog se administra si el rol POR PROYECTO es product_owner
   *  (ProyectoDto.miRol, nunca el rol global de cuenta). */
  get esProductOwner(): boolean {
    return this.proyecto?.miRol === ROL_PRODUCT_OWNER;
  }

  ngOnInit(): void {
    try {
      const p = localStorage.getItem('mpdia_proyecto_activo');
      this.proyecto = p ? JSON.parse(p) : null;
    } catch { /* ignore */ }

    if (!this.proyecto) { this.cargando = false; return; }
    this.cargarHistorias();
    this.sprintService.listar(this.proyecto.id).pipe(
      catchError(() => of([]))
    ).subscribe(s => this.sprints = s);
  }

  cargarHistorias(): void {
    this.cargando = true;
    this.historiaService.listar(this.proyecto!.id).pipe(
      catchError(() => of([]))
    ).subscribe(h => {
      this.historias = h;
      this.cargando = false;
    });
  }

  historiasFiltradas(): HistoriaUsuarioDto[] {
    if (this.filtroEstado === 'todas') return this.historias;
    return this.historias.filter(h => h.estado === this.filtroEstado);
  }

  contarPorEstado(valor: FiltroEstado): number {
    if (valor === 'todas') return this.historias.length;
    return this.historias.filter(h => h.estado === valor).length;
  }

  nombreSprint(sprintId: string): string {
    const s = this.sprints.find(x => x.id === sprintId);
    return s ? `Sprint ${s.numero}` : 'Sprint';
  }

  badgePrioridad(p: PrioridadHistoria): string {
    return p === 'alta' ? 'bg-danger' : p === 'media' ? 'bg-warning text-dark' : 'bg-secondary';
  }

  badgeEstado(e: EstadoHistoria): string {
    return e === 'completada' ? 'bg-success' : e === 'en_progreso' ? 'bg-primary' : 'bg-light text-dark border';
  }

  etiquetaEstado(e: EstadoHistoria): string {
    return e === 'pendiente' ? 'Pendiente' : e === 'en_progreso' ? 'En progreso' : 'Completada';
  }

  crear(): void {
    if (!this.proyecto || !this.formCrear.titulo.trim()) return;
    this.guardando = true;
    this.historiaService.crear(this.proyecto.id, { ...this.formCrear }).pipe(
      catchError(err => {
        this.showAlert(err?.error?.error ?? 'No se pudo crear la historia.', 'alert-danger');
        this.guardando = false;
        return of(null);
      })
    ).subscribe(h => {
      if (h) {
        this.historias = [...this.historias, h];
        this.cancelarCrear();
        this.showAlert('Historia creada.', 'alert-success');
      }
      this.guardando = false;
    });
  }

  cancelarCrear(): void {
    this.mostrarFormulario = false;
    this.formCrear = { ...FORM_VACIO };
  }

  editar(h: HistoriaUsuarioDto): void {
    this.historiaEditando = h;
    this.formEditar = {
      titulo: h.titulo,
      descripcion: h.descripcion ?? '',
      criteriosAceptacion: h.criteriosAceptacion ?? '',
      prioridad: h.prioridad
    };
  }

  cancelarEdicion(): void {
    this.historiaEditando = null;
  }

  guardarEdicion(): void {
    if (!this.historiaEditando || !this.formEditar.titulo.trim()) return;
    this.guardando = true;
    const id = this.historiaEditando.id;
    this.historiaService.actualizar(id, {
      titulo: this.formEditar.titulo,
      descripcion: this.formEditar.descripcion,
      criteriosAceptacion: this.formEditar.criteriosAceptacion
    }).pipe(
      catchError(err => {
        this.showAlert(err?.error?.error ?? 'No se pudo editar la historia.', 'alert-danger');
        this.guardando = false;
        return of(null);
      })
    ).subscribe(actualizada => {
      if (actualizada) {
        this.historias = this.historias.map(h => h.id === id ? actualizada : h);
        this.historiaEditando = null;
        this.showAlert('Historia actualizada.', 'alert-success');
      }
      this.guardando = false;
    });
  }

  cambiarPrioridad(h: HistoriaUsuarioDto, prioridad: PrioridadHistoria): void {
    if (prioridad === h.prioridad) return;
    this.historiaService.cambiarPrioridad(h.id, prioridad).pipe(
      catchError(err => {
        this.showAlert(err?.error?.error ?? 'No se pudo cambiar la prioridad.', 'alert-danger');
        return of(null);
      })
    ).subscribe(actualizada => {
      if (actualizada) this.historias = this.historias.map(x => x.id === h.id ? actualizada : x);
    });
  }

  cambiarEstado(h: HistoriaUsuarioDto, estado: EstadoHistoria): void {
    if (estado === h.estado) return;
    this.historiaService.cambiarEstado(h.id, estado).pipe(
      catchError(err => {
        this.showAlert(err?.error?.error ?? 'No se pudo cambiar el estado.', 'alert-danger');
        return of(null);
      })
    ).subscribe(actualizada => {
      if (actualizada) this.historias = this.historias.map(x => x.id === h.id ? actualizada : x);
    });
  }

  asignarSprint(h: HistoriaUsuarioDto, sprintId: string): void {
    const nuevoId = sprintId || null;
    if (nuevoId === h.sprintId) return;
    this.historiaService.asignarSprint(h.id, nuevoId).pipe(
      catchError(err => {
        this.showAlert(err?.error?.error ?? 'No se pudo asignar el sprint.', 'alert-danger');
        return of(null);
      })
    ).subscribe(actualizada => {
      if (actualizada) this.historias = this.historias.map(x => x.id === h.id ? actualizada : x);
    });
  }

  pedirEliminar(h: HistoriaUsuarioDto): void {
    this.historiaAEliminar = h;
  }

  cancelarEliminar(): void {
    this.historiaAEliminar = null;
  }

  confirmarEliminar(): void {
    if (!this.historiaAEliminar) return;
    const id = this.historiaAEliminar.id;
    this.historiaService.eliminar(id).subscribe({
      next: () => {
        this.historias = this.historias.filter(h => h.id !== id);
        this.historiaAEliminar = null;
        this.showAlert('Historia eliminada.', 'alert-success');
      },
      error: (err) => {
        this.showAlert(err?.error?.error ?? 'No se pudo eliminar la historia.', 'alert-danger');
        this.historiaAEliminar = null;
      }
    });
  }

  private showAlert(msg: string, cls: string): void {
    this.alertMsg = msg; this.alertClass = cls;
    setTimeout(() => this.alertMsg = '', 5000);
  }
}
