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

        <!-- Cerrar sprint e iniciar siguiente (solo SM) -->
        @if (esScrumMaster && sprintActivo) {
          <div class="card mb-4">
            <div class="card-header fw-semibold small">
              <i class="bi bi-arrow-right-circle me-1 text-warning"></i>
              Cerrar Sprint {{ sprintActivo.numero }} e iniciar Sprint {{ sprintActivo.numero + 1 }}
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
                      [disabled]="!nuevoSprintGoal.trim() || cerrando"
                      (click)="cerrarEIniciarSiguiente()">
                @if (cerrando) {
                  <span class="spinner-border spinner-border-sm me-1"></span>
                } @else {
                  <i class="bi bi-arrow-clockwise me-1"></i>
                }
                Cerrar Sprint {{ sprintActivo.numero }} e iniciar Sprint {{ sprintActivo.numero + 1 }}
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
                    </tr>
                  }
                </tbody>
              </table>
            }
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
  cerrando        = false;
  nuevoSprintGoal = '';
  alertMsg   = '';
  alertClass = 'alert-success';

  constructor(
    public  auth: AuthService,
    public  router: Router,
    private sprintService: SprintService
  ) {}

  get esScrumMaster(): boolean {
    return this.auth.currentUser()?.role === 'scrum_master';
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

  cerrarEIniciarSiguiente(): void {
    if (!this.nuevoSprintGoal.trim() || !this.proyecto) return;
    this.cerrando = true;
    this.sprintService.cerrarEIniciarSiguiente(this.proyecto.id, this.nuevoSprintGoal).pipe(
      catchError(err => {
        this.showAlert(err?.error?.error ?? 'Error al cerrar el sprint.', 'alert-danger');
        this.cerrando = false;
        return of(null);
      })
    ).subscribe(nuevoSprint => {
      if (nuevoSprint) {
        localStorage.setItem('mpdia_sprint_activo', JSON.stringify(nuevoSprint));
        this.nuevoSprintGoal = '';
        this.showAlert(`Sprint ${nuevoSprint.numero} iniciado.`, 'alert-success');
        this.cargar();
      }
      this.cerrando = false;
    });
  }

  private showAlert(msg: string, cls: string): void {
    this.alertMsg = msg; this.alertClass = cls;
    setTimeout(() => this.alertMsg = '', 4000);
  }
}
