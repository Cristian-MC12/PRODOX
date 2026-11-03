// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ShellComponent } from '../../layout/shell/shell.component';
import { MetricaIAService } from '../../services/metrica-ia.service';
import { SeleccionService } from '../../services/seleccion.service';
import { PlaneacionService } from '../../services/planeacion.service';
import { MetricaExistenteDto, MetricaIAPropuestaDto, PosibleDuplicadoDto } from '../../models/metrica-ia.model';
import { EscalaTipo, Parametrizacion } from '../../models/seleccion.model';
import { ProyectoDto } from '../../models/proyecto.model';

type Paso = 'necesidad' | 'revision';
type ModoCreacion = 'ia' | 'manual';

export interface FormularioMetrica extends Parametrizacion {
  categoriaId: number;
  nombre: string;
  descripcion: string;
  // Campos de compatibilidad con propuestas IA
  queMide?: string;
  variablesSugeridas?: string;
  tipoOperacionSugerido?: string;
  formulaSugerida?: string;
  fuenteSugerida?: string;
}

/**
 * FASE 15 — "Crear métrica con IA" y Creación manual directa.
 *
 * Regla fundamental: LA IA PROPONE, EL SCRUM MASTER DECIDE. Esta pantalla
 * nunca persiste nada hasta que el Scrum Master pulsa explícitamente
 * "Guardar y continuar" — ni siquiera al generar la propuesta con Gemini.
 * Al confirmar, se crea la Metrica y se asocia al proyecto reutilizando el
 * flujo existente (PlaneacionService.seleccionar), y se continúa
 * por el flujo de parametrización con todos los campos ya validados y precargados.
 */
@Component({
  selector: 'app-crear-metrica-ia',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell title="Crear métrica">

      @if (!proyecto) {
        <div class="text-center py-5 text-muted">
          <i class="bi bi-folder-x fs-1 d-block mb-3 opacity-25"></i>
          <p>Seleccioná un proyecto primero.</p>
          <button class="btn btn-primary btn-sm" (click)="router.navigate(['/proyectos'])">
            Ir a Proyectos
          </button>
        </div>
      } @else {

        <nav aria-label="breadcrumb" class="mb-3">
          <ol class="breadcrumb small mb-0">
            <li class="breadcrumb-item">
              <a href="#" (click)="$event.preventDefault(); router.navigate(['/planeacion'])">
                <i class="bi bi-layers me-1"></i>Planeación
              </a>
            </li>
            <li class="breadcrumb-item active">
              {{ modo === 'ia' ? 'Crear métrica con IA' : 'Crear métrica manualmente' }}
            </li>
          </ol>
        </nav>

        <!-- Selector de modo (Pestañas IA / Manual) -->
        <div class="d-flex justify-content-between align-items-center mb-3">
          <ul class="nav nav-pills small">
            <li class="nav-item">
              <button class="nav-link py-1 px-3" [class.active]="modo === 'ia'"
                      (click)="cambiarModo('ia')">
                <i class="bi bi-stars me-1"></i>Generar con IA
              </button>
            </li>
            <li class="nav-item">
              <button class="nav-link py-1 px-3" [class.active]="modo === 'manual'"
                      (click)="cambiarModo('manual')">
                <i class="bi bi-pencil-square me-1"></i>Crear manualmente
              </button>
            </li>
          </ul>

          @if (modo === 'ia' && paso === 'revision') {
            <button class="btn btn-outline-secondary btn-sm" (click)="volverANecesidad()">
              <i class="bi bi-arrow-repeat me-1"></i>Modificar solicitud de IA
            </button>
          }
        </div>

        <!-- MODO IA: PASO 1 - NECESIDAD EN TEXTO LIBRE -->
        @if (modo === 'ia' && paso === 'necesidad') {
          <div class="card mb-4">
            <div class="card-header d-flex align-items-center gap-2">
              <i class="bi bi-robot text-primary fs-5"></i>
              <div>
                <div class="fw-semibold small">¿Qué necesitás medir?</div>
                <div class="text-muted" style="font-size:0.75rem">
                  Describilo en tus propias palabras (soporta descripciones detalladas). La IA propondrá una estructura completa que vas a poder revisar, ajustar y validar.
                </div>
              </div>
            </div>
            <div class="card-body">
              <textarea class="form-control" rows="4" maxlength="10000"
                        placeholder="Ej: Quiero medir el tiempo que toma resolver un defecto de alta severidad desde que se reporta hasta su cierre..."
                        [(ngModel)]="necesidad"></textarea>
              <div class="d-flex justify-content-between align-items-center mt-1">
                <span class="text-muted small" style="font-size:0.72rem">
                  Podés pegar un texto extenso o detallado sobre tu proceso.
                </span>
                <span class="text-muted small" style="font-size:0.72rem">
                  {{ necesidad.length }} / 10000 caracteres
                </span>
              </div>
              @if (error) {
                <div class="alert alert-danger small mt-3 mb-0 py-2">
                  <i class="bi bi-exclamation-triangle me-1"></i>{{ error }}
                </div>
              }
            </div>
            <div class="card-footer d-flex justify-content-between">
              <button class="btn btn-outline-secondary btn-sm" (click)="router.navigate(['/planeacion'])">
                <i class="bi bi-arrow-left me-1"></i>Volver a Planeación
              </button>
              <button class="btn btn-primary btn-sm" [disabled]="!necesidad.trim() || generando"
                      (click)="generarPropuesta()">
                @if (generando) {
                  <span class="spinner-border spinner-border-sm me-2"></span>Generando propuesta...
                } @else {
                  <i class="bi bi-stars me-2"></i>Generar propuesta
                }
              </button>
            </div>
          </div>
        }

        <!-- FORMULARIO COMPLETO: CORRESPONDE EXACTAMENTE CON PARAMETRIZACIÓN -->
        @if (paso === 'revision' || modo === 'manual') {
          <div class="card" [class.border-primary]="modo === 'ia'">
            <div class="card-header d-flex align-items-center justify-content-between"
                 [class.bg-primary]="modo === 'ia'"
                 [class.bg-opacity-10]="modo === 'ia'">
              <div>
                <div class="fw-semibold small">
                  @if (modo === 'ia') {
                    <i class="bi bi-stars me-1 text-primary"></i>Propuesta de métrica generada por IA
                  } @else {
                    <i class="bi bi-pencil-square me-1 text-primary"></i>Nueva métrica manual
                  }
                </div>
                <div class="text-muted" style="font-size:0.72rem">
                  @if (modo === 'ia') {
                    <i class="bi bi-info-circle me-1"></i>Revisá y completá todos los campos antes de guardar.
                  } @else {
                    <i class="bi bi-info-circle me-1"></i>Ingresá los datos del catálogo y su parametrización completa.
                  }
                </div>
              </div>
              <span class="badge" [class.bg-primary]="modo === 'ia'" [class.bg-secondary]="modo === 'manual'">
                {{ modo === 'ia' ? 'Asistido por IA' : 'Configuración manual' }}
              </span>
            </div>

            <div class="card-body">
              <div class="row g-3">

                <!-- 1. IDENTIFICACIÓN DE LA MÉTRICA -->
                <div class="col-12">
                  <h6 class="fw-semibold small mb-0 text-muted">
                    <i class="bi bi-card-heading me-1"></i>1. Definición en catálogo
                  </h6>
                  <hr class="mt-1 mb-3">
                </div>

                <div class="col-md-8">
                  <label class="form-label small fw-semibold">
                    Nombre de la métrica <span class="text-danger">*</span>
                  </label>
                  <input type="text" class="form-control form-control-sm" maxlength="120"
                         placeholder="Ej: Tasa de resolución de defectos"
                         [(ngModel)]="edit.nombre">
                  <div class="d-flex justify-content-between text-muted" style="font-size:0.7rem">
                    <span>Máximo 120 caracteres</span>
                    <span>{{ edit.nombre.length || 0 }} / 120</span>
                  </div>
                </div>

                <div class="col-md-4">
                  <label class="form-label small fw-semibold">
                    Categoría <span class="text-danger">*</span>
                  </label>
                  <select class="form-select form-select-sm" [(ngModel)]="edit.categoriaId">
                    @for (cat of categorias; track cat.id) {
                      <option [value]="cat.id">{{ cat.nombre }}</option>
                    }
                  </select>
                </div>

                <div class="col-12">
                  <label class="form-label small fw-semibold">
                    Descripción <span class="text-danger">*</span>
                  </label>
                  <textarea class="form-control form-control-sm" rows="2"
                            placeholder="Describe qué representa esta métrica y cómo impacta en el proyecto..."
                            [(ngModel)]="edit.descripcion"></textarea>
                </div>

                <!-- 2. DETALLES DE MEDICIÓN (PARAMETRIZACIÓN) -->
                <div class="col-12 mt-4">
                  <h6 class="fw-semibold small mb-0 text-muted">
                    <i class="bi bi-sliders me-1"></i>2. Detalles de medición (Parametrización)
                  </h6>
                  <hr class="mt-1 mb-3">
                </div>

                <div class="col-12">
                  <label class="form-label small fw-semibold">
                    Objetivo de medición <span class="text-danger">*</span>
                    <i class="bi bi-info-circle text-muted ms-1" style="cursor: help"
                       title="Define el propósito de esta métrica. ¿Qué insight o mejora esperas obtener al medirla?"></i>
                  </label>
                  <textarea class="form-control form-control-sm" rows="2"
                            placeholder="¿Qué se quiere lograr midiendo esta métrica?"
                            [(ngModel)]="edit.objetivo"></textarea>
                </div>

                <div class="col-12">
                  <label class="form-label small fw-semibold">
                    Procedimiento / Fórmula <span class="text-danger">*</span>
                    <i class="bi bi-info-circle text-muted ms-1" style="cursor: help"
                       title="Describe cómo se obtiene el valor de esta métrica. Incluye fórmulas matemáticas o pasos específicos para calcularla."></i>
                  </label>
                  <textarea class="form-control form-control-sm" rows="3"
                            placeholder="Fórmula o pasos para calcular el valor de la métrica..."
                            [(ngModel)]="edit.procedimiento"></textarea>
                </div>

                <div class="col-md-12">
                  <label class="form-label small fw-semibold">
                    Indicador y Variables <span class="text-danger">*</span>
                    <i class="bi bi-info-circle text-muted ms-1" style="cursor: help"
                       title="Define el nombre del indicador y las variables que intervienen en su cálculo. Ej: Velocidad = SP completados / SP planificados."></i>
                  </label>
                  <input type="text" class="form-control form-control-sm"
                         placeholder="Ej: DefectosCerrados = defectos_cerrados / total_defectos"
                         [(ngModel)]="edit.indicadorVariable">
                </div>

                <!-- 3. ESCALA DE MEDICIÓN -->
                <div class="col-12">
                  <label class="form-label small fw-semibold">
                    Escala de medición <span class="text-danger">*</span>
                    <i class="bi bi-info-circle text-muted ms-1" style="cursor: help"
                       title="Define el rango y tipo de valores permitidos para esta métrica."></i>
                  </label>
                  <div class="row g-2 p-2 rounded border" style="background-color:var(--background)">
                    <div class="col-md-3">
                      <label class="form-label small text-muted mb-1">
                        Tipo <span class="text-danger">*</span>
                      </label>
                      <select class="form-select form-select-sm" [(ngModel)]="edit.escalaTipo">
                        <option value="NUMERICA_ENTERA">Numérica entera</option>
                        <option value="NUMERICA_DECIMAL">Numérica decimal</option>
                      </select>
                    </div>

                    <div class="col-md-3">
                      <label class="form-label small text-muted mb-1">
                        Mínimo <span class="text-danger">*</span>
                      </label>
                      <input type="number" class="form-control form-control-sm" [(ngModel)]="edit.escalaMin">
                    </div>

                    <div class="col-md-3">
                      <label class="form-label small text-muted mb-1">
                        Máximo @if (!edit.escalaSinLimite) { <span class="text-danger">*</span> }
                      </label>
                      <input type="number" class="form-control form-control-sm"
                             [(ngModel)]="edit.escalaMax" [disabled]="!!edit.escalaSinLimite">
                    </div>

                    <div class="col-md-3">
                      <label class="form-label small text-muted mb-1">
                        Paso <span class="text-danger">*</span>
                      </label>
                      <input type="number" class="form-control form-control-sm" step="0.01" min="0.01"
                             [(ngModel)]="edit.escalaPaso">
                    </div>

                    <div class="col-md-6 d-flex align-items-center">
                      <div class="form-check mt-2">
                        <input class="form-check-input" type="checkbox" id="escalaSinLimite"
                               [(ngModel)]="edit.escalaSinLimite" (ngModelChange)="onEscalaSinLimiteChange()">
                        <label class="form-check-label small" for="escalaSinLimite">
                          Sin límite superior
                        </label>
                      </div>
                    </div>

                    <div class="col-md-6">
                      <label class="form-label small text-muted mb-1">
                        Descripción de los valores (opcional)
                      </label>
                      <input type="text" class="form-control form-control-sm"
                             placeholder="Ej: 0 = Muy bajo; 100 = Excelente"
                             [(ngModel)]="edit.escalaDescripcion">
                    </div>

                    @if (errorEscala) {
                      <div class="col-12">
                        <div class="text-danger small mb-0">{{ errorEscala }}</div>
                      </div>
                    }
                  </div>
                </div>

                <!-- 4. CONFIGURACIÓN DE CAPTURA -->
                <div class="col-md-6">
                  <label class="form-label small fw-semibold">
                    Frecuencia de captura
                    <i class="bi bi-info-circle text-muted ms-1" style="cursor: help"
                       title="Define cuándo debe registrarse esta métrica."></i>
                  </label>
                  <select class="form-select form-select-sm" [(ngModel)]="edit.frecuenciaCaptura">
                    <option value="por_sprint">Al finalizar sprint</option>
                    <option value="semanal">Una vez por semana</option>
                    <option value="diaria">Diariamente</option>
                    <option value="ilimitada">Cuando ocurra el evento</option>
                  </select>
                </div>

                <div class="col-md-6">
                  <label class="form-label small fw-semibold">
                    Responsable de captura
                    <i class="bi bi-info-circle text-muted ms-1" style="cursor: help"
                       title="Quién puede registrar valores para esta métrica."></i>
                  </label>
                  <select class="form-select form-select-sm" [(ngModel)]="edit.responsableCaptura">
                    <option value="SCRUM_MASTER">Scrum Master</option>
                    <option value="EQUIPO">Equipo</option>
                  </select>
                  <div class="form-hint text-muted small mt-1">
                    {{ edit.responsableCaptura === 'EQUIPO' ? 'Cada integrante registra su propio valor.' : 'Solo el Scrum Master registra el valor.' }}
                  </div>
                </div>

                <!-- 5. CAMPOS ACADÉMICOS (OPCIONALES) -->
                <div class="col-12 mt-3">
                  <hr>
                  <h6 class="text-muted small mb-2">
                    <i class="bi bi-mortarboard me-1"></i>Campos académicos y analíticos (opcionales)
                  </h6>
                </div>

                <div class="col-md-6">
                  <label class="form-label small text-muted mb-1">Fórmula académica</label>
                  <input type="text" class="form-control form-control-sm"
                         placeholder="Ej: Σ(defectos_cerrados) / total_defectos"
                         [(ngModel)]="edit.formulaAcademica">
                </div>

                <div class="col-md-6">
                  <label class="form-label small text-muted mb-1">Tipo de operación</label>
                  <select class="form-select form-select-sm" [(ngModel)]="edit.tipoOperacion">
                    <option value="">Sin definir</option>
                    <option value="SUMA">SUMA</option>
                    <option value="PROMEDIO">PROMEDIO</option>
                    <option value="DIRECTO">DIRECTO</option>
                    <option value="FORMULA">FORMULA</option>
                  </select>
                </div>

                <div class="col-md-6">
                  <label class="form-label small text-muted mb-1">Unidad de resultado</label>
                  <input type="text" class="form-control form-control-sm"
                         placeholder="Ej: %, puntos, horas, incidentes"
                         [(ngModel)]="edit.unidadResultado">
                </div>

                <div class="col-md-6">
                  <label class="form-label small text-muted mb-1">Fuente académica / Práctica</label>
                  <input type="text" class="form-control form-control-sm"
                         placeholder="Ej: Guía Scrum, ISO/IEC 25010, etc."
                         [(ngModel)]="edit.fuenteAcademica">
                </div>

              </div>

              <!-- ERRORES DE VALIDACIÓN -->
              @if (erroresValidacion.length > 0) {
                <div class="alert alert-danger small mt-3 mb-0 py-2">
                  <div class="fw-semibold mb-1">
                    <i class="bi bi-exclamation-triangle-fill me-1"></i>Para poder continuar, completa los siguientes campos obligatorios:
                  </div>
                  <ul class="mb-0 ps-3">
                    @for (err of erroresValidacion; track err) {
                      <li>{{ err }}</li>
                    }
                  </ul>
                </div>
              }

              @if (error) {
                <div class="alert alert-danger small mt-3 mb-0 py-2">
                  <i class="bi bi-exclamation-triangle me-1"></i>{{ error }}
                </div>
              }

              <!-- CASO B: Métrica duplicada exacta en catálogo global -->
              @if (metricaExistente) {
                <div class="alert alert-info small mt-3 mb-0">
                  <div class="fw-semibold mb-1">
                    <i class="bi bi-info-circle me-1"></i>Esta métrica ya existe en el catálogo.
                  </div>
                  <p class="mb-2">
                    El catálogo ya tiene una métrica llamada <strong>{{ metricaExistente.nombre }}</strong>
                    ({{ metricaExistente.categoria }}). En vez de crear una copia, podés reutilizarla en
                    este proyecto.
                  </p>
                  <p class="text-muted mb-2" style="font-size:0.75rem">{{ metricaExistente.descripcion }}</p>
                  @if (reutilizarError) {
                    <p class="text-danger mb-2">{{ reutilizarError }}</p>
                  }
                  <div class="d-flex gap-2">
                    <button class="btn btn-primary btn-sm" [disabled]="reutilizando" (click)="usarMetricaExistente()">
                      @if (reutilizando) {
                        <span class="spinner-border spinner-border-sm me-2"></span>Asociando...
                      } @else {
                        <i class="bi bi-link-45deg me-1"></i>Usar la métrica existente
                      }
                    </button>
                    <button class="btn btn-outline-secondary btn-sm" [disabled]="reutilizando"
                            (click)="metricaExistente = null">
                      Volver a editar
                    </button>
                  </div>
                </div>
              }

              <!-- CASO C: Posibles duplicados conceptuales -->
              @if (posiblesDuplicados && posiblesDuplicados.length > 0) {
                <div class="alert alert-warning small mt-3 mb-0">
                  <div class="fw-semibold mb-1">
                    <i class="bi bi-exclamation-triangle me-1"></i>
                    Esta métrica ya existe o es muy similar a una métrica existente.
                  </div>
                  <p class="mb-2">
                    Estás por crear <strong>{{ edit.nombre }}</strong>, pero el catálogo ya tiene
                    {{ posiblesDuplicados.length === 1 ? 'una métrica que' : 'métricas que' }}
                    parece{{ posiblesDuplicados.length === 1 ? '' : 'n' }} medir un concepto similar.
                    ¿Querés reutilizar la métrica existente en vez de crear una nueva?
                  </p>
                  @if (reutilizarError) {
                    <p class="text-danger mb-2">{{ reutilizarError }}</p>
                  }
                  @for (candidato of posiblesDuplicados; track candidato.metrica.id) {
                    <div class="border rounded p-2 mb-2 bg-white">
                      <div class="fw-semibold">{{ candidato.metrica.nombre }}</div>
                      <div class="text-muted" style="font-size:0.75rem">{{ candidato.metrica.descripcion }}</div>
                      <div class="text-muted mb-2" style="font-size:0.7rem">
                        <i class="bi bi-info-circle me-1"></i>{{ candidato.razones.join(' · ') }}
                      </div>
                      <button class="btn btn-primary btn-sm" [disabled]="reutilizando"
                              (click)="reutilizarPosibleDuplicado(candidato)">
                        @if (reutilizando) {
                          <span class="spinner-border spinner-border-sm me-2"></span>Asociando...
                        } @else {
                          <i class="bi bi-link-45deg me-1"></i>Reutilizar existente
                        }
                      </button>
                    </div>
                  }
                  <div class="d-flex gap-2 mt-2">
                    <button class="btn btn-outline-primary btn-sm" [disabled]="creando" (click)="crearComoDiferente()">
                      @if (creando) {
                        <span class="spinner-border spinner-border-sm me-2"></span>Creando...
                      } @else {
                        <i class="bi bi-plus-lg me-1"></i>Crear como métrica diferente
                      }
                    </button>
                    <button class="btn btn-outline-secondary btn-sm" [disabled]="reutilizando || creando"
                            (click)="cancelar()">
                      Cancelar
                    </button>
                  </div>
                </div>
              }
            </div>

            <div class="card-footer d-flex justify-content-between align-items-center">
              <button class="btn btn-outline-secondary btn-sm" [disabled]="creando" (click)="cancelar()">
                <i class="bi bi-x-lg me-1"></i>Cancelar
              </button>
              <button class="btn btn-success btn-sm"
                      [disabled]="creando"
                      (click)="usarPropuesta()">
                @if (creando) {
                  <span class="spinner-border spinner-border-sm me-2"></span>Creando métrica...
                } @else {
                  <i class="bi bi-check-lg me-2"></i>Guardar y continuar a parametrización
                }
              </button>
            </div>
          </div>
        }
      }
    </app-shell>
  `
})
export class CrearMetricaIAComponent implements OnInit {
  proyecto: ProyectoDto | null = null;
  paso: Paso = 'necesidad';
  modo: ModoCreacion = 'ia';
  necesidad = '';
  generando = false;
  creando = false;
  error = '';
  errorEscala = '';
  erroresValidacion: string[] = [];
  intentadoGuardar = false;

  /** CASO B: el catálogo global ya tiene una métrica con este nombre normalizado. */
  metricaExistente: MetricaExistenteDto | null = null;
  reutilizando = false;
  reutilizarError = '';

  /** FASE 23 — CASO C: posible(s) duplicado(s) CONCEPTUAL(es), sin nombre exacto. */
  posiblesDuplicados: PosibleDuplicadoDto[] | null = null;

  propuesta: MetricaIAPropuestaDto | null = null;
  edit: FormularioMetrica = this.formularioVacio();

  readonly categorias = [
    { id: 1, nombre: 'Significado' },
    { id: 2, nombre: 'Flexibilidad' },
    { id: 3, nombre: 'Impacto' },
    { id: 4, nombre: 'Socio-Humano FSH' },
  ];

  constructor(
    public router: Router,
    private metricaIAService: MetricaIAService,
    private seleccionService: SeleccionService,
    private planeacionService: PlaneacionService
  ) {}

  ngOnInit(): void {
    try {
      const p = localStorage.getItem('mpdia_proyecto_activo');
      this.proyecto = p ? JSON.parse(p) : null;
    } catch { /* ignore */ }
  }

  private formularioVacio(): FormularioMetrica {
    return {
      nombre: '',
      categoriaId: 1,
      descripcion: '',
      objetivo: '',
      procedimiento: '',
      indicadorVariable: '',
      escala: '',
      escalaTipo: 'NUMERICA_ENTERA',
      escalaMin: 0,
      escalaMax: 100,
      escalaPaso: 1,
      escalaSinLimite: false,
      escalaDescripcion: '',
      frecuenciaCaptura: 'por_sprint',
      responsableCaptura: 'SCRUM_MASTER',
      formulaAcademica: '',
      tipoOperacion: '',
      unidadResultado: '',
      fuenteAcademica: '',
      queMide: '',
      variablesSugeridas: '',
      tipoOperacionSugerido: '',
      formulaSugerida: '',
      fuenteSugerida: ''
    };
  }

  cambiarModo(nuevoModo: ModoCreacion): void {
    this.modo = nuevoModo;
    this.error = '';
    this.erroresValidacion = [];
    if (nuevoModo === 'manual') {
      this.paso = 'revision';
    } else {
      this.paso = this.propuesta ? 'revision' : 'necesidad';
    }
  }

  volverANecesidad(): void {
    this.paso = 'necesidad';
  }

  generarPropuesta(): void {
    if (!this.necesidad.trim()) return;
    this.generando = true;
    this.error = '';
    this.erroresValidacion = [];

    const limpiar = (v?: string | null) => (v && v.trim() && v.trim() !== 'No determinado') ? v.trim() : '';

    this.metricaIAService.generarPropuesta(this.necesidad.trim()).pipe(
      catchError(err => {
        this.error = err?.error?.message || err?.error?.error || 'No se pudo generar la propuesta. Intenta nuevamente.';
        return of(null);
      })
    ).subscribe(propuesta => {
      this.generando = false;
      if (!propuesta) return;
      this.propuesta = propuesta;

      const tipoOp = limpiar(propuesta.tipoOperacionSugerido);
      const formSugerida = limpiar(propuesta.formulaSugerida);
      const qMide = limpiar(propuesta.queMide);
      const varSugeridas = limpiar(propuesta.variablesSugeridas);

      this.edit = {
        nombre: propuesta.nombre || '',
        categoriaId: 1,
        descripcion: propuesta.descripcion || '',
        objetivo: propuesta.objetivo || '',
        procedimiento: formSugerida || qMide || '',
        indicadorVariable: varSugeridas || qMide || '',
        escala: '',
        escalaTipo: propuesta.escalaTipo === 'NUMERICA_DECIMAL' ? 'NUMERICA_DECIMAL' : 'NUMERICA_ENTERA',
        escalaMin: propuesta.escalaMin != null ? propuesta.escalaMin : 0,
        escalaMax: propuesta.escalaMax != null ? propuesta.escalaMax : (propuesta.escalaSinLimite ? undefined : 100),
        escalaPaso: propuesta.escalaPaso != null ? propuesta.escalaPaso : 1,
        escalaSinLimite: propuesta.escalaSinLimite ?? false,
        escalaDescripcion: propuesta.escalaDescripcion || '',
        frecuenciaCaptura: 'por_sprint',
        responsableCaptura: 'SCRUM_MASTER',
        formulaAcademica: formSugerida,
        tipoOperacion: tipoOp,
        unidadResultado: limpiar(propuesta.unidadResultado),
        fuenteAcademica: limpiar(propuesta.fuenteSugerida),
        queMide: propuesta.queMide || '',
        variablesSugeridas: propuesta.variablesSugeridas || '',
        tipoOperacionSugerido: propuesta.tipoOperacionSugerido || '',
        formulaSugerida: propuesta.formulaSugerida || '',
        fuenteSugerida: propuesta.fuenteSugerida || ''
      };
      this.paso = 'revision';
    });
  }

  cancelar(): void {
    // Descartar la propuesta sin persistir nada.
    this.propuesta = null;
    this.edit = this.formularioVacio();
    this.necesidad = '';
    this.paso = 'necesidad';
    this.modo = 'ia';
    this.metricaExistente = null;
    this.posiblesDuplicados = null;
    this.erroresValidacion = [];
    this.error = '';
    this.errorEscala = '';
    this.intentadoGuardar = false;
  }

  onEscalaSinLimiteChange(): void {
    if (this.edit.escalaSinLimite) {
      this.edit.escalaMax = undefined;
    }
  }

  validarEscala(): boolean {
    this.errorEscala = '';
    const { escalaTipo, escalaMin, escalaMax, escalaPaso, escalaSinLimite } = this.edit;

    if (!escalaTipo) {
      this.errorEscala = 'Seleccioná el tipo de escala (entera o decimal).';
      return false;
    }
    if (escalaMin == null || isNaN(Number(escalaMin))) {
      this.errorEscala = 'El mínimo de la escala es obligatorio.';
      return false;
    }
    if (escalaPaso == null || isNaN(Number(escalaPaso)) || Number(escalaPaso) <= 0) {
      this.errorEscala = 'El paso de la escala debe ser un número mayor que 0.';
      return false;
    }
    if (!escalaSinLimite) {
      if (escalaMax == null || isNaN(Number(escalaMax))) {
        this.errorEscala = 'El máximo de la escala es obligatorio (o marcá "Sin límite superior").';
        return false;
      }
      if (Number(escalaMax) < Number(escalaMin)) {
        this.errorEscala = 'El máximo de la escala no puede ser menor que el mínimo.';
        return false;
      }
    }
    if (escalaTipo === 'NUMERICA_ENTERA') {
      const esEntero = (n: any) => Number.isInteger(Number(n));
      if (!esEntero(escalaMin) || !esEntero(escalaPaso) || (!escalaSinLimite && !esEntero(escalaMax))) {
        this.errorEscala = 'Para escala numérica entera, mínimo, máximo y paso deben ser números enteros.';
        return false;
      }
    }
    return true;
  }

  validarFormulario(): boolean {
    this.erroresValidacion = [];
    const e = this.edit;

    if (!e.nombre || !e.nombre.trim()) {
      this.erroresValidacion.push('El nombre de la métrica es obligatorio.');
    } else if (e.nombre.trim().length > 120) {
      this.erroresValidacion.push('El nombre no puede superar los 120 caracteres.');
    }

    if (!e.categoriaId) {
      this.erroresValidacion.push('Debes seleccionar una categoría.');
    }

    if (!e.descripcion || !e.descripcion.trim()) {
      this.erroresValidacion.push('La descripción de la métrica es obligatoria.');
    }

    if (!e.objetivo || !e.objetivo.trim()) {
      this.erroresValidacion.push('El objetivo de medición es obligatorio.');
    }

    if (!e.procedimiento || !e.procedimiento.trim()) {
      this.erroresValidacion.push('El procedimiento o fórmula de medición es obligatorio.');
    }

    if (!e.indicadorVariable || !e.indicadorVariable.trim()) {
      this.erroresValidacion.push('El indicador y variables involucradas son obligatorios.');
    }

    const escalaValida = this.validarEscala();
    if (!escalaValida && this.errorEscala) {
      this.erroresValidacion.push(this.errorEscala);
    }

    return this.erroresValidacion.length === 0;
  }

  escalaTexto(): string {
    const e = this.edit;
    if (!e.escalaTipo || e.escalaMin == null) {
      return e.escala || 'Escala no definida';
    }
    const tipo = e.escalaTipo === 'NUMERICA_ENTERA' ? 'Numérica entera' : 'Numérica decimal';
    const rango = e.escalaSinLimite ? `${e.escalaMin} o más` : `${e.escalaMin} a ${e.escalaMax}`;
    const paso = e.escalaPaso != null ? `, paso ${e.escalaPaso}` : '';
    return `${tipo}, ${rango}${paso}`;
  }

  /**
   * Valida todos los campos y persiste la nueva métrica en el catálogo.
   * Luego continúa a Parametrización con todos los campos configurados.
   */
  usarPropuesta(forzarCreacionDiferente = false): void {
    if (!this.proyecto) return;
    this.intentadoGuardar = true;

    if (!this.validarFormulario()) {
      return;
    }

    this.creando = true;
    this.error = '';
    this.metricaExistente = null;
    this.reutilizarError = '';
    this.posiblesDuplicados = null;

    const edit = this.edit;

    this.metricaIAService.crear({
      proyectoId: this.proyecto.id,
      categoriaId: edit.categoriaId,
      nombre: edit.nombre.trim(),
      descripcion: edit.descripcion.trim(),
      objetivo: edit.objetivo ? edit.objetivo.trim() : undefined,
      queMide: edit.queMide ? edit.queMide.trim() : (edit.procedimiento ? edit.procedimiento.trim() : undefined),
      variablesSugeridas: edit.variablesSugeridas ? edit.variablesSugeridas.trim() : (edit.indicadorVariable ? edit.indicadorVariable.trim() : undefined),
      confirmarCreacionDiferente: forzarCreacionDiferente
    }).pipe(
      catchError(err => {
        this.creando = false;
        const tipo = err?.error?.tipo;
        if (err?.status === 409 && tipo === 'duplicado_exacto' && err?.error?.metricaExistente) {
          this.metricaExistente = err.error.metricaExistente as MetricaExistenteDto;
          return of(null);
        }
        if (err?.status === 409 && tipo === 'posible_duplicado' && err?.error?.candidatos) {
          this.posiblesDuplicados = err.error.candidatos as PosibleDuplicadoDto[];
          return of(null);
        }
        this.error = err?.status === 403
          ? 'No tienes permiso para crear métricas en este proyecto.'
          : 'No se pudo crear la métrica. Intenta nuevamente.';
        return of(null);
      })
    ).subscribe(creada => {
      if (!creada) return;
      this.creando = false;
      this.continuarHaciaParametrizacion(creada.metricaId, edit);
    });
  }

  crearComoDiferente(): void {
    this.usarPropuesta(true);
  }

  /**
   * Continúa al flujo de Parametrización asegurando que TODOS los campos
   * definidos en esta pantalla queden guardados en el estado local de Selección.
   */
  private continuarHaciaParametrizacion(metricaId: string, edit: FormularioMetrica): void {
    if (!this.proyecto) return;
    const categoriaNombre = this.categorias.find(c => c.id === edit.categoriaId)?.nombre ?? 'Significado';
    const limpiar = (v?: string | null) => (v && v.trim() && v.trim() !== 'No determinado') ? v.trim() : '';

    this.seleccionService.agregar({
      factorId: metricaId,
      factorNombre: edit.nombre.trim(),
      factorCategoria: categoriaNombre,
      metricaNombre: edit.nombre.trim(),
      metricaDescripcion: edit.descripcion.trim(),
      proyectoId: this.proyecto.id
    });

    const sel = this.seleccionService.getSnapshot().find(s => s.factorId === metricaId);
    if (!sel) {
      this.router.navigate(['/planeacion']);
      return;
    }

    const objetivo = limpiar(edit.objetivo);
    const procedimiento = limpiar(edit.procedimiento) || limpiar(edit.formulaSugerida) || limpiar(edit.queMide);
    const indicadorVariable = limpiar(edit.indicadorVariable) || limpiar(edit.variablesSugeridas);
    const tipoOperacion = limpiar(edit.tipoOperacion) || limpiar(edit.tipoOperacionSugerido);

    this.seleccionService.parametrizar(sel.id, {
      objetivo,
      procedimiento,
      indicadorVariable,
      escala: this.escalaTexto(),
      frecuenciaCaptura: edit.frecuenciaCaptura || 'por_sprint',
      responsableCaptura: edit.responsableCaptura || 'SCRUM_MASTER',
      escalaTipo: edit.escalaTipo,
      escalaMin: edit.escalaMin != null ? Number(edit.escalaMin) : undefined,
      escalaMax: edit.escalaMax != null ? Number(edit.escalaMax) : undefined,
      escalaPaso: edit.escalaPaso != null ? Number(edit.escalaPaso) : undefined,
      escalaSinLimite: !!edit.escalaSinLimite,
      escalaDescripcion: limpiar(edit.escalaDescripcion) || undefined,
      fuenteAcademica: limpiar(edit.fuenteAcademica) || limpiar(edit.fuenteSugerida) || undefined,
      formulaAcademica: limpiar(edit.formulaAcademica) || limpiar(edit.formulaSugerida) || undefined,
      tipoOperacion: tipoOperacion || undefined,
      unidadResultado: limpiar(edit.unidadResultado) || undefined
    });

    this.router.navigate(['/parametrizacion', sel.id]);
  }

  reutilizarPosibleDuplicado(candidato: PosibleDuplicadoDto): void {
    this.posiblesDuplicados = null;
    this.metricaExistente = candidato.metrica;
    this.usarMetricaExistente();
  }

  usarMetricaExistente(): void {
    if (!this.proyecto || !this.metricaExistente) return;
    this.reutilizando = true;
    this.reutilizarError = '';

    const metricaExistente = this.metricaExistente;
    let fallo = false;
    this.planeacionService.seleccionar(this.proyecto.id, metricaExistente.id).pipe(
      catchError(err => {
        fallo = true;
        this.reutilizarError = err?.status === 403
          ? 'No tienes permiso para asociar métricas en este proyecto.'
          : 'No se pudo asociar la métrica existente. Intenta nuevamente.';
        this.reutilizando = false;
        return of(null);
      })
    ).subscribe(() => {
      if (fallo) return;
      this.reutilizando = false;

      this.seleccionService.agregar({
        factorId: metricaExistente.id,
        factorNombre: metricaExistente.nombre,
        factorCategoria: metricaExistente.categoria,
        metricaNombre: metricaExistente.nombre,
        metricaDescripcion: metricaExistente.descripcion,
        proyectoId: this.proyecto!.id
      });
      const sel = this.seleccionService.getSnapshot().find(s => s.factorId === metricaExistente.id);
      if (sel && (this.edit.objetivo || this.edit.procedimiento || this.edit.indicadorVariable)) {
        this.seleccionService.parametrizar(sel.id, {
          objetivo: this.edit.objetivo || '',
          procedimiento: this.edit.procedimiento || '',
          indicadorVariable: this.edit.indicadorVariable || '',
          escala: this.escalaTexto(),
          frecuenciaCaptura: this.edit.frecuenciaCaptura || 'por_sprint',
          responsableCaptura: this.edit.responsableCaptura || 'SCRUM_MASTER',
          escalaTipo: this.edit.escalaTipo,
          escalaMin: this.edit.escalaMin != null ? Number(this.edit.escalaMin) : undefined,
          escalaMax: this.edit.escalaMax != null ? Number(this.edit.escalaMax) : undefined,
          escalaPaso: this.edit.escalaPaso != null ? Number(this.edit.escalaPaso) : undefined,
          escalaSinLimite: !!this.edit.escalaSinLimite,
          escalaDescripcion: this.edit.escalaDescripcion || undefined
        });
      }
      this.router.navigate(sel ? ['/parametrizacion', sel.id] : ['/planeacion']);
    });
  }
}
