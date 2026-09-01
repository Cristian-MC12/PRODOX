// Autor: Cristian Santiago Martinez Cordoba — PRODOX
// Revisión de navegación (Planeación): notificación de parametrizaciones
// pendientes de aprobación para el Scrum Master. NO crea un sistema de
// notificaciones paralelo: reutiliza exactamente los mismos endpoints que ya
// consumían Planeación/Resumen de Selección/Verificación
// (MetricRankingService.getResumen / getPendientes, GET /metric-ranking/...),
// sin ningún dato ni ID hardcodeado — todo sale del proyecto activo real en
// localStorage, igual que el resto de la app.
import { Component, HostListener, OnDestroy, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NavigationEnd, Router } from '@angular/router';
import { catchError, filter, of, Subscription } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { MetricRankingService } from '../../services/metric-ranking.service';
import { PendienteNotificacion } from '../../models/metric-ranking.model';

@Component({
  selector: 'app-notificaciones-bell',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (esScrumMaster() && proyectoActivoId()) {
      <div class="position-relative">
        <button type="button" class="btn btn-outline-secondary btn-sm position-relative"
                (click)="$event.stopPropagation(); alternarPanel()" title="Parametrizaciones pendientes de aprobación">
          <i class="bi bi-bell"></i>
          @if (totalPendientes() > 0) {
            <span class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger"
                  style="font-size:9px">
              {{ totalPendientes() }}
            </span>
          }
        </button>

        @if (panelAbierto()) {
          <div class="card shadow-sm position-absolute end-0 mt-1"
               style="width:340px;z-index:1050" (click)="$event.stopPropagation()">
            <div class="card-header py-2 d-flex justify-content-between align-items-center">
              <span class="fw-semibold small">
                <i class="bi bi-clipboard-check me-1"></i>Pendientes de aprobación
              </span>
              <button type="button" class="btn-close small" (click)="panelAbierto.set(false)"></button>
            </div>
            <div class="card-body p-0" style="max-height:320px;overflow-y:auto">
              @if (cargando()) {
                <div class="text-center text-muted small py-3">
                  <span class="spinner-border spinner-border-sm me-2"></span>Cargando...
                </div>
              } @else if (pendientes().length === 0) {
                <div class="text-center text-muted small py-3">
                  <i class="bi bi-check-all text-success me-1"></i>No hay nada pendiente.
                </div>
              } @else {
                @for (p of pendientes(); track p.id) {
                  <div class="px-3 py-2 border-bottom small">
                    <div class="fw-semibold">{{ p.factorNombre }}</div>
                    <div class="text-muted" style="font-size:0.75rem">
                      Propuesta por <strong>{{ p.userEmail }}</strong> — requiere tu aprobación
                    </div>
                    <button type="button" class="btn btn-primary btn-sm mt-2 py-0" (click)="revisar()">
                      <i class="bi bi-arrow-right-circle me-1"></i>Revisar
                    </button>
                  </div>
                }
              }
            </div>
          </div>
        }
      </div>
    }
  `
})
export class NotificacionesBellComponent implements OnInit, OnDestroy {
  panelAbierto     = signal(false);
  cargando         = signal(false);
  pendientes       = signal<PendienteNotificacion[]>([]);
  totalPendientes  = signal(0);
  proyectoActivoId = signal<string | null>(this.leerProyectoId());

  private routerSub?: Subscription;

  constructor(
    private auth: AuthService,
    public  router: Router,
    private rankingService: MetricRankingService
  ) {}

  ngOnInit(): void {
    this.refrescarConteo();
    // Mismo patrón que SidebarComponent: releer proyecto activo y refrescar el
    // conteo en cada navegación (ej. al cambiar de proyecto o al aprobar/rechazar
    // en Verificación), sin depender de un polling propio.
    this.routerSub = this.router.events.pipe(
      filter(e => e instanceof NavigationEnd)
    ).subscribe(() => {
      this.proyectoActivoId.set(this.leerProyectoId());
      this.refrescarConteo();
    });
  }

  ngOnDestroy(): void {
    this.routerSub?.unsubscribe();
  }

  /**
   * Corrección: Scrum Master es siempre relativo al proyecto activo (su
   * scrumMasterEmail, fijado por el backend al crearlo), nunca el rol
   * global de cuenta — mismo patrón ya corregido en dashboard.component.ts
   * (esScrumMasterDelProyecto).
   */
  esScrumMaster(): boolean {
    return this.leerScrumMasterEmail() === this.auth.currentUser()?.email;
  }

  private leerProyectoId(): string | null {
    try {
      const raw = localStorage.getItem('mpdia_proyecto_activo');
      return raw ? (JSON.parse(raw)?.id ?? null) : null;
    } catch { return null; }
  }

  private leerScrumMasterEmail(): string | null {
    try {
      const raw = localStorage.getItem('mpdia_proyecto_activo');
      return raw ? (JSON.parse(raw)?.scrumMasterEmail ?? null) : null;
    } catch { return null; }
  }

  private refrescarConteo(): void {
    const proyectoId = this.proyectoActivoId();
    if (!this.esScrumMaster() || !proyectoId) { this.totalPendientes.set(0); return; }
    this.rankingService.getResumen(proyectoId).pipe(
      catchError(() => of(null))
    ).subscribe(resumen => this.totalPendientes.set(resumen?.pendientes ?? 0));
  }

  alternarPanel(): void {
    const abrir = !this.panelAbierto();
    this.panelAbierto.set(abrir);
    if (abrir) this.cargarPendientes();
  }

  /** Cierra el panel al hacer clic fuera (el panel detiene su propia propagación). */
  @HostListener('document:click')
  cerrarAlHacerClicAfuera(): void {
    if (this.panelAbierto()) this.panelAbierto.set(false);
  }

  private cargarPendientes(): void {
    const proyectoId = this.proyectoActivoId();
    if (!proyectoId) return;
    this.cargando.set(true);
    this.rankingService.getPendientes(proyectoId).pipe(
      catchError(() => of([]))
    ).subscribe(list => {
      this.pendientes.set(list);
      this.totalPendientes.set(list.length);
      this.cargando.set(false);
    });
  }

  /** Lleva directamente a la pantalla real donde el Scrum Master revisa/aprueba. */
  revisar(): void {
    this.panelAbierto.set(false);
    this.router.navigate(['/verificacion']);
  }
}
