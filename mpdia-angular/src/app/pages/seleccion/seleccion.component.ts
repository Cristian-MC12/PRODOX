// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ShellComponent } from '../../layout/shell/shell.component';
import { FactorService } from '../../services/factor.service';
import { SeleccionService } from '../../services/seleccion.service';
import { Factor } from '../../models/factor.model';

@Component({
  selector: 'app-seleccion',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell title="Selección de Métricas">

      <p class="text-muted small mb-3">
        Seleccioná las métricas que vas a medir en este sprint y movelas al panel derecho.
      </p>

      @if (alertMsg) {
        <div class="alert py-2 small" [class]="alertClass">{{ alertMsg }}</div>
      }

      <!-- Dropdown de Factor -->
      <div class="mb-3" style="max-width:280px">
        <label class="form-label small fw-semibold">Factor</label>
        @if (cargando) {
          <div class="d-flex align-items-center gap-2 text-muted small">
            <span class="spinner-border spinner-border-sm"></span> Cargando...
          </div>
        } @else {
          <select class="form-select form-select-sm" [(ngModel)]="factorSeleccionado"
                  (ngModelChange)="onFactorChange()">
            <option value="">Seleccionar factor...</option>
            @for (f of factores; track f) {
              <option [value]="f">{{ f }}</option>
            }
          </select>
        }
      </div>

      <!-- Dos paneles + flechas -->
      <div class="d-flex align-items-start gap-3">

        <!-- Panel izquierdo: métricas del factor -->
        <div class="card flex-grow-1" style="min-width:0">
          <div class="card-header small fw-semibold py-2">
            <i class="bi bi-bar-chart-line me-1"></i>Métricas disponibles
            @if (factorSeleccionado) {
              <span class="badge ms-2" [class]="categoryBadge(factorSeleccionado)">
                {{ factorSeleccionado }}
              </span>
            }
          </div>
          <div style="height:320px;overflow-y:auto">
            @if (!factorSeleccionado) {
              <div class="text-center text-muted py-5 small">
                <i class="bi bi-arrow-up fs-3 d-block mb-2 opacity-25"></i>
                Seleccioná un factor para ver sus métricas.
              </div>
            } @else if (metricasDisponibles.length === 0) {
              <div class="text-center text-muted py-5 small">
                <i class="bi bi-inbox fs-3 d-block mb-2 opacity-25"></i>
                No hay métricas para este factor.
              </div>
            } @else {
              <table class="table table-sm table-hover mb-0">
                <thead class="table-light sticky-top">
                  <tr>
                    <th class="ps-3">Métrica</th>
                    <th style="width:32px"></th>
                  </tr>
                </thead>
                <tbody>
                  @for (m of metricasDisponibles; track m.id) {
                    <tr [class.table-secondary]="yaSeleccionada(m)"
                        style="cursor:pointer"
                        (click)="!yaSeleccionada(m) && mover(m)">
                      <td class="ps-3">
                        <div class="small fw-semibold">{{ m.name }}</div>
                        <div class="text-muted" style="font-size:0.71rem">
                          {{ m.description | slice:0:65 }}...
                        </div>
                      </td>
                      <td class="text-center align-middle">
                        @if (yaSeleccionada(m)) {
                          <i class="bi bi-check-circle-fill text-success"></i>
                        } @else {
                          <i class="bi bi-plus-circle text-primary opacity-50"></i>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          </div>
        </div>

        <!-- Flechas centrales -->
        <div class="d-flex flex-column align-items-center justify-content-center gap-2"
             style="padding-top:120px;min-width:44px">
          <button class="btn btn-outline-primary btn-sm px-3"
                  [disabled]="metricasDisponibles.length === 0"
                  (click)="moverTodas()"
                  title="Agregar todas">
            <i class="bi bi-chevron-right"></i>
          </button>
          <button class="btn btn-outline-secondary btn-sm px-3"
                  [disabled]="seleccionadas.length === 0"
                  (click)="quitarTodas()"
                  title="Quitar todas">
            <i class="bi bi-chevron-left"></i>
          </button>
        </div>

        <!-- Panel derecho: métricas seleccionadas -->
        <div class="card flex-grow-1" style="min-width:0">
          <div class="card-header small fw-semibold py-2 d-flex justify-content-between align-items-center">
            <span><i class="bi bi-check2-square me-1"></i>Métricas seleccionadas</span>
            <span class="badge bg-primary rounded-pill">{{ seleccionadas.length }}</span>
          </div>
          <div style="height:320px;overflow-y:auto">
            @if (seleccionadas.length === 0) {
              <div class="text-center text-muted py-5 small">
                <i class="bi bi-arrow-left-circle fs-3 d-block mb-2 opacity-25"></i>
                Hacé click en una métrica para agregarla.
              </div>
            } @else {
              <table class="table table-sm table-hover mb-0">
                <thead class="table-light sticky-top">
                  <tr>
                    <th class="ps-3">Métrica</th>
                    <th>Factor</th>
                    <th style="width:32px"></th>
                  </tr>
                </thead>
                <tbody>
                  @for (s of seleccionadas; track s.id) {
                    <tr>
                      <td class="ps-3">
                        <div class="small fw-semibold">{{ s.metricaNombre }}</div>
                      </td>
                      <td>
                        <span class="badge" [class]="categoryBadge(s.factorCategoria)"
                              style="font-size:0.65rem">
                          {{ s.factorCategoria }}
                        </span>
                      </td>
                      <td class="text-center align-middle">
                        <button class="btn btn-sm btn-outline-danger py-0 px-1"
                                (click)="quitar(s.id)" title="Quitar">
                          <i class="bi bi-x"></i>
                        </button>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          </div>
          <!-- Botón Seleccionar -->
          @if (seleccionadas.length > 0) {
            <div class="card-footer d-flex justify-content-end py-2">
              <button class="btn btn-primary btn-sm" (click)="continuar()">
                Seleccionar <i class="bi bi-arrow-right ms-1"></i>
              </button>
            </div>
          }
        </div>

      </div>
    </app-shell>
  `
})
export class SeleccionComponent implements OnInit {
  /** Todos los registros del backend */
  todas: Factor[]        = [];
  /** Métricas del factor actualmente seleccionado */
  metricasDisponibles: Factor[] = [];
  seleccionadas          = this.seleccionService.getSnapshot();
  factorSeleccionado     = '';
  cargando               = true;
  alertMsg   = '';
  alertClass = 'alert-success';

  constructor(
    private factorService: FactorService,
    private seleccionService: SeleccionService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.factorService.list().pipe(
      catchError(err => {
        if (err.status === 401 || err.status === 403) {
          this.showAlert('Sesión expirada. Iniciá sesión nuevamente.', 'alert-warning');
          setTimeout(() => this.router.navigate(['/auth']), 2000);
        } else {
          this.showAlert('Error al cargar las métricas. Verificá la conexión.', 'alert-danger');
        }
        return of([]);
      })
    ).subscribe(data => {
      this.todas   = data;
      this.cargando = false;
    });

    this.seleccionService.getAll().subscribe(s => this.seleccionadas = s);
  }

  /** Lista de factores únicos (categorías) para el dropdown */
  get factores(): string[] {
    return [...new Set(this.todas.map(m => m.category))].sort();
  }

  onFactorChange(): void {
    this.metricasDisponibles = this.todas.filter(m => m.category === this.factorSeleccionado);
  }

  yaSeleccionada(m: Factor): boolean {
    return this.seleccionadas.some(s => s.factorId === m.id);
  }

  mover(m: Factor): void {
    this.seleccionService.agregar({
      factorId:           m.id,
      factorNombre:       m.name,
      factorCategoria:    m.category,
      metricaNombre:      m.name,
      metricaDescripcion: m.description
    });
  }

  moverTodas(): void {
    this.metricasDisponibles.forEach(m => {
      if (!this.yaSeleccionada(m)) this.mover(m);
    });
  }

  quitar(id: string): void {
    this.seleccionService.quitar(id);
  }

  quitarTodas(): void {
    [...this.seleccionadas].forEach(s => this.seleccionService.quitar(s.id));
  }

  continuar(): void {
    this.router.navigate(['/resumen-seleccion']);
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
