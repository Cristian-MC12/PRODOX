// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, NavigationEnd, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../services/auth.service';
import { ProyectoDto } from '../../models/proyecto.model';
import { filter, Subscription } from 'rxjs';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  template: `
    <nav class="sidebar d-flex flex-column" [class.open]="open()" [class.collapsed]="collapsed()">

      <!-- Botón de colapsar/expandir -->
      <button class="btn-collapse" 
              (click)="toggleCollapse()"
              [title]="collapsed() ? 'Expandir sidebar' : 'Colapsar sidebar'"
              type="button">
        <i class="bi" [class.bi-chevron-left]="!collapsed()" [class.bi-chevron-right]="collapsed()"></i>
      </button>

      <!-- Brand -->
      <a routerLink="/" class="sidebar-brand">
        <img src="assets/images/logos/Logo-PRODOX-AI.jpg" alt="PRODOX AI" class="sidebar-brand-logo">
      </a>

      <!-- Project Card Container -->
      @if (proyectoActivo()) {
        <div class="px-3" style="padding-bottom: 10px;">
          <div style="background: rgba(15, 23, 42, 0.6); border-radius: 8px; padding: 8px 10px;">
            <!-- Proyecto Activo -->
            <small class="text-uppercase d-block" style="font-size: 8px; letter-spacing: 0.05em; color: #FFFFFF; opacity: 0.5; margin-bottom: 5px;">
              Proyecto activo
            </small>
            <div class="d-flex align-items-center justify-content-between" style="margin-bottom: 6px;">
              <span style="font-size: 13px; font-weight: 600; color: #fff;">{{ proyectoActivo()!.nombre }}</span>
              <i class="bi bi-chevron-down" style="font-size: 10px; color: rgba(148, 163, 184, 0.4);"></i>
            </div>
            <div class="d-flex gap-2" style="margin-bottom: 8px;">
              <span class="text-uppercase" 
                    style="background: #14b8a6; color: #0f172a; font-size: 8px; font-weight: 700; padding: 2px 8px; border-radius: 4px;">
                {{ proyectoActivo()!.metodo === 'scrum' ? 'Scrum' : 'XP' }}
              </span>
              <span style="color: rgba(148, 163, 184, 0.8); font-size: 9px; padding: 2px 0; font-weight: 500;">
                {{ proyectoActivo()!.numeroSprints }} sprints
              </span>
            </div>
            
            <!-- Scrum Master -->
            <div style="border-top: 1px solid rgba(148, 163, 184, 0.08); padding-top: 8px;">
              <div class="d-flex align-items-center justify-content-between">
                <div class="d-flex align-items-center gap-2">
                  <i class="bi bi-award" style="font-size: 14px; color: #fff;"></i>
                  <span style="font-size: 12px; font-weight: 500; color: #fff;">{{ esScrumMaster() ? 'Scrum Master' : 'Scrum Member' }}</span>
                </div>
                <i class="bi bi-chevron-down" style="font-size: 10px; color: rgba(148, 163, 184, 0.4);"></i>
              </div>
            </div>
          </div>
        </div>
      }

      <!-- Nav links -->
      <ul class="nav flex-column flex-grow-1" style="margin-top: 4px;">
        <!-- Separador Proyecto -->
        <li class="nav-item px-3" style="padding-top: 4px; padding-bottom: 2px;">
          <small class="text-muted text-uppercase" style="font-size:var(--text-2xs);letter-spacing:.05em">
            Proyecto
          </small>
        </li>

        <!-- Proyectos siempre visible -->
        <li class="nav-item">
          <a class="nav-link" routerLink="/proyectos" routerLinkActive="active" 
             (click)="close()" title="Proyectos">
            <i class="bi bi-folder2-open"></i>Proyectos
          </a>
        </li>

        <!-- Sprints: estructura del proyecto (no es una fase secuencial del
             proceso como Planeación/Ejecución/Evaluación), agrupado junto a
             Proyectos/Equipo. Mismo routerLink /sprints, sin duplicar la ruta. -->
        @if (proyectoActivo()) {
          <li class="nav-item">
            <a class="nav-link" routerLink="/sprints" routerLinkActive="active" 
               (click)="close()" title="Sprints">
              <i class="bi bi-calendar3-range"></i>
              <span>Sprints</span>
            </a>
          </li>
        }

        <!-- Equipo: inmediatamente debajo de Proyectos/Sprints (reorganización de navegación) -->
        <li class="nav-item">
          <a class="nav-link" routerLink="/equipo" routerLinkActive="active" 
             (click)="close()" title="Equipo">
            <i class="bi bi-people"></i>Equipo
          </a>
        </li>

        @if (proyectoActivo()) {
          <!-- Separador de fase -->
          <li class="nav-item px-3" style="padding-top: 8px; padding-bottom: 2px;">
            <small class="text-muted text-uppercase" style="font-size:var(--text-2xs);letter-spacing:.05em">
              Fases del proyecto
            </small>
          </li>

          <li class="nav-item">
            <a class="nav-link" routerLink="/planeacion" routerLinkActive="active" 
               (click)="close()" title="Planeación">
              <i class="bi bi-layers"></i>
              <span>Planeación</span>
            </a>
          </li>

          <li class="nav-item">
            <a class="nav-link" routerLink="/ejecucion" routerLinkActive="active" 
               (click)="close()" title="Ejecución">
              <i class="bi bi-pencil-square"></i>
              <span>Ejecución</span>
            </a>
          </li>

          <li class="nav-item">
            <a class="nav-link" routerLink="/evaluacion" routerLinkActive="active" 
               (click)="close()" title="Evaluación">
              <i class="bi bi-bar-chart-line"></i>
              <span>Evaluación</span>
            </a>
          </li>

          <!-- Separador IA -->
          <li class="nav-item px-3" style="padding-top: 8px; padding-bottom: 2px;">
            <small class="text-muted text-uppercase" style="font-size:var(--text-2xs);letter-spacing:.05em">
              Análisis IA
            </small>
          </li>

          <li class="nav-item">
            <a class="nav-link" routerLink="/dashboard" routerLinkActive="active" 
               (click)="close()" title="Dashboard">
              <i class="bi bi-speedometer2"></i>
              <span>Dashboard</span>
            </a>
          </li>

          <!-- AI Insights: sin entrada propia (reorganización de navegación) —
               su funcionalidad ya está integrada en Dashboard vía
               app-insights-quick-view. Ruta /ai-insights preservada e intacta,
               solo dejó de listarse aquí. -->

          <li class="nav-item">
            <a class="nav-link" routerLink="/ai-report" routerLinkActive="active" 
               (click)="close()" title="Reportes">
              <i class="bi bi-file-earmark-bar-graph"></i>
              <span>Reportes</span>
            </a>
          </li>

          <!-- Retrospectivas: sin entrada propia (reorganización de navegación) —
               su acceso ahora vive dentro de Dashboard (app-retrospective-panel).
               Ruta /ai-retrospective preservada e intacta, solo dejó de listarse aquí. -->
        }

        <!-- Copiloto deshabilitado (no se usa en esta versión): entrada de menú
             removida. La ruta /configuracion (config. de sincronización del
             Copiloto) se preserva intacta, solo queda inaccesible desde la
             navegación. -->
      </ul>

      <!-- User / logout -->
      <div class="border-top p-3">
        <small class="text-muted text-uppercase d-block mb-2" style="font-size:var(--text-2xs);letter-spacing:.05em">
          General
        </small>
        <div class="d-flex align-items-center gap-2 mb-2">
          <div>
            <strong>{{ nombreMostrado() }}</strong>
            <small class="text-muted d-block">{{ esScrumMaster() ? 'Scrum Master' : 'Scrum Member' }}</small>
          </div>
          <i class="bi bi-three-dots-vertical ms-auto"></i>
        </div>
        <button class="btn btn-sm btn-outline-secondary w-100" (click)="auth.logout()">
          <i class="bi bi-box-arrow-right me-1"></i>Cerrar sesión
        </button>
      </div>
    </nav>
  `
})
export class SidebarComponent implements OnInit, OnDestroy {
  open = signal(false);
  collapsed = signal(false);
  proyectoActivo = signal<ProyectoDto | null>(this.leerProyectoActivo());

  private routerSub?: Subscription;

  constructor(public auth: AuthService, private router: Router) {}

  ngOnInit(): void {
    // Actualizar proyecto activo en cada navegación (por si cambió en localStorage)
    this.routerSub = this.router.events.pipe(
      filter(e => e instanceof NavigationEnd)
    ).subscribe(() => {
      this.proyectoActivo.set(this.leerProyectoActivo());
    });
  }

  ngOnDestroy(): void {
    this.routerSub?.unsubscribe();
  }

  private leerProyectoActivo(): ProyectoDto | null {
    try {
      const raw = localStorage.getItem('mpdia_proyecto_activo');
      return raw ? JSON.parse(raw) : null;
    } catch { return null; }
  }

  /**
   * Corrección: Scrum Master es siempre relativo al proyecto activo (su
   * scrumMasterEmail, fijado por el backend al crearlo), nunca el rol
   * global de cuenta — mismo patrón ya corregido en dashboard.component.ts
   * (esScrumMasterDelProyecto).
   */
  esScrumMaster(): boolean {
    return this.proyectoActivo()?.scrumMasterEmail === this.auth.currentUser()?.email;
  }

  // El backend solo garantiza `nombre` para usuarios creados después de V33
  // (migración que agregó la columna) o autenticados vía Google. Para
  // cuentas anteriores sin nombre guardado, se usa el correo como último
  // recurso en vez de dejar el sidebar vacío.
  nombreMostrado(): string {
    const user = this.auth.currentUser();
    if (user?.nombre?.trim()) return user.nombre.trim();
    if (user?.email) return user.email.split('@')[0];
    return 'Usuario';
  }

  toggle(): void { this.open.update(v => !v); }
  close(): void  { this.open.set(false); }
  toggleCollapse(): void { this.collapsed.update(v => !v); }
}
