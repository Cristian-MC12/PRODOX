// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { catchError, of } from 'rxjs';
import { ShellComponent } from '../../layout/shell/shell.component';
import { SeleccionService } from '../../services/seleccion.service';
import { MetricRankingService } from '../../services/metric-ranking.service';
import { MetricaSeleccionada, Parametrizacion, PropuestaGenAI } from '../../models/seleccion.model';
import { MetricParametrizacionBase, TopParametrizacion } from '../../models/metric-ranking.model';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-parametrizacion',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell title="Parametrización de Métrica">

      @if (!metrica) {
        <div class="text-center py-5 text-muted">Cargando...</div>
      } @else {

        <!-- Breadcrumb de navegación -->
        <nav aria-label="breadcrumb" class="mb-3">
          <ol class="breadcrumb small mb-0">
            <li class="breadcrumb-item">
              <a href="#" (click)="$event.preventDefault(); router.navigate(['/planeacion'])">
                <i class="bi bi-layers me-1"></i>Planeación
              </a>
            </li>
            <li class="breadcrumb-item">
              <a href="#" (click)="$event.preventDefault(); volver()">Resumen</a>
            </li>
            <li class="breadcrumb-item active">{{ metrica.metricaNombre }}</li>
          </ol>
        </nav>

        <!-- Info de la métrica -->
        <div class="card mb-4 border-primary">
          <div class="card-body py-3">
            <div class="row align-items-center">
              <div class="col-md-6">
                <div class="text-muted small">Factor</div>
                <div class="fw-semibold">{{ metrica.factorNombre }}</div>
                <span class="badge" [class]="categoryBadge(metrica.factorCategoria)">
                  {{ metrica.factorCategoria }}
                </span>
              </div>
              <div class="col-md-6 mt-2 mt-md-0">
                <div class="text-muted small">Métrica</div>
                <div class="fw-semibold">{{ metrica.metricaNombre }}</div>
                <div class="text-muted small">{{ metrica.metricaDescripcion }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- Ranking Top 3 de esta métrica -->
        @if (top3.length > 0) {
          <div class="card mb-4">
            <div class="card-header d-flex align-items-center gap-2">
              <i class="bi bi-trophy-fill text-warning"></i>
              <span class="fw-semibold small">Top {{ top3.length }} parametrizaciones más usadas para esta métrica</span>
            </div>
            <div class="card-body p-0">
              <table class="table table-sm table-hover mb-0">
                <thead class="table-light">
                  <tr>
                    <th class="ps-3" style="width:40px">#</th>
                    <th>Objetivo</th>
                    <th>Autor</th>
                    <th class="text-center" style="width:80px">Usos</th>
                    <th style="width:120px"></th>
                  </tr>
                </thead>
                <tbody>
                  @for (t of top3; track t.id; let i = $index) {
                    <tr>
                      <td class="ps-3 align-middle">
                        <span class="badge rounded-pill"
                              [class]="i === 0 ? 'bg-warning text-dark' : i === 1 ? 'bg-secondary' : 'bg-secondary'"
                              style="font-size:0.7rem;min-width:22px">
                          {{ i + 1 }}
                        </span>
                      </td>
                      <td class="align-middle">
                        <div class="small fw-semibold">{{ t.objetivo | slice:0:70 }}...</div>
                        <div class="text-muted" style="font-size:0.7rem">
                          Escala: {{ t.escala }}
                        </div>
                      </td>
                      <td class="align-middle small text-muted">{{ t.userEmail }}</td>
                      <td class="text-center align-middle">
                        <span class="badge bg-primary rounded-pill">{{ t.usos }}</span>
                      </td>
                      <td class="align-middle text-end pe-3">
                        <button class="btn btn-sm btn-outline-primary py-0"
                                (click)="usarDelTop(t)">
                          <i class="bi bi-clipboard-check me-1"></i>Usar
                        </button>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        }

        <!-- Parametrización base de otro usuario (solo lectura) -->
        @if (parametrizacionBase && top3.length === 0) {
          <div class="card mb-4 border-info">
            <div class="card-header d-flex align-items-center gap-2 bg-info bg-opacity-10">
              <i class="bi bi-person-check text-info fs-5"></i>
              <div>
                <div class="fw-semibold small">Parametrización de referencia</div>
                <div class="text-muted" style="font-size:0.75rem">
                  Creada por <strong>{{ parametrizacionBase.userEmail }}</strong>
                  el {{ parametrizacionBase.createdAt | date:'dd/MM/yyyy' }}.
                  Podés usarla como base o crear la tuya propia.
                </div>
              </div>
              <button class="btn btn-sm btn-outline-info ms-auto"
                      (click)="usarBase()">
                <i class="bi bi-clipboard-check me-1"></i>Usar como base
              </button>
            </div>
            <div class="card-body py-2">
              <dl class="row mb-0 small">
                <dt class="col-sm-3 text-muted">Objetivo</dt>
                <dd class="col-sm-9">{{ parametrizacionBase.objetivo }}</dd>
                <dt class="col-sm-3 text-muted">Procedimiento</dt>
                <dd class="col-sm-9">{{ parametrizacionBase.procedimiento }}</dd>
                <dt class="col-sm-3 text-muted">Indicador / Variables</dt>
                <dd class="col-sm-9">{{ parametrizacionBase.indicadorVariable }}</dd>
                <dt class="col-sm-3 text-muted">Escala</dt>
                <dd class="col-sm-9 mb-0">{{ parametrizacionBase.escala }}</dd>
              </dl>
            </div>
          </div>
        }

        <!-- Botón GenAI -->
        <div class="card mb-4">
          <div class="card-header d-flex align-items-center gap-2">
            <i class="bi bi-robot text-primary fs-5"></i>
            <div>
              <div class="fw-semibold small">GenAI — Proponer proceso de medición</div>
              <div class="text-muted" style="font-size:0.75rem">
                La IA generará 3 propuestas de parametrización para esta métrica.
                Elegí la que mejor se adapte a tu equipo.
              </div>
            </div>
          </div>
          <div class="card-body">
            <button class="btn btn-primary"
                    [disabled]="generando"
                    (click)="generarPropuestas()">
              @if (generando) {
                <span class="spinner-border spinner-border-sm me-2"></span>
                Generando propuestas...
              } @else {
                <i class="bi bi-stars me-2"></i>
                Generar 3 propuestas con GenAI
              }
            </button>
            @if (errorGenAI) {
              <div class="alert alert-danger small mt-2 mb-0 py-2">{{ errorGenAI }}</div>
            }
          </div>
        </div>

        <!-- 3 Propuestas de GenAI -->
        @if (propuestas.length > 0) {
          <h6 class="fw-semibold mb-3">
            <i class="bi bi-list-ol me-1"></i>Propuestas generadas — elegí una:
          </h6>
          <div class="row g-3 mb-4">
            @for (p of propuestas; track $index) {
              <div class="col-lg-4">
                <div class="card h-100 propuesta-card"
                     [class.border-primary]="propuestaElegida === $index"
                     [class.bg-primary]="propuestaElegida === $index"
                     [class.bg-opacity-10]="propuestaElegida === $index"
                     style="cursor:pointer"
                     (click)="elegirPropuesta($index, p)">
                  <div class="card-header d-flex justify-content-between align-items-center py-2">
                    <span class="fw-semibold small">
                      <span class="badge bg-primary me-1">{{ $index + 1 }}</span>
                      {{ p.titulo }}
                    </span>
                    @if (propuestaElegida === $index) {
                      <i class="bi bi-check-circle-fill text-primary"></i>
                    }
                  </div>
                  <div class="card-body py-2">
                    <dl class="mb-0" style="font-size:0.78rem">
                      <dt class="text-muted">Objetivo</dt>
                      <dd>{{ p.objetivo }}</dd>
                      <dt class="text-muted">Procedimiento</dt>
                      <dd>{{ p.procedimiento }}</dd>
                      <dt class="text-muted">Indicador / Variables</dt>
                      <dd>{{ p.indicadorVariable }}</dd>
                      <dt class="text-muted">Escala</dt>
                      <dd class="mb-0">{{ p.escala }}</dd>
                    </dl>
                  </div>
                  <div class="card-footer py-2" style="font-size:0.72rem">
                    <i class="bi bi-info-circle me-1 text-muted"></i>
                    {{ p.justificacion }}
                  </div>
                </div>
              </div>
            }
          </div>
        }

        <!-- Formulario de parametrización -->
        <div class="card mb-4">
          <div class="card-header fw-semibold small">
            <i class="bi bi-pencil me-1"></i>
            {{ propuestaElegida !== null ? 'Editar propuesta seleccionada' : 'Parametrización manual' }}
          </div>
          <div class="card-body">
            <div class="row g-3">
              <div class="col-12">
                <label class="form-label small fw-semibold">
                  Objetivo de medición <span class="text-danger">*</span>
                </label>
                <textarea class="form-control form-control-sm" rows="2"
                          placeholder="¿Qué se quiere lograr midiendo esta métrica?"
                          [(ngModel)]="form.objetivo"></textarea>
              </div>
              <div class="col-12">
                <label class="form-label small fw-semibold">
                  Procedimiento / Fórmula <span class="text-danger">*</span>
                </label>
                <textarea class="form-control form-control-sm" rows="3"
                          placeholder="Fórmula o pasos para calcular el valor de la métrica..."
                          [(ngModel)]="form.procedimiento"></textarea>
              </div>
              <div class="col-md-6">
                <label class="form-label small fw-semibold">
                  Indicador y Variables <span class="text-danger">*</span>
                </label>
                <input type="text" class="form-control form-control-sm"
                       placeholder="Ej: Velocidad = SP completados / SP planificados"
                       [(ngModel)]="form.indicadorVariable">
              </div>
              <div class="col-md-6">
                <label class="form-label small fw-semibold">
                  Escala de medición <span class="text-danger">*</span>
                </label>
                <input type="text" class="form-control form-control-sm"
                       placeholder="Ej: Porcentual 0-100%, Numérica 0-50 pts..."
                       [(ngModel)]="form.escala">
              </div>
            </div>
          </div>
          <div class="card-footer d-flex justify-content-between align-items-center">
            <button class="btn btn-outline-secondary btn-sm" (click)="volver()">
              <i class="bi bi-arrow-left me-1"></i>Volver
            </button>
            <div class="d-flex align-items-center gap-3">
              <span class="small">
                Estado: <span class="badge" [class]="estadoBadge()">{{ estadoLabel() }}</span>
              </span>
              <button class="btn btn-success btn-sm"
                      [disabled]="guardando"
                      (click)="guardar()">
                @if (guardando) {
                  <span class="spinner-border spinner-border-sm me-1"></span>
                } @else {
                  <i class="bi bi-floppy me-1"></i>
                }
                Guardar parametrización
              </button>
            </div>
          </div>
        </div>

      }
    </app-shell>
  `,
  styles: [`
    .propuesta-card { transition: border-color 0.15s, background-color 0.15s; }
    .propuesta-card:hover { border-color: var(--bs-primary) !important; }
  `]
})
export class ParametrizacionComponent implements OnInit {
  metrica: MetricaSeleccionada | null       = null;
  parametrizacionBase: MetricParametrizacionBase | null = null;
  top3: TopParametrizacion[]                = [];
  propuestas: PropuestaGenAI[]              = [];
  propuestaElegida: number | null           = null;
  generando  = false;
  guardando  = false;
  errorGenAI = '';

  form: Parametrizacion = {
    objetivo: '', procedimiento: '', indicadorVariable: '', escala: ''
  };

  private readonly apiBase = environment.apiBaseUrl;

  constructor(
    private route: ActivatedRoute,
    public  router: Router,
    private seleccionService: SeleccionService,
    private rankingService: MetricRankingService,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    this.seleccionService.getAll().subscribe(list => {
      this.metrica = list.find(s => s.id === id) ?? null;
      if (this.metrica?.parametrizacion) {
        this.form = { ...this.metrica.parametrizacion };
        this.propuestaElegida = this.metrica.parametrizacion.propuestaElegida ?? null;
      }
      // Cargar parametrización base del backend si existe
      if (this.metrica) {
        const metricaId = this.metrica.factorId;
        // Buscar primero por metricaId (flujo Planeación), luego por factorId (flujo Selección)
        this.rankingService.getTop3ByMetricaId(metricaId).pipe(
          catchError(() => of([] as TopParametrizacion[]))
        ).subscribe(top => {
          if (top.length > 0) {
            this.top3 = top;
          } else {
            this.rankingService.getTop3(metricaId).pipe(
              catchError(() => of([] as TopParametrizacion[]))
            ).subscribe(t => this.top3 = t);
          }
        });

        this.rankingService.getBaseByMetricaId(metricaId).pipe(
          catchError(() => this.rankingService.getBase(metricaId).pipe(catchError(() => of(null))))
        ).subscribe(base => this.parametrizacionBase = base);
      }
    });
  }

  /** Copiar una entrada del top 3 al formulario */
  usarDelTop(t: TopParametrizacion): void {
    this.form = {
      objetivo:          t.objetivo,
      procedimiento:     t.procedimiento,
      indicadorVariable: t.indicadorVariable,
      escala:            t.escala
    };
    this.propuestaElegida = null;
  }

  /** Copiar la parametrización base al formulario para editarla */
  usarBase(): void {
    if (!this.parametrizacionBase) return;
    this.form = {
      objetivo:          this.parametrizacionBase.objetivo,
      procedimiento:     this.parametrizacionBase.procedimiento,
      indicadorVariable: this.parametrizacionBase.indicadorVariable,
      escala:            this.parametrizacionBase.escala
    };
    this.propuestaElegida = null;
  }

  generarPropuestas(): void {
    if (!this.metrica) return;
    this.generando  = true;
    this.errorGenAI = '';
    this.propuestas = [];

    this.http.post<PropuestaGenAI[]>(`${this.apiBase}/parametrizacion/propuestas`, {
      factorNombre:       this.metrica.factorNombre,
      factorCategoria:    this.metrica.factorCategoria,
      metricaNombre:      this.metrica.metricaNombre,
      metricaDescripcion: this.metrica.metricaDescripcion
    }).subscribe({
      next:  p  => { this.propuestas = p; this.generando = false; },
      error: () => { this.errorGenAI = 'Error al conectar con GenAI.'; this.generando = false; }
    });
  }

  elegirPropuesta(idx: number, p: PropuestaGenAI): void {
    this.propuestaElegida = idx;
    this.form = {
      objetivo:          p.objetivo,
      procedimiento:     p.procedimiento,
      indicadorVariable: p.indicadorVariable,
      escala:            p.escala,
      propuestaElegida:  idx
    };
  }

  calcularEstado(): 'sin_parametrizar' | 'parcial' | 'completa' {
    const completos = [this.form.objetivo, this.form.procedimiento,
                       this.form.indicadorVariable, this.form.escala]
      .filter(c => !!c?.trim()).length;
    if (completos === 0) return 'sin_parametrizar';
    if (completos === 4) return 'completa';
    return 'parcial';
  }

  estadoLabel(): string {
    return { sin_parametrizar: 'Sin parametrizar', parcial: 'Parcial', completa: 'Completa' }
      [this.calcularEstado()];
  }

  estadoBadge(): string {
    return { sin_parametrizar: 'bg-secondary', parcial: 'bg-warning text-dark', completa: 'bg-success' }
      [this.calcularEstado()];
  }

  guardar(): void {
    if (!this.metrica) return;
    this.guardando = true;

    // Guardar en localStorage (estado local del sprint)
    this.seleccionService.parametrizar(this.metrica.id, {
      ...this.form,
      propuestaElegida: this.propuestaElegida ?? undefined
    });

    // Guardar en el backend (persistencia compartida + ranking)
    const proyectoActivo = localStorage.getItem('mpdia_proyecto_activo');
    const proyectoId = proyectoActivo ? JSON.parse(proyectoActivo)?.id ?? null : null;

    this.rankingService.guardar({
      factorId:          null,
      objetivo:          this.form.objetivo,
      procedimiento:     this.form.procedimiento,
      indicadorVariable: this.form.indicadorVariable,
      escala:            this.form.escala,
      metricaBaseId:     this.parametrizacionBase?.id ?? null,
      proyectoId:        proyectoId,
      metricaId:         this.metrica.factorId  // desde Planeación, factorId contiene el metricaId
    }).pipe(
      catchError(() => of(null))
    ).subscribe(() => {
      this.guardando = false;
      this.router.navigate(['/resumen-seleccion']);
    });
  }

  volver(): void {
    this.router.navigate(['/resumen-seleccion']);
  }

  categoryBadge(cat: string): string {
    const map: Record<string, string> = {
      'Productividad': 'bg-primary', 'Calidad': 'bg-warning text-dark',
      'Cumplimiento':  'bg-success',  'Sociohumano': 'bg-info text-dark'
    };
    return map[cat] ?? 'bg-secondary';
  }
}
