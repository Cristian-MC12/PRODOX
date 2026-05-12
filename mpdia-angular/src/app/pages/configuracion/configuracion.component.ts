// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ShellComponent } from '../../layout/shell/shell.component';
import { CopilotService } from '../../services/copilot.service';
import { CopilotConfig } from '../../models/copilot.model';

@Component({
  selector: 'app-configuracion',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ShellComponent],
  template: `
    <app-shell title="Configuración del Copiloto (HU3)">
      <div style="max-width:720px">

        @if (alertMsg) {
          <div class="alert py-2 small" [class]="alertClass">{{ alertMsg }}</div>
        }

        <div class="card">
          <div class="card-header">
            <h6 class="mb-0">Integración con la herramienta de gestión</h6>
            <p class="text-muted small mb-0 mt-1">
              Conectá Jira o GitHub para que el Copiloto MPDIA sincronice automáticamente
              los datos requeridos para calcular los indicadores.
            </p>
          </div>
          <div class="card-body">
            <form [formGroup]="form" (ngSubmit)="save()" novalidate>

              <div class="row g-3 mb-3">
                <!-- Herramienta -->
                <div class="col-md-6">
                  <label class="form-label">Herramienta</label>
                  <select class="form-select" formControlName="tool">
                    <option value="jira">Jira</option>
                    <option value="github">GitHub</option>
                  </select>
                </div>

                <!-- Frecuencia -->
                <div class="col-md-6">
                  <label class="form-label">Frecuencia de sincronización</label>
                  <select class="form-select" formControlName="frequency">
                    <option value="hourly">Cada hora</option>
                    <option value="every_6h">Cada 6 horas</option>
                    <option value="daily">Diaria</option>
                    <option value="weekly">Semanal</option>
                  </select>
                </div>
              </div>

              <!-- URL -->
              <div class="mb-3">
                <label class="form-label">URL de la herramienta</label>
                <input type="url" class="form-control"
                       placeholder="https://tu-empresa.atlassian.net"
                       formControlName="url"
                       [class.is-invalid]="f['url'].invalid && f['url'].touched">
                <div class="invalid-feedback">Debe ser una URL válida (https://...).</div>
              </div>

              <!-- API Key -->
              <div class="mb-3">
                <label class="form-label">API Key</label>
                <input type="password" class="form-control"
                       placeholder="••••••••"
                       formControlName="apiKey"
                       [class.is-invalid]="f['apiKey'].invalid && f['apiKey'].touched">
                <div class="invalid-feedback">Mínimo 8 caracteres.</div>
              </div>

              <!-- Toggle activo -->
              <div class="d-flex align-items-center justify-content-between border rounded p-3 mb-3">
                <div>
                  <label class="form-check-label fw-medium" for="activeToggle">
                    Sincronización activa
                  </label>
                  <p class="text-muted small mb-0">Si está desactivado, el Copiloto no traerá datos.</p>
                </div>
                <div class="form-check form-switch">
                  <input class="form-check-input" type="checkbox" role="switch"
                         id="activeToggle" formControlName="active">
                </div>
              </div>

              <!-- Footer -->
              <div class="d-flex flex-column flex-sm-row align-items-sm-center
                          justify-content-between gap-2">
                <small class="text-muted">
                  Última sincronización:
                  <strong class="text-dark">
                    {{ lastSync ? (lastSync | date:'dd/MM/yy HH:mm') : 'Nunca' }}
                  </strong>
                </small>
                <div class="d-flex gap-2">
                  <button type="button" class="btn btn-outline-secondary btn-sm"
                          [disabled]="!existingConfig || syncing"
                          (click)="syncNow()">
                    <i class="bi bi-arrow-repeat me-1" [class.spin]="syncing"></i>
                    Sincronizar ahora
                  </button>
                  <button type="submit" class="btn btn-primary btn-sm"
                          [disabled]="form.invalid || saving">
                    <i class="bi bi-floppy me-1"></i>
                    {{ saving ? 'Guardando...' : 'Guardar' }}
                  </button>
                </div>
              </div>
            </form>
          </div>
        </div>
      </div>
    </app-shell>
  `,
  styles: [`
    .spin { animation: spin 1s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }
  `]
})
export class ConfiguracionComponent implements OnInit {
  form!: FormGroup;
  existingConfig: CopilotConfig | null = null;
  lastSync: string | null = null;
  saving  = false;
  syncing = false;
  alertMsg   = '';
  alertClass = 'alert-success';

  constructor(
    private fb: FormBuilder,
    private copilotService: CopilotService
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      tool:      ['jira',  Validators.required],
      url:       ['',      [Validators.required, Validators.pattern(/^https?:\/\/.+/)]],
      apiKey:    ['',      [Validators.required, Validators.minLength(8)]],
      frequency: ['daily', Validators.required],
      active:    [true]
    });

    this.copilotService.get().subscribe({
      next: cfg => {
        this.existingConfig = cfg;
        this.lastSync = cfg.lastSyncAt ?? null;
        this.form.patchValue({
          tool: cfg.tool, url: cfg.url, apiKey: cfg.apiKey,
          frequency: cfg.frequency, active: cfg.active
        });
      },
      error: () => {}
    });
  }

  get f() { return this.form.controls; }

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.saving = true;
    this.copilotService.save(this.form.value as CopilotConfig).subscribe({
      next: cfg => {
        this.existingConfig = cfg;
        this.lastSync = cfg.lastSyncAt ?? null;
        this.showAlert('Configuración guardada.', 'alert-success');
        this.saving = false;
      },
      error: () => { this.showAlert('Error al guardar.', 'alert-danger'); this.saving = false; }
    });
  }

  syncNow(): void {
    this.syncing = true;
    this.copilotService.syncNow().subscribe({
      next: cfg => {
        this.lastSync = cfg.lastSyncAt ?? null;
        this.showAlert('Sincronización ejecutada.', 'alert-success');
        this.syncing = false;
      },
      error: () => { this.showAlert('Error al sincronizar.', 'alert-danger'); this.syncing = false; }
    });
  }

  private showAlert(msg: string, cls: string): void {
    this.alertMsg = msg; this.alertClass = cls;
    setTimeout(() => this.alertMsg = '', 3000);
  }
}
