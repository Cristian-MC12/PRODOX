// Autor: Cristian Santiago Martinez Cordoba — PRODOX
import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, NavigationEnd, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../services/auth.service';
import { ProyectoDto } from '../../models/proyecto.model';
import { etiquetaRol, ROL_SCRUM_MASTER } from '../../models/project-role.model';
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

      <!-- Project Card Container - Solo visible cuando NO está colapsado -->
      @if (proyectoActivo() && !collapsed()) {
        <div class="px-3 project-card-wrapper" style="padding-bottom: 2px;">
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
                  <span style="font-size: 12px; font-weight: 500; color: #fff;">{{ rolLabel() }}</span>
                </div>
                <i class="bi bi-chevron-down" style="font-size: 10px; color: rgba(148, 163, 184, 0.4);"></i>
              </div>
            </div>
          </div>
        </div>
      }

      <!-- Nav links -->
      <ul class="nav flex-column flex-grow-1" style="margin-top: 0px;">
        <!-- Separador Proyecto - Solo visible cuando NO está colapsado -->
        @if (!collapsed()) {
          <li class="nav-item px-3" style="padding-top: 0px; padding-bottom: 2px;">
            <small class="text-muted text-uppercase" style="font-size:var(--text-2xs);letter-spacing:.05em">
              Proyecto
            </small>
          </li>
        }

        <!-- Proyectos siempre visible -->
        <li class="nav-item">
          <a class="nav-link" routerLink="/proyectos" routerLinkActive="active" 
             [title]="collapsed() ? 'Proyectos' : ''">
            <i class="bi bi-folder2-open"></i>
            @if (!collapsed()) {
              <span>Proyectos</span>
            }
          </a>
        </li>

        <!-- Sprints: estructura del proyecto (no es una fase secuencial del
             proceso como Planeación/Ejecución/Evaluación), agrupado junto a
             Proyectos/Equipo. Mismo routerLink /sprints, sin duplicar la ruta. -->
        @if (proyectoActivo()) {
          <li class="nav-item">
            <a class="nav-link" routerLink="/sprints" routerLinkActive="active" 
               [title]="collapsed() ? 'Sprints' : ''">
              <i class="bi bi-calendar3-range"></i>
              @if (!collapsed()) {
                <span>Sprints</span>
              }
            </a>
          </li>
        }

        <!-- Equipo: inmediatamente debajo de Proyectos/Sprints (reorganización de navegación) -->
        <li class="nav-item">
          <a class="nav-link" routerLink="/equipo" routerLinkActive="active" 
             [title]="collapsed() ? 'Equipo' : ''">
            <i class="bi bi-people"></i>
            @if (!collapsed()) {
              <span>Equipo</span>
            }
          </a>
        </li>

        @if (proyectoActivo()) {
          <!-- Separador de fase - Solo visible cuando NO está colapsado -->
          @if (!collapsed()) {
            <li class="nav-item px-3" style="padding-top: 0px; padding-bottom: 2px;">
              <small class="text-muted text-uppercase" style="font-size:var(--text-2xs);letter-spacing:.05em">
                Fases del proyecto
              </small>
            </li>
          }

          <li class="nav-item">
            <a class="nav-link" routerLink="/planeacion" routerLinkActive="active"
               [title]="collapsed() ? 'Planeación' : ''">
              <i class="bi bi-layers"></i>
              @if (!collapsed()) {
                <span>Planeación</span>
              }
            </a>
          </li>

          <!-- Backlog NO es una cuarta fase del proyecto — PRODOX tiene
               exactamente 3 fases (Planeación, Ejecución, Evaluación). El
               Backlog de producto es una funcionalidad DENTRO de Planeación,
               por eso se muestra indentado como sub-ítem suyo (clase
               nav-subitem) aunque conserve su propia ruta /backlog. Sigue
               visible para todo miembro del proyecto (lectura garantizada
               por el backend a cualquier miembro); solo el Product Owner ve,
               dentro de la propia vista, las acciones de crear/editar/priorizar. -->
          <li class="nav-item">
            <a class="nav-link nav-subitem" routerLink="/backlog" routerLinkActive="active"
               [title]="collapsed() ? 'Backlog' : ''">
              <i class="bi bi-card-list"></i>
              @if (!collapsed()) {
                <span>Backlog</span>
              }
            </a>
          </li>

          <li class="nav-item">
            <a class="nav-link" routerLink="/ejecucion" routerLinkActive="active" 
               [title]="collapsed() ? 'Ejecución' : ''">
              <i class="bi bi-pencil-square"></i>
              @if (!collapsed()) {
                <span>Ejecución</span>
              }
            </a>
          </li>

          <li class="nav-item">
            <a class="nav-link" routerLink="/evaluacion" routerLinkActive="active" 
               [title]="collapsed() ? 'Evaluación' : ''">
              <i class="bi bi-bar-chart-line"></i>
              @if (!collapsed()) {
                <span>Evaluación</span>
              }
            </a>
          </li>

          <!-- Separador IA - Solo visible cuando NO está colapsado -->
          @if (!collapsed()) {
            <li class="nav-item px-3" style="padding-top: 8px; padding-bottom: 2px;">
              <small class="text-muted text-uppercase" style="font-size:var(--text-2xs);letter-spacing:.05em">
                Análisis IA
              </small>
            </li>
          }

          <li class="nav-item">
            <a class="nav-link" routerLink="/dashboard" routerLinkActive="active" 
               [title]="collapsed() ? 'Dashboard' : ''">
              <i class="bi bi-speedometer2"></i>
              @if (!collapsed()) {
                <span>Dashboard</span>
              }
            </a>
          </li>

          <!-- AI Insights: sin entrada propia (reorganización de navegación) —
               su funcionalidad ya está integrada en Dashboard vía
               app-insights-quick-view. Ruta /ai-insights preservada e intacta,
               solo dejó de listarse aquí. -->

          <!-- Reportes: sin entrada propia (reorganización de navegación) —
               su acceso ahora vive dentro de Dashboard como botón de acceso rápido,
               junto a Insights IA y Retrospectiva. Ruta /ai-report preservada e
               intacta, solo dejó de listarse aquí. -->

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
      <div class="border-top user-section" style="padding: 0.5rem 1rem;">
        @if (!collapsed()) {
          <div class="d-flex align-items-center gap-2 mb-2">
            <div class="user-avatar">
              {{ nombreMostrado().charAt(0).toUpperCase() }}
            </div>
            <div class="flex-grow-1">
              <strong class="d-block">{{ nombreMostrado() }}</strong>
              <small class="text-muted d-block">{{ rolLabel() }}</small>
            </div>
            <i class="bi bi-three-dots-vertical"></i>
          </div>
          <button class="btn btn-sm btn-outline-secondary w-100" (click)="auth.logout()">
            <i class="bi bi-box-arrow-right me-1"></i>Cerrar sesión
          </button>
        } @else {
          <!-- Vista colapsada: solo avatar y botón de logout -->
          <div class="d-flex flex-column align-items-center gap-2">
            <div class="user-avatar-collapsed" [title]="nombreMostrado()">
              {{ nombreMostrado().charAt(0).toUpperCase() }}
            </div>
            <button class="btn btn-icon-only" (click)="auth.logout()" title="Cerrar sesión">
              <i class="bi bi-box-arrow-right"></i>
            </button>
          </div>
        }
      </div>
    </nav>
  `
})
export class SidebarComponent implements OnInit, OnDestroy {
  open = signal(false);
  collapsed = signal(this.leerEstadoColapsado());
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

  private leerEstadoColapsado(): boolean {
    try {
      const raw = localStorage.getItem('mpdia_sidebar_collapsed');
      return raw === 'true';
    } catch { return false; }
  }

  private guardarEstadoColapsado(collapsed: boolean): void {
    try {
      localStorage.setItem('mpdia_sidebar_collapsed', String(collapsed));
    } catch { }
  }

  /**
   * V39: el rol POR PROYECTO sale de ProyectoDto.miRol (calculado por el
   * backend a partir de ProjectMember.rol), ya NO de comparar
   * proyecto.scrumMasterEmail contra el email de la cuenta — ese email
   * seguía siendo válido para distinguir SM de "no-SM", pero con un tercer
   * rol (Product Owner) hace falta la fuente real, no una comparación que
   * solo distingue dos casos.
   *
   * Fallback: si `miRol` todavía no llega (proyecto activo cacheado en
   * localStorage ANTES de V39, o un backend que aún no fue reiniciado con
   * este cambio), se recupera el criterio previo — comparar el email de la
   * cuenta contra scrumMasterEmail — para no mostrarle "Scrum Member" a un
   * Scrum Master real mientras esos datos se actualizan.
   */
  esScrumMaster(): boolean {
    const proyecto = this.proyectoActivo();
    if (proyecto?.miRol) return proyecto.miRol === ROL_SCRUM_MASTER;
    return proyecto?.scrumMasterEmail === this.auth.currentUser()?.email;
  }

  /** Etiqueta a mostrar para el rol del usuario en el proyecto activo. */
  rolLabel(): string {
    const proyecto = this.proyectoActivo();
    if (proyecto?.miRol) return etiquetaRol(proyecto.miRol);
    return this.esScrumMaster() ? 'Scrum Master' : 'Scrum Member';
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
  close(): void  { 
    // Solo cerrar en modo móvil, NO cambiar el estado collapsed
    if (this.open()) {
      this.open.set(false); 
    }
  }
  toggleCollapse(): void { 
    this.collapsed.update(v => {
      const newValue = !v;
      this.guardarEstadoColapsado(newValue);
      return newValue;
    });
  }
}
