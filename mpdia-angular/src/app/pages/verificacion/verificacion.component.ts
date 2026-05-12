// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../layout/shell/shell.component';
import { SeleccionService } from '../../services/seleccion.service';
import { MetricaSeleccionada } from '../../models/seleccion.model';

@Component({
  selector: 'app-verificacion',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell title="Verificación del Scrum Master">

      <p class="text-muted small mb-4">
        El <strong>Scrum Master</strong> verifica que todas las métricas seleccionadas
        estén parametrizadas antes de iniciar la ejecución del sprint.
      </p>

      <!-- KPIs -->
      <div class="row g-3 mb-4">
        <div class="col-md-3">
          <div class="card text-center kpi-card">
            <div class="card-body py-3">
              <div class="kpi-label">Métricas seleccionadas</div>
              <div class="kpi-value text-primary">{{ total }}</div>
            </div>
          </div>
        </div>
        <div class="col-md-3">
          <div class="card text-center kpi-card">
            <div class="card-body py-3">
              <div class="kpi-label">Parametrizadas</div>
              <div class="kpi-value text-success">{{ completadas }}</div>
            </div>
          </div>
        </div>
        <div class="col-md-3">
          <div class="card text-center kpi-card">
            <div class="card-body py-3">
              <div class="kpi-label">Sin parametrizar</div>
              <div class="kpi-value text-danger">{{ sinParametrizar }}</div>
            </div>
          </div>
        </div>
        <div class="col-md-3">
          <div class="card text-center kpi-card">
            <div class="card-body py-3">
              <div class="kpi-label">Estado planeación</div>
              <div class="kpi-value" style="font-size:1rem"
                   [class]="planningReady ? 'text-success' : 'text-warning'">
                {{ planningReady ? '✓ Lista' : '⚠ Incompleta' }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Checklist -->
      <div class="card mb-4">
        <div class="card-header fw-semibold small">
          <i class="bi bi-clipboard-check me-1"></i>Checklist de planeación
        </div>
        <ul class="list-group list-group-flush">
          <li class="list-group-item d-flex align-items-center gap-3">
            <i class="bi fs-5" [class]="total > 0 ? 'bi-check-circle-fill text-success' : 'bi-x-circle-fill text-danger'"></i>
            <div class="flex-grow-1">
              <div class="small fw-semibold">Factores y métricas seleccionados</div>
              <div class="text-muted" style="font-size:0.75rem">
                {{ total > 0 ? total + ' métrica(s) seleccionada(s)' : 'No hay métricas seleccionadas' }}
              </div>
            </div>
            @if (total === 0) {
              <a routerLink="/seleccion" class="btn btn-sm btn-outline-primary">Seleccionar</a>
            }
          </li>

          <li class="list-group-item d-flex align-items-center gap-3">
            <i class="bi fs-5" [class]="completadas === total && total > 0 ? 'bi-check-circle-fill text-success' : 'bi-exclamation-circle-fill text-warning'"></i>
            <div class="flex-grow-1">
              <div class="small fw-semibold">Métricas parametrizadas</div>
              <div class="text-muted" style="font-size:0.75rem">
                @if (total === 0) {
                  Sin métricas para parametrizar
                } @else if (completadas === total) {
                  Todas las métricas están parametrizadas
                } @else {
                  {{ sinParametrizar }} métrica(s) sin parametrizar
                }
              </div>
            </div>
            @if (sinParametrizar > 0) {
              <a routerLink="/resumen-seleccion" class="btn btn-sm btn-outline-warning">Parametrizar</a>
            }
          </li>
        </ul>
      </div>

      <!-- Tabla de métricas -->
      <div class="card mb-4">
        <div class="card-header fw-semibold small">
          <i class="bi bi-table me-1"></i>Estado por métrica
        </div>
        <div class="card-body p-0">
          @if (seleccionadas.length === 0) {
            <div class="text-center py-4 text-muted small">No hay métricas seleccionadas.</div>
          } @else {
            <div class="table-responsive">
              <table class="table table-hover mb-0">
                <thead class="table-light">
                  <tr>
                    <th style="width:6px"></th>
                    <th>Factor</th>
                    <th>Métrica</th>
                    <th>Parametrización</th>
                    <th>Estado</th>
                  </tr>
                </thead>
                <tbody>
                  @for (s of seleccionadas; track s.id) {
                    <tr>
                      <td class="p-0">
                        <div style="width:6px;min-height:48px;border-radius:3px 0 0 3px"
                             [style.background-color]="colorEstado(s.estadoParametrizacion)">
                        </div>
                      </td>
                      <td>
                        <span class="badge" [class]="categoryBadge(s.factorCategoria)" style="font-size:0.65rem">
                          {{ s.factorCategoria }}
                        </span>
                        <div class="small">{{ s.factorNombre }}</div>
                      </td>
                      <td class="small fw-semibold">{{ s.metricaNombre }}</td>
                      <td class="small text-muted">
                        @if (s.parametrizacion?.objetivo) {
                          {{ s.parametrizacion!.objetivo | slice:0:60 }}...
                        } @else {
                          <span class="text-danger">Sin parametrizar</span>
                        }
                      </td>
                      <td>
                        @switch (s.estadoParametrizacion) {
                          @case ('completa') {
                            <span class="badge bg-success">Completa</span>
                          }
                          @case ('parcial') {
                            <span class="badge bg-warning text-dark">Parcial</span>
                          }
                          @default {
                            <span class="badge bg-danger">Sin parametrizar</span>
                          }
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>
      </div>

      @if (planningReady) {
        <div class="alert alert-success d-flex align-items-center gap-3">
          <i class="bi bi-check-circle-fill fs-4"></i>
          <div>
            <strong>Planeación completada.</strong><br>
            <small>
              Todas las métricas están seleccionadas y parametrizadas.
              El sprint puede iniciar la fase de ejecución.
            </small>
          </div>
        </div>
      }

    </app-shell>
  `
})
export class VerificacionComponent implements OnInit {
  seleccionadas: MetricaSeleccionada[] = [];

  constructor(private seleccionService: SeleccionService) {}

  ngOnInit(): void {
    this.seleccionService.getAll().subscribe(s => this.seleccionadas = s);
  }

  get total()            { return this.seleccionadas.length; }
  get completadas()      { return this.seleccionadas.filter(s => s.estadoParametrizacion === 'completa').length; }
  get sinParametrizar()  { return this.seleccionadas.filter(s => s.estadoParametrizacion === 'sin_parametrizar').length; }
  get planningReady()    { return this.total > 0 && this.completadas === this.total; }

  colorEstado(estado: string): string {
    const map: Record<string, string> = {
      'sin_parametrizar': '#dc3545',
      'parcial':          '#ffc107',
      'completa':         '#198754'
    };
    return map[estado] ?? '#6c757d';
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
}
