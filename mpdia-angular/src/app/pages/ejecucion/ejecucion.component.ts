// Autor: Cristian Santiago Martinez Cordoba — MPDIA
// FASE 7: Ejecución reescrita para operar sobre las 5 métricas oficiales,
// agrupadas por métrica, usando la parametrización aprobada vigente de cada
// una (no una lista plana de "todas las variables activas del proyecto").
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, of, forkJoin } from 'rxjs';
import { ShellComponent } from '../../layout/shell/shell.component';
import { AuthService } from '../../services/auth.service';
import { SprintService } from '../../services/sprint.service';
import { MetricaAcademicaService } from '../../services/metrica-academica.service';
import { VariableDinamicaService, VariableConValor } from '../../services/variable-dinamica.service';
import { ProyectoDto } from '../../models/proyecto.model';
import { SprintDto } from '../../models/sprint.model';
import { ParametrizacionAcademicaDto, ResultadoMetricaDto } from '../../models/metrica-academica.model';

/** Variable ya resuelta y lista para mostrarse en el formulario de captura. */
interface VariableCaptura {
  nombre:        string;   // identificador técnico (clave real hacia el backend)
  nombreHumano:  string;   // texto legible
  tipo:          'INTEGER' | 'DECIMAL' | 'TEXT' | 'BOOLEAN';
  unidad:        string;
  requerida:     boolean;
}

/** Bloque de captura + cálculo de UNA de las 5 métricas oficiales. */
interface BloqueMetrica {
  id:              string;
  nombre:          string;
  parametrizacion: ParametrizacionAcademicaDto | null;
  variables:       VariableCaptura[];
  valores:         { [nombre: string]: any };
  resultado:       ResultadoMetricaDto | null; // último resultado del sprint actual (auto-mostrado, no ejecutado)
  historico:       ResultadoMetricaDto[];
  cargando:        boolean;
  ejecutando:      boolean;
  error:           string;
  mostrarErrores:  boolean;
}

/** Fila de la vista combinada de Historial (todas las métricas oficiales). */
interface FilaHistorial {
  metricaNombre: string;
  sprintNumero:  number | null;
  resultado:     number;
  unidad:        string;
  version:       number;
  fecha:         string;
}

@Component({
  selector: 'app-ejecucion',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell title="Ejecución — Captura y Cálculo">

      @if (!proyecto) {
        <div class="text-center py-5 text-muted">
          <i class="bi bi-folder-x fs-1 d-block mb-3 opacity-25"></i>
          <p>Seleccioná un proyecto primero.</p>
          <button class="btn btn-primary btn-sm" (click)="router.navigate(['/proyectos'])">
            Ir a Proyectos
          </button>
        </div>
      } @else {

        <!-- Selector de sprint -->
        <div class="card mb-3">
          <div class="card-body py-2">
            <div class="d-flex flex-wrap gap-3 align-items-center">
              <div style="min-width:260px">
                <label class="form-label small fw-semibold mb-1">Sprint</label>
                <select class="form-select form-select-sm"
                        [(ngModel)]="sprintSeleccionadoId"
                        (ngModelChange)="onSprintChange()">
                  <option value="">Seleccionar sprint...</option>
                  @for (s of sprints; track s.id) {
                    <option [value]="s.id" [disabled]="s.estado === 'pendiente'">
                      Sprint {{ s.numero }} — {{ s.fechaInicio | date:'dd/MM' }}
                      al {{ s.fechaFin | date:'dd/MM/yyyy' }}
                      ({{ labelEstado(s.estado) }})
                    </option>
                  }
                </select>
              </div>
              @if (sprintActual) {
                <span class="badge" [class]="badgeSprint(sprintActual.estado)">
                  {{ labelEstado(sprintActual.estado) }}
                </span>
                <span class="small text-muted">
                  {{ sprintActual.fechaInicio | date:'dd/MM/yyyy' }} —
                  {{ sprintActual.fechaFin | date:'dd/MM/yyyy' }}
                </span>
              }
            </div>
          </div>
        </div>

        @if (!sprintActual) {
          <div class="text-center py-5 text-muted small">
            <i class="bi bi-calendar-check fs-2 d-block mb-2 opacity-25"></i>
            Seleccioná un sprint para capturar y calcular las métricas.
          </div>
        } @else {

          <ul class="nav nav-tabs mb-3">
            <li class="nav-item">
              <button class="nav-link" [class.active]="tab === 'captura'" (click)="tab = 'captura'">
                <i class="bi bi-clipboard-data me-1"></i>Captura y Cálculo
              </button>
            </li>
            <li class="nav-item">
              <button class="nav-link" [class.active]="tab === 'historial'" (click)="tab = 'historial'">
                <i class="bi bi-clock-history me-1"></i>Historial
              </button>
            </li>
          </ul>

          <!-- CAPTURA Y CÁLCULO -->
          @if (tab === 'captura') {

            @if (!esScrumMaster) {
              <div class="alert alert-info small mb-3">
                <i class="bi bi-info-circle me-1"></i>
                Las métricas oficiales las registra y calcula el Scrum Master. Podés consultar los resultados abajo.
              </div>
            }

            @if (sprintBloqueado) {
              <div class="alert alert-warning small mb-3">
                <i class="bi bi-lock me-1"></i>
                Sprint <strong>{{ labelEstado(sprintActual.estado) }}</strong>. No se pueden registrar nuevos valores;
                se muestran los resultados existentes.
              </div>
            }

            @for (b of bloques; track b.id) {
              <div class="card mb-3">
                <div class="card-header fw-semibold small py-2 d-flex justify-content-between align-items-center">
                  <span><i class="bi bi-graph-up me-1"></i>{{ b.nombre.toUpperCase() }}</span>
                  @if (b.parametrizacion) {
                    <span class="badge bg-secondary" style="font-size:.65rem">v{{ b.parametrizacion.version }}</span>
                  }
                </div>
                <div class="card-body py-2">

                  @if (b.cargando) {
                    <div class="text-center py-3 text-muted small">
                      <span class="spinner-border spinner-border-sm me-2"></span>Cargando...
                    </div>
                  } @else if (!b.parametrizacion) {
                    <div class="alert alert-warning small mb-0">
                      Esta métrica no tiene una parametrización aprobada en este proyecto.
                    </div>
                  } @else {

                    <div class="small text-muted mb-2">
                      <code>{{ b.parametrizacion.formulaAcademica }}</code>
                    </div>

                    @if (b.variables.length === 0) {
                      <div class="text-muted small mb-2">Sin variables configuradas.</div>
                    } @else if (esScrumMaster && !sprintBloqueado) {
                      <div class="row g-2 mb-2">
                        @for (v of b.variables; track v.nombre) {
                          <div class="col-auto">
                            <label class="form-label small mb-1">{{ v.nombreHumano }}</label>
                            <input type="number" class="form-control form-control-sm" style="width:130px" step="0.01"
                                   [(ngModel)]="b.valores[v.nombre]"
                                   [name]="'v-' + b.id + '-' + v.nombre">
                            @if (b.mostrarErrores && !esValido(b, v)) {
                              <div class="text-danger" style="font-size:.7rem">Requerido</div>
                            }
                          </div>
                        }
                      </div>
                      <button type="button" class="btn btn-primary btn-sm" [disabled]="b.ejecutando"
                              (click)="guardarYCalcular(b)">
                        @if (b.ejecutando) {
                          <span class="spinner-border spinner-border-sm me-1"></span>
                        } @else {
                          <i class="bi bi-calculator me-1"></i>
                        }
                        Guardar y calcular
                      </button>
                      @if (b.error) {
                        <div class="text-danger small mt-2">{{ b.error }}</div>
                      }
                    } @else {
                      <div class="small text-muted mb-2">
                        @for (v of b.variables; track v.nombre) {
                          <div>{{ v.nombreHumano }}: <strong>{{ b.valores[v.nombre] ?? '—' }}</strong></div>
                        }
                      </div>
                    }

                    <div class="mt-2 pt-2 border-top">
                      @if (b.resultado) {
                        <span class="text-muted small">Resultado:</span>
                        <strong class="text-success ms-1">{{ b.resultado.resultado }} {{ b.parametrizacion.unidadResultado }}</strong>
                        <span class="badge bg-light text-dark border ms-2" style="font-size:.65rem">
                          <i class="bi bi-lock-fill me-1"></i>Calculado automáticamente
                        </span>
                      } @else {
                        <span class="text-muted small">Resultado: —</span>
                      }
                    </div>
                  }
                </div>
              </div>
            }
          }

          <!-- HISTORIAL -->
          @if (tab === 'historial') {
            <div class="card">
              <div class="card-header fw-semibold small py-2">
                <i class="bi bi-clock-history me-1"></i>Historial de resultados
              </div>
              <div class="card-body p-0">
                @if (historialCombinado().length === 0) {
                  <div class="text-center py-4 text-muted small">Sin resultados calculados todavía.</div>
                } @else {
                  <div class="table-responsive">
                    <table class="table table-sm mb-0 align-middle">
                      <thead class="table-light">
                        <tr>
                          <th class="ps-3">Métrica</th>
                          <th>Sprint</th>
                          <th>Resultado</th>
                          <th>Unidad</th>
                          <th>Versión</th>
                          <th>Fecha</th>
                        </tr>
                      </thead>
                      <tbody>
                        @for (f of historialCombinado(); track f.metricaNombre + f.fecha) {
                          <tr>
                            <td class="ps-3 fw-semibold small">{{ f.metricaNombre }}</td>
                            <td class="small">{{ f.sprintNumero !== null ? 'Sprint ' + f.sprintNumero : '—' }}</td>
                            <td class="fw-bold">{{ f.resultado }}</td>
                            <td class="small text-muted">{{ f.unidad }}</td>
                            <td><span class="badge bg-secondary" style="font-size:.65rem">v{{ f.version }}</span></td>
                            <td class="small text-muted">{{ f.fecha | date:'dd/MM/yyyy HH:mm' }}</td>
                          </tr>
                        }
                      </tbody>
                    </table>
                  </div>
                }
              </div>
            </div>
          }
        }
      }

    </app-shell>
  `
})
export class EjecucionComponent implements OnInit {
  proyecto:  ProyectoDto | null = null;
  sprints:   SprintDto[]        = [];

  sprintSeleccionadoId = '';
  sprintActual: SprintDto | null = null;
  tab: 'captura' | 'historial' = 'captura';

  bloques: BloqueMetrica[] = [];

  /**
   * Las 5 métricas oficiales de MPDIA (mismos IDs que
   * PlaneacionComponent.METRICAS_VISIBLES). Ejecución solo captura/calcula
   * estas — nunca "todas las variables activas del proyecto".
   */
  private static readonly METRICAS_OFICIALES: { id: string; nombre: string }[] = [
    { id: 'ec0d74fe-0bf4-4970-af89-dcaa0736c8ed', nombre: 'Defectos' },
    { id: 'dde97e2b-1b25-493e-9273-a6b59564b053', nombre: 'Impedimentos por sprint' },
    { id: '2ba0cf34-0bec-4e7d-8dc5-40795f050ec9', nombre: 'Problemas reportados por el cliente' },
    { id: 'beb22a94-0e1b-496a-8b9e-a08a8f6d77c3', nombre: 'Aprendizaje organizacional (FAT)' },
    { id: '40beffdf-13f4-4772-8820-4df93fae525c', nombre: 'Deuda técnica gestionada' },
  ];

  constructor(
    public  router: Router,
    public  auth: AuthService,
    private sprintService: SprintService,
    private metricaService: MetricaAcademicaService,
    private variableService: VariableDinamicaService
  ) {
    this.bloques = EjecucionComponent.METRICAS_OFICIALES.map(m => this.nuevoBloque(m.id, m.nombre));
  }

  private nuevoBloque(id: string, nombre: string): BloqueMetrica {
    return {
      id, nombre, parametrizacion: null, variables: [], valores: {},
      resultado: null, historico: [], cargando: false, ejecutando: false,
      error: '', mostrarErrores: false
    };
  }

  get esScrumMaster() { return this.auth.currentUser()?.role === 'scrum_master'; }
  get sprintBloqueado() { return this.sprintActual?.estado === 'finalizado' || this.sprintActual?.estado === 'pendiente'; }

  ngOnInit(): void {
    try {
      const p = localStorage.getItem('mpdia_proyecto_activo');
      this.proyecto = p ? JSON.parse(p) : null;
    } catch { /**/ }

    if (this.proyecto) {
      this.sprintService.listar(this.proyecto.id).pipe(catchError(() => of([]))).subscribe(sprints => {
        this.sprints = [...sprints].sort((a, b) => a.numero - b.numero);
        const activo = this.sprints.find(s => s.estado === 'en_ejecucion');
        if (activo) { this.sprintSeleccionadoId = activo.id; this.onSprintChange(); }
      });
    }
  }

  /**
   * Cambiar de sprint SOLO consulta datos (parametrización + variables +
   * histórico de cada métrica oficial). Nunca ejecuta un cálculo — el
   * resultado mostrado es, si existe, el ya calculado previamente para ese
   * sprint (idéntico criterio que MetricaAcademicaComponent).
   */
  onSprintChange(): void {
    this.sprintActual = this.sprints.find(s => s.id === this.sprintSeleccionadoId) ?? null;
    if (!this.sprintActual || !this.proyecto) return;
    for (const b of this.bloques) {
      this.cargarBloque(b);
    }
  }

  private cargarBloque(b: BloqueMetrica): void {
    if (!this.proyecto || !this.sprintActual) return;
    b.cargando = true;
    b.error = '';
    b.resultado = null;
    forkJoin({
      parametrizacion: this.metricaService.obtenerParametrizacionAprobada(b.id, this.proyecto.id)
        .pipe(catchError(() => of(null))),
      variablesResp: this.variableService.obtenerVariables(b.id, this.proyecto.id, this.sprintActual.id)
        .pipe(catchError(() => of(null))),
      historico: this.metricaService.obtenerHistorico(b.id, this.proyecto.id)
        .pipe(catchError(() => of([] as ResultadoMetricaDto[])))
    }).subscribe(({ parametrizacion, variablesResp, historico }) => {
      b.parametrizacion = parametrizacion;
      b.variables = variablesResp
        ? variablesResp.variables.map(v => this.mapVariable(v))
        : [];
      b.valores = {};
      if (variablesResp) {
        for (const v of variablesResp.variables) {
          const existente = this.valorExistente(v);
          if (existente !== null) b.valores[v.nombre] = existente;
        }
      }
      b.historico = historico;
      b.resultado = historico.find(h => h.sprintId === this.sprintActual!.id) ?? null;
      b.cargando = false;
    });
  }

  private mapVariable(v: VariableConValor): VariableCaptura {
    return {
      nombre: v.nombre,
      nombreHumano: this.humanizarNombre(v.nombre),
      tipo: this.mapTipoDato(v.tipoDato),
      unidad: v.unidad || '',
      requerida: v.obligatorio ?? true
    };
  }

  private humanizarNombre(nombre: string): string {
    const legible = nombre.replace(/_/g, ' ').trim();
    if (!legible) return nombre;
    return legible.charAt(0).toUpperCase() + legible.slice(1);
  }

  private valorExistente(v: VariableConValor): number | string | boolean | null {
    if (v.valorBool !== undefined && v.valorBool !== null) return v.valorBool;
    if (v.valorTexto !== undefined && v.valorTexto !== null) return v.valorTexto;
    if (v.valorNum !== undefined && v.valorNum !== null) return v.valorNum;
    return null;
  }

  private mapTipoDato(tipoDato: string): 'INTEGER' | 'DECIMAL' | 'TEXT' | 'BOOLEAN' {
    switch (tipoDato) {
      case 'texto': return 'TEXT';
      case 'booleano': return 'BOOLEAN';
      default: return 'INTEGER';
    }
  }

  esValido(b: BloqueMetrica, v: VariableCaptura): boolean {
    const valor = b.valores[v.nombre];
    if (valor === null || valor === undefined || valor === '') return false;
    const n = Number(valor);
    if (Number.isNaN(n) || n < 0) return false;
    return true;
  }

  /**
   * Único camino de cálculo: MetricaAcademicaService.ejecutar() →
   * MetricaAcademicaService.ejecutarMetricaAcademica() (backend) →
   * calcularSegunTipo() → FormulaEvaluator para FORMULA. Angular nunca
   * calcula fórmulas — solo captura y muestra el resultado devuelto.
   */
  guardarYCalcular(b: BloqueMetrica): void {
    b.mostrarErrores = true;
    if (b.variables.length === 0 || !this.proyecto || !this.sprintActual) return;
    const todasValidas = b.variables.every(v => this.esValido(b, v));
    if (!todasValidas) return;

    b.ejecutando = true;
    b.error = '';
    this.metricaService.ejecutar(b.id, {
      proyectoId: this.proyecto.id,
      sprintId: this.sprintActual.id,
      valores: b.valores
    }).subscribe({
      next: (r) => {
        b.resultado = r;
        b.ejecutando = false;
        b.mostrarErrores = false;
        this.metricaService.obtenerHistorico(b.id, this.proyecto!.id).subscribe(h => b.historico = h);
      },
      error: (e) => {
        b.ejecutando = false;
        b.error = e.status === 403 ? 'No tienes permiso para calcular esta métrica' :
                  e.status === 409 ? 'La parametrización no está aprobada o hay un conflicto de estado' :
                  e.status === 400 ? (e.error?.message || 'Datos inválidos') :
                  'Error al calcular la métrica';
      }
    });
  }

  /** Vista combinada de historial: todas las métricas oficiales, más reciente primero. */
  historialCombinado(): FilaHistorial[] {
    const filas: FilaHistorial[] = [];
    for (const b of this.bloques) {
      for (const h of b.historico) {
        filas.push({
          metricaNombre: b.nombre,
          sprintNumero: this.sprints.find(s => s.id === h.sprintId)?.numero ?? null,
          resultado: h.resultado,
          unidad: h.unidad || '',
          version: h.parametrizacionVersion,
          fecha: h.calculadoAt
        });
      }
    }
    return filas.sort((a, b2) => new Date(b2.fecha).getTime() - new Date(a.fecha).getTime());
  }

  labelEstado(e: string): string {
    return ({ 'en_ejecucion': 'En ejecución', 'pendiente': 'Pendiente', 'finalizado': 'Finalizado', 'reabierto': 'Reabierto' } as Record<string, string>)[e] ?? e;
  }

  badgeSprint(e: string): string {
    return ({ 'en_ejecucion': 'bg-success', 'pendiente': 'bg-warning text-dark', 'finalizado': 'bg-secondary', 'reabierto': 'bg-info text-dark' } as Record<string, string>)[e] ?? 'bg-secondary';
  }
}
