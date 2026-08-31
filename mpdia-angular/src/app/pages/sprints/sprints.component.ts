// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ShellComponent } from '../../layout/shell/shell.component';
import { AuthService } from '../../services/auth.service';
import { SprintService } from '../../services/sprint.service';
import { SprintDto } from '../../models/sprint.model';
import { ProyectoDto } from '../../models/proyecto.model';

type AccionSprint = 'cerrar' | 'reabrir' | 'finalizar';

@Component({
  selector: 'app-sprints',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell title="Sprints" [showBanner]="false">

      @if (alertMsg) {
        <div class="alert py-2 small" [class]="alertClass">{{ alertMsg }}</div>
      }

      @if (!proyecto) {
        <div class="text-center py-5 text-muted">
          <i class="bi bi-folder-x fs-1 d-block mb-3 opacity-25"></i>
          <p>No hay proyecto activo. Seleccioná uno primero.</p>
          <button class="btn btn-primary btn-sm" (click)="router.navigate(['/proyectos'])">
            Ir a Proyectos
          </button>
        </div>

      } @else {

        <!-- Info del proyecto -->
        <div class="card mb-4 border-primary">
          <div class="card-body py-2">
            <div class="d-flex align-items-center gap-3 flex-wrap">
              <div>
                <div class="text-muted small">Proyecto</div>
                <div class="fw-semibold">{{ proyecto.nombre }}</div>
              </div>
              <div class="vr"></div>
              <span class="badge" [class]="proyecto.metodo === 'scrum' ? 'bg-primary' : 'bg-info text-dark'">
                {{ proyecto.metodo === 'scrum' ? 'Scrum' : 'XP' }}
              </span>
              <div class="vr"></div>
              <div>
                <div class="text-muted small">Time Box</div>
                <div class="fw-semibold">{{ proyecto.timeBoxSemanas }} semana(s)</div>
              </div>
              <div class="vr"></div>
              <div class="flex-grow-1">
                <div class="text-muted small">Product Goal</div>
                <div class="small">{{ proyecto.productGoal }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- Finalizar sprint en ejecución e iniciar el siguiente (solo SM) -->
        @if (esScrumMaster && sprintActivo) {
          <div class="card mb-4">
            <div class="card-header fw-semibold small">
              <i class="bi bi-arrow-right-circle me-1 text-warning"></i>
              Finalizar Sprint {{ sprintActivo.numero }} e iniciar Sprint {{ sprintActivo.numero + 1 }}
            </div>
            <div class="card-body">
              <div class="mb-3">
                <label class="form-label small fw-semibold">
                  Sprint Goal del Sprint {{ sprintActivo.numero + 1 }}
                  <span class="text-danger">*</span>
                </label>
                <textarea class="form-control form-control-sm" rows="2"
                          placeholder="¿Qué se quiere lograr en el siguiente sprint?"
                          [(ngModel)]="nuevoSprintGoal"></textarea>
              </div>
              <button class="btn btn-warning btn-sm"
                      [disabled]="!nuevoSprintGoal.trim() || procesando"
                      (click)="pedirCerrarSiguiente()">
                <i class="bi bi-arrow-clockwise me-1"></i>
                Finalizar Sprint {{ sprintActivo.numero }} e iniciar Sprint {{ sprintActivo.numero + 1 }}
              </button>
            </div>
          </div>
        }

        <!-- Historial de sprints -->
        <div class="card">
          <div class="card-header fw-semibold small">
            <i class="bi bi-list-ol me-1"></i>Historial de sprints
          </div>
          <div class="card-body p-0">
            @if (cargando) {
              <div class="text-center py-4 text-muted small">
                <span class="spinner-border spinner-border-sm me-2"></span>Cargando...
              </div>
            } @else if (sprints.length === 0) {
              <div class="text-center py-4 text-muted small">
                No hay sprints registrados.
              </div>
            } @else {
              <table class="table table-sm table-hover mb-0">
                <thead class="table-light">
                  <tr>
                    <th class="ps-3" style="width:60px">#</th>
                    <th>Sprint Goal</th>
                    <th style="width:120px">Inicio</th>
                    <th style="width:120px">Fin</th>
                    <th style="width:100px">Estado</th>
                    @if (esScrumMaster) {
                      <th style="width:140px" class="text-end pe-3">Acciones</th>
                    }
                  </tr>
                </thead>
                <tbody>
                  @for (s of sprints; track s.id) {
                    <tr [class.table-success]="s.estado === 'en_ejecucion'"
                        [class.bg-opacity-25]="s.estado === 'en_ejecucion'">
                      <td class="ps-3 fw-bold align-middle">{{ s.numero }}</td>
                      <td class="small align-middle">{{ s.sprintGoal }}</td>
                      <td class="small text-muted align-middle">
                        {{ s.fechaInicio | date:'dd/MM/yyyy' }}
                      </td>
                      <td class="small text-muted align-middle">
                        {{ s.fechaFin ? (s.fechaFin | date:'dd/MM/yyyy') : '—' }}
                      </td>
                      <td class="align-middle">
                        <span class="badge"
                              [class]="s.estado === 'en_ejecucion' ? 'bg-success' :
                                       s.estado === 'pendiente'    ? 'bg-warning text-dark' :
                                       s.estado === 'reabierto'    ? 'bg-info text-dark' : 'bg-secondary'"
                              style="font-size:0.65rem">
                          {{ s.estado === 'en_ejecucion' ? 'En ejecución' :
                             s.estado === 'pendiente'    ? 'Pendiente' :
                             s.estado === 'reabierto'    ? 'Reabierto' : 'Finalizado' }}
                        </span>
                      </td>
                      @if (esScrumMaster) {
                        <td class="align-middle text-end pe-3">
                          @if (s.estado === 'finalizado') {
                            <button class="btn btn-outline-info btn-sm py-0 px-2"
                                    [disabled]="procesando"
                                    (click)="pedirReabrir(s)">
                              <i class="bi bi-arrow-counterclockwise me-1"></i>Reabrir
                            </button>
                          } @else if (s.estado === 'reabierto') {
                            <button class="btn btn-outline-secondary btn-sm py-0 px-2"
                                    [disabled]="procesando"
                                    (click)="pedirFinalizar(s)">
                              <i class="bi bi-check2-circle me-1"></i>Finalizar
                            </button>
                          }
                        </td>
                      }
                    </tr>
                  }
                </tbody>
              </table>
            }
          </div>
        </div>
      }

      <!-- Modal de confirmación (mismo patrón visual que Proyectos: eliminar proyecto) -->
      @if (accionPendiente) {
        <div class="modal d-block" style="background-color: rgba(0,0,0,0.5)" (click)="cancelarAccion()">
          <div class="modal-dialog" (click)="$event.stopPropagation()">
            <div class="modal-content">
              <div class="modal-header">
                <h5 class="modal-title">
                  <i class="bi bi-question-circle-fill me-2 text-warning"></i>
                  {{ tituloAccion() }}
                </h5>
                <button type="button" class="btn-close" [disabled]="procesando" (click)="cancelarAccion()"></button>
              </div>
              <div class="modal-body">
                <p>{{ mensajeAccion() }}</p>
              </div>
              <div class="modal-footer">
                <button class="btn btn-outline-secondary btn-sm" [disabled]="procesando" (click)="cancelarAccion()">
                  Cancelar
                </button>
                <button class="btn btn-warning btn-sm" [disabled]="procesando" (click)="confirmarAccion()">
                  @if (procesando) { <span class="spinner-border spinner-border-sm me-1"></span> }
                  Sí, confirmar
                </button>
              </div>
            </div>
          </div>
        </div>
      }

    </app-shell>
  `
})
export class SprintsComponent implements OnInit {
  sprints: SprintDto[]          = [];
  sprintActivo: SprintDto | null = null;
  proyecto: ProyectoDto | null   = null;
  cargando        = true;
  nuevoSprintGoal = '';
  alertMsg   = '';
  alertClass = 'alert-success';

  /** Acción de cambio de estado pendiente de confirmación. */
  accionPendiente: { tipo: AccionSprint; sprint?: SprintDto } | null = null;
  procesando = false;

  constructor(
    public  auth: AuthService,
    public  router: Router,
    private sprintService: SprintService
  ) {}

  /**
   * Corrección: Scrum Master es siempre relativo a ESTE proyecto (su
   * scrumMasterEmail, fijado por el backend al crearlo), nunca el rol
   * global de cuenta — mismo patrón ya corregido en dashboard.component.ts
   * (esScrumMasterDelProyecto) y ejecucion.component.ts.
   */
  get esScrumMaster(): boolean {
    return this.proyecto?.scrumMasterEmail === this.auth.currentUser()?.email;
  }

  ngOnInit(): void {
    try {
      const p = localStorage.getItem('mpdia_proyecto_activo');
      this.proyecto = p ? JSON.parse(p) : null;
    } catch { /* ignore */ }

    if (this.proyecto) this.cargar();
    else this.cargando = false;
  }

  cargar(): void {
    this.cargando = true;
    this.sprintService.listar(this.proyecto!.id).pipe(
      catchError(() => of([]))
    ).subscribe(list => {
      this.sprints      = list;
      this.sprintActivo = list.find(s => s.estado === 'en_ejecucion') ?? null;
      this.cargando     = false;
    });
  }

  // ── Confirmación de acciones de estado ──────────────────────────────────

  /** Cerrar el sprint activo e iniciar el siguiente; no elimina nada todavía. */
  pedirCerrarSiguiente(): void {
    if (!this.nuevoSprintGoal.trim() || !this.sprintActivo) return;
    this.accionPendiente = { tipo: 'cerrar' };
  }

  pedirReabrir(s: SprintDto): void {
    this.accionPendiente = { tipo: 'reabrir', sprint: s };
  }

  pedirFinalizar(s: SprintDto): void {
    this.accionPendiente = { tipo: 'finalizar', sprint: s };
  }

  cancelarAccion(): void {
    if (this.procesando) return;
    this.accionPendiente = null;
  }

  tituloAccion(): string {
    switch (this.accionPendiente?.tipo) {
      case 'cerrar':    return 'Finalizar sprint';
      case 'reabrir':   return 'Reabrir sprint';
      case 'finalizar': return 'Finalizar sprint';
      default:          return '';
    }
  }

  mensajeAccion(): string {
    const accion = this.accionPendiente;
    if (!accion) return '';
    if (accion.tipo === 'cerrar' && this.sprintActivo) {
      return `¿Seguro que querés finalizar el Sprint ${this.sprintActivo.numero} e iniciar el Sprint ${this.sprintActivo.numero + 1}?`;
    }
    if (accion.tipo === 'reabrir' && accion.sprint) {
      return `¿Seguro que querés reabrir el Sprint ${accion.sprint.numero}?`;
    }
    if (accion.tipo === 'finalizar' && accion.sprint) {
      return `¿Seguro que querés volver a finalizar el Sprint ${accion.sprint.numero}?`;
    }
    return '';
  }

  confirmarAccion(): void {
    if (!this.accionPendiente || this.procesando) return;
    const accion = this.accionPendiente;
    this.procesando = true;

    const obs = accion.tipo === 'cerrar'
      ? this.sprintService.cerrarEIniciarSiguiente(this.proyecto!.id, this.nuevoSprintGoal)
      : accion.tipo === 'reabrir'
        ? this.sprintService.reabrir(accion.sprint!.id)
        : this.sprintService.finalizarReabierto(accion.sprint!.id);

    obs.pipe(
      catchError(err => {
        this.showAlert(err?.error?.error ?? 'Error al actualizar el sprint.', 'alert-danger');
        return of(null);
      })
    ).subscribe(resultado => {
      if (resultado) {
        if (accion.tipo === 'cerrar') {
          localStorage.setItem('mpdia_sprint_activo', JSON.stringify(resultado));
          this.nuevoSprintGoal = '';
          this.showAlert(`Sprint ${resultado.numero} iniciado.`, 'alert-success');
        } else if (accion.tipo === 'reabrir') {
          this.showAlert(`Sprint ${resultado.numero} reabierto.`, 'alert-success');
        } else {
          this.showAlert(`Sprint ${resultado.numero} finalizado.`, 'alert-success');
        }
        this.cargar();
      }
      this.procesando = false;
      this.accionPendiente = null;
    });
  }

  private showAlert(msg: string, cls: string): void {
    this.alertMsg = msg; this.alertClass = cls;
    setTimeout(() => this.alertMsg = '', 4000);
  }
}
