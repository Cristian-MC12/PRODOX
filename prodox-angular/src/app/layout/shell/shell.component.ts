// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { NotificacionesBellComponent } from '../notificaciones/notificaciones-bell.component';
import { SprintService } from '../../services/sprint.service';
import { ProyectoDto } from '../../models/proyecto.model';
import { SprintDto } from '../../models/sprint.model';
import { timeboxAbreviado } from '../../models/timebox.model';
import { catchError, of } from 'rxjs';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, SidebarComponent, NotificacionesBellComponent],
  template: `
    <div class="d-flex" [class.sidebar-collapsed]="sidebar.collapsed()">
      <!-- Overlay oscuro para móvil -->
      @if (sidebar.open()) {
        <div class="sidebar-overlay" (click)="sidebar.close()"></div>
      }
      
      <app-sidebar #sidebar></app-sidebar>

      <div class="main-content w-100">
        <!-- Header -->
        <header class="page-header d-flex align-items-center justify-content-between gap-3">
          <button class="btn btn-sidebar-toggle"
                  (click)="toggleSidebar(sidebar)"
                  [title]="getTooltip(sidebar)">
            <i class="bi bi-list"></i>
          </button>
          @if (title) {
            <h1>{{ title }}</h1>
          }
          <ng-content select="[slot='header']"></ng-content>
          <app-notificaciones-bell class="ms-auto"></app-notificaciones-bell>
        </header>

        <!-- Banner contextual: proyecto + sprint activo -->
        @if (showBanner && proyecto) {
          <div class="px-3 pt-2">
            <div class="alert alert-primary py-2 mb-0 d-flex align-items-center gap-3 flex-wrap"
                 style="font-size:var(--text-sm)">
              <div class="d-flex align-items-center gap-2">
                <i class="bi bi-folder2-open text-primary"></i>
                <strong>{{ proyecto.nombre }}</strong>
              </div>
              <div class="vr d-none d-sm-block"></div>
              <div class="d-flex align-items-center gap-2">
                <span class="badge"
                      [class]="proyecto.metodo === 'scrum' ? 'bg-primary' : 'bg-info text-dark'">
                  {{ proyecto.metodo === 'scrum' ? 'Scrum' : 'XP' }}
                </span>
                <span class="text-muted">{{ timeboxAbreviado(proyecto) }}/iteración</span>
              </div>
              @if (sprint) {
                <div class="vr d-none d-sm-block"></div>
                <div class="d-flex align-items-center gap-2">
                  <span class="badge" [class]="badgeSprint(sprint.estado)">
                    Sprint {{ sprint.numero }}
                  </span>
                  <span class="text-muted text-truncate" style="max-width:280px">
                    {{ sprint.sprintGoal }}
                  </span>
                </div>
              }
            </div>
          </div>
        }

        <!-- Content -->
        <main class="page-body">
          <ng-content></ng-content>
        </main>
      </div>
    </div>

    <!-- AI Copilot deshabilitado (reorganización de navegación): el Copiloto
         no se usa en esta versión y sus preguntas abiertas podían generar
         consumo innecesario de cuota de Gemini durante pruebas con múltiples
         equipos. Componente AICopilotComponent preservado sin cambios en
         components/ai-copilot/ — solo se dejó de instanciar aquí, que era el
         único punto de la app que lo montaba. Al no instanciarse, su
         ngOnInit/sendMessage nunca corren, por lo que no puede disparar
         ninguna llamada a Gemini desde la interfaz. -->
  `
})
export class ShellComponent implements OnInit {
  @Input() title = '';
  @Input() showBanner = true;

  proyecto: ProyectoDto | null = null;
  sprint: SprintDto | null     = null;

  /** Expuesto al template para no repetir la lógica de unidades en cada vista. */
  readonly timeboxAbreviado = timeboxAbreviado;

  constructor(private sprintService: SprintService) {}

  ngOnInit(): void {
    try {
      const p = localStorage.getItem('mpdia_proyecto_activo');
      this.proyecto = p ? JSON.parse(p) : null;
    } catch { /* ignore */ }

    if (this.proyecto) {
      // Siempre carga el sprint activo fresco del backend
      this.sprintService.getActivo(this.proyecto.id).pipe(
        catchError(() => {
          // Fallback a localStorage si el backend falla
          try {
            const s = localStorage.getItem('mpdia_sprint_activo');
            return of(s ? JSON.parse(s) : null);
          } catch { return of(null); }
        })
      ).subscribe(s => {
        if (s) {
          this.sprint = s;
          localStorage.setItem('mpdia_sprint_activo', JSON.stringify(s));
        }
      });
    }
  }

  badgeSprint(estado: string): string {
    return ({
      'en_ejecucion': 'bg-success',
      'pendiente':    'bg-warning text-dark',
      'finalizado':   'bg-secondary',
      'reabierto':    'bg-info text-dark'
    } as Record<string, string>)[estado] ?? 'bg-secondary';
  }

  // Función inteligente para el botón hamburguesa
  toggleSidebar(sidebar: any): void {
    if (window.innerWidth < 768) {
      // En móvil: abrir/cerrar overlay
      sidebar.toggle();
    } else {
      // En desktop: colapsar/expandir
      sidebar.toggleCollapse();
    }
  }

  // Tooltip dinámico según el contexto
  getTooltip(sidebar: any): string {
    if (window.innerWidth < 768) {
      return sidebar.open() ? 'Cerrar menú' : 'Abrir menú';
    } else {
      return sidebar.collapsed() ? 'Expandir sidebar' : 'Colapsar sidebar';
    }
  }
}
