// Autor: Cristian Santiago Martinez Cordoba — PRODOX
// Fase 16.9.2-A: Componente para ejecución de métricas académicas
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ShellComponent } from '../../layout/shell/shell.component';
import { MetricaAcademicaService } from '../../services/metrica-academica.service';
import { SprintService } from '../../services/sprint.service';
import { VariableDinamicaService, VariableConValor } from '../../services/variable-dinamica.service';
import {
  ParametrizacionAcademicaDto,
  EjecutarMetricaAcademicaRequest,
  ResultadoMetricaDto,
  InterpretacionIADto,
  VariableAcademica
} from '../../models/metrica-academica.model';
import { SprintDto } from '../../models/sprint.model';
import { ProyectoDto } from '../../models/proyecto.model';

@Component({
  selector: 'app-metrica-academica',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell [title]="metricaNombre">
      <div *ngIf="loading" class="text-center py-5">
        <div class="spinner-border text-primary"></div>
        <p class="text-muted mt-3">Cargando...</p>
      </div>
      
      <div *ngIf="error" class="alert alert-danger">
        <i class="bi bi-exclamation-triangle me-2"></i>{{ error }}
        <button class="btn btn-sm btn-outline-secondary mt-2" (click)="volver()">Volver</button>
      </div>
      
      <div *ngIf="!loading && !error">
        <div *ngIf="sinParametrizacion" class="alert alert-warning">
          <i class="bi bi-exclamation-triangle me-2"></i>
          <strong>No existe una parametrización aprobada para esta métrica.</strong>
          <p class="mb-2 mt-2 small">Para ejecutar esta métrica académica, primero debes crear y aprobar una parametrización desde el módulo de Planeación.</p>
          <div class="d-flex gap-2">
            <button class="btn btn-sm btn-primary" (click)="irAPlaneacion()">
              <i class="bi bi-layers me-1"></i>Ir a Planeación
            </button>
            <button class="btn btn-sm btn-outline-secondary" (click)="volver()">
              <i class="bi bi-arrow-left me-1"></i>Volver
            </button>
          </div>
        </div>
        
        <div *ngIf="parametrizacionPropuesta" class="alert alert-warning">
          <i class="bi bi-hourglass-split me-2"></i>
          <strong>Existe una parametrización propuesta, pero aún no ha sido aprobada.</strong>
        </div>
        
        <div *ngIf="parametrizacion && parametrizacion.status === 'aprobada'" class="card mb-3">
          <div class="card-body">
            <p class="small text-muted mb-2" *ngIf="parametrizacion.objetivo">{{ parametrizacion.objetivo }}</p>
            <div class="row g-2 small">
              <div class="col-md-4" *ngIf="sprintActivo">
                <strong>Sprint / período:</strong>
                Sprint {{ sprintActivo.numero }}
                <span *ngIf="sprintActivo.fechaInicio">
                  ({{ sprintActivo.fechaInicio | date:'dd/MM/yyyy' }} - {{ sprintActivo.fechaFin | date:'dd/MM/yyyy' }})
                </span>
              </div>
              <div class="col-md-4"><strong>Frecuencia:</strong> {{ frecuenciaLabel(parametrizacion.frecuenciaCaptura) }}</div>
              <div class="col-md-4"><strong>Versión aprobada:</strong> v{{ parametrizacion.version }}</div>
            </div>
          </div>
        </div>

        <div *ngIf="parametrizacion && parametrizacion.status === 'aprobada'" class="card mb-3">
          <div class="card-header bg-success bg-opacity-10">
            <strong>Parametrización Aprobada</strong> <span class="badge bg-success ms-2">v{{ parametrizacion.version }}</span>
          </div>
          <div class="card-body">
            <div class="row g-2 small">
              <div class="col-12"><strong>Fuente:</strong> {{ parametrizacion.fuenteAcademica }}</div>
              <div class="col-md-6"><strong>Fórmula:</strong> {{ parametrizacion.formulaAcademica }}</div>
              <div class="col-md-3"><strong>Operación:</strong> {{ parametrizacion.tipoOperacion }}</div>
              <div class="col-md-3"><strong>Unidad:</strong> {{ parametrizacion.unidadResultado }}</div>
            </div>
          </div>
        </div>

        <div *ngIf="parametrizacion && parametrizacion.status === 'aprobada'" class="card mb-3">
          <div class="card-header"><strong>Datos a capturar</strong></div>
          <div class="card-body">
            <div *ngIf="cargandoVariables" class="text-center py-3">
              <div class="spinner-border spinner-border-sm text-primary"></div>
              <span class="text-muted ms-2">Cargando variables...</span>
            </div>

            <div *ngIf="!cargandoVariables && errorVariables" class="alert alert-danger">
              <i class="bi bi-exclamation-triangle me-1"></i>{{ errorVariables }}
            </div>

            <div *ngIf="!cargandoVariables && !errorVariables && variables.length === 0" class="alert alert-warning">
              <i class="bi bi-exclamation-triangle me-1"></i>
              La parametrización aprobada no tiene variables configuradas.
            </div>

            <ng-container *ngIf="!cargandoVariables && !errorVariables && variables.length > 0">
              <div class="table-responsive">
                <table class="table table-sm align-middle">
                  <thead class="table-light">
                    <tr><th>Variable</th><th>Descripción</th><th>Tipo</th><th>Valor</th></tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let v of variables">
                      <td class="small">
                        <div class="fw-semibold">{{ v.nombreHumano }} <span *ngIf="v.requerida" class="text-danger">*</span></div>
                        <div class="text-muted" style="font-size:0.7rem"><code>{{ v.nombre }}</code></div>
                      </td>
                      <td class="small text-muted">{{ v.etiqueta }}</td>
                      <td class="small text-muted">{{ v.tipo }}</td>
                      <td style="min-width:180px">
                        <div class="input-group input-group-sm" style="max-width:220px">
                          <ng-container [ngSwitch]="v.tipo">
                            <input *ngSwitchCase="'TEXT'" type="text" class="form-control"
                                   [(ngModel)]="valores[v.nombre]"
                                   [class.is-invalid]="mostrarErrores && !esValido(v)"
                                   [attr.aria-label]="v.etiqueta">
                            <div *ngSwitchCase="'BOOLEAN'" class="form-check form-switch pt-1">
                              <input class="form-check-input" type="checkbox"
                                     [(ngModel)]="valores[v.nombre]"
                                     [attr.aria-label]="v.etiqueta">
                            </div>
                            <input *ngSwitchDefault type="number" class="form-control"
                                   [step]="v.tipo === 'INTEGER' ? 1 : 0.01"
                                   [(ngModel)]="valores[v.nombre]"
                                   [class.is-invalid]="mostrarErrores && !esValido(v)"
                                   [attr.aria-label]="v.etiqueta" min="0">
                          </ng-container>
                          <span class="input-group-text" *ngIf="v.unidad">{{ v.unidad }}</span>
                        </div>
                        <div *ngIf="mostrarErrores && !esValido(v)" class="invalid-feedback d-block">
                          Ingresa un valor válido.
                        </div>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <div class="alert alert-light border small mb-3" *ngIf="parametrizacion.tipoOperacion">
                <div><strong>Operación:</strong> {{ parametrizacion.tipoOperacion }}</div>
                <div *ngIf="parametrizacion.formulaAcademica"><strong>Fórmula:</strong> {{ parametrizacion.formulaAcademica }}</div>
                <div *ngIf="parametrizacion.unidadResultado"><strong>Unidad:</strong> {{ parametrizacion.unidadResultado }}</div>
                <div class="text-muted mt-1">Estos valores se combinarán mediante {{ parametrizacion.tipoOperacion }}.</div>
              </div>

              <button class="btn btn-primary" [disabled]="ejecutando" (click)="ejecutar()">
                <span *ngIf="ejecutando" class="spinner-border spinner-border-sm me-2"></span>
                {{ ejecutando ? 'Ejecutando...' : 'Ejecutar' }}
              </button>
            </ng-container>

            <div *ngIf="errorEjecucion" class="alert alert-danger mt-3 mb-0">
              <i class="bi bi-exclamation-triangle me-1"></i>{{ errorEjecucion }}
            </div>
          </div>
        </div>

        <div *ngIf="!resultado && parametrizacion && parametrizacion.status === 'aprobada' && !cargandoVariables"
             class="alert alert-secondary small mb-3">
          <i class="bi bi-info-circle me-1"></i>Aún no hay un resultado calculado para este sprint.
        </div>

        <div *ngIf="resultado" class="card mb-3 border-success">
          <div class="card-header bg-success text-white"><strong>Resultado calculado</strong></div>
          <div class="card-body">
            <div class="row g-2 small mb-2">
              <div class="col-md-3"><strong>Tipo de operación:</strong> {{ resultado.tipoCalculo }}</div>
              <div class="col-md-6" *ngIf="resultado.expresion"><strong>Fórmula:</strong> {{ resultado.expresion }}</div>
              <div class="col-md-3"><strong>Unidad:</strong> {{ resultado.unidad || '—' }}</div>
            </div>
            <h3 class="text-success mb-1">{{ resultado.resultado }} <span class="fs-6 text-muted">{{ resultado.unidad }}</span></h3>
            <p class="small text-muted mb-2"><i class="bi bi-lock-fill me-1"></i>Calculado automáticamente — no editable.</p>
            <p class="small text-muted mb-3">
              Sprint: Sprint {{ sprintActivo?.numero }} | Versión: v{{ resultado.parametrizacionVersion }} |
              {{ resultado.calculadoAt | date:'dd/MM/yyyy HH:mm' }}
            </p>
            <button class="btn btn-sm btn-outline-primary" [disabled]="interpretando" (click)="analizarConIA()">
              <span *ngIf="interpretando" class="spinner-border spinner-border-sm me-2"></span>
              <i class="bi bi-robot me-1"></i>
              {{ interpretando ? 'Analizando...' : 'Analizar con IA' }}
            </button>
            <div *ngIf="interpretacion" class="alert alert-info mt-3 mb-0">
              <strong><i class="bi bi-robot me-1"></i>Interpretación IA:</strong>
              <div>{{ interpretacion.interpretacion }}</div>
            </div>
            <div *ngIf="errorInterpretacion" class="alert alert-danger mt-3 mb-0">
              <i class="bi bi-exclamation-triangle me-1"></i>{{ errorInterpretacion }}
            </div>
          </div>
        </div>
        
        <div *ngIf="historico.length > 0" class="card">
          <div class="card-header"><strong>Histórico</strong></div>
          <div class="table-responsive">
            <table class="table table-sm table-hover mb-0">
              <thead class="table-light">
                <tr><th>Sprint</th><th>Resultado</th><th>Versión</th><th>Fecha</th><th></th></tr>
              </thead>
              <tbody>
                <tr *ngFor="let h of historico">
                  <td>{{ nombreSprint(h.sprintId) }}</td>
                  <td class="fw-semibold">{{ h.resultado }} {{ h.unidad }}</td>
                  <td><span class="badge bg-secondary">v{{ h.parametrizacionVersion }}</span></td>
                  <td class="small">{{ h.calculadoAt | date:'dd/MM/yyyy HH:mm' }}</td>
                  <td><button class="btn btn-sm btn-outline-primary" (click)="verDetalle(h)"><i class="bi bi-eye"></i></button></td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </app-shell>
  `,
  styles: []
})
export class MetricaAcademicaComponent implements OnInit {
  metricaId = '';
  metricaNombre = 'Métrica académica';
  loading = true;
  error = '';
  proyectoActivo: ProyectoDto | null = null;
  sprintActivo: SprintDto | null = null;
  parametrizacion: ParametrizacionAcademicaDto | null = null;
  sinParametrizacion = false;
  parametrizacionPropuesta = false;
  variables: VariableAcademica[] = [];
  cargandoVariables = false;
  errorVariables = '';
  valores: { [key: string]: any } = {};
  mostrarErrores = false;
  ejecutando = false;
  errorEjecucion = '';
  resultado: ResultadoMetricaDto | null = null;
  historico: ResultadoMetricaDto[] = [];
  interpretando = false;
  interpretacion: InterpretacionIADto | null = null;
  errorInterpretacion = '';
  /** id de sprint -> número, para mostrar "Sprint N" en vez del UUID en el histórico. */
  sprintsPorId: { [id: string]: number } = {};

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private metricaService: MetricaAcademicaService,
    private variableService: VariableDinamicaService,
    private sprintService: SprintService
  ) {}

  ngOnInit(): void {
    this.metricaId = this.route.snapshot.paramMap.get('id') || '';
    this.cargarContexto();
  }

  cargarContexto(): void {
    const pStr = localStorage.getItem('mpdia_proyecto_activo');
    const sStr = localStorage.getItem('mpdia_sprint_activo');
    if (!pStr || !sStr) {
      this.error = 'No hay un proyecto o sprint activo seleccionado';
      this.loading = false;
      return;
    }
    this.proyectoActivo = JSON.parse(pStr);
    this.sprintActivo = JSON.parse(sStr);
    this.cargarParametrizacion();
    this.cargarSprints();
  }

  /** Solo para resolver "Sprint N" en el histórico — no inventa nombres si falla. */
  cargarSprints(): void {
    if (!this.proyectoActivo) return;
    this.sprintService.listar(this.proyectoActivo.id).subscribe({
      next: (sprints) => {
        this.sprintsPorId = {};
        for (const s of sprints) {
          this.sprintsPorId[s.id] = s.numero;
        }
      },
      error: () => { /* degrada a mostrar el id crudo, ver nombreSprint() */ }
    });
  }

  nombreSprint(sprintId: string): string {
    const numero = this.sprintsPorId[sprintId];
    return numero !== undefined ? 'Sprint ' + numero : 'Sprint ' + sprintId;
  }

  cargarParametrizacion(): void {
    if (!this.proyectoActivo) return;
    this.metricaService.obtenerParametrizacionAprobada(this.metricaId, this.proyectoActivo.id).subscribe({
      next: (param) => {
        if (param) {
          this.parametrizacion = param;
          if (param.status === 'propuesta') {
            this.parametrizacionPropuesta = true;
          } else {
            this.sinParametrizacion = false;
          }
        } else {
          this.sinParametrizacion = true;
        }
        this.loading = false;
        if (param && param.status === 'aprobada') {
          this.cargarHistorico();
          this.cargarVariables();
        }
      },
      error: (err) => {
        if (err.status === 204) {
          this.sinParametrizacion = true;
        } else {
          this.error = 'Error al cargar parametrización';
        }
        this.loading = false;
      }
    });
  }

  cargarVariables(): void {
    if (!this.proyectoActivo || !this.sprintActivo) return;
    this.cargandoVariables = true;
    this.errorVariables = '';
    this.variableService.obtenerVariables(this.metricaId, this.proyectoActivo.id, this.sprintActivo.id).subscribe({
      next: (response) => {
        this.variables = response.variables.map(v => this.mapVariable(v, response.variables.length));
        // Precargar el valor ya capturado (si existe) para no volver a pedirlo.
        this.valores = {};
        for (const v of response.variables) {
          const existente = this.valorExistente(v);
          if (existente !== null) {
            this.valores[v.nombre] = existente;
          }
        }
        this.cargandoVariables = false;
      },
      error: () => {
        this.errorVariables = 'Error al cargar las variables de la parametrización';
        this.cargandoVariables = false;
      }
    });
  }

  private mapVariable(v: VariableConValor, totalVariables: number): VariableAcademica {
    // parametrizacion.indicadorVariable es un texto único de la parametrización
    // (no uno por variable) — solo es fiable usarlo como descripción cuando hay
    // exactamente 1 variable, que es el único caso que el sistema soporta hoy
    // (ninguna parametrización real ha producido más de una variable).
    const descripcionAcademica = totalVariables === 1 ? this.parametrizacion?.indicadorVariable : null;
    return {
      nombre: v.nombre,
      nombreHumano: this.humanizarNombre(v.nombre),
      etiqueta: descripcionAcademica || v.descripcion || v.nombre,
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

  esValido(variable: VariableAcademica): boolean {
    const valor = this.valores[variable.nombre];
    if (variable.tipo === 'BOOLEAN') {
      return valor === true || valor === false;
    }
    if (variable.tipo === 'TEXT') {
      return valor !== null && valor !== undefined && String(valor).trim() !== '';
    }
    // INTEGER | DECIMAL
    if (valor === null || valor === undefined || valor === '') return false;
    const n = Number(valor);
    if (Number.isNaN(n) || n < 0) return false;
    if (variable.tipo === 'INTEGER' && !Number.isInteger(n)) return false;
    return true;
  }

  ejecutar(): void {
    this.mostrarErrores = true;
    if (this.variables.length === 0) return;
    const todasValidas = this.variables.every(v => this.esValido(v));
    if (!todasValidas) return;
    if (!this.proyectoActivo || !this.sprintActivo) return;
    this.ejecutando = true;
    this.errorEjecucion = '';
    this.metricaService.ejecutar(this.metricaId, {
      proyectoId: this.proyectoActivo.id,
      sprintId: this.sprintActivo.id,
      valores: this.valores
    }).subscribe({
      next: (r) => {
        this.resultado = r;
        this.metricaNombre = r.metricaNombre;
        this.ejecutando = false;
        this.mostrarErrores = false;
        this.cargarHistorico();
      },
      error: (e) => {
        this.ejecutando = false;
        this.errorEjecucion = e.status === 403 ? 'No tienes permiso para ejecutar esta métrica' :
                              e.status === 409 ? 'La parametrización no está aprobada o hay un conflicto de estado' :
                              e.status === 400 ? (e.error?.message || 'Datos inválidos') :
                              'Error al ejecutar la métrica';
      }
    });
  }

  cargarHistorico(): void {
    if (!this.proyectoActivo) return;
    this.metricaService.obtenerHistorico(this.metricaId, this.proyectoActivo.id).subscribe({
      next: (h) => {
        this.historico = h;
        if (h.length > 0) {
          this.metricaNombre = h[0].metricaNombre;
        }
        this.cargarUltimoResultadoDelSprint();
      },
      error: () => {}
    });
  }

  /**
   * Muestra el último resultado YA CALCULADO para el sprint actual, si existe,
   * sin volver a ejecutar la métrica ni crear una fila nueva en
   * resultados_metricas. El histórico ya viene ordenado por fecha
   * descendente (ResultadoMetricaRepository.findByMetrica_IdAndProyectoIdOrderByCalculadoAtDesc),
   * así que el primer elemento que coincide con el sprint actual es el vigente.
   */
  private cargarUltimoResultadoDelSprint(): void {
    if (!this.sprintActivo || this.resultado) return; // no pisar un resultado recién ejecutado en esta sesión
    const existente = this.historico.find(h => h.sprintId === this.sprintActivo!.id);
    if (existente) {
      this.resultado = existente;
    }
  }

  analizarConIA(): void {
    if (!this.resultado) return;
    this.interpretando = true;
    this.errorInterpretacion = '';
    this.interpretacion = null;
    this.metricaService.solicitarInterpretacion(this.resultado.resultadoId).subscribe({
      next: (i) => {
        this.interpretacion = i;
        this.interpretando = false;
      },
      error: (e) => {
        this.interpretando = false;
        this.errorInterpretacion = e.status === 403 ? 'No tienes permiso para solicitar interpretación IA' :
                                    e.status === 400 ? 'Resultado no encontrado' :
                                    'Error al solicitar interpretación IA';
      }
    });
  }

  frecuenciaLabel(f: string): string {
    const l: any = {'por_sprint': 'Por sprint', 'semanal': 'Semanal', 'diaria': 'Diaria', 'ilimitada': 'Cuando ocurra el evento'};
    return l[f] || f;
  }

  verDetalle(r: ResultadoMetricaDto): void {
    this.resultado = r;
    this.interpretacion = null;
  }

  irAParametrizacion(): void {
    this.router.navigate(['/parametrizacion', this.metricaId]);
  }

  irAPlaneacion(): void {
    this.router.navigate(['/planeacion']);
  }

  volver(): void {
    this.router.navigate(['/planeacion']);
  }
}
