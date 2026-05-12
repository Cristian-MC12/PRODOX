// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ShellComponent } from '../../layout/shell/shell.component';
import { SeleccionService } from '../../services/seleccion.service';
import { MetricaSeleccionada, Parametrizacion, PropuestaGenAI } from '../../models/seleccion.model';
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

        <!-- Formulario de parametrización manual / edición -->
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
              <!-- Indicador de estado en tiempo real -->
              <span class="small">
                Estado:
                <span class="badge" [class]="estadoBadge()">{{ estadoLabel() }}</span>
              </span>
              <button class="btn btn-success btn-sm" (click)="guardar()">
                <i class="bi bi-floppy me-1"></i>Guardar parametrización
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
  metrica: MetricaSeleccionada | null = null;
  propuestas: PropuestaGenAI[]        = [];
  propuestaElegida: number | null     = null;
  generando                           = false;
  errorGenAI                          = '';

  form: Parametrizacion = {
    objetivo:          '',
    procedimiento:     '',
    indicadorVariable: '',
    escala:            ''
  };

  private readonly apiBase = environment.apiBaseUrl;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private seleccionService: SeleccionService,
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
    });
  }

  generarPropuestas(): void {
    if (!this.metrica) return;
    this.generando   = true;
    this.errorGenAI  = '';
    this.propuestas  = [];

    this.http.post<PropuestaGenAI[]>(`${this.apiBase}/parametrizacion/propuestas`, {
      factorNombre:      this.metrica.factorNombre,
      factorCategoria:   this.metrica.factorCategoria,
      metricaNombre:     this.metrica.metricaNombre,
      metricaDescripcion: this.metrica.metricaDescripcion
    }).subscribe({
      next: (props) => {
        this.propuestas = props;
        this.generando  = false;
      },
      error: () => {
        this.errorGenAI = 'Error al conectar con GenAI. Verificá que el backend esté corriendo.';
        this.generando  = false;
      }
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

  /** Calcula el estado según cuántos campos están completos */
  calcularEstado(): 'sin_parametrizar' | 'parcial' | 'completa' {
    const campos = [
      this.form.objetivo,
      this.form.procedimiento,
      this.form.indicadorVariable,
      this.form.escala
    ];
    const completos = campos.filter(c => !!c?.trim()).length;
    if (completos === 0)             return 'sin_parametrizar';
    if (completos === campos.length) return 'completa';
    return 'parcial';
  }

  estadoLabel(): string {
    const map: Record<string, string> = {
      'sin_parametrizar': 'Sin parametrizar',
      'parcial':          'Parcial',
      'completa':         'Completa'
    };
    return map[this.calcularEstado()];
  }

  estadoBadge(): string {
    const map: Record<string, string> = {
      'sin_parametrizar': 'bg-secondary',
      'parcial':          'bg-warning text-dark',
      'completa':         'bg-success'
    };
    return map[this.calcularEstado()];
  }

  guardar(): void {
    if (!this.metrica) return;
    this.seleccionService.parametrizar(this.metrica.id, {
      ...this.form,
      propuestaElegida: this.propuestaElegida ?? undefined
    });
    this.router.navigate(['/resumen-seleccion']);
  }

  volver(): void {
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
}
