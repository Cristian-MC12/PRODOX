// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ShellComponent } from '../../layout/shell/shell.component';
import { AuthService } from '../../services/auth.service';
import { ProyectoService } from '../../services/proyecto.service';
import { SprintService } from '../../services/sprint.service';
import { ProjectMemberService } from '../../services/project-member.service';
import { ProyectoDto } from '../../models/proyecto.model';
import { timeboxAbreviado } from '../../models/timebox.model';

@Component({
  selector: 'app-proyectos',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, ShellComponent],
  template: `
    <app-shell title="Proyectos" [showBanner]="false">

      @if (alertMsg) {
        <div class="alert py-2 small" [class]="alertClass">{{ alertMsg }}</div>
      }

      <!-- Acciones superiores -->
      <div class="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-2">
        <p class="text-muted small mb-0">
          {{ esScrumMaster ? 'Gestioná tus proyectos y seleccioná uno para trabajar.' : 'Seleccioná un proyecto para comenzar a trabajar.' }}
        </p>
        <div class="d-flex gap-2">
          <!-- Unirse con código (Scrum Member) -->
          @if (!esScrumMaster) {
            <button class="btn btn-outline-success btn-sm" (click)="mostrarUnirse = !mostrarUnirse">
              <i class="bi bi-key me-1"></i>Unirse con código
            </button>
          }
          <!-- Crear proyecto (Scrum Master) -->
          @if (esScrumMaster) {
            <button class="btn btn-primary btn-sm" (click)="mostrarFormulario = !mostrarFormulario">
              <i class="bi bi-plus-lg me-1"></i>Nuevo proyecto
            </button>
          }
        </div>
      </div>

      <!-- Panel unirse con código -->
      @if (mostrarUnirse && !esScrumMaster) {
        <div class="card mb-4 border-success">
          <div class="card-header fw-semibold small bg-success text-white">
            <i class="bi bi-key me-1"></i>Unirse a un proyecto
          </div>
          <div class="card-body">
            <p class="text-muted small">Ingresá el código que te compartió el Scrum Master.</p>
            <div class="input-group input-group-sm" style="max-width:320px">
              <input type="text" class="form-control"
                     placeholder="Ej: PRJ-ABC123"
                     [(ngModel)]="codigoUnirse"
                     style="text-transform:uppercase;letter-spacing:2px">
              <button class="btn btn-success"
                      [disabled]="!codigoUnirse.trim() || uniendose"
                      (click)="unirseConCodigo()">
                @if (uniendose) {
                  <span class="spinner-border spinner-border-sm"></span>
                } @else {
                  Unirse
                }
              </button>
            </div>
          </div>
        </div>
      }

      <!-- Formulario crear proyecto (SM) -->
      @if (mostrarFormulario && esScrumMaster) {
        <div class="card mb-4 border-primary">
          <div class="card-header fw-semibold small bg-primary text-white">
            <i class="bi bi-folder-plus me-1"></i>Nuevo proyecto
          </div>
          <div class="card-body">
            <form [formGroup]="form" (ngSubmit)="crearProyecto()">
              <div class="row g-3">
                <div class="col-md-8">
                  <label class="form-label small fw-semibold">Nombre <span class="text-danger">*</span></label>
                  <input type="text" class="form-control form-control-sm"
                         placeholder="Ej: Sistema de gestión de inventarios"
                         formControlName="nombre"
                         [class.is-invalid]="f['nombre'].invalid && f['nombre'].touched">
                  <div class="invalid-feedback">Requerido.</div>
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold">Método ágil <span class="text-danger">*</span></label>
                  <select class="form-select form-select-sm" formControlName="metodo"
                          [class.is-invalid]="f['metodo'].invalid && f['metodo'].touched">
                    <option value="">Seleccionar...</option>
                    <option value="scrum">Scrum</option>
                    <option value="xp">XP (Extreme Programming)</option>
                  </select>
                  <div class="invalid-feedback">Requerido.</div>
                </div>
                <div class="col-12">
                  <label class="form-label small fw-semibold">Descripción</label>
                  <input type="text" class="form-control form-control-sm"
                         placeholder="Breve descripción..." formControlName="descripcion">
                </div>
                <div class="col-12">
                  <label class="form-label small fw-semibold">Timebox de la iteración <span class="text-danger">*</span></label>
                  <div class="row g-2">
                    <div class="col-md-3">
                      <input type="number" class="form-control form-control-sm" min="1"
                             placeholder="Duración" formControlName="timeboxDuracion"
                             [class.is-invalid]="f['timeboxDuracion'].invalid && f['timeboxDuracion'].touched">
                    </div>
                    <div class="col-md-4">
                      <select class="form-select form-select-sm" formControlName="timeboxUnidad">
                        <option value="HORAS">Horas</option>
                        <option value="DIAS">Días</option>
                        <option value="SEMANAS">Semanas</option>
                      </select>
                    </div>
                    @if (f['timeboxUnidad'].value === 'HORAS') {
                      <div class="col-md-5">
                        <input type="time" class="form-control form-control-sm"
                               formControlName="horaInicio"
                               [class.is-invalid]="horaInicioInvalida()">
                        <div class="form-text">Hora de inicio del primer sprint</div>
                      </div>
                    }
                  </div>
                  @if (f['timeboxDuracion'].invalid && f['timeboxDuracion'].touched) {
                    <div class="text-danger small mt-1">Duración requerida, mayor a 0.</div>
                  }
                  @if (horaInicioInvalida()) {
                    <div class="text-danger small mt-1">Indicá la hora de inicio para un timebox en horas.</div>
                  }
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold">Número de Sprints <span class="text-danger">*</span></label>
                  <input type="number" class="form-control form-control-sm" min="1" max="20"
                         placeholder="Ej: 4" formControlName="numeroSprints"
                         [class.is-invalid]="f['numeroSprints'].invalid && f['numeroSprints'].touched">
                  <div class="invalid-feedback">Requerido (1-20).</div>
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold">Fecha de Inicio <span class="text-danger">*</span></label>
                  <input type="date" class="form-control form-control-sm"
                         formControlName="fechaInicio"
                         [class.is-invalid]="f['fechaInicio'].invalid && f['fechaInicio'].touched">
                  <div class="invalid-feedback">Requerido.</div>
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold">Product Goal <span class="text-danger">*</span></label>
                  <textarea class="form-control form-control-sm" rows="2"
                            placeholder="¿Qué se quiere lograr con el producto a largo plazo?"
                            formControlName="productGoal"
                            [class.is-invalid]="f['productGoal'].invalid && f['productGoal'].touched"></textarea>
                  <div class="invalid-feedback">Requerido.</div>
                </div>
              </div>
              <div class="d-flex gap-2 mt-3">
                <button type="submit" class="btn btn-primary btn-sm" [disabled]="form.invalid || creando">
                  @if (creando) { <span class="spinner-border spinner-border-sm me-1"></span> }
                  @else { <i class="bi bi-floppy me-1"></i> }
                  Crear proyecto
                </button>
                <button type="button" class="btn btn-outline-secondary btn-sm"
                        (click)="mostrarFormulario = false">Cancelar</button>
              </div>
            </form>
          </div>
        </div>
      }

      <!-- Lista de proyectos -->
      @if (cargando) {
        <div class="text-center py-5 text-muted">
          <span class="spinner-border me-2"></span>Cargando proyectos...
        </div>
      } @else if (proyectos.length === 0) {
        <div class="prox-empty-state">
          <i class="bi bi-folder-x"></i>
          @if (esScrumMaster) {
            <p>No tenés proyectos aún. Creá uno para comenzar.</p>
            <button class="btn btn-primary btn-sm" (click)="mostrarFormulario = true">
              <i class="bi bi-plus-lg me-1"></i>Crear primer proyecto
            </button>
          } @else {
            <p>No estás en ningún proyecto todavía.</p>
            <p class="small text-muted">Tu Scrum Master debe invitarte con un código de proyecto.</p>
            <button class="btn btn-outline-success btn-sm" (click)="mostrarUnirse = true">
              <i class="bi bi-key me-1"></i>Ingresar código de invitación
            </button>
          }
        </div>
      } @else {
        <div class="row g-3">
          @for (p of proyectos; track p.id) {
            <div class="col-md-6 col-lg-4">
              <div class="card h-100"
                   [class.border-primary]="p.estado === 'activo'"
                   [class.border-secondary]="p.estado === 'finalizado'">
                <div class="card-header d-flex justify-content-between align-items-center py-2">
                  <span class="fw-semibold small text-truncate me-2">{{ p.nombre }}</span>
                  <div class="d-flex gap-1 flex-shrink-0">
                    <span class="badge prox-badge-sm"
                          [class]="p.metodo === 'scrum' ? 'bg-primary' : 'bg-info text-dark'">
                      {{ p.metodo === 'scrum' ? 'Scrum' : 'XP' }}
                    </span>
                    <span class="badge prox-badge-sm"
                          [class]="p.estado === 'activo' ? 'bg-success' : 'bg-secondary'">
                      {{ p.estado }}
                    </span>
                  </div>
                </div>
                <div class="card-body py-2">
                  <div class="small mb-1 text-muted">
                    <i class="bi bi-clock me-1"></i>Time Box: <strong>{{ timeboxAbreviado(p) }}</strong>
                    <span class="ms-2"><i class="bi bi-people me-1"></i>{{ p.totalMiembros }} miembro(s)</span>
                  </div>
                  <div class="small mb-1">
                    <div class="text-muted fw-semibold">Product Goal:</div>
                    <div>{{ p.productGoal | slice:0:100 }}{{ p.productGoal.length > 100 ? '...' : '' }}</div>
                  </div>
                </div>
                <div class="card-footer py-2 d-flex justify-content-between align-items-center gap-2">
                  <small class="text-muted text-truncate">{{ p.scrumMasterEmail }}</small>
                  <div class="d-flex gap-1 flex-shrink-0">
                    @if (esScrumMaster && p.scrumMasterEmail === auth.currentUser()?.email) {
                      <button class="btn btn-outline-danger btn-sm py-0 px-2" title="Eliminar proyecto"
                              (click)="pedirEliminar(p)">
                        <i class="bi bi-trash"></i>
                      </button>
                    }
                    @if (p.estado === 'activo') {
                      <button class="btn btn-primary btn-sm py-0 px-2" (click)="seleccionarProyecto(p)">
                        <i class="bi bi-arrow-right me-1"></i>Trabajar
                      </button>
                    }
                  </div>
                </div>
              </div>
            </div>
          }
        </div>
      }

      <!-- Modal confirmación eliminar proyecto (FASE 21) -->
      @if (proyectoAEliminar) {
        <div class="modal d-block" style="background-color: rgba(0,0,0,0.5)" (click)="cancelarEliminar()">
          <div class="modal-dialog" (click)="$event.stopPropagation()">
            <div class="modal-content">
              <div class="modal-header">
                <h5 class="modal-title text-danger">
                  <i class="bi bi-exclamation-triangle-fill me-2"></i>Eliminar proyecto
                </h5>
                <button type="button" class="btn-close" [disabled]="eliminando" (click)="cancelarEliminar()"></button>
              </div>
              <div class="modal-body">
                <p>¿Seguro que querés eliminar el proyecto <strong>{{ proyectoAEliminar.nombre }}</strong>?</p>
                <p class="text-danger small mb-0">
                  <i class="bi bi-exclamation-circle me-1"></i>
                  Esta acción es irreversible: se eliminarán también sus sprints, miembros e información asociada.
                </p>
              </div>
              <div class="modal-footer">
                <button class="btn btn-outline-secondary btn-sm" [disabled]="eliminando" (click)="cancelarEliminar()">
                  Cancelar
                </button>
                <button class="btn btn-danger btn-sm" [disabled]="eliminando" (click)="confirmarEliminar()">
                  @if (eliminando) { <span class="spinner-border spinner-border-sm me-1"></span> }
                  @else { <i class="bi bi-trash me-1"></i> }
                  Sí, eliminar proyecto
                </button>
              </div>
            </div>
          </div>
        </div>
      }

    </app-shell>
  `
})
export class ProyectosComponent implements OnInit {
  proyectos: ProyectoDto[] = [];
  form!: FormGroup;
  cargando         = true;
  creando          = false;
  uniendose        = false;
  mostrarFormulario = false;
  mostrarUnirse    = false;
  codigoUnirse     = '';
  alertMsg   = '';
  alertClass = 'alert-success';

  /** Proyecto pendiente de confirmación de borrado (FASE 21) */
  proyectoAEliminar: ProyectoDto | null = null;
  eliminando = false;

  /** V41 — controla cuándo mostrar el error de "falta hora de inicio"
   *  (timeboxUnidad=HORAS): el campo solo existe en el DOM condicionalmente,
   *  así que "touched" por sí solo no alcanza para decidir cuándo mostrarlo. */
  intentoEnviar = false;

  /** Expuesto al template para no repetir la lógica de unidades en cada vista. */
  readonly timeboxAbreviado = timeboxAbreviado;

  constructor(
    public  auth: AuthService,
    private proyectoService: ProyectoService,
    private sprintService: SprintService,
    private memberService: ProjectMemberService,
    private fb: FormBuilder,
    private router: Router
  ) {}

  get esScrumMaster(): boolean {
    return this.auth.currentUser()?.role === 'scrum_master';
  }

  get f() { return this.form.controls; }

  ngOnInit(): void {
    this.form = this.fb.group({
      nombre:          ['', Validators.required],
      descripcion:     [''],
      metodo:          ['', Validators.required],
      timeboxUnidad:   ['SEMANAS', Validators.required],
      timeboxDuracion: [2, [Validators.required, Validators.min(1)]],
      horaInicio:      [''],
      numeroSprints:   [3, [Validators.required, Validators.min(1), Validators.max(20)]],
      fechaInicio:     ['', Validators.required],
      productGoal:     ['', Validators.required]
    });
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.proyectoService.getMisProyectos().pipe(
      catchError(() => of([]))
    ).subscribe(p => {
      this.proyectos = p;
      this.cargando  = false;
    });
  }

  /** V41 — solo exige hora de inicio cuando el timebox está en horas; el
   *  campo ni siquiera existe en el DOM para días/semanas. */
  horaInicioInvalida(): boolean {
    return this.intentoEnviar
        && this.form.value.timeboxUnidad === 'HORAS'
        && !this.form.value.horaInicio;
  }

  crearProyecto(): void {
    this.intentoEnviar = true;
    if (this.form.invalid || this.horaInicioInvalida()) {
      this.form.markAllAsTouched();
      return;
    }
    this.creando = true;
    const val = this.form.value;
    this.proyectoService.crear({
      nombre:          val.nombre,
      descripcion:     val.descripcion ?? '',
      metodo:          val.metodo,
      timeboxUnidad:   val.timeboxUnidad,
      timeboxDuracion: Number(val.timeboxDuracion),
      horaInicio:      val.timeboxUnidad === 'HORAS' ? val.horaInicio : null,
      numeroSprints:   Number(val.numeroSprints),
      fechaInicio:     val.fechaInicio,
      productGoal:     val.productGoal
    }).pipe(
      catchError(err => {
        this.showAlert(err?.error?.error ?? 'Error al crear el proyecto.', 'alert-danger');
        this.creando = false;
        return of(null);
      })
    ).subscribe(p => {
      if (p) {
        this.proyectos = [p, ...this.proyectos];
        this.mostrarFormulario = false;
        this.intentoEnviar = false;
        this.form.reset({ timeboxUnidad: 'SEMANAS', timeboxDuracion: 2, numeroSprints: 3 });
        this.showAlert('Proyecto creado exitosamente.', 'alert-success');
      }
      this.creando = false;
    });
  }

  unirseConCodigo(): void {
    if (!this.codigoUnirse.trim()) return;
    this.uniendose = true;
    this.memberService.unirse(this.codigoUnirse.toUpperCase()).pipe(
      catchError(err => {
        this.showAlert(err?.error?.error ?? 'Código inválido.', 'alert-danger');
        this.uniendose = false;
        return of(null);
      })
    ).subscribe(m => {
      if (m) {
        this.codigoUnirse = '';
        this.mostrarUnirse = false;
        this.showAlert('Te uniste al proyecto exitosamente.', 'alert-success');
        this.cargar();
      }
      this.uniendose = false;
    });
  }

  seleccionarProyecto(p: ProyectoDto): void {
    if (p.estado !== 'activo') return;
    localStorage.setItem('mpdia_proyecto_activo', JSON.stringify(p));
    this.sprintService.getActivo(p.id).pipe(
      catchError(() => of(null))
    ).subscribe(sprint => {
      if (sprint) localStorage.setItem('mpdia_sprint_activo', JSON.stringify(sprint));
      this.router.navigate(['/planeacion']);
    });
  }

  /** Abre el modal de confirmación; no elimina nada todavía. */
  pedirEliminar(p: ProyectoDto): void {
    this.proyectoAEliminar = p;
  }

  cancelarEliminar(): void {
    if (this.eliminando) return;
    this.proyectoAEliminar = null;
  }

  confirmarEliminar(): void {
    if (!this.proyectoAEliminar || this.eliminando) return;
    const p = this.proyectoAEliminar;
    this.eliminando = true;
    this.proyectoService.eliminar(p.id).subscribe({
      next: () => {
        this.proyectos = this.proyectos.filter(x => x.id !== p.id);
        this.limpiarSeleccionSiCorresponde(p.id);
        this.eliminando = false;
        this.proyectoAEliminar = null;
        this.showAlert('Proyecto eliminado exitosamente.', 'alert-success');
      },
      error: (err) => {
        this.eliminando = false;
        this.proyectoAEliminar = null;
        this.showAlert(err?.error?.error ?? 'Error al eliminar el proyecto.', 'alert-danger');
        // Si el DELETE falló (ej. el proyecto ya no existe porque se eliminó
        // desde otra sesión/pestaña mientras este modal estaba abierto), tanto
        // la lista local como el proyecto activo en localStorage podrían haber
        // quedado apuntando a un proyecto que ya no existe en el backend.
        // Recargar la lista y limpiar la selección activa evita dejar en
        // pantalla una tarjeta o una sesión apuntando a ese proyecto inexistente.
        this.cargar();
        this.limpiarSeleccionSiCorresponde(p.id);
      }
    });
  }

  /** Si el proyecto eliminado era el proyecto/sprint activo, limpia la selección. */
  private limpiarSeleccionSiCorresponde(proyectoId: string): void {
    const raw = localStorage.getItem('mpdia_proyecto_activo');
    if (!raw) return;
    try {
      const activo = JSON.parse(raw);
      if (activo?.id === proyectoId) {
        localStorage.removeItem('mpdia_proyecto_activo');
        localStorage.removeItem('mpdia_sprint_activo');
      }
    } catch {
      localStorage.removeItem('mpdia_proyecto_activo');
    }
  }

  private showAlert(msg: string, cls: string): void {
    this.alertMsg = msg; this.alertClass = cls;
    setTimeout(() => this.alertMsg = '', 5000);
  }
}
