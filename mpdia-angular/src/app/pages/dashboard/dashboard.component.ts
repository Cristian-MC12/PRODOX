// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ShellComponent } from '../../layout/shell/shell.component';
import { MetricaPlanService } from '../../services/metrica-plan.service';
import { FactorService } from '../../services/factor.service';
import { MetricaPlan } from '../../models/metrica-plan.model';
import { Factor } from '../../models/factor.model';
import { environment } from '../../../environments/environment';

interface MetricaSugeridaGemini {
  nombre: string;
  descripcion: string;
  unidad: string;
  valorMeta: number;
  fuente: string;
  justificacion: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, ShellComponent],
  template: `
    <app-shell title="Definición de Métricas">

      <p class="text-muted small mb-4">
        En esta etapa se define <strong>qué se va a medir</strong> y <strong>cómo</strong>
        para cada factor seleccionado. Los valores reales se registrarán durante la
        <em>fase de Ejecución</em>.
      </p>

      @if (alertMsg) {
        <div class="alert py-2 small" [class]="alertClass">{{ alertMsg }}</div>
      }

      <!-- KPIs de planeación -->
      <div class="row g-3 mb-4">
        <div class="col-6 col-md-3">
          <div class="card kpi-card text-center">
            <div class="card-body py-3">
              <div class="kpi-label">Métricas definidas</div>
              <div class="kpi-value text-primary">{{ total }}</div>
            </div>
          </div>
        </div>
        <div class="col-6 col-md-3">
          <div class="card kpi-card text-center">
            <div class="card-body py-3">
              <div class="kpi-label">Aprobadas</div>
              <div class="kpi-value text-success">{{ aprobadas }}</div>
            </div>
          </div>
        </div>
        <div class="col-6 col-md-3">
          <div class="card kpi-card text-center">
            <div class="card-body py-3">
              <div class="kpi-label">En revisión</div>
              <div class="kpi-value text-warning">{{ borradores }}</div>
            </div>
          </div>
        </div>
        <div class="col-6 col-md-3">
          <div class="card kpi-card text-center">
            <div class="card-body py-3">
              <div class="kpi-label">Rechazadas</div>
              <div class="kpi-value text-danger">{{ rechazadas }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- Copiloto: sugerir métricas automáticamente -->
      <div class="card mb-3">
        <div class="card-header d-flex align-items-center gap-2">
          <i class="bi bi-robot text-primary"></i>
          <span class="fw-semibold small">Copiloto — Sugerir métricas para un factor</span>
        </div>
        <div class="card-body">
          <p class="text-muted small mb-3">
            El Copiloto sugiere automáticamente las métricas a definir para el factor seleccionado,
            incluyendo unidad, valor meta y fuente de datos recomendada.
          </p>
          <div class="row g-2 align-items-end">
            <div class="col-md-6">
              <label class="form-label small">Factor</label>
              <select class="form-select form-select-sm" [(ngModel)]="autoFactorId">
                <option value="">Seleccionar factor...</option>
                @for (f of factors; track f.id) {
                  <option [value]="f.id">{{ f.name }} — {{ f.category }}</option>
                }
              </select>
            </div>
            <div class="col-md-4">
              <button class="btn btn-primary btn-sm w-100"
                      [disabled]="!autoFactorId || generating"
                      (click)="generateAuto()">
                @if (generating) {
                  <span class="spinner-border spinner-border-sm me-1"></span>Generando...
                } @else {
                  <i class="bi bi-robot me-1"></i>Sugerir métricas
                }
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Formulario: definir métrica manualmente -->
      <div class="card mb-4">
        <div class="card-header fw-semibold small">
          <i class="bi bi-plus-circle me-1"></i>
          {{ editingId ? 'Editar definición de métrica' : 'Definir métrica manualmente' }}
        </div>
        <div class="card-body">
          <form [formGroup]="form" (ngSubmit)="submit()">
            <div class="row g-3">
              <div class="col-md-4">
                <label class="form-label small">Factor <span class="text-danger">*</span></label>
                <select class="form-select form-select-sm" formControlName="factorId"
                        [class.is-invalid]="f['factorId'].invalid && f['factorId'].touched">
                  <option value="">Seleccionar...</option>
                  @for (fac of factors; track fac.id) {
                    <option [value]="fac.id">{{ fac.name }}</option>
                  }
                </select>
                <div class="invalid-feedback">Requerido.</div>
              </div>
              <div class="col-md-2">
                <label class="form-label small">Unidad <span class="text-danger">*</span></label>
                <input type="text" class="form-control form-control-sm"
                       placeholder="%, pts, días..."
                       formControlName="unidad"
                       [class.is-invalid]="f['unidad'].invalid && f['unidad'].touched">
                <div class="invalid-feedback">Requerido.</div>
              </div>
              <div class="col-md-2">
                <label class="form-label small">Valor meta <span class="text-danger">*</span></label>
                <input type="number" class="form-control form-control-sm"
                       placeholder="Ej: 80"
                       formControlName="valorMeta"
                       [class.is-invalid]="f['valorMeta'].invalid && f['valorMeta'].touched">
                <div class="invalid-feedback">Requerido.</div>
              </div>
              <div class="col-md-4">
                <label class="form-label small">Fuente de datos <span class="text-danger">*</span></label>
                <select class="form-select form-select-sm" formControlName="fuente"
                        [class.is-invalid]="f['fuente'].invalid && f['fuente'].touched">
                  <option value="">Seleccionar...</option>
                  <option value="Jira">Jira</option>
                  <option value="GitHub">GitHub</option>
                  <option value="Manual">Manual</option>
                </select>
                <div class="invalid-feedback">Requerido.</div>
              </div>
              <div class="col-12">
                <label class="form-label small">Descripción — cómo se recopilará el dato <span class="text-danger">*</span></label>
                <input type="text" class="form-control form-control-sm"
                       placeholder="Ej: Story points completados vs. planificados al cierre del sprint."
                       formControlName="descripcion"
                       [class.is-invalid]="f['descripcion'].invalid && f['descripcion'].touched">
                <div class="invalid-feedback">Requerido.</div>
              </div>
              <div class="col-12 d-flex gap-2">
                <button type="submit" class="btn btn-primary btn-sm" [disabled]="form.invalid">
                  <i class="bi bi-floppy me-1"></i>{{ editingId ? 'Actualizar' : 'Guardar métrica' }}
                </button>
                @if (editingId) {
                  <button type="button" class="btn btn-outline-secondary btn-sm" (click)="cancelEdit()">
                    Cancelar
                  </button>
                }
              </div>
            </div>
          </form>
        </div>
      </div>

      <!-- Tabla de métricas definidas -->
      <div class="card">
        <div class="card-header d-flex flex-column flex-md-row gap-2 align-items-md-center justify-content-between">
          <span class="fw-semibold small">Métricas definidas para el sprint</span>
          <div class="d-flex gap-2 flex-wrap">
            <input type="text" class="form-control form-control-sm"
                   placeholder="Buscar por factor..." [(ngModel)]="search"
                   style="max-width:180px">
            <select class="form-select form-select-sm" [(ngModel)]="statusFilter" style="max-width:150px">
              <option value="all">Todas</option>
              <option value="borrador">En revisión</option>
              <option value="aprobada">Aprobadas</option>
              <option value="rechazada">Rechazadas</option>
            </select>
          </div>
        </div>
        <div class="card-body p-0">
          <div class="table-responsive">
            <table class="table table-hover mb-0">
              <thead class="table-light">
                <tr>
                  <th>Factor</th>
                  <th>Categoría</th>
                  <th>Unidad</th>
                  <th>Valor meta</th>
                  <th>Fuente</th>
                  <th>Estado</th>
                  <th class="text-end">Acciones</th>
                </tr>
              </thead>
              <tbody>
                @if (filtered.length === 0) {
                  <tr>
                    <td colspan="7" class="text-center text-muted py-5">
                      <i class="bi bi-clipboard-plus fs-3 d-block mb-2"></i>
                      No hay métricas definidas. Usá el Copiloto o definí una manualmente.
                    </td>
                  </tr>
                } @else {
                  @for (m of filtered; track m.id) {
                    <tr [class]="m.status === 'rechazada' ? 'table-danger' : m.status === 'aprobada' ? 'table-success bg-opacity-25' : ''">
                      <td class="fw-medium">{{ m.factorName }}</td>
                      <td>
                        <span class="badge" [class]="categoryBadge(m.factorCategory ?? '')">
                          {{ m.factorCategory }}
                        </span>
                      </td>
                      <td>{{ m.unidad }}</td>
                      <td class="fw-semibold">{{ m.valorMeta }} {{ m.unidad }}</td>
                      <td>
                        <span class="badge bg-light text-dark border">
                          <i class="bi me-1" [class]="m.fuente === 'Jira' ? 'bi-kanban' : m.fuente === 'GitHub' ? 'bi-github' : 'bi-person'"></i>
                          {{ m.fuente }}
                        </span>
                      </td>
                      <td>
                        @switch (m.status) {
                          @case ('aprobada') {
                            <span class="badge bg-success">
                              <i class="bi bi-check-circle me-1"></i>Aprobada
                            </span>
                          }
                          @case ('rechazada') {
                            <span class="badge bg-danger" [title]="m.rechazadoMotivo ?? ''">
                              <i class="bi bi-x-circle me-1"></i>Rechazada
                            </span>
                          }
                          @default {
                            <span class="badge bg-warning text-dark">
                              <i class="bi bi-clock me-1"></i>En revisión
                            </span>
                          }
                        }
                      </td>
                      <td class="text-end">
                        <div class="d-flex gap-1 justify-content-end">
                          @if (m.status === 'borrador') {
                            <button class="btn btn-sm btn-outline-success"
                                    (click)="approve(m.id!)" title="Aprobar definición">
                              <i class="bi bi-check-circle"></i>
                            </button>
                            <button class="btn btn-sm btn-outline-danger"
                                    (click)="openReject(m)" title="Rechazar definición">
                              <i class="bi bi-x-circle"></i>
                            </button>
                          }
                          <button class="btn btn-sm btn-outline-secondary"
                                  (click)="edit(m)" title="Editar">
                            <i class="bi bi-pencil"></i>
                          </button>
                          <button class="btn btn-sm btn-outline-danger"
                                  (click)="delete(m.id!)" title="Eliminar">
                            <i class="bi bi-trash"></i>
                          </button>
                        </div>
                        @if (m.status === 'rechazada' && m.rechazadoMotivo) {
                          <small class="text-danger fst-italic d-block mt-1" style="max-width:200px">
                            {{ m.rechazadoMotivo }}
                          </small>
                        }
                      </td>
                    </tr>
                  }
                }
              </tbody>
            </table>
          </div>
        </div>

        @if (total > 0) {
          <div class="card-footer small">
            @if (borradores > 0) {
              <span class="text-warning">
                <i class="bi bi-exclamation-triangle me-1"></i>
                Hay <strong>{{ borradores }}</strong> métrica(s) pendiente(s) de aprobación.
              </span>
            } @else if (rechazadas > 0) {
              <span class="text-danger">
                <i class="bi bi-x-circle me-1"></i>
                Hay <strong>{{ rechazadas }}</strong> métrica(s) rechazada(s).
              </span>
            } @else {
              <span class="text-success">
                <i class="bi bi-check-all me-1"></i>
                Todas las métricas están aprobadas.
              </span>
            }
          </div>
        }
      </div>

      <!-- Modal rechazo -->
      @if (rejectingMetrica) {
        <div class="modal d-block" tabindex="-1" style="background:rgba(0,0,0,.4)">
          <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content">
              <div class="modal-header">
                <h6 class="modal-title">
                  <i class="bi bi-x-circle text-danger me-2"></i>Rechazar definición de métrica
                </h6>
                <button type="button" class="btn-close" (click)="cancelReject()"></button>
              </div>
              <div class="modal-body">
                <p class="small text-muted mb-2">
                  Factor: <strong>{{ rejectingMetrica.factorName }}</strong> —
                  Meta: <strong>{{ rejectingMetrica.valorMeta }} {{ rejectingMetrica.unidad }}</strong>
                </p>
                <label class="form-label small">Motivo del rechazo <span class="text-danger">*</span></label>
                <textarea class="form-control form-control-sm" rows="3"
                          placeholder="Ej: El valor meta no es alcanzable en un sprint..."
                          [(ngModel)]="rejectReason"></textarea>
                @if (rejectError) {
                  <div class="text-danger small mt-1">{{ rejectError }}</div>
                }
              </div>
              <div class="modal-footer">
                <button class="btn btn-secondary btn-sm" (click)="cancelReject()">Cancelar</button>
                <button class="btn btn-danger btn-sm"
                        [disabled]="!rejectReason.trim()"
                        (click)="confirmReject()">
                  <i class="bi bi-x-circle me-1"></i>Confirmar rechazo
                </button>
              </div>
            </div>
          </div>
        </div>
      }

    </app-shell>
  `
})
export class DashboardComponent implements OnInit {
  metricas: MetricaPlan[] = [];
  factors: Factor[]       = [];
  form!: FormGroup;
  editingId: string | null = null;
  generating = false;
  autoFactorId = '';
  search       = '';
  statusFilter = 'all';
  rejectingMetrica: MetricaPlan | null = null;
  rejectReason = '';
  rejectError  = '';
  alertMsg   = '';
  alertClass = 'alert-success';
  private readonly apiBase = environment.apiBaseUrl;

  constructor(
    private metricaPlanService: MetricaPlanService,
    private factorService: FactorService,
    private fb: FormBuilder,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      factorId:    ['', Validators.required],
      unidad:      ['', Validators.required],
      valorMeta:   [null, [Validators.required, Validators.min(0)]],
      descripcion: ['', Validators.required],
      fuente:      ['', Validators.required]
    });

    this.factorService.list().subscribe(f => this.factors = f);
    this.metricaPlanService.getAll().subscribe(list => {
      this.metricas = list.filter(m => m.sprintName === 'Sprint Actual');
    });
  }

  get f() { return this.form.controls; }
  get total()      { return this.metricas.length; }
  get aprobadas()  { return this.metricas.filter(m => m.status === 'aprobada').length; }
  get borradores() { return this.metricas.filter(m => m.status === 'borrador').length; }
  get rechazadas() { return this.metricas.filter(m => m.status === 'rechazada').length; }

  get filtered(): MetricaPlan[] {
    return this.metricas.filter(m => {
      if (this.statusFilter !== 'all' && m.status !== this.statusFilter) return false;
      if (this.search && !(m.factorName ?? '').toLowerCase().includes(this.search.toLowerCase())) return false;
      return true;
    });
  }

  generateAuto(): void {
    if (!this.autoFactorId) return;
    const factor = this.factors.find(f => f.id === this.autoFactorId);
    if (!factor) return;
    this.generating = true;

    this.http.post<MetricaSugeridaGemini[]>(
      `${this.apiBase}/copiloto-plan/generar-metricas?factorId=${this.autoFactorId}`, {}
    ).subscribe({
      next: (sugeridas) => {
        sugeridas.forEach(s => {
          this.metricaPlanService.save({
            factorId:       factor.id,
            factorName:     factor.name,
            factorCategory: factor.category,
            sprintName:     'Sprint Actual',
            unidad:         s.unidad,
            valorMeta:      s.valorMeta,
            descripcion:    s.descripcion,
            fuente:         s.fuente,
            status:         'borrador'
          });
        });
        this.autoFactorId = '';
        this.generating = false;
        this.showAlert(`Copiloto generó ${sugeridas.length} métrica(s) para "${factor.name}".`, 'alert-success');
      },
      error: () => {
        this.generating = false;
        this.showAlert('Error al conectar con el Copiloto.', 'alert-danger');
      }
    });
  }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const factor = this.factors.find(f => f.id === this.form.value.factorId);
    const metrica: MetricaPlan = {
      ...this.form.value,
      id:             this.editingId ?? undefined,
      factorName:     factor?.name,
      factorCategory: factor?.category,
      sprintName:     'Sprint Actual',
      status:         'borrador'
    };
    this.metricaPlanService.save(metrica);
    this.form.reset({ factorId: '', unidad: '', valorMeta: null, descripcion: '', fuente: '' });
    this.editingId = null;
    this.showAlert('Métrica guardada. Pendiente de aprobación.', 'alert-success');
  }

  edit(m: MetricaPlan): void {
    this.editingId = m.id!;
    this.form.patchValue(m);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  cancelEdit(): void {
    this.editingId = null;
    this.form.reset({ factorId: '', unidad: '', valorMeta: null, descripcion: '', fuente: '' });
  }

  approve(id: string): void {
    this.metricaPlanService.approve(id);
    this.showAlert('Métrica aprobada.', 'alert-success');
  }

  delete(id: string): void {
    this.metricaPlanService.delete(id);
    this.showAlert('Métrica eliminada.', 'alert-secondary');
  }

  openReject(m: MetricaPlan): void {
    this.rejectingMetrica = m;
    this.rejectReason = '';
    this.rejectError  = '';
  }

  cancelReject(): void { this.rejectingMetrica = null; }

  confirmReject(): void {
    if (!this.rejectReason.trim()) { this.rejectError = 'El motivo es obligatorio.'; return; }
    this.metricaPlanService.reject(this.rejectingMetrica!.id!, this.rejectReason);
    this.rejectingMetrica = null;
    this.showAlert('Métrica rechazada.', 'alert-warning');
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
