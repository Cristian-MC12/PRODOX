// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { catchError, of } from 'rxjs';
import { ShellComponent } from '../../layout/shell/shell.component';
import { AuthService } from '../../services/auth.service';
import { MetricRankingService } from '../../services/metric-ranking.service';
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
}

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

        <p class="text-muted small mb-4">
          Revisá las parametrizaciones enviadas por el equipo y aprobá o rechazá cada una.
        </p>

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

        <!-- Tabla de pendientes -->
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
                          <span class="badge mb-1" [class]="categoryBadge(p.factorCategoria)"
                                style="font-size:0.65rem">
                            {{ p.factorCategoria }}
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
                    <span class="badge" [class]="categoryBadge(detalle.factorCategoria)">
                      {{ detalle.factorCategoria }}
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
  motivoRechazo = '';
  errorRechazo  = '';
  alertMsg   = '';
  alertClass = 'alert-success';
  aprobadas  = 0;
  rechazadas = 0;

  private readonly apiBase = environment.apiBaseUrl;

  constructor(
    public  auth: AuthService,
    private http: HttpClient
  ) {}

  get esScrumMaster(): boolean {
    return this.auth.currentUser()?.role === 'scrum_master';
  }

  ngOnInit(): void {
    if (this.esScrumMaster) this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.http.get<Pendiente[]>(`${this.apiBase}/metric-ranking/pendientes`).pipe(
      catchError(() => of([]))
    ).subscribe(list => {
      this.pendientes = list;
      this.cargando   = false;
    });
  }

  verDetalle(p: Pendiente): void {
    this.detalle = p;
  }

  aprobar(p: Pendiente): void {
    this.procesando = p.id;
    this.http.post(`${this.apiBase}/metric-ranking/verificar`, {
      parametrizacionId: p.id,
      accion: 'aprobar'
    }).pipe(catchError(() => of(null))).subscribe(() => {
      this.procesando = '';
      this.aprobadas++;
      this.pendientes = this.pendientes.filter(x => x.id !== p.id);
      this.showAlert(`Parametrización de "${p.factorNombre}" aprobada.`, 'alert-success');
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
    }).pipe(catchError(() => of(null))).subscribe(() => {
      this.procesando  = '';
      this.rechazadas++;
      this.rechazando  = null;
      this.pendientes  = this.pendientes.filter(x => x.id !== p.id);
      this.showAlert(`Parametrización de "${p.factorNombre}" rechazada.`, 'alert-warning');
    });
  }

  categoryBadge(cat: string): string {
    const map: Record<string, string> = {
      'Productividad': 'bg-primary',
      'Calidad':       'bg-warning text-dark',
      'Cumplimiento':  'bg-success',
      'Sociohumano':   'bg-info text-dark'
    };
    return map[cat] ?? 'bg-secondary';
  }

  private showAlert(msg: string, cls: string): void {
    this.alertMsg = msg; this.alertClass = cls;
    setTimeout(() => this.alertMsg = '', 4000);
  }
}
