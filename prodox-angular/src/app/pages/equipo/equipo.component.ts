// Autor: Cristian Santiago Martinez Cordoba — PRODOX
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
import { etiquetaRol, ROL_PRODUCT_OWNER, ROL_SCRUM_MASTER, ROL_SCRUM_MEMBER } from '../../models/project-role.model';
import { timeboxAbreviado } from '../../models/timebox.model';

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
                    {{ timeboxAbreviado(proyecto) }}/iteración
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
                <div class="col-md-5">
                  <label class="form-label small">Correo electrónico</label>
                  <input type="email" class="form-control form-control-sm"
                         placeholder="nombre@ejemplo.com"
                         [(ngModel)]="emailInvitar">
                </div>
                <div class="col-md-4">
                  <label class="form-label small">Rol en el proyecto</label>
                  <select class="form-select form-select-sm" [(ngModel)]="rolInvitar">
                    <option value="scrum_member">Scrum Member</option>
                    @if (!hayProductOwner()) {
                      <option value="product_owner">Product Owner</option>
                    }
                  </select>
                  @if (hayProductOwner()) {
                    <div class="form-text text-muted">Este proyecto ya tiene un Product Owner.</div>
                  }
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
              <div class="prox-empty-state">
                <p>No hay miembros registrados.</p>
              </div>
            } @else {
              <div class="table-responsive">
                <table class="table table-sm table-hover mb-0">
                  <thead class="table-light">
                    <tr>
                      <th class="ps-3">Email</th>
                      <th>Rol</th>
                      <th>Desde</th>
                      @if (esScrumMaster) {
                        <th>Cambiar rol</th>
                      }
                    </tr>
                  </thead>
                  <tbody>
                    @for (m of miembros; track m.userId) {
                      <tr>
                        <td class="ps-3 small align-middle text-nowrap">
                          <i class="bi bi-person-circle me-1 text-secondary"></i>
                          {{ m.userEmail }}
                          @if (m.userEmail === proyecto.scrumMasterEmail) {
                            <span class="badge bg-primary ms-1" style="font-size:0.6rem">SM</span>
                          }
                        </td>
                        <td class="align-middle text-nowrap">
                          <span class="badge prox-badge-sm" [class]="badgeRol(m.rol)">
                            {{ etiquetaRol(m.rol) }}
                          </span>
                        </td>
                        <td class="small text-muted align-middle text-nowrap">
                          {{ m.joinedAt | date:'dd/MM/yyyy' }}
                        </td>
                        @if (esScrumMaster) {
                          <td class="align-middle text-nowrap">
                            @if (m.rol !== ROL_SCRUM_MASTER) {
                              <select class="form-select form-select-sm"
                                      style="width:auto;display:inline-block"
                                      [ngModel]="m.rol"
                                      [disabled]="cambiandoRolDe === m.userId"
                                      (ngModelChange)="cambiarRolMiembro(m, $event)">
                                <option [value]="ROL_SCRUM_MEMBER">Scrum Member</option>
                                @if (puedeOfrecerProductOwner(m)) {
                                  <option [value]="ROL_PRODUCT_OWNER">Product Owner</option>
                                }
                              </select>
                            } @else {
                              <span class="text-muted small">—</span>
                            }
                          </td>
                        }
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
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
  rolInvitar: 'scrum_member' | 'product_owner' = 'scrum_member';
  codigoGenerado = '';
  cambiandoRolDe: string | null = null;
  alertMsg   = '';
  alertClass = 'alert-success';

  /** Expuestos al template para no repetir literales de string en el HTML. */
  readonly ROL_SCRUM_MASTER = ROL_SCRUM_MASTER;
  readonly ROL_PRODUCT_OWNER = ROL_PRODUCT_OWNER;
  readonly ROL_SCRUM_MEMBER = ROL_SCRUM_MEMBER;
  readonly etiquetaRol = etiquetaRol;
  readonly timeboxAbreviado = timeboxAbreviado;

  constructor(
    public  auth: AuthService,
    private memberService: ProjectMemberService,
    public  router: Router
  ) {}

  /**
   * V39: el rol POR PROYECTO sale de ProyectoDto.miRol (calculado por el
   * backend a partir de ProjectMember.rol), ya no de comparar
   * proyecto.scrumMasterEmail contra el email de la cuenta — con un tercer
   * rol (Product Owner) esa comparación solo alcanzaba para distinguir dos
   * casos, no tres.
   *
   * Fallback: si `miRol` todavía no llega (proyecto activo cacheado en
   * localStorage ANTES de V39, o un backend que aún no fue reiniciado con
   * este cambio), se recupera el criterio previo — comparar el email de la
   * cuenta contra scrumMasterEmail — para no ocultarle el panel de invitar a
   * un Scrum Master real mientras esos datos se actualizan.
   */
  get esScrumMaster(): boolean {
    if (this.proyecto?.miRol) return this.proyecto.miRol === ROL_SCRUM_MASTER;
    return this.proyecto?.scrumMasterEmail === this.auth.currentUser()?.email;
  }

  badgeRol(rol: string): string {
    if (rol === ROL_SCRUM_MASTER) return 'bg-primary';
    if (rol === ROL_PRODUCT_OWNER) return 'bg-info text-dark';
    return 'bg-secondary';
  }

  /**
   * Regla de negocio: a lo sumo un Product Owner activo por proyecto. Se
   * calcula acá a partir de los miembros ya cargados (sin endpoint nuevo);
   * el backend es quien realmente la garantiza (ProjectMemberService —
   * invitar/unirse/cambiarRol — más un índice único parcial en base de
   * datos), esto solo evita ofrecer en el selector una opción que el
   * backend rechazaría igual.
   */
  hayProductOwner(): boolean {
    return this.miembros.some(m => m.rol === ROL_PRODUCT_OWNER);
  }

  /** El propio Product Owner conserva la opción (para poder degradarlo);
   *  cualquier otra fila la pierde mientras ya exista uno en el proyecto. */
  puedeOfrecerProductOwner(m: ProjectMemberDto): boolean {
    return !this.hayProductOwner() || m.rol === ROL_PRODUCT_OWNER;
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
      // Si el select de invitar había quedado en "product_owner" (por ejemplo,
      // otra pestaña acaba de asignar uno) y ya no corresponde ofrecerlo,
      // se vuelve a scrum_member para no dejar seleccionada una opción inválida.
      if (this.rolInvitar === 'product_owner' && this.hayProductOwner()) {
        this.rolInvitar = 'scrum_member';
      }
    });
  }

  invitar(): void {
    if (!this.emailInvitar.trim() || !this.proyecto) return;
    this.enviando = true;
    this.memberService.invitar(this.proyecto.id, this.emailInvitar, this.rolInvitar).pipe(
      catchError(err => {
        this.showAlert(err?.error?.error ?? 'Error al generar invitación.', 'alert-danger');
        this.enviando = false;
        return of(null);
      })
    ).subscribe(res => {
      if (res) {
        this.codigoGenerado = res.codigo;
        this.emailInvitar   = '';
        if (res.emailEnviado) {
          this.showAlert(`Código generado: ${res.codigo}. Se envió un correo con la invitación.`, 'alert-success');
        } else {
          this.showAlert(`Código generado: ${res.codigo}. No se pudo enviar el correo automáticamente; compartí el código manualmente.`, 'alert-warning');
        }
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

  /**
   * Cambia el rol de un miembro existente. La autorización real la hace el
   * backend (solo el Scrum Master del proyecto — ver
   * ProjectMemberService.cambiarRol); acá solo se refleja el resultado o el
   * error, sin duplicar ninguna regla de negocio en el frontend.
   */
  cambiarRolMiembro(m: ProjectMemberDto, nuevoRol: string): void {
    if (!this.proyecto || nuevoRol === m.rol) return;
    const anterior = m.rol;
    this.cambiandoRolDe = m.userId;
    this.memberService.cambiarRol(this.proyecto.id, m.userId, nuevoRol as 'scrum_member' | 'product_owner').pipe(
      catchError(err => {
        this.showAlert(err?.error?.error ?? 'No se pudo cambiar el rol.', 'alert-danger');
        m.rol = anterior;
        this.cambiandoRolDe = null;
        return of(null);
      })
    ).subscribe(actualizado => {
      if (actualizado) {
        m.rol = actualizado.rol;
        this.showAlert(`Rol actualizado a ${etiquetaRol(actualizado.rol)}.`, 'alert-success');
      }
      this.cambiandoRolDe = null;
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
