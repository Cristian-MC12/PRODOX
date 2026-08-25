// Autor: Cristian Santiago Martinez Cordoba — MPDIA
// FASE 16: Ejecución reescrita — deja de depender de una lista hardcodeada
// de 5 métricas oficiales. Muestra dinámicamente las métricas realmente
// aprobadas/parametrizadas del proyecto (sin ninguna condición especial por
// código, UUID o categoría), permite registrar un valor con una fecha de
// captura explícita (no siempre "ahora") y grafica los registros reales.
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, of, forkJoin } from 'rxjs';
import { ShellComponent } from '../../layout/shell/shell.component';
import { AuthService } from '../../services/auth.service';
import { SprintService } from '../../services/sprint.service';
import { PlaneacionService } from '../../services/planeacion.service';
import { MetricaAcademicaService } from '../../services/metrica-academica.service';
import { VariableDinamicaService, VariableConValor } from '../../services/variable-dinamica.service';
import { EvaluacionService } from '../../services/evaluacion.service';
import { MiniChartComponent, PuntoMiniChart } from '../../shared/mini-chart/mini-chart.component';
import { ProyectoDto } from '../../models/proyecto.model';
import { SprintDto } from '../../models/sprint.model';
import { MetricaEvaluacionDetalleDto, RegistroPuntoDto } from '../../models/evaluacion-detalle.model';

/** Una variable de una métrica aprobada, con su bloque de captura + gráfica. */
interface BloqueVariable {
  variableId: string;
  nombre: string;
  descripcion: string;
  tipoDato: string;
  frecuenciaCaptura: string;
  unidad?: string;
  escalaMin?: number;
  escalaMax?: number;
  fecha: string;          // yyyy-MM-dd, ligado al input de fecha
  valorNum: number | null;
  valorTexto: string;
  valorBool: boolean;
  registrando: boolean;
  error: string;
  ultimoMensaje: string;
  puntos: PuntoMiniChart[];
  /** Capturas ya registradas para este sprint (más reciente primero) — nunca mezcla otros sprints. */
  capturas: RegistroPuntoDto[];
  /**
   * Para frecuencia 'por_sprint': false = mostrar el resumen de solo lectura
   * del valor ya registrado; true = mostrar el formulario de captura/edición.
   * Para el resto de las frecuencias no se usa (el formulario siempre está visible).
   */
  editando: boolean;
  /**
   * Revisión de Ejecución — id del RegistroValor cargado en el formulario
   * para editar (null = el formulario representa una captura nueva). Se
   * envía al backend para que actualice SIEMPRE esa misma fila, incluso si
   * la fecha cambia — corrige el bug donde editar una captura 'por_sprint'
   * cambiando su fecha era rechazado como si fuera una captura nueva en
   * conflicto con el propio registro que se estaba editando.
   */
  registroEditandoId: string | null;
}

/** Una métrica aprobada del proyecto (puede tener 1 o más variables). */
interface MetricaEjecucion {
  metricaId: string;
  nombre: string;
  cargando: boolean;
  sinParametrizacion: boolean;
  variables: BloqueVariable[];
}

function hoyISO(): string {
  const d = new Date();
  const mes = String(d.getMonth() + 1).padStart(2, '0');
  const dia = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mes}-${dia}`;
}

@Component({
  selector: 'app-ejecucion',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent, MiniChartComponent],
  template: `
    <app-shell title="Ejecución — Captura de valores">

      @if (!proyecto) {
        <div class="prox-empty-state">
          <i class="bi bi-folder-x"></i>
          <p>Seleccioná un proyecto primero.</p>
          <button class="btn btn-primary btn-sm mt-3" (click)="router.navigate(['/proyectos'])">
            Ir a Proyectos
          </button>
        </div>
      } @else {

        <div class="card mb-3">
          <div class="card-body py-2">
            <div class="d-flex flex-wrap gap-3 align-items-center">
              <div class="d-flex align-items-end gap-2">
                <button type="button" class="btn btn-outline-secondary btn-sm"
                        [disabled]="!haySprintAnterior"
                        (click)="irASprintAnterior()"
                        title="Ir al sprint anterior">
                  <i class="bi bi-chevron-left me-1"></i>Sprint anterior
                </button>
                <div style="min-width:260px">
                  <label class="form-label small fw-semibold mb-1">Sprint</label>
                  <select class="form-select form-select-sm"
                          [(ngModel)]="sprintSeleccionadoId"
                          (ngModelChange)="onSprintChange()">
                    <option value="">Seleccionar sprint...</option>
                    @for (s of sprints; track s.id) {
                      <option [value]="s.id">
                        Sprint {{ s.numero }} — {{ s.fechaInicio | date:'dd/MM' }}
                        al {{ s.fechaFin | date:'dd/MM/yyyy' }}
                        ({{ labelEstado(s.estado) }})
                      </option>
                    }
                  </select>
                </div>
                <button type="button" class="btn btn-outline-secondary btn-sm"
                        [disabled]="!haySprintSiguiente"
                        (click)="irASprintSiguiente()"
                        title="Ir al sprint siguiente">
                  Sprint siguiente<i class="bi bi-chevron-right ms-1"></i>
                </button>
              </div>
              @if (sprintActual) {
                <span class="badge prox-badge-sm" [class]="badgeSprint(sprintActual.estado)">
                  {{ labelEstado(sprintActual.estado) }}
                </span>
              }
            </div>
          </div>
        </div>

        @if (!sprintActual) {
          <div class="prox-empty-state">
            <i class="bi bi-calendar-check"></i>
            <p>Seleccioná un sprint para capturar valores.</p>
          </div>
        } @else if (cargandoMetricas) {
          <div class="text-center py-4 text-muted small">
            <span class="spinner-border spinner-border-sm me-2"></span>Cargando métricas aprobadas...
          </div>
        } @else if (metricas.length === 0) {
          <div class="prox-empty-state">
            <i class="bi bi-clipboard-x"></i>
            <p>
              Todavía no hay ninguna métrica aprobada en este proyecto.
              Aprobá una parametrización en Verificación para que aparezca aquí.
            </p>
          </div>
        } @else {

          @if (!esScrumMaster) {
            <div class="alert alert-info small mb-3">
              <i class="bi bi-info-circle me-1"></i>
              Las métricas las registra el Scrum Master. Podés consultar los resultados abajo.
            </div>
          }
          @if (sprintBloqueado) {
            <div class="alert alert-warning small mb-3">
              <i class="bi bi-lock me-1"></i>
              Sprint {{ labelEstado(sprintActual.estado) }}. No se pueden registrar nuevos valores;
              se muestran los resultados existentes.
            </div>
          } @else if (sprintActual.estado === 'pendiente') {
            <div class="alert alert-info small mb-3">
              <i class="bi bi-info-circle me-1"></i>
              Sprint pendiente (todavía no arrancó). Podés capturar valores igual para probar la
              aplicación — se validan contra el rango de fechas de este sprint
              ({{ sprintActual.fechaInicio | date:'dd/MM/yyyy' }}
              – {{ sprintActual.fechaFin ? (sprintActual.fechaFin | date:'dd/MM/yyyy') : 'sin definir' }}).
            </div>
          }

          @for (m of metricas; track m.metricaId) {
            <div class="card mb-3">
              <div class="card-header fw-semibold small py-2">
                <i class="bi bi-graph-up me-1"></i>{{ m.nombre.toUpperCase() }}
              </div>
              <div class="card-body py-2">
                @if (m.cargando) {
                  <div class="text-center py-3 text-muted small">
                    <span class="spinner-border spinner-border-sm me-2"></span>Cargando...
                  </div>
                } @else if (m.sinParametrizacion) {
                  <div class="alert alert-warning small mb-0">
                    Esta métrica no tiene una parametrización aprobada en este proyecto.
                  </div>
                } @else if (m.variables.length === 0) {
                  <div class="text-muted small mb-0">Sin variables configuradas.</div>
                } @else {
                  @for (v of m.variables; track v.variableId) {
                    <div class="row g-3 mb-2 pb-2 border-bottom">
                      <div class="col-12">
                        <div class="fw-semibold small text-uppercase">{{ v.nombre }}</div>
                        @if (v.descripcion) {
                          <div class="text-muted mt-1" style="font-size:0.72rem">
                            <strong>¿Qué mide?</strong> {{ v.descripcion }}
                          </div>
                        }
                        <div class="text-muted mt-1" style="font-size:0.72rem">
                          <strong>Valor esperado:</strong>
                          @if (v.tipoDato === 'numerico' && v.escalaMin != null && v.escalaMax != null) {
                            Numérico, escala {{ v.escalaMin }}–{{ v.escalaMax }}
                          } @else {
                            {{ v.tipoDato }}{{ v.unidad ? ' (' + v.unidad + ')' : '' }}
                          }
                        </div>
                        <div class="text-muted mt-1" style="font-size:0.72rem">
                          <strong>Frecuencia:</strong> {{ labelFrecuencia(v.frecuenciaCaptura) }}
                        </div>
                        @if (sprintActual) {
                          <div class="text-muted mt-1" style="font-size:0.72rem">
                            <strong>Sprint:</strong> Sprint {{ sprintActual.numero }}
                            · {{ sprintActual.fechaInicio | date:'dd/MM/yyyy' }}
                            – {{ sprintActual.fechaFin ? (sprintActual.fechaFin | date:'dd/MM/yyyy') : 'en curso' }}
                          </div>
                        }
                      </div>

                      @if (esScrumMaster && !sprintBloqueado) {

                        <!-- 'por_sprint' con valor ya registrado: resumen de solo lectura, nunca
                             un formulario que parezca admitir una segunda captura independiente. -->
                        @if (v.frecuenciaCaptura === 'por_sprint' && v.capturas.length > 0 && !v.editando) {
                          <div class="col-12">
                            <div class="alert alert-light border py-2 mb-0 d-flex justify-content-between align-items-center">
                              <div>
                                <div class="text-muted" style="font-size:0.68rem">Valor registrado</div>
                                <div class="fw-semibold">
                                  {{ v.capturas[0].valor }}
                                  <span class="text-muted small fw-normal">
                                    · Fecha: {{ v.capturas[0].registradoAt | date:'dd/MM/yyyy':'UTC' }}
                                  </span>
                                </div>
                              </div>
                              <button type="button" class="btn btn-sm btn-outline-primary"
                                      (click)="editarValorExistente(v, v.capturas[0])">
                                <i class="bi bi-pencil me-1"></i>Editar valor
                              </button>
                            </div>
                          </div>
                        } @else {

                          @if (v.frecuenciaCaptura === 'por_sprint' && v.capturas.length === 0) {
                            <div class="col-12">
                              <span class="badge bg-light text-dark border prox-badge-sm">
                                <i class="bi bi-hourglass-split me-1"></i>Pendiente de captura
                              </span>
                            </div>
                          }

                          <div class="col-auto">
                            <label class="form-label small mb-1">Fecha de captura</label>
                            <input type="date" class="form-control form-control-sm" style="width:160px"
                                   [(ngModel)]="v.fecha" [name]="'fecha-' + v.variableId"
                                   [min]="sprintActual?.fechaInicio" [max]="sprintActual?.fechaFin ?? undefined">
                            @if (sprintActual) {
                              <div class="text-muted" style="font-size:0.62rem">
                                Permitido: {{ sprintActual.fechaInicio | date:'dd/MM/yyyy' }}
                                – {{ sprintActual.fechaFin ? (sprintActual.fechaFin | date:'dd/MM/yyyy') : 'hoy' }}
                              </div>
                            }
                          </div>
                          <div class="col-auto">
                            <label class="form-label small mb-1">Valor</label>
                            @if (v.tipoDato === 'booleano') {
                              <select class="form-select form-select-sm" style="width:120px"
                                      [(ngModel)]="v.valorBool" [name]="'valor-' + v.variableId">
                                <option [ngValue]="true">Sí</option>
                                <option [ngValue]="false">No</option>
                              </select>
                            } @else if (v.tipoDato === 'texto') {
                              <input type="text" class="form-control form-control-sm" style="width:160px"
                                     [(ngModel)]="v.valorTexto" [name]="'valor-' + v.variableId">
                            } @else if (esEscalaDiscretaPequena(v)) {
                              <div class="btn-group" role="group" [attr.aria-label]="'Valor de ' + v.nombre">
                                @for (opcion of opcionesEscala(v); track opcion) {
                                  <button type="button" class="btn btn-sm"
                                          [class]="v.valorNum === opcion ? 'btn-primary' : 'btn-outline-primary'"
                                          (click)="v.valorNum = opcion">
                                    {{ opcion }}
                                  </button>
                                }
                              </div>
                            } @else if (v.escalaMin != null && v.escalaMax != null) {
                              <input type="number" class="form-control form-control-sm" style="width:120px" step="0.01"
                                     [min]="v.escalaMin" [max]="v.escalaMax"
                                     [(ngModel)]="v.valorNum" [name]="'valor-' + v.variableId">
                              <div class="text-muted" style="font-size:0.62rem">
                                Debe estar entre {{ v.escalaMin }} y {{ v.escalaMax }}.
                              </div>
                            } @else {
                              <input type="number" class="form-control form-control-sm" style="width:120px" step="0.01"
                                     [(ngModel)]="v.valorNum" [name]="'valor-' + v.variableId">
                            }
                          </div>
                          <div class="col-auto">
                            <button type="button" class="btn btn-primary btn-sm" [disabled]="v.registrando"
                                    (click)="registrarValor(m, v)">
                              @if (v.registrando) {
                                <span class="spinner-border spinner-border-sm me-1"></span>
                              } @else {
                                <i class="bi bi-check-lg me-1"></i>
                              }
                              {{ v.frecuenciaCaptura === 'por_sprint' && v.capturas.length > 0 ? 'Guardar cambios' : 'Registrar valor' }}
                            </button>
                            @if (v.frecuenciaCaptura === 'por_sprint' && v.capturas.length > 0) {
                              <button type="button" class="btn btn-outline-secondary btn-sm ms-1"
                                      [disabled]="v.registrando" (click)="cancelarEdicion(v)">
                                Cancelar
                              </button>
                            }
                          </div>
                          @if (v.error) {
                            <div class="col-12"><div class="text-danger small mt-1">{{ v.error }}</div></div>
                          }
                          @if (v.ultimoMensaje) {
                            <div class="col-12"><div class="text-success small mt-1">{{ v.ultimoMensaje }}</div></div>
                          }
                        }

                        <!-- diaria/semanal/ilimitada: historial de capturas de este sprint, cada
                             una editable — nunca un formulario que sugiera crear otra en la misma
                             fecha/ventana ya satisfecha. -->
                        @if (v.frecuenciaCaptura !== 'por_sprint' && v.capturas.length > 0) {
                          <div class="col-12">
                            <div class="small fw-semibold text-muted mb-1">Capturas de este sprint</div>
                            <div class="d-flex flex-wrap gap-2">
                              @for (c of v.capturas; track c.id) {
                                <button type="button" class="btn btn-sm btn-outline-secondary py-0"
                                        (click)="editarValorExistente(v, c)"
                                        title="Editar este valor">
                                  {{ c.registradoAt | date:'dd/MM/yyyy':'UTC' }}: {{ c.valor }}
                                  <i class="bi bi-pencil ms-1"></i>
                                </button>
                              }
                            </div>
                          </div>
                        }
                      }

                      <div class="col-12 mt-2">
                        <app-mini-chart [puntos]="v.puntos" [unidad]="v.unidad ?? ''"></app-mini-chart>
                      </div>
                    </div>
                  }
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
  proyecto: ProyectoDto | null = null;
  sprints: SprintDto[] = [];
  sprintSeleccionadoId = '';
  sprintActual: SprintDto | null = null;

  metricas: MetricaEjecucion[] = [];
  cargandoMetricas = false;

  constructor(
    public router: Router,
    public auth: AuthService,
    private sprintService: SprintService,
    private planeacionService: PlaneacionService,
    private metricaAcademicaService: MetricaAcademicaService,
    private variableService: VariableDinamicaService,
    private evaluacionService: EvaluacionService
  ) {}

  get esScrumMaster() { return this.auth.currentUser()?.role === 'scrum_master'; }
  /**
   * Revisión de Ejecución — 'finalizado' sigue siendo de solo lectura
   * (protección de datos históricos ya cerrados; regla de negocio sin
   * cambios). 'pendiente' YA NO bloquea: antes impedía seleccionar/probar
   * sprints futuros desde esta pantalla, algo que no depende de ninguna
   * regla de negocio real — el backend nunca validó el estado del sprint
   * para aceptar una captura, solo el rango de fechas propio del sprint
   * (ver EjecucionService.validarCapturaConFecha()), que sigue intacto.
   */
  get sprintBloqueado() { return this.sprintActual?.estado === 'finalizado'; }

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

  onSprintChange(): void {
    this.sprintActual = this.sprints.find(s => s.id === this.sprintSeleccionadoId) ?? null;
    if (!this.sprintActual || !this.proyecto) return;
    this.cargarMetricasAprobadas();
  }

  /**
   * Revisión de Ejecución — navegación directa entre sprints (botones
   * "Sprint anterior"/"Sprint siguiente"). El orden es el mismo que ya usa
   * el selector: `sprints` está ordenado por `numero` (ver ngOnInit), nunca
   * por fecha de captura — así que el índice dentro de ese array ya es el
   * orden real de sprints del proyecto.
   *
   * Ambos botones reutilizan exactamente `onSprintChange()`, el mismo
   * método que dispara el selector manual: cambiar `sprintSeleccionadoId` y
   * llamarlo reconstruye `this.metricas` desde cero (cargarMetricasAprobadas
   * → cargarVariablesDeMetrica → construirBloque), con lo cual:
   *  - nunca mezcla capturas de otro sprint (mismo aislamiento que ya existe);
   *  - nunca "arrastra" un registroEditandoId/edición pendiente del sprint
   *    anterior, porque los BloqueVariable viejos se descartan por completo
   *    y los nuevos siempre nacen con registroEditandoId: null (ver
   *    construirBloque()) — no hace falta ni un guardado automático ni una
   *    limpieza manual aparte: no hay ningún estado de edición que sobreviva
   *    al cambio de sprintActual;
   *  - respeta intacto sprintBloqueado/el aviso de "pendiente" (dependen de
   *    sprintActual.estado, que se recalcula igual que con el selector).
   */
  private get indiceSprintActual(): number {
    if (!this.sprintActual) return -1;
    return this.sprints.findIndex(s => s.id === this.sprintActual!.id);
  }

  get haySprintAnterior(): boolean {
    return this.indiceSprintActual > 0;
  }

  get haySprintSiguiente(): boolean {
    const i = this.indiceSprintActual;
    return i >= 0 && i < this.sprints.length - 1;
  }

  irASprintAnterior(): void {
    if (!this.haySprintAnterior) return;
    this.irASprintPorIndice(this.indiceSprintActual - 1);
  }

  irASprintSiguiente(): void {
    if (!this.haySprintSiguiente) return;
    this.irASprintPorIndice(this.indiceSprintActual + 1);
  }

  private irASprintPorIndice(indice: number): void {
    const destino = this.sprints[indice];
    if (!destino) return;
    this.sprintSeleccionadoId = destino.id;
    this.onSprintChange();
  }

  /**
   * FASE 16: fuente de verdad = métricas realmente aprobadas del proyecto
   * (GET /api/planeacion/{proyectoId}/metricas, ya existente). Sin ninguna
   * lista hardcodeada ni condición especial por código/UUID/categoría — una
   * métrica creada con IA que ya esté aprobada aparece exactamente igual que
   * cualquier otra.
   */
  private cargarMetricasAprobadas(): void {
    if (!this.proyecto || !this.sprintActual) return;
    this.cargandoMetricas = true;

    this.planeacionService.listarMetricas(this.proyecto.id).pipe(
      catchError(() => of([]))
    ).subscribe(todas => {
      const aprobadas = todas.filter(m => m.aprobada);
      this.metricas = aprobadas.map(m => ({
        metricaId: m.metricaId,
        nombre: m.nombre,
        cargando: true,
        sinParametrizacion: false,
        variables: []
      }));
      this.cargandoMetricas = false;
      for (const m of this.metricas) this.cargarVariablesDeMetrica(m);
    });
  }

  private cargarVariablesDeMetrica(m: MetricaEjecucion): void {
    if (!this.proyecto || !this.sprintActual) return;
    const proyectoId = this.proyecto.id;
    const sprintId = this.sprintActual.id;

    forkJoin({
      parametrizacion: this.metricaAcademicaService.obtenerParametrizacionAprobada(m.metricaId, proyectoId)
        .pipe(catchError(() => of(null))),
      variablesResp: this.variableService.obtenerVariables(m.metricaId, proyectoId, sprintId)
        .pipe(catchError(() => of(null))),
      detalle: this.evaluacionService.detalle(proyectoId).pipe(catchError(() => of([])))
    }).subscribe(({ parametrizacion, variablesResp, detalle }) => {
      m.cargando = false;
      m.sinParametrizacion = !parametrizacion;
      if (!variablesResp) { m.variables = []; return; }

      m.variables = variablesResp.variables.map(v => this.construirBloque(v, detalle));
    });
  }

  private construirBloque(v: VariableConValor, detalle: MetricaEvaluacionDetalleDto[]): BloqueVariable {
    const detalleVariable = detalle.find(d => d.variableId === v.id);
    // Solo los registros del sprint actualmente seleccionado: la gráfica y el
    // estado de captura de Ejecución reflejan la evolución DENTRO de este
    // sprint, nunca mezclada con los valores de otros sprints (esa
    // comparación entre sprints vive en Evaluación).
    const capturas = this.capturasDelSprintActual(detalleVariable);
    const puntos: PuntoMiniChart[] = capturas
      .map(r => ({ fecha: r.registradoAt, valor: r.valor }))
      .reverse(); // capturas viene más-reciente-primero; la gráfica quiere orden cronológico

    const frecuenciaCaptura = v.frecuenciaCaptura || detalleVariable?.frecuenciaCaptura || 'por_sprint';
    const capturaVigente = capturas[0] ?? null;

    return {
      variableId: v.id,
      nombre: this.humanizarNombre(v.nombre),
      descripcion: v.descripcion || '',
      tipoDato: v.tipoDato,
      frecuenciaCaptura,
      unidad: v.unidad,
      escalaMin: v.escalaMin,
      escalaMax: v.escalaMax,
      fecha: capturaVigente ? capturaVigente.registradoAt.substring(0, 10) : this.fechaCapturaPorDefecto(),
      valorNum: v.valorNum ?? null,
      valorTexto: v.valorTexto ?? '',
      valorBool: v.valorBool ?? false,
      registrando: false,
      error: '',
      ultimoMensaje: '',
      puntos,
      capturas,
      // 'por_sprint' con un valor ya registrado arranca colapsado (resumen de
      // solo lectura); cualquier otro caso arranca mostrando el formulario.
      editando: !(frecuenciaCaptura === 'por_sprint' && capturas.length > 0),
      registroEditandoId: null
    };
  }

  /** Registros del sprint actualmente seleccionado para una variable, más reciente primero. */
  private capturasDelSprintActual(detalleVariable: MetricaEvaluacionDetalleDto | undefined): RegistroPuntoDto[] {
    if (!detalleVariable || !this.sprintActual) return [];
    return detalleVariable.registros
      .filter(r => r.sprintNumero === this.sprintActual!.numero)
      .slice()
      .sort((a, b) => new Date(b.registradoAt).getTime() - new Date(a.registradoAt).getTime());
  }

  /**
   * Fecha inicial del input de captura: hoy si cae dentro del rango del
   * sprint; si no (sprint ya finalizado con fechaFin pasada, o que todavía
   * no arrancó), la fecha válida más cercana dentro del sprint — así el
   * usuario nunca arranca con una fecha que el backend va a rechazar.
   */
  private fechaCapturaPorDefecto(): string {
    const hoy = hoyISO();
    if (!this.sprintActual) return hoy;
    const { fechaInicio, fechaFin } = this.sprintActual;
    if (fechaInicio && hoy < fechaInicio) return fechaInicio;
    if (fechaFin && hoy > fechaFin) return fechaFin;
    return hoy;
  }

  private humanizarNombre(nombre: string): string {
    const legible = nombre.replace(/_/g, ' ').trim();
    if (!legible) return nombre;
    return legible.charAt(0).toUpperCase() + legible.slice(1);
  }

  /** yyyy-MM-dd → instante ISO determinístico (medianoche UTC de ese día). */
  private fechaAInstant(fecha: string): string {
    return `${fecha}T00:00:00Z`;
  }

  registrarValor(m: MetricaEjecucion, v: BloqueVariable): void {
    if (!this.proyecto || !this.sprintActual || !v.fecha) return;
    v.error = '';
    v.ultimoMensaje = '';

    const valorFaltante =
      (v.tipoDato === 'numerico' && (v.valorNum === null || v.valorNum === undefined)) ||
      (v.tipoDato === 'texto' && !v.valorTexto.trim());
    if (valorFaltante) {
      v.error = 'Ingresá un valor antes de registrar.';
      return;
    }

    // Validación de rango en frontend: nunca dejar que el usuario descubra el
    // límite recién después de pulsar "Registrar" — se rechaza acá mismo,
    // con el mismo rango que valida el backend (EjecucionService.
    // validarRangoValor), como defensa en profundidad de ambos lados.
    if (v.tipoDato === 'numerico' && v.escalaMin != null && v.escalaMax != null && v.valorNum != null) {
      if (v.valorNum < v.escalaMin || v.valorNum > v.escalaMax) {
        v.error = `El valor debe estar entre ${v.escalaMin} y ${v.escalaMax}.`;
        return;
      }
    }

    v.registrando = true;
    const proyectoId = this.proyecto.id;

    this.variableService.guardarValores(m.metricaId, {
      proyectoId,
      sprintId: this.sprintActual.id,
      valores: [{
        variableId: v.variableId,
        valorNum: v.tipoDato === 'numerico' ? (v.valorNum ?? undefined) : undefined,
        valorTexto: v.tipoDato === 'texto' ? v.valorTexto : undefined,
        valorBool: v.tipoDato === 'booleano' ? v.valorBool : undefined,
        fechaCaptura: this.fechaAInstant(v.fecha),
        // Revisión de Ejecución: si se está editando una captura existente,
        // el backend actualiza SIEMPRE esa misma fila por ID (nunca crea una
        // nueva), sin importar si la fecha cambió.
        registroId: v.registroEditandoId ?? undefined
      }]
    }).pipe(
      catchError(err => {
        // Superficie el mensaje real del backend (fecha fuera de sprint,
        // frecuencia ya satisfecha, valor fuera de rango, etc. — ver
        // EjecucionService) en vez de un genérico que oculta la causa real.
        v.error = err?.status === 403
          ? 'No tienes permiso para registrar valores.'
          : (err?.error?.error || 'No se pudo registrar el valor.');
        v.registrando = false;
        return of(null);
      })
    ).subscribe(resultado => {
      if (resultado === null && v.error) return;
      v.registrando = false;
      v.ultimoMensaje = 'Valor registrado.';
      // La edición terminó: el próximo "Registrar valor" es una captura
      // nueva otra vez, salvo que se abra explícitamente otra edición.
      v.registroEditandoId = null;
      // 'por_sprint' vuelve al resumen de solo lectura tras guardar.
      if (v.frecuenciaCaptura === 'por_sprint') v.editando = false;
      // La gráfica y el estado de captura se reconstruyen siempre a partir de
      // datos reales ya persistidos — nunca se agrega el punto localmente sin
      // confirmar — y siempre acotados al sprint actual (nunca mezclando
      // otros sprints, el mismo criterio que construirBloque()).
      this.evaluacionService.detalle(proyectoId).pipe(catchError(() => of([]))).subscribe(detalle => {
        const detalleVariable = detalle.find(d => d.variableId === v.variableId);
        v.capturas = this.capturasDelSprintActual(detalleVariable);
        v.puntos = v.capturas.map(r => ({ fecha: r.registradoAt, valor: r.valor })).reverse();
      });
    });
  }

  /** Carga una captura ya registrada en el formulario para corregirla (nunca crea una fila nueva). */
  editarValorExistente(v: BloqueVariable, registro: RegistroPuntoDto): void {
    v.fecha = registro.registradoAt.substring(0, 10);
    if (v.tipoDato === 'numerico') v.valorNum = registro.valor;
    v.editando = true;
    v.error = '';
    v.ultimoMensaje = '';
    // Identifica de forma inequívoca la fila a actualizar — así el backend
    // nunca la confunde con una captura nueva, aunque se cambie la fecha.
    v.registroEditandoId = registro.id;
  }

  /** Cancela la edición de una variable 'por_sprint' y vuelve al resumen de solo lectura. */
  cancelarEdicion(v: BloqueVariable): void {
    v.editando = false;
    v.error = '';
    v.registroEditandoId = null;
  }

  /** Rangos de escala pequeños y discretos (ej. 1-5) se capturan con un selector de botones. */
  esEscalaDiscretaPequena(v: BloqueVariable): boolean {
    if (v.tipoDato !== 'numerico' || v.escalaMin == null || v.escalaMax == null) return false;
    const rango = v.escalaMax - v.escalaMin;
    return Number.isInteger(v.escalaMin) && Number.isInteger(v.escalaMax) && rango >= 1 && rango <= 9;
  }

  opcionesEscala(v: BloqueVariable): number[] {
    if (v.escalaMin == null || v.escalaMax == null) return [];
    const opciones: number[] = [];
    for (let i = v.escalaMin; i <= v.escalaMax; i++) opciones.push(i);
    return opciones;
  }

  labelFrecuencia(f: string): string {
    return ({ diaria: 'Diaria', semanal: 'Semanal', por_sprint: 'Por sprint', ilimitada: 'Ilimitada' } as Record<string, string>)[f] ?? f;
  }

  labelEstado(e: string): string {
    return ({ 'en_ejecucion': 'En ejecución', 'pendiente': 'Pendiente', 'finalizado': 'Finalizado', 'reabierto': 'Reabierto' } as Record<string, string>)[e] ?? e;
  }

  badgeSprint(e: string): string {
    return ({ 'en_ejecucion': 'bg-success', 'pendiente': 'bg-warning text-dark', 'finalizado': 'bg-secondary', 'reabierto': 'bg-info text-dark' } as Record<string, string>)[e] ?? 'bg-secondary';
  }
}
