// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { ShellComponent } from '../../layout/shell/shell.component';
import { SeleccionService } from '../../services/seleccion.service';
import { MetricRankingService } from '../../services/metric-ranking.service';
import { MetricaSeleccionada } from '../../models/seleccion.model';

@Component({
  selector: 'app-resumen-seleccion',
  standalone: true,
  imports: [CommonModule, ShellComponent],
  template: `
    <app-shell title="Resumen de Selección">

      <!-- Breadcrumb de navegación -->
      <nav aria-label="breadcrumb" class="mb-3">
        <ol class="breadcrumb small mb-0">
          <li class="breadcrumb-item">
            <a href="#" (click)="$event.preventDefault(); router.navigate(['/planeacion'])">
              <i class="bi bi-layers me-1"></i>Planeación
            </a>
          </li>
          <li class="breadcrumb-item active">Resumen de parametrización</li>
        </ol>
      </nav>

      <p class="text-muted small mb-4">
        Estas son las métricas seleccionadas para el sprint. Parametrizá cada una con GenAI
        y luego enviá al Scrum Master para verificación.
      </p>

      <!-- Leyenda de colores -->
      <div class="d-flex gap-3 mb-3 flex-wrap">
        <span class="small"><span class="badge bg-danger me-1">●</span>Sin parametrizar</span>
        <span class="small"><span class="badge bg-warning text-dark me-1">●</span>Parcial</span>
        <span class="small"><span class="badge bg-success me-1">●</span>Completa</span>
      </div>

      <div class="card">
        <div class="card-header d-flex justify-content-between align-items-center">
          <span class="fw-semibold small">
            <i class="bi bi-table me-1"></i>Métricas seleccionadas
          </span>
          <button class="btn btn-outline-secondary btn-sm" (click)="router.navigate(['/planeacion'])">
            <i class="bi bi-arrow-left me-1"></i>Volver a Planeación
          </button>
        </div>

        <div class="card-body p-0">
          @if (seleccionadas.length === 0) {
            <div class="text-center text-muted py-5">
              <i class="bi bi-inbox fs-2 d-block mb-2"></i>
              No hay métricas seleccionadas.
              <br>
              <button class="btn btn-primary btn-sm mt-3" (click)="router.navigate(['/planeacion'])">
                Ir a Planeación
              </button>
            </div>
          } @else {
            <div class="table-responsive">
              <table class="table table-hover mb-0">
                <thead class="table-light">
                  <tr>
                    <th style="width:8px"></th>
                    <th>Métrica</th>
                    <th>Estado</th>
                    <th class="text-center">Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  @for (s of seleccionadas; track s.id) {
                    <tr>
                      <td class="p-0">
                        <div class="h-100 rounded-start"
                             style="width:6px;min-height:48px"
                             [style.background-color]="colorEstado(s.estadoParametrizacion)">
                        </div>
                      </td>
                      <td>
                        <span class="badge mb-1" [class]="categoryBadge(s.factorCategoria)"
                              style="font-size:0.62rem">
                          {{ s.factorCategoria }}
                        </span>
                        <div class="small fw-semibold">{{ s.metricaNombre }}</div>
                        @if (s.parametrizacion) {
                          <div class="text-muted" style="font-size:0.7rem">
                            {{ s.parametrizacion.objetivo | slice:0:60 }}...
                          </div>
                        }
                      </td>
                      <td class="align-middle">
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
                      <td class="text-center align-middle">
                        <div class="d-flex gap-1 justify-content-center">
                          @if (s.estadoParametrizacion !== 'sin_parametrizar') {
                            <button class="btn btn-sm btn-outline-info py-0 px-2"
                                    (click)="toggleDetalle(s.id)"
                                    title="Ver detalle de parametrización">
                              <i class="bi" [class.bi-eye]="!esDetalleVisible(s.id)" [class.bi-eye-slash]="esDetalleVisible(s.id)"></i>
                            </button>
                          }
                          <button class="btn btn-sm btn-outline-primary py-0 px-2"
                                  (click)="parametrizar(s)"
                                  [title]="s.estadoParametrizacion === 'sin_parametrizar' ? 'Parametrizar con GenAI' : 'Editar parametrización'">
                            <i class="bi bi-stars"></i>
                          </button>
                          <button class="btn btn-sm btn-outline-danger py-0 px-2"
                                  (click)="quitar(s.id)"
                                  title="Quitar">
                            <i class="bi bi-trash"></i>
                          </button>
                        </div>
                      </td>
                    </tr>
                    <!-- Fila expandible con detalle de parametrización -->
                    @if (esDetalleVisible(s.id) && s.parametrizacion) {
                      <tr class="table-active">
                        <td colspan="4" class="py-3 px-4">
                          <div class="small">
                            <div class="mb-3">
                              <strong class="text-primary"><i class="bi bi-bullseye me-1"></i>Objetivo:</strong>
                              <p class="mb-0 mt-1 text-muted">{{ s.parametrizacion.objetivo }}</p>
                            </div>
                            <div class="mb-3">
                              <strong class="text-primary"><i class="bi bi-list-ol me-1"></i>Procedimiento:</strong>
                              <p class="mb-0 mt-1 text-muted">{{ s.parametrizacion.procedimiento }}</p>
                            </div>
                            <div class="mb-3">
                              <strong class="text-primary"><i class="bi bi-speedometer2 me-1"></i>Indicador/Variable:</strong>
                              <p class="mb-0 mt-1 text-muted">{{ s.parametrizacion.indicadorVariable }}</p>
                            </div>
                            <div>
                              <strong class="text-primary"><i class="bi bi-bar-chart-steps me-1"></i>Escala:</strong>
                              <p class="mb-0 mt-1 text-muted">{{ s.parametrizacion.escala }}</p>
                            </div>
                          </div>
                        </td>
                      </tr>
                    }
                  }
                </tbody>
              </table>
            </div>
          }
        </div>

        @if (seleccionadas.length > 0) {
          <div class="card-footer d-flex justify-content-between align-items-center flex-wrap gap-2">
            <div class="small text-muted">
              {{ completadas }} de {{ seleccionadas.length }} parametrizadas
            </div>
            <div class="d-flex gap-2">
              <button class="btn btn-outline-secondary btn-sm" (click)="router.navigate(['/planeacion'])">
                <i class="bi bi-arrow-left me-1"></i>Editar selección
              </button>
              <button class="btn btn-primary btn-sm"
                      [disabled]="completadas === 0 || enviando"
                      (click)="aceptar()">
                @if (enviando) {
                  <span class="spinner-border spinner-border-sm me-1"></span>
                } @else {
                  <i class="bi bi-send me-1"></i>
                }
                Enviar al Scrum Master
              </button>
            </div>
          </div>
        }
      </div>

      @if (errorMsg) {
        <div class="alert alert-danger small mt-3 py-2">{{ errorMsg }}</div>
      }

    </app-shell>
  `
})
export class ResumenSeleccionComponent implements OnInit {
  seleccionadas: MetricaSeleccionada[] = [];
  enviando  = false;
  errorMsg  = '';
  detallesVisibles = new Set<string>(); // IDs de métricas con detalle visible

  constructor(
    public  router: Router,
    private seleccionService: SeleccionService,
    private rankingService: MetricRankingService
  ) {}

  ngOnInit(): void {
    this.seleccionService.getAll().subscribe(s => this.seleccionadas = s);
  }

  get completadas(): number {
    return this.seleccionadas.filter(s => s.estadoParametrizacion === 'completa').length;
  }

  parametrizar(s: MetricaSeleccionada): void {
    this.router.navigate(['/parametrizacion', s.id]);
  }

  quitar(id: string): void {
    this.seleccionService.quitar(id);
  }

  toggleDetalle(id: string): void {
    if (this.detallesVisibles.has(id)) {
      this.detallesVisibles.delete(id);
    } else {
      this.detallesVisibles.add(id);
    }
  }

  esDetalleVisible(id: string): boolean {
    return this.detallesVisibles.has(id);
  }

  /** Guarda todas las parametrizaciones completas en el backend y navega a verificación */
  aceptar(): void {
    const proyectoActivo = localStorage.getItem('mpdia_proyecto_activo');
    const proyectoId: string | null = proyectoActivo ? (JSON.parse(proyectoActivo)?.id ?? null) : null;

    const completas = this.seleccionadas.filter(s =>
      s.estadoParametrizacion === 'completa' && s.parametrizacion
    );

    if (completas.length === 0 || !proyectoId) return;

    this.enviando = true;
    this.errorMsg = '';

    const guardados$ = completas.map(s =>
      this.rankingService.guardar({
        factorId:          null,
        objetivo:          s.parametrizacion!.objetivo,
        procedimiento:     s.parametrizacion!.procedimiento,
        indicadorVariable: s.parametrizacion!.indicadorVariable,
        escala:            s.parametrizacion!.escala,
        metricaBaseId:     null,
        proyectoId,
        metricaId:         s.factorId   // desde Planeación, factorId contiene el metricaId
      }).pipe(catchError(err => { this.errorMsg = 'Error al guardar: ' + (err?.error?.error ?? err?.message ?? 'desconocido'); return of(null); }))
    );

    forkJoin(guardados$).subscribe(() => {
      this.enviando = false;
      this.seleccionService.limpiar(); // Limpiar localStorage
      // Navegar a la siguiente fase
      this.router.navigate(['/planeacion']);
    });
  }

  colorEstado(estado: string): string {
    return ({ sin_parametrizar: '#dc3545', parcial: '#ffc107', completa: '#198754' } as Record<string, string>)[estado] ?? '#6c757d';
  }

  categoryBadge(cat: string): string {
    const map: Record<string, string> = {
      'Significado':      'bg-primary',
      'Flexibilidad':     'bg-warning text-dark',
      'Impacto':          'bg-danger',
      'Socio-Humano FSH': 'bg-info text-dark'
    };
    return map[cat] ?? 'bg-secondary';
  }
}
