// Autor: Cristian Santiago Martinez Cordoba — PRODOX
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
  /** Corrección del manejo de escalas: fuente real para tipo/paso/sin-límite (ver EjecucionService.validarRangoValor). */
  escalaTipo?: 'NUMERICA_ENTERA' | 'NUMERICA_DECIMAL';
  escalaPaso?: number;
  escalaSinLimite?: boolean;
  /** Revisión de captura individual: grupal | individual (Variable.tipoAlcance). */
  tipoAlcance?: string;
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
  /** Capturas históricas completas desde el primer sprint hasta el actual (para el historial desplegable). */
  capturasHistoricas: RegistroPuntoDto[];
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
  /**
   * Control de visibilidad del historial de capturas
   */
  mostrarHistorial: boolean;
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
  templateUrl: './ejecucion.component.html',
  styleUrls: ['./ejecucion.component.scss']
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

  /**
   * Corrección: Scrum Master es SIEMPRE relativo a ESTE proyecto (el que lo
   * creó — Proyecto.scrumMasterId en el backend), nunca el rol global de la
   * cuenta (auth.currentUser()?.role, elegido al registrarse y usado solo
   * para decidir quién puede CREAR un proyecto nuevo). Antes esta comparación
   * usaba el rol global: un usuario que creó su propio proyecto (y por eso
   * tiene rol de cuenta "scrum_master") aparecía también como Scrum Master
   * en CUALQUIER otro proyecto donde solo fuera un miembro más — mismo
   * patrón ya corregido en dashboard.component.ts (esScrumMasterDelProyecto).
   */
  get esScrumMaster() { return this.proyecto?.scrumMasterEmail === this.auth.currentUser()?.email; }
  /** Revisión de captura universal: id del usuario autenticado, para permitir
   *  que cada miembro edite SU PROPIO registro (la autorización real ya la
   *  garantiza el backend vía JWT — esto solo decide qué botones mostrar). */
  get currentUserId() { return this.auth.currentUser()?.userId; }
  /**
   * Autorización condicional por parametrización (refleja, sin decidir por
   * su cuenta, lo que ya exige el backend en
   * EjecucionService.validarPuedeRegistrar() — la autoridad real es siempre
   * el backend, esto solo evita mostrar un formulario que el backend va a
   * rechazar):
   * - tipoAlcance='individual' (alcance "EQUIPO" en la parametrización):
   *   cualquier integrante del proyecto —Scrum Member o Scrum Master por
   *   igual, porque el Scrum Master también es parte del equipo— puede
   *   registrar su propio valor.
   * - cualquier otro tipoAlcance (hoy solo 'grupal', alcance "SCRUM MASTER"):
   *   únicamente el Scrum Master puede registrar el valor.
   */
  puedeCapturar(v: { tipoAlcance?: string }): boolean {
    return this.esScrumMaster || v.tipoAlcance === 'individual';
  }
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
    // Para el historial desplegable: todas las capturas hasta el sprint actual
    const capturasHistoricas = this.capturasHistoricasHastaSprintActual(detalleVariable);
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
      escalaTipo: v.escalaTipo,
      escalaPaso: v.escalaPaso,
      escalaSinLimite: v.escalaSinLimite,
      tipoAlcance: v.tipoAlcance,
      fecha: capturaVigente ? capturaVigente.registradoAt.substring(0, 10) : this.fechaCapturaPorDefecto(),
      valorNum: v.valorNum ?? null,
      valorTexto: v.valorTexto ?? '',
      valorBool: v.valorBool ?? false,
      registrando: false,
      error: '',
      ultimoMensaje: '',
      puntos,
      capturas,
      capturasHistoricas,
      // 'por_sprint' con un valor ya registrado arranca colapsado (resumen de
      // solo lectura); cualquier otro caso arranca mostrando el formulario.
      editando: !(frecuenciaCaptura === 'por_sprint' && capturas.length > 0),
      registroEditandoId: null,
      mostrarHistorial: false
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
   * Registros históricos completos hasta el sprint actual (para el historial desplegable).
   * Incluye UNA captura por sprint desde el primer sprint hasta el sprint actualmente seleccionado,
   * mostrando el valor más reciente de cada sprint, ordenadas de más reciente a más antigua.
   */
  private capturasHistoricasHastaSprintActual(detalleVariable: MetricaEvaluacionDetalleDto | undefined): RegistroPuntoDto[] {
    if (!detalleVariable || !this.sprintActual) return [];
    
    // Filtrar registros hasta el sprint actual (solo los que tienen sprintNumero)
    const registrosHastaActual = detalleVariable.registros
      .filter(r => r.sprintNumero != null && r.sprintNumero <= this.sprintActual!.numero);
    
    // Agrupar por sprint y obtener el más reciente de cada uno
    const porSprint = new Map<number, RegistroPuntoDto>();
    
    for (const registro of registrosHastaActual) {
      const sprintNum = registro.sprintNumero!;
      const existente = porSprint.get(sprintNum);
      
      if (!existente || new Date(registro.registradoAt) > new Date(existente.registradoAt)) {
        porSprint.set(sprintNum, registro);
      }
    }
    
    // Convertir a array y ordenar por sprint (más reciente primero)
    return Array.from(porSprint.values())
      .sort((a, b) => b.sprintNumero! - a.sprintNumero!);
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

    // Validación de escala en frontend: nunca dejar que el usuario descubra el
    // límite recién después de pulsar "Registrar" — se rechaza acá mismo,
    // con la misma escala estructurada que valida el backend (EjecucionService.
    // validarRangoValor: min/max/tipo/paso), como defensa en profundidad de
    // ambos lados. El backend sigue siendo la autoridad final.
    if (v.tipoDato === 'numerico' && v.valorNum != null) {
      if (v.escalaMin != null && v.valorNum < v.escalaMin) {
        v.error = `El valor debe ser mayor o igual a ${v.escalaMin}.`;
        return;
      }
      if (v.escalaMax != null && v.valorNum > v.escalaMax) {
        v.error = `El valor debe ser menor o igual a ${v.escalaMax}.`;
        return;
      }
      if (v.escalaTipo === 'NUMERICA_ENTERA' && !Number.isInteger(v.valorNum)) {
        v.error = 'El valor debe ser un número entero.';
        return;
      }
      if (v.escalaPaso != null && v.escalaPaso > 0) {
        const referencia = v.escalaMin ?? 0;
        const pasos = (v.valorNum - referencia) / v.escalaPaso;
        if (Math.abs(pasos - Math.round(pasos)) > 1e-9) {
          v.error = `El valor no respeta el paso permitido (${v.escalaPaso}).`;
          return;
        }
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
        v.capturasHistoricas = this.capturasHistoricasHastaSprintActual(detalleVariable);
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

  /**
   * Máximo de opciones discretas mostradas como botones en vez de un campo
   * numérico libre — cubre explícitamente una escala 0-10 (11 valores:
   * 0,1,2,...,10). Antes el corte era "rango <= 9" (escalaMax - escalaMin),
   * lo que EXCLUÍA 0-10 (rango 10) por error: se corrige contando el número
   * real de opciones (respetando el paso) en vez del tamaño del rango, así
   * que el límite queda expresado en la unidad correcta y no es un parche
   * exclusivo para 0-10 — cualquier escala con este mismo conteo de
   * opciones se beneficia igual.
   */
  private static readonly MAX_OPCIONES_DISCRETAS = 11;

  /** Rangos de escala enteros y acotados (ej. 0-10) se capturan con un selector de botones. */
  esEscalaDiscretaPequena(v: BloqueVariable): boolean {
    if (v.tipoDato !== 'numerico' || v.escalaMin == null || v.escalaMax == null) return false;
    // Compatibilidad: variables sin escalaTipo estructurado (históricas) se
    // tratan como enteras solo si min/max ya lo eran, igual que antes.
    const esEntera = v.escalaTipo ? v.escalaTipo === 'NUMERICA_ENTERA'
      : Number.isInteger(v.escalaMin) && Number.isInteger(v.escalaMax);
    if (!esEntera) return false;
    const paso = v.escalaPaso && v.escalaPaso > 0 ? v.escalaPaso : 1;
    const numOpciones = Math.round((v.escalaMax - v.escalaMin) / paso) + 1;
    return numOpciones >= 2 && numOpciones <= EjecucionComponent.MAX_OPCIONES_DISCRETAS;
  }

  opcionesEscala(v: BloqueVariable): number[] {
    if (v.escalaMin == null || v.escalaMax == null) return [];
    const paso = v.escalaPaso && v.escalaPaso > 0 ? v.escalaPaso : 1;
    const opciones: number[] = [];
    for (let i = v.escalaMin; i <= v.escalaMax + 1e-9; i += paso) {
      opciones.push(Math.round(i * 1000) / 1000);
    }
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

  /**
   * Revisión de autorización condicional: "capturada" no puede significar
   * simplemente "alguien ya registró algo" para una variable de alcance
   * EQUIPO (tipoAlcance='individual') — cada integrante tiene su propia
   * obligación de capturar. Para EQUIPO, el resumen refleja MI propio
   * progreso (¿ya registré yo?); para SCRUM MASTER (grupal) hay un único
   * capturador posible, así que "existe algún registro" sigue siendo
   * equivalente a "está capturada".
   */
  private estaCapturadaParaMi(v: BloqueVariable): boolean {
    return v.tipoAlcance === 'individual' ? this.yaRegistreMiValor(v) : v.capturas.length > 0;
  }

  // Métodos para el resumen del sprint
  obtenerTotalMetricas(): number {
    return this.metricas.reduce((total, m) => total + m.variables.length, 0);
  }

  obtenerMetricasCapturadas(): number {
    return this.metricas.reduce((total, m) => {
      return total + m.variables.filter(v => this.estaCapturadaParaMi(v)).length;
    }, 0);
  }

  obtenerMetricasPendientes(): number {
    return this.metricas.reduce((total, m) => {
      return total + m.variables.filter(v =>
        !this.estaCapturadaParaMi(v) && v.frecuenciaCaptura === 'por_sprint'
      ).length;
    }, 0);
  }

  obtenerMetricasSinRegistros(): number {
    return this.metricas.reduce((total, m) => {
      return total + m.variables.filter(v =>
        !this.estaCapturadaParaMi(v) && v.frecuenciaCaptura !== 'por_sprint'
      ).length;
    }, 0);
  }

  calcularPorcentajeProgreso(): number {
    const total = this.obtenerTotalMetricas();
    if (total === 0) return 0;
    const capturadas = this.obtenerMetricasCapturadas();
    return Math.round((capturadas / total) * 100);
  }

  calcularDashArray(valor: number, total: number): string {
    if (total === 0) return '0 251.2';
    const circumference = 2 * Math.PI * 40; // radio = 40
    const porcentaje = valor / total;
    const dash = circumference * porcentaje;
    return `${dash} ${circumference}`;
  }

  calcularOffset(valorAnterior: number, total: number): number {
    if (total === 0) return 0;
    const circumference = 2 * Math.PI * 40;
    const porcentaje = valorAnterior / total;
    return circumference * porcentaje;
  }

  // Determinar el icono según el nombre de la métrica
  obtenerIcono(nombreMetrica: string): string {
    const nombre = nombreMetrica.toLowerCase();
    
    // Velocidad
    if (nombre.includes('velocidad')) return 'bi-lightning-charge';
    
    // Calidad
    if (nombre.includes('calidad') || nombre.includes('twg')) return 'bi-shield-check';
    
    // Capacidad / Trabajo
    if (nombre.includes('capacidad') || nombre.includes('trabajo')) return 'bi-speedometer2';
    
    // Errores / Bugs
    if (nombre.includes('error') || nombre.includes('bug') || nombre.includes('defecto')) return 'bi-bug';
    
    // Entrega / Completado
    if (nombre.includes('entrega') || nombre.includes('completado') || nombre.includes('done')) return 'bi-check-circle';
    
    // Equipo / Colaboración
    if (nombre.includes('equipo') || nombre.includes('colabora') || nombre.includes('team')) return 'bi-people';
    
    // Ánimo / Satisfacción
    if (nombre.includes('animo') || nombre.includes('ánimo') || nombre.includes('satisfaccion') || nombre.includes('satisfacción')) return 'bi-emoji-smile';
    
    // Tiempo / Duración
    if (nombre.includes('tiempo') || nombre.includes('duracion') || nombre.includes('duración')) return 'bi-clock';
    
    // Retrospectiva
    if (nombre.includes('retrospectiva') || nombre.includes('retro')) return 'bi-chat-left-text';
    
    // Aprendizaje
    if (nombre.includes('aprendizaje') || nombre.includes('fat')) return 'bi-book';
    
    // Default
    return 'bi-graph-up';
  }

  // Determinar la clase de color según el nombre de la métrica
  obtenerClaseIcono(nombreMetrica: string): string {
    const nombre = nombreMetrica.toLowerCase();
    
    // Velocidad - Verde
    if (nombre.includes('velocidad')) return 'icon-velocity';
    
    // Calidad - Morado
    if (nombre.includes('calidad') || nombre.includes('twg')) return 'icon-quality';
    
    // Capacidad / Trabajo - Azul
    if (nombre.includes('capacidad') || nombre.includes('trabajo')) return 'icon-capacity';
    
    // Errores / Bugs - Rojo
    if (nombre.includes('error') || nombre.includes('bug') || nombre.includes('defecto')) return 'icon-error';
    
    // Entrega / Completado - Verde oscuro
    if (nombre.includes('entrega') || nombre.includes('completado') || nombre.includes('done')) return 'icon-delivery';
    
    // Equipo / Colaboración - Naranja
    if (nombre.includes('equipo') || nombre.includes('colabora') || nombre.includes('team')) return 'icon-team';
    
    // Ánimo / Satisfacción - Amarillo
    if (nombre.includes('animo') || nombre.includes('ánimo') || nombre.includes('satisfaccion') || nombre.includes('satisfacción')) return 'icon-mood';
    
    // Tiempo / Duración - Gris azulado
    if (nombre.includes('tiempo') || nombre.includes('duracion') || nombre.includes('duración')) return 'icon-time';
    
    // Retrospectiva - Púrpura claro
    if (nombre.includes('retrospectiva') || nombre.includes('retro')) return 'icon-retro';
    
    // Aprendizaje - Índigo
    if (nombre.includes('aprendizaje') || nombre.includes('fat')) return 'icon-learning';
    
    // Default - Azul
    return 'icon-default';
  }

  // Alternar visibilidad del historial de capturas
  toggleHistorial(v: BloqueVariable): void {
    v.mostrarHistorial = !v.mostrarHistorial;
  }

  /**
   * Progreso de captura del EQUIPO para una variable de alcance "EQUIPO"
   * (tipoAlcance='individual'): cuántos integrantes distintos ya registraron
   * su propio valor en el sprint actual, sobre el total de integrantes del
   * proyecto — ej. "2 de 4 integrantes registrados". No aplica a variables
   * de alcance "SCRUM MASTER" (un único capturador posible).
   */
  integrantesRegistrados(v: BloqueVariable): number {
    return new Set(v.capturas.map(c => c.userId)).size;
  }

  get totalIntegrantes(): number {
    return this.proyecto?.totalMiembros ?? 0;
  }

  /** true si YO ya registré mi propio valor para esta variable en el sprint actual. */
  yaRegistreMiValor(v: BloqueVariable): boolean {
    return v.capturas.some(c => c.userId === this.currentUserId);
  }
}
