// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ShellComponent } from '../../layout/shell/shell.component';
import { EvaluacionService } from '../../services/evaluacion.service';
import { SprintService } from '../../services/sprint.service';
import { ProyectoDto } from '../../models/proyecto.model';
import { SprintDto } from '../../models/sprint.model';
import { EvaluacionSprintDto } from '../../models/planeacion.model';

interface FilaComparacion {
  variable:  string;
  categoria: string;
  valores:   (number | null)[];
  tendencia: 'up' | 'down' | 'stable' | null;
}

@Component({
  selector: 'app-evaluacion',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell title="Evaluación">

      @if (!proyecto) {
        <div class="text-center py-5 text-muted">
          <i class="bi bi-folder-x fs-1 d-block mb-3 opacity-25"></i>
          <p>Seleccioná un proyecto primero.</p>
          <button class="btn btn-primary btn-sm" (click)="router.navigate(['/proyectos'])">
            Ir a Proyectos
          </button>
        </div>
      } @else {

        <!-- Info del proyecto -->
        <div class="d-flex align-items-center gap-3 mb-3 flex-wrap">
          <div class="fw-semibold">{{ proyecto.nombre }}</div>
          <span class="badge" [class]="proyecto.metodo === 'scrum' ? 'bg-primary' : 'bg-info text-dark'">
            {{ proyecto.metodo | uppercase }}
          </span>
          <span class="text-muted small">{{ sprintsConDatos.length }} sprint(s) con datos</span>
          <button class="btn btn-sm btn-outline-primary ms-auto" (click)="cargar()" [disabled]="cargando">
            <i class="bi bi-arrow-clockwise me-1"></i>Actualizar
          </button>
        </div>

        <!-- Tabs -->
        <ul class="nav nav-tabs mb-3">
          <li class="nav-item">
            <button class="nav-link" [class.active]="tab === 'comparacion'" (click)="tab = 'comparacion'">
              <i class="bi bi-table me-1"></i>Comparación entre Sprints
            </button>
          </li>
          <li class="nav-item">
            <button class="nav-link" [class.active]="tab === 'tendencias'" (click)="tab = 'tendencias'">
              <i class="bi bi-graph-up me-1"></i>Tendencias
            </button>
          </li>
          <li class="nav-item">
            <button class="nav-link" [class.active]="tab === 'estadisticas'" (click)="tab = 'estadisticas'">
              <i class="bi bi-bar-chart me-1"></i>Estadísticas
            </button>
          </li>
        </ul>

        @if (cargando) {
          <div class="text-center py-5 text-muted small">
            <span class="spinner-border spinner-border-sm me-2"></span>Calculando evaluación...
          </div>
        } @else if (datos.length === 0) {
          <div class="text-center py-5 text-muted">
            <i class="bi bi-bar-chart fs-1 d-block mb-3 opacity-25"></i>
            <p>No hay datos registrados aún. Completá la fase de Ejecución primero.</p>
          </div>
        } @else {

          <!-- ── COMPARACIÓN ─────────────────────────────────────────── -->
          @if (tab === 'comparacion') {
            <div class="card">
              <div class="card-header fw-semibold small py-2">
                <i class="bi bi-table me-1"></i>Promedios por Variable y Sprint
              </div>
              <div class="table-responsive">
                <table class="table table-sm table-bordered mb-0">
                  <thead class="table-light">
                    <tr>
                      <th class="ps-3" style="min-width:160px">Variable</th>
                      <th style="min-width:80px">Categoría</th>
                      @for (s of sprintsConDatos; track s) {
                        <th class="text-center">Sprint {{ s }}</th>
                      }
                      <th class="text-center">Tendencia</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (fila of filasComparacion; track fila.variable) {
                      <tr>
                        <td class="ps-3 small fw-semibold align-middle">{{ fila.variable }}</td>
                        <td class="align-middle">
                          <span class="badge" style="font-size:0.62rem"
                                [class]="badgeCat(fila.categoria)">
                            {{ fila.categoria }}
                          </span>
                        </td>
                        @for (v of fila.valores; track $index) {
                          <td class="text-center align-middle fw-semibold"
                              [class.text-muted]="v === null">
                            {{ v !== null ? v : '—' }}
                          </td>
                        }
                        <td class="text-center align-middle">
                          @if (fila.tendencia === 'up') {
                            <i class="bi bi-arrow-up-circle-fill text-success fs-5"
                               title="Mejora"></i>
                          } @else if (fila.tendencia === 'down') {
                            <i class="bi bi-arrow-down-circle-fill text-danger fs-5"
                               title="Retroceso"></i>
                          } @else if (fila.tendencia === 'stable') {
                            <i class="bi bi-dash-circle-fill text-warning fs-5"
                               title="Estable"></i>
                          } @else {
                            <span class="text-muted">—</span>
                          }
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </div>
          }

          <!-- ── TENDENCIAS ──────────────────────────────────────────── -->
          @if (tab === 'tendencias') {
            <div class="row g-3">
              @for (fila of filasComparacion; track fila.variable) {
                @if (tieneValores(fila)) {
                  <div class="col-md-6">
                    <div class="card h-100">
                      <div class="card-header py-2 small d-flex justify-content-between">
                        <span class="fw-semibold">{{ fila.variable }}</span>
                        <span class="badge" [class]="badgeCat(fila.categoria)"
                              style="font-size:0.6rem">
                          {{ fila.categoria }}
                        </span>
                      </div>
                      <div class="card-body py-2">
                        <!-- Sparkline con divs -->
                        <div class="d-flex align-items-end gap-1 mb-2" style="height:60px">
                          @for (v of fila.valores; track $index) {
                            <div class="d-flex flex-column align-items-center flex-grow-1">
                              @if (v !== null) {
                                <div class="rounded-top"
                                     [style.height.px]="barHeight(v, fila.valores)"
                                     [class]="barColor(fila.tendencia)"
                                     style="width:100%;min-height:4px;transition:height .3s">
                                </div>
                              } @else {
                                <div style="height:4px;width:100%;background:#eee;border-radius:2px">
                                </div>
                              }
                            </div>
                          }
                        </div>
                        <!-- Labels sprints -->
                        <div class="d-flex gap-1">
                          @for (s of sprintsConDatos; track s) {
                            <div class="text-center text-muted flex-grow-1"
                                 style="font-size:0.65rem">S{{ s }}</div>
                          }
                        </div>
                        <!-- Valores -->
                        <div class="d-flex gap-1 mt-1">
                          @for (v of fila.valores; track $index) {
                            <div class="text-center flex-grow-1 fw-semibold"
                                 style="font-size:0.7rem"
                                 [class.text-muted]="v === null">
                              {{ v !== null ? v : '—' }}
                            </div>
                          }
                        </div>
                      </div>
                      <div class="card-footer py-1 small d-flex justify-content-between">
                        <span class="text-muted">
                          Min: <strong>{{ minValor(fila.valores) }}</strong>
                          Max: <strong>{{ maxValor(fila.valores) }}</strong>
                        </span>
                        <span>
                          @if (fila.tendencia === 'up') {
                            <span class="text-success"><i class="bi bi-arrow-up me-1"></i>Mejora</span>
                          } @else if (fila.tendencia === 'down') {
                            <span class="text-danger"><i class="bi bi-arrow-down me-1"></i>Retroceso</span>
                          } @else if (fila.tendencia === 'stable') {
                            <span class="text-warning">Estable</span>
                          }
                        </span>
                      </div>
                    </div>
                  </div>
                }
              }
            </div>
          }

          <!-- ── ESTADÍSTICAS ────────────────────────────────────────── -->
          @if (tab === 'estadisticas') {
            <div class="row g-3">
              <!-- KPIs globales -->
              <div class="col-12">
                <div class="row g-2">
                  <div class="col-6 col-md-3">
                    <div class="card text-center border-primary">
                      <div class="card-body py-2">
                        <div class="text-muted small">Variables medidas</div>
                        <div class="fs-3 fw-bold text-primary">{{ filasComparacion.length }}</div>
                      </div>
                    </div>
                  </div>
                  <div class="col-6 col-md-3">
                    <div class="card text-center border-success">
                      <div class="card-body py-2">
                        <div class="text-muted small">Sprints con datos</div>
                        <div class="fs-3 fw-bold text-success">{{ sprintsConDatos.length }}</div>
                      </div>
                    </div>
                  </div>
                  <div class="col-6 col-md-3">
                    <div class="card text-center border-warning">
                      <div class="card-body py-2">
                        <div class="text-muted small">Mejoras detectadas</div>
                        <div class="fs-3 fw-bold text-warning">{{ totalMejoras }}</div>
                      </div>
                    </div>
                  </div>
                  <div class="col-6 col-md-3">
                    <div class="card text-center border-danger">
                      <div class="card-body py-2">
                        <div class="text-muted small">Retrocesos</div>
                        <div class="fs-3 fw-bold text-danger">{{ totalRetrocesos }}</div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Por categoría -->
              @for (cat of categorias; track cat) {
                @if (filasPorCategoria(cat).length > 0) {
                  <div class="col-md-6">
                    <div class="card h-100">
                      <div class="card-header py-2 small d-flex align-items-center gap-2">
                        <span class="badge" [class]="badgeCat(cat)">{{ cat }}</span>
                        <span class="fw-semibold">{{ filasPorCategoria(cat).length }} variable(s)</span>
                      </div>
                      <div class="card-body py-2">
                        @for (fila of filasPorCategoria(cat); track fila.variable) {
                          <div class="d-flex justify-content-between align-items-center mb-2">
                            <span class="small">{{ fila.variable }}</span>
                            <div class="d-flex align-items-center gap-2">
                              <div class="progress" style="width:100px;height:8px">
                                <div class="progress-bar"
                                     [class]="'bg-' + progressColor(fila)"
                                     [style.width.%]="progressPct(fila)">
                                </div>
                              </div>
                              <span class="small fw-semibold" style="width:35px;text-align:right">
                                {{ ultimoValor(fila) ?? '—' }}
                              </span>
                              @if (fila.tendencia === 'up') {
                                <i class="bi bi-arrow-up text-success"></i>
                              } @else if (fila.tendencia === 'down') {
                                <i class="bi bi-arrow-down text-danger"></i>
                              } @else {
                                <i class="bi bi-dash text-muted"></i>
                              }
                            </div>
                          </div>
                        }
                      </div>
                    </div>
                  </div>
                }
              }
            </div>
          }

        }
      }
    </app-shell>
  `
})
export class EvaluacionComponent implements OnInit {
  proyecto: ProyectoDto | null = null;
  datos:    EvaluacionSprintDto[] = [];
  sprints:  SprintDto[] = [];
  tab: 'comparacion' | 'tendencias' | 'estadisticas' = 'comparacion';
  cargando = true;

  readonly categorias = ['Significado', 'Flexibilidad', 'Impacto', 'Socio-Humano FSH'];

  constructor(
    public  router: Router,
    private evaluacionService: EvaluacionService,
    private sprintService: SprintService
  ) {}

  get sprintsConDatos(): number[] {
    return [...new Set(this.datos.map(d => d.sprintNumero))].sort((a, b) => a - b);
  }

  get totalMejoras()   { return this.filasComparacion.filter(f => f.tendencia === 'up').length; }
  get totalRetrocesos(){ return this.filasComparacion.filter(f => f.tendencia === 'down').length; }

  get filasComparacion(): FilaComparacion[] {
    const variables = [...new Set(this.datos.map(d => d.variableNombre))];
    return variables.map(nombre => {
      const cat = this.datos.find(d => d.variableNombre === nombre)?.categoria ?? '';
      const valores = this.sprintsConDatos.map(num => {
        const d = this.datos.find(d => d.variableNombre === nombre && d.sprintNumero === num);
        return d ? Number(d.promedio) : null;
      });
      return { variable: nombre, categoria: cat, valores, tendencia: this.calcTendencia(valores) };
    });
  }

  filasPorCategoria(cat: string): FilaComparacion[] {
    return this.filasComparacion.filter(f => f.categoria === cat);
  }

  tieneValores(fila: FilaComparacion): boolean {
    return fila.valores.some(v => v !== null);
  }

  ngOnInit(): void {
    try {
      const p = localStorage.getItem('mpdia_proyecto_activo');
      this.proyecto = p ? JSON.parse(p) : null;
    } catch { /* ignore */ }
    if (this.proyecto) this.cargar();
  }

  cargar(): void {
    if (!this.proyecto) return;
    this.cargando = true;
    this.evaluacionService.porProyecto(this.proyecto.id).pipe(
      catchError(() => of([]))
    ).subscribe(d => {
      this.datos   = d;
      this.cargando = false;
    });
  }

  private calcTendencia(valores: (number | null)[]): 'up' | 'down' | 'stable' | null {
    const reales = valores.filter((v): v is number => v !== null);
    if (reales.length < 2) return null;
    const ultimo = reales[reales.length - 1];
    const penultimo = reales[reales.length - 2];
    const diff = ultimo - penultimo;
    if (Math.abs(diff) < 0.01) return 'stable';
    return diff > 0 ? 'up' : 'down';
  }

  barHeight(v: number, valores: (number | null)[]): number {
    const reales = valores.filter((x): x is number => x !== null);
    const max = Math.max(...reales);
    if (max === 0) return 4;
    return Math.max(4, Math.round((v / max) * 56));
  }

  barColor(t: 'up' | 'down' | 'stable' | null): string {
    if (t === 'up')   return 'bg-success';
    if (t === 'down') return 'bg-danger';
    return 'bg-primary';
  }

  minValor(valores: (number | null)[]): number | null {
    const r = valores.filter((v): v is number => v !== null);
    return r.length ? Math.min(...r) : null;
  }

  maxValor(valores: (number | null)[]): number | null {
    const r = valores.filter((v): v is number => v !== null);
    return r.length ? Math.max(...r) : null;
  }

  ultimoValor(fila: FilaComparacion): number | null {
    const r = fila.valores.filter((v): v is number => v !== null);
    return r.length ? r[r.length - 1] : null;
  }

  progressPct(fila: FilaComparacion): number {
    const v = this.ultimoValor(fila);
    const max = this.maxValor(fila.valores);
    if (v === null || !max) return 0;
    return Math.round((v / max) * 100);
  }

  progressColor(fila: FilaComparacion): string {
    if (fila.tendencia === 'up')   return 'success';
    if (fila.tendencia === 'down') return 'danger';
    return 'primary';
  }

  badgeCat(cat: string): string {
    const map: Record<string, string> = {
      'Significado':      'bg-primary',
      'Flexibilidad':     'bg-warning text-dark',
      'Impacto':          'bg-danger',
      'Socio-Humano FSH': 'bg-info text-dark'
    };
    return map[cat] ?? 'bg-secondary';
  }
}
