// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ShellComponent } from '../../layout/shell/shell.component';
import { AuthService } from '../../services/auth.service';
import { ProjectMemberService } from '../../services/project-member.service';
import { ProjectMemberDto } from '../../models/project-member.model';
import { ProyectoDto } from '../../models/proyecto.model';

@Component({
  selector: 'app-equipo',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell title="Equipo Scrum">

      @if (alertMsg) {
        <div class="alert py-2 small" [class]="alertClass">{{ alertMsg }}</div>
      }

      @if (!proyecto) {
        <!-- Sin proyecto activo: mostrar formulario para unirse con código -->
        <div class="row justify-content-center">
          <div class="col-md-6">
            <div class="card">
              <div class="card-header fw-semibold small">
                <i class="bi bi-key me-1"></i>Unirse a un proyecto
              </div>
              <div class="card-body">
                <p class="text-muted small">
                  Ingresá el código que te compartió el Scrum Master para unirte al proyecto.
                </p>
                <div class="mb-3">
                  <label class="form-label small fw-semibold">Código de invitación</label>
                  <input type="text" class="form-control form-control-sm"
                         placeholder="Ej: PRJ-ABC123"
                         [(ngModel)]="codigoInput"
                         style="text-transform:uppercase;letter-spacing:2px">
                </div>
                <button class="btn btn-success btn-sm w-100"
                        [disabled]="!codigoInput.trim() || procesando"
                        (click)="unirse()">
                  @if (procesando) {
                    <span class="spinner-border spinner-border-sm me-1"></span>
                  } @else {
                    <i class="bi bi-door-open me-1"></i>
                  }
                  Unirse al proyecto
                </button>
              </div>
            </div>
          </div>
        </div>

      } @else {

        <!-- Info del proyecto activo -->
        <div class="card mb-4 border-primary">
          <div class="card-body py-2">
            <div class="row align-items-center">
              <div class="col-md-6">
                <h5 class="fw-bold mb-1">
                  <i class="bi bi-folder2-open text-primary me-2"></i>{{ proyecto.nombre }}
                </h5>
                <div class="text-muted small">
                  Scrum Master: <strong>{{ proyecto.scrumMasterEmail }}</strong>
                </div>
                <div class="d-flex gap-2 mt-1">
                  <span class="badge" [class]="proyecto.metodo === 'scrum' ? 'bg-primary' : 'bg-info text-dark'">
                    {{ proyecto.metodo === 'scrum' ? 'Scrum' : 'XP' }}
                  </span>
                  <span class="badge bg-light text-dark border">
                    {{ proyecto.timeBoxSemanas }} sem/iteración
                  </span>
                  <span class="badge" [class]="proyecto.estado === 'activo' ? 'bg-success' : 'bg-secondary'">
                    {{ proyecto.estado }}
                  </span>
                </div>
              </div>

              <!-- Código de invitación (solo SM) -->
              @if (esScrumMaster && codigoGenerado) {
                <div class="col-md-6 mt-3 mt-md-0">
                  <div class="alert alert-info mb-0 py-2">
                    <div class="small fw-semibold mb-1">
                      <i class="bi bi-key me-1"></i>Código generado
                    </div>
                    <div class="d-flex align-items-center gap-2">
                      <code class="fs-5 fw-bold text-primary">{{ codigoGenerado }}</code>
                      <button class="btn btn-sm btn-outline-primary py-0"
                              (click)="copiarCodigo()" title="Copiar">
                        <i class="bi bi-clipboard"></i>
                      </button>
                    </div>
                    <div class="text-muted mt-1" style="font-size:0.72rem">
                      Compartí este código con el miembro invitado.
                    </div>
                  </div>
                </div>
              }
            </div>
          </div>
        </div>

        <!-- Invitar por email (solo SM) -->
        @if (esScrumMaster) {
          <div class="card mb-4">
            <div class="card-header fw-semibold small">
              <i class="bi bi-envelope me-1"></i>Invitar miembro a este proyecto
            </div>
            <div class="card-body">
              <p class="text-muted small mb-3">
                Se generará un código de invitación único para este proyecto.
                Si tenés email configurado, se enviará automáticamente.
              </p>
              <div class="row g-2 align-items-end">
                <div class="col-md-7">
                  <label class="form-label small">Correo electrónico</label>
                  <input type="email" class="form-control form-control-sm"
                         placeholder="nombre@ejemplo.com"
                         [(ngModel)]="emailInvitar">
                </div>
                <div class="col-md-3">
                  <button class="btn btn-primary btn-sm w-100"
                          [disabled]="!emailInvitar.trim() || enviando"
                          (click)="invitar()">
                    @if (enviando) {
                      <span class="spinner-border spinner-border-sm me-1"></span>Generando...
                    } @else {
                      <i class="bi bi-send me-1"></i>Generar código
                    }
                  </button>
                </div>
              </div>
            </div>
          </div>
        }

        <!-- Lista de miembros del proyecto -->
        <div class="card">
          <div class="card-header fw-semibold small d-flex justify-content-between align-items-center">
            <span><i class="bi bi-people me-1"></i>Miembros del proyecto</span>
            <span class="badge bg-primary rounded-pill">{{ miembros.length }}</span>
          </div>
          <div class="card-body p-0">
            @if (cargando) {
              <div class="text-center py-4 text-muted small">
                <span class="spinner-border spinner-border-sm me-2"></span>Cargando...
              </div>
            } @else if (miembros.length === 0) {
              <div class="text-center py-4 text-muted small">
                No hay miembros registrados.
              </div>
            } @else {
              <table class="table table-sm table-hover mb-0">
                <thead class="table-light">
                  <tr>
                    <th class="ps-3">Email</th>
                    <th>Rol</th>
                    <th>Desde</th>
                  </tr>
                </thead>
                <tbody>
                  @for (m of miembros; track m.userId) {
                    <tr>
                      <td class="ps-3 small align-middle">
                        <i class="bi bi-person-circle me-1 text-secondary"></i>
                        {{ m.userEmail }}
                        @if (m.userEmail === proyecto.scrumMasterEmail) {
                          <span class="badge bg-primary ms-1" style="font-size:0.6rem">SM</span>
                        }
                      </td>
                      <td class="align-middle">
                        <span class="badge"
                              [class]="m.rol === 'scrum_master' ? 'bg-primary' : 'bg-secondary'"
                              style="font-size:0.65rem">
                          {{ m.rol === 'scrum_master' ? 'Scrum Master' : 'Scrum Member' }}
                        </span>
                      </td>
                      <td class="small text-muted align-middle">
                        {{ m.joinedAt | date:'dd/MM/yyyy' }}
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
export class EquipoComponent implements OnInit {
  proyecto: ProyectoDto | null    = null;
  miembros: ProjectMemberDto[]    = [];
  cargando      = true;
  procesando    = false;
  enviando      = false;
  codigoInput   = '';
  emailInvitar  = '';
  codigoGenerado = '';
  alertMsg   = '';
  alertClass = 'alert-success';

  constructor(
    public  auth: AuthService,
    private memberService: ProjectMemberService,
    public  router: Router
  ) {}

  get esScrumMaster(): boolean {
    return this.auth.currentUser()?.role === 'scrum_master';
  }

  ngOnInit(): void {
    try {
      const p = localStorage.getItem('mpdia_proyecto_activo');
      this.proyecto = p ? JSON.parse(p) : null;
    } catch { /* ignore */ }

    if (this.proyecto) this.cargarMiembros();
    else this.cargando = false;
  }

  cargarMiembros(): void {
    this.cargando = true;
    this.memberService.listar(this.proyecto!.id).pipe(
      catchError(() => of([]))
    ).subscribe(m => {
      this.miembros = m;
      this.cargando = false;
    });
  }

  invitar(): void {
    if (!this.emailInvitar.trim() || !this.proyecto) return;
    this.enviando = true;
    this.memberService.invitar(this.proyecto.id, this.emailInvitar).pipe(
      catchError(err => {
        this.showAlert(err?.error?.error ?? 'Error al generar invitación.', 'alert-danger');
        this.enviando = false;
        return of(null);
      })
    ).subscribe(res => {
      if (res) {
        this.codigoGenerado = res.codigo;
        this.emailInvitar   = '';
        this.showAlert(`Código generado: ${res.codigo}. Compartilo con el miembro.`, 'alert-success');
      }
      this.enviando = false;
    });
  }

  unirse(): void {
    if (!this.codigoInput.trim()) return;
    this.procesando = true;
    this.memberService.unirse(this.codigoInput.toUpperCase()).pipe(
      catchError(err => {
        this.showAlert(err?.error?.error ?? 'Código inválido.', 'alert-danger');
        this.procesando = false;
        return of(null);
      })
    ).subscribe(m => {
      if (m) {
        this.showAlert('Te uniste al proyecto exitosamente. Recargá la página de Proyectos.', 'alert-success');
        this.codigoInput = '';
        // Redirigir a proyectos para seleccionar el proyecto recién unido
        setTimeout(() => this.router.navigate(['/proyectos']), 2000);
      }
      this.procesando = false;
    });
  }

  copiarCodigo(): void {
    navigator.clipboard.writeText(this.codigoGenerado);
    this.showAlert('Código copiado al portapapeles.', 'alert-info');
  }

  private showAlert(msg: string, cls: string): void {
    this.alertMsg = msg; this.alertClass = cls;
    setTimeout(() => this.alertMsg = '', 5000);
  }
}
