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
import { ProyectoDto } from '../../models/proyecto.model';

type Paso = 'necesidad' | 'revision';

interface EditablePropuesta extends MetricaIAPropuestaDto {
  categoriaId: number;
}

/**
 * FASE 15 — "Crear métrica con IA".
 *
 * Regla fundamental: LA IA PROPONE, EL SCRUM MASTER DECIDE. Esta pantalla
 * nunca persiste nada hasta que el Scrum Master pulsa explícitamente
 * "Usar esta propuesta" — ni siquiera al generar la propuesta con Gemini.
 * Al confirmar, se crea la Metrica y se asocia al proyecto reutilizando el
 * flujo existente (PlaneacionService.seleccionar), y se continúa
 * exactamente por el flujo ya existente de "Parametrizar con GenAI" — sin
 * ninguna lógica paralela de parametrización.
 */
@Component({
  selector: 'app-crear-metrica-ia',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell title="Crear métrica con IA">

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
            <li class="breadcrumb-item active">Crear métrica con IA</li>
          </ol>
        </nav>

        @if (paso === 'necesidad') {
          <div class="card">
            <div class="card-header d-flex align-items-center gap-2">
              <i class="bi bi-robot text-primary fs-5"></i>
              <div>
                <div class="fw-semibold small">¿Qué necesitás medir?</div>
                <div class="text-muted" style="font-size:0.75rem">
                  Describilo en tus propias palabras. La IA propondrá una estructura inicial
                  que vas a poder revisar y modificar antes de continuar.
                </div>
              </div>
            </div>
            <div class="card-body">
              <textarea class="form-control" rows="3"
                        placeholder="Ej: Quiero medir el estado de ánimo del equipo"
                        [(ngModel)]="necesidad"></textarea>
              @if (error) {
                <div class="alert alert-danger small mt-3 mb-0 py-2">
                  <i class="bi bi-exclamation-triangle me-1"></i>{{ error }}
                </div>
              }
            </div>
            <div class="card-footer d-flex justify-content-between">
              <button class="btn btn-outline-secondary btn-sm" (click)="router.navigate(['/planeacion'])">
                <i class="bi bi-arrow-left me-1"></i>Volver
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

        @if (paso === 'revision' && propuesta) {
          <div class="card border-primary">
            <div class="card-header bg-primary bg-opacity-10">
              <div class="fw-semibold small"><i class="bi bi-stars me-1"></i>Propuesta de métrica</div>
              <div class="text-muted" style="font-size:0.72rem">
                <i class="bi bi-exclamation-circle me-1"></i>
                Esta es una propuesta generada por IA. Revisá y modificá la propuesta antes de continuar.
              </div>
            </div>
            <div class="card-body">
              <div class="row g-3">
                <div class="col-md-8">
                  <label class="form-label small fw-semibold">Nombre <span class="text-danger">*</span></label>
                  <input type="text" class="form-control form-control-sm" [(ngModel)]="edit.nombre">
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold">Categoría <span class="text-danger">*</span></label>
                  <select class="form-select form-select-sm" [(ngModel)]="edit.categoriaId">
                    @for (cat of categorias; track cat.id) {
                      <option [value]="cat.id">{{ cat.nombre }}</option>
                    }
                  </select>
                </div>
                <div class="col-12">
                  <label class="form-label small fw-semibold">Descripción <span class="text-danger">*</span></label>
                  <textarea class="form-control form-control-sm" rows="2" [(ngModel)]="edit.descripcion"></textarea>
                </div>
                <div class="col-12">
                  <label class="form-label small fw-semibold">Objetivo</label>
                  <textarea class="form-control form-control-sm" rows="2" [(ngModel)]="edit.objetivo"></textarea>
                </div>
                <div class="col-md-6">
                  <label class="form-label small fw-semibold">Qué pretende medir</label>
                  <input type="text" class="form-control form-control-sm" [(ngModel)]="edit.queMide">
                </div>
                <div class="col-md-6">
                  <label class="form-label small fw-semibold">Variables sugeridas</label>
                  <input type="text" class="form-control form-control-sm" [(ngModel)]="edit.variablesSugeridas">
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold">Tipo de operación sugerido</label>
                  <input type="text" class="form-control form-control-sm" [(ngModel)]="edit.tipoOperacionSugerido">
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold">Fórmula sugerida</label>
                  <input type="text" class="form-control form-control-sm" [(ngModel)]="edit.formulaSugerida">
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold">Unidad de resultado</label>
                  <input type="text" class="form-control form-control-sm" [(ngModel)]="edit.unidadResultado">
                </div>
                <div class="col-12">
                  <label class="form-label small fw-semibold">Fuente sugerida</label>
                  <input type="text" class="form-control form-control-sm" [(ngModel)]="edit.fuenteSugerida">
                </div>
              </div>
              @if (error) {
                <div class="alert alert-danger small mt-3 mb-0 py-2">
                  <i class="bi bi-exclamation-triangle me-1"></i>{{ error }}
                </div>
              }

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
                      [disabled]="!edit.nombre.trim() || !edit.descripcion.trim() || creando"
                      (click)="usarPropuesta()">
                @if (creando) {
                  <span class="spinner-border spinner-border-sm me-2"></span>Creando métrica...
                } @else {
                  <i class="bi bi-check-lg me-2"></i>Usar esta propuesta
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
  necesidad = '';
  generando = false;
  creando = false;
  error = '';

  /** CASO B: el catálogo global ya tiene una métrica con este nombre normalizado. */
  metricaExistente: MetricaExistenteDto | null = null;
  reutilizando = false;
  reutilizarError = '';

  /** FASE 23 — CASO C: posible(s) duplicado(s) CONCEPTUAL(es), sin nombre exacto. */
  posiblesDuplicados: PosibleDuplicadoDto[] | null = null;

  propuesta: MetricaIAPropuestaDto | null = null;
  edit: EditablePropuesta = this.propuestaVacia();

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

  private propuestaVacia(): EditablePropuesta {
    return {
      nombre: '', descripcion: '', objetivo: '', queMide: '',
      variablesSugeridas: '', tipoOperacionSugerido: '', formulaSugerida: '',
      unidadResultado: '', fuenteSugerida: '', categoriaId: 1
    };
  }

  generarPropuesta(): void {
    if (!this.necesidad.trim()) return;
    this.generando = true;
    this.error = '';

    this.metricaIAService.generarPropuesta(this.necesidad.trim()).pipe(
      catchError(() => { this.error = 'No se pudo generar la propuesta. Intenta nuevamente.'; return of(null); })
    ).subscribe(propuesta => {
      this.generando = false;
      if (!propuesta) return;
      this.propuesta = propuesta;
      this.edit = { ...propuesta, categoriaId: 1 };
      this.paso = 'revision';
    });
  }

  cancelar(): void {
    // Descartar la propuesta sin persistir absolutamente nada.
    this.propuesta = null;
    this.edit = this.propuestaVacia();
    this.necesidad = '';
    this.paso = 'necesidad';
    this.metricaExistente = null;
    this.posiblesDuplicados = null;
  }

  /**
   * FASE 23 — forzarCreacionDiferente=true se usa cuando el Scrum Master ya
   * vio el aviso de posible(s) duplicado(s) conceptual(es) y decidió crear la
   * métrica de todas formas: se envía confirmarCreacionDiferente al backend
   * para que omita esa búsqueda (el chequeo de nombre EXACTO sigue aplicando
   * siempre, sin excepción).
   */
  usarPropuesta(forzarCreacionDiferente = false): void {
    if (!this.proyecto) return;
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
      objetivo: edit.objetivo,
      queMide: edit.queMide,
      variablesSugeridas: edit.variablesSugeridas,
      confirmarCreacionDiferente: forzarCreacionDiferente
    }).pipe(
      catchError(err => {
        this.creando = false;
        const tipo = err?.error?.tipo;
        // CASO B: el catálogo global ya tiene una métrica con este nombre —
        // el backend la devuelve para ofrecer reutilizarla en vez de fallar.
        if (err?.status === 409 && tipo === 'duplicado_exacto' && err?.error?.metricaExistente) {
          this.metricaExistente = err.error.metricaExistente as MetricaExistenteDto;
          return of(null);
        }
        // CASO C: sin nombre exacto, pero el catálogo ya tiene una o más
        // métricas que probablemente miden el mismo concepto.
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

  /** FASE 23 — "Crear como métrica diferente": omite el aviso y crea igual. */
  crearComoDiferente(): void {
    this.usarPropuesta(true);
  }

  /** Continúa exactamente por el flujo existente de "Parametrizar con GenAI". */
  private continuarHaciaParametrizacion(metricaId: string, edit: EditablePropuesta): void {
    if (!this.proyecto) return;
    const categoriaNombre = this.categorias.find(c => c.id === edit.categoriaId)?.nombre ?? 'Significado';
    const limpiar = (v: string) => (v && v.trim() && v.trim() !== 'No determinado') ? v.trim() : '';

    this.seleccionService.agregar({
      factorId: metricaId,
      factorNombre: edit.nombre,
      factorCategoria: categoriaNombre,
      metricaNombre: edit.nombre,
      metricaDescripcion: edit.descripcion,
      proyectoId: this.proyecto.id
    });

    const sel = this.seleccionService.getSnapshot().find(s => s.factorId === metricaId);
    if (!sel) {
      this.router.navigate(['/planeacion']);
      return;
    }

    const objetivo = limpiar(edit.objetivo);
    const procedimiento = limpiar(edit.formulaSugerida) || limpiar(edit.queMide);
    const indicadorVariable = limpiar(edit.variablesSugeridas);
    const tipoOperacion = limpiar(edit.tipoOperacionSugerido);
    if (objetivo || procedimiento || indicadorVariable) {
      this.seleccionService.parametrizar(sel.id, {
        objetivo,
        procedimiento,
        indicadorVariable,
        escala: '',
        frecuenciaCaptura: 'por_sprint',
        fuenteAcademica: limpiar(edit.fuenteSugerida),
        formulaAcademica: limpiar(edit.formulaSugerida),
        tipoOperacion,
        unidadResultado: limpiar(edit.unidadResultado)
      });
    }

    this.router.navigate(['/parametrizacion', sel.id]);
  }

  /**
   * CASO B: asocia al proyecto la métrica que YA existe en el catálogo
   * global, en vez de crear una copia — mismo flujo que ya usa cualquier
   * métrica del catálogo (PlaneacionService.seleccionar), sin ninguna
   * lógica paralela.
   */
  /**
   * FASE 23 — "Reutilizar existente" sobre un candidato de posible duplicado
   * CONCEPTUAL: reutiliza EXACTAMENTE el mismo flujo ya probado que el CASO B
   * (nombre exacto) — sin ninguna lógica paralela — asociando la métrica
   * global existente al proyecto en vez de crear una copia.
   */
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
      // Reutilizar significa reutilizar: llevar directamente al flujo de
      // parametrización existente (mismo mecanismo que continuarHaciaParametrizacion()
      // para métricas nuevas), en vez de dejar al Scrum Master en Planeación para
      // que tenga que volver a encontrar la métrica y entrar manualmente.
      // seleccionService.agregar() es idempotente (de-duplica por factorId+
      // metricaNombre+proyectoId, ver SeleccionService), así que reutilizar una
      // métrica ya seleccionada nunca duplica la entrada local.
      this.seleccionService.agregar({
        factorId: metricaExistente.id,
        factorNombre: metricaExistente.nombre,
        factorCategoria: metricaExistente.categoria,
        metricaNombre: metricaExistente.nombre,
        metricaDescripcion: metricaExistente.descripcion,
        proyectoId: this.proyecto!.id
      });
      const sel = this.seleccionService.getSnapshot().find(s => s.factorId === metricaExistente.id);
      this.router.navigate(sel ? ['/parametrizacion', sel.id] : ['/planeacion']);
    });
  }
}
