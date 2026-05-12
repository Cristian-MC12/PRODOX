// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ShellComponent } from '../../layout/shell/shell.component';
import { SeleccionService } from '../../services/seleccion.service';
import { MetricaSeleccionada } from '../../models/seleccion.model';

@Component({
  selector: 'app-resumen-seleccion',
  standalone: true,
  imports: [CommonModule, ShellComponent],
  template: `
    <app-shell title="Resumen de Selección">

      <p class="text-muted small mb-4">
        Estas son las métricas seleccionadas para el sprint. Cada una debe ser
        <strong>parametrizada</strong> antes de iniciar la ejecución.
        Usá el botón <i class="bi bi-pencil-square"></i> para parametrizar con GenAI.
      </p>

      <!-- Leyenda de colores -->
      <div class="d-flex gap-3 mb-3 flex-wrap">
        <span class="small">
          <span class="badge bg-danger me-1">●</span>Sin parametrizar
        </span>
        <span class="small">
          <span class="badge bg-warning text-dark me-1">●</span>Parametrizada parcialmente
        </span>
        <span class="small">
          <span class="badge bg-success me-1">●</span>Parametrizada completamente
        </span>
      </div>

      <div class="card">
        <div class="card-header d-flex justify-content-between align-items-center">
          <span class="fw-semibold small">
            <i class="bi bi-table me-1"></i>Métricas seleccionadas
          </span>
          <button class="btn btn-outline-secondary btn-sm"
                  (click)="volver()">
            <i class="bi bi-arrow-left me-1"></i>Volver a selección
          </button>
        </div>

        <div class="card-body p-0">
          @if (seleccionadas.length === 0) {
            <div class="text-center text-muted py-5">
              <i class="bi bi-inbox fs-2 d-block mb-2"></i>
              No hay métricas seleccionadas.
              <br>
              <button class="btn btn-primary btn-sm mt-3" (click)="volver()">
                Ir a selección
              </button>
            </div>
          } @else {
            <div class="table-responsive">
              <table class="table table-hover mb-0">
                <thead class="table-light">
                  <tr>
                    <th style="width:8px"></th>
                    <th>Factor</th>
                    <th>Métrica</th>
                    <th>Estado parametrización</th>
                    <th class="text-center">Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  @for (s of seleccionadas; track s.id) {
                    <tr>
                      <!-- Indicador de color -->
                      <td class="p-0">
                        <div class="h-100 rounded-start"
                             style="width:6px;min-height:48px"
                             [style.background-color]="colorEstado(s.estadoParametrizacion)">
                        </div>
                      </td>

                      <td>
                        <span class="badge mb-1" [class]="categoryBadge(s.factorCategoria)">
                          {{ s.factorCategoria }}
                        </span>
                        <div class="small fw-semibold">{{ s.factorNombre }}</div>
                      </td>

                      <td>
                        <div class="small fw-semibold">{{ s.metricaNombre }}</div>
                        <div class="text-muted" style="font-size:0.72rem">
                          {{ s.metricaDescripcion | slice:0:70 }}...
                        </div>
                        @if (s.parametrizacion) {
                          <div class="mt-1" style="font-size:0.7rem">
                            <span class="text-muted">Objetivo:</span>
                            {{ s.parametrizacion.objetivo | slice:0:60 }}...
                          </div>
                        }
                      </td>

                      <td>
                        @switch (s.estadoParametrizacion) {
                          @case ('completa') {
                            <span class="badge bg-success">
                              <i class="bi bi-check-circle me-1"></i>Completa
                            </span>
                          }
                          @case ('parcial') {
                            <span class="badge bg-warning text-dark">
                              <i class="bi bi-exclamation-circle me-1"></i>Parcial
                            </span>
                          }
                          @default {
                            <span class="badge bg-danger">
                              <i class="bi bi-x-circle me-1"></i>Sin parametrizar
                            </span>
                          }
                        }
                      </td>

                      <td class="text-center">
                        <div class="d-flex gap-1 justify-content-center">
                          <!-- Ver parametrización -->
                          @if (s.parametrizacion) {
                            <button class="btn btn-sm btn-outline-success"
                                    (click)="verParametrizacion(s)"
                                    title="Ver parametrización">
                              <i class="bi bi-eye"></i>
                            </button>
                          }
                          <!-- Editar / Parametrizar con GenAI -->
                          <button class="btn btn-sm btn-outline-primary"
                                  (click)="parametrizar(s)"
                                  title="Parametrizar con GenAI">
                            <i class="bi bi-pencil-square"></i>
                          </button>
                          <!-- Quitar -->
                          <button class="btn btn-sm btn-outline-danger"
                                  (click)="quitar(s.id)"
                                  title="Quitar métrica">
                            <i class="bi bi-trash"></i>
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

        @if (seleccionadas.length > 0) {
          <div class="card-footer d-flex justify-content-between align-items-center">
            <div class="small text-muted">
              {{ completadas }} de {{ seleccionadas.length }} métricas parametrizadas
            </div>
            <div class="d-flex gap-2">
              <button class="btn btn-outline-secondary btn-sm" (click)="volver()">
                <i class="bi bi-arrow-left me-1"></i>Editar selección
              </button>
              <button class="btn btn-success btn-sm"
                      [disabled]="completadas === 0"
                      (click)="aceptar()">
                <i class="bi bi-check-lg me-1"></i>Aceptar selección
              </button>
            </div>
          </div>
        }
      </div>

      <!-- Modal: ver parametrización -->
      @if (viendoParametrizacion) {
        <div class="modal d-block" tabindex="-1" style="background:rgba(0,0,0,.4)">
          <div class="modal-dialog modal-lg modal-dialog-centered">
            <div class="modal-content">
              <div class="modal-header">
                <h6 class="modal-title">
                  <i class="bi bi-eye me-2"></i>Parametrización — {{ viendoParametrizacion.metricaNombre }}
                </h6>
                <button type="button" class="btn-close" (click)="viendoParametrizacion = null"></button>
              </div>
              <div class="modal-body">
                <dl class="row mb-0">
                  <dt class="col-sm-3 small">Objetivo</dt>
                  <dd class="col-sm-9 small">{{ viendoParametrizacion.parametrizacion?.objetivo }}</dd>
                  <dt class="col-sm-3 small">Procedimiento</dt>
                  <dd class="col-sm-9 small">{{ viendoParametrizacion.parametrizacion?.procedimiento }}</dd>
                  <dt class="col-sm-3 small">Indicador / Variables</dt>
                  <dd class="col-sm-9 small">{{ viendoParametrizacion.parametrizacion?.indicadorVariable }}</dd>
                  <dt class="col-sm-3 small">Escala</dt>
                  <dd class="col-sm-9 small">{{ viendoParametrizacion.parametrizacion?.escala }}</dd>
                </dl>
              </div>
              <div class="modal-footer">
                <button class="btn btn-outline-primary btn-sm" (click)="parametrizar(viendoParametrizacion!); viendoParametrizacion = null">
                  <i class="bi bi-pencil-square me-1"></i>Editar con GenAI
                </button>
                <button class="btn btn-secondary btn-sm" (click)="viendoParametrizacion = null">Cerrar</button>
              </div>
            </div>
          </div>
        </div>
      }

    </app-shell>
  `
})
export class ResumenSeleccionComponent implements OnInit {
  seleccionadas: MetricaSeleccionada[] = [];
  viendoParametrizacion: MetricaSeleccionada | null = null;

  constructor(
    private seleccionService: SeleccionService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.seleccionService.getAll().subscribe(s => this.seleccionadas = s);
  }

  get completadas(): number {
    return this.seleccionadas.filter(s => s.estadoParametrizacion === 'completa').length;
  }

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

  verParametrizacion(s: MetricaSeleccionada): void {
    this.viendoParametrizacion = s;
  }

  parametrizar(s: MetricaSeleccionada): void {
    this.router.navigate(['/parametrizacion', s.id]);
  }

  quitar(id: string): void {
    this.seleccionService.quitar(id);
  }

  volver(): void {
    this.router.navigate(['/seleccion']);
  }

  aceptar(): void {
    this.router.navigate(['/verificacion']);
  }
}
