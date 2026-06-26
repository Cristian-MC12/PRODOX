// Autor: Cristian Santiago Martinez Cordoba — MPDIA
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
    <nav class="sidebar d-flex flex-column" [class.open]="open()">

      <!-- Brand -->
      <a routerLink="/" class="sidebar-brand">
        <i class="bi bi-speedometer2 me-2"></i>MPDIA
      </a>

      <!-- Proyecto activo -->
      @if (proyectoActivo()) {
        <div class="px-3 pb-2">
          <div class="small text-muted" style="font-size:0.7rem">Proyecto activo:</div>
          <div class="small fw-semibold text-truncate">{{ proyectoActivo()!.nombre }}</div>
          <div class="d-flex gap-1 mt-1 flex-wrap">
            <span class="badge"
                  [class]="proyectoActivo()!.metodo === 'scrum' ? 'bg-primary' : 'bg-info text-dark'"
                  style="font-size:0.6rem">
              {{ proyectoActivo()!.metodo === 'scrum' ? 'Scrum' : 'XP' }}
            </span>
            <span class="badge bg-light text-dark border" style="font-size:0.6rem">
              {{ proyectoActivo()!.numeroSprints }} sprints
            </span>
          </div>
        </div>
      }

      <!-- Rol del usuario -->
      <div class="px-3 pb-2">
        <span class="badge w-100 py-1"
              [class]="esScrumMaster() ? 'bg-primary' : 'bg-secondary'">
          <i class="bi me-1" [class]="esScrumMaster() ? 'bi-shield-check' : 'bi-person'"></i>
          {{ esScrumMaster() ? 'Scrum Master' : 'Scrum Member' }}
        </span>
      </div>

      <!-- Nav links -->
      <ul class="nav flex-column mt-1 flex-grow-1">
        <!-- Proyectos siempre visible -->
        <li class="nav-item">
          <a class="nav-link" routerLink="/proyectos" routerLinkActive="active" (click)="close()">
            <i class="bi bi-folder2-open"></i>Proyectos
          </a>
        </li>

        @if (proyectoActivo()) {
          <!-- Separador de fase -->
          <li class="nav-item px-3 pt-2">
            <small class="text-muted text-uppercase" style="font-size:0.65rem;letter-spacing:.05em">
              Fases del proyecto
            </small>
          </li>

          <li class="nav-item">
            <a class="nav-link" routerLink="/planeacion" routerLinkActive="active" (click)="close()">
              <i class="bi bi-layers"></i>
              <span>Planeación</span>
            </a>
          </li>

          <li class="nav-item">
            <a class="nav-link" routerLink="/ejecucion" routerLinkActive="active" (click)="close()">
              <i class="bi bi-pencil-square"></i>
              <span>Ejecución</span>
            </a>
          </li>

          <li class="nav-item">
            <a class="nav-link" routerLink="/evaluacion" routerLinkActive="active" (click)="close()">
              <i class="bi bi-bar-chart-line"></i>
              <span>Evaluación</span>
            </a>
          </li>

          <!-- Separador general -->
          <li class="nav-item px-3 pt-2">
            <small class="text-muted text-uppercase" style="font-size:0.65rem;letter-spacing:.05em">
              General
            </small>
          </li>

          <li class="nav-item">
            <a class="nav-link" routerLink="/equipo" routerLinkActive="active" (click)="close()">
              <i class="bi bi-people"></i>Equipo
            </a>
          </li>
        }

        <li class="nav-item">
          <a class="nav-link" routerLink="/configuracion" routerLinkActive="active" (click)="close()">
            <i class="bi bi-robot"></i>Copiloto
          </a>
        </li>
      </ul>

      <!-- User / logout -->
      <div class="border-top p-3">
        <div class="d-flex align-items-center gap-2 mb-2">
          <i class="bi bi-person-circle text-secondary"></i>
          <small class="text-muted text-truncate" style="max-width:160px">
            {{ auth.currentUser()?.email ?? 'Usuario' }}
          </small>
        </div>
        <button class="btn btn-sm btn-outline-secondary w-100" (click)="auth.logout()">
          <i class="bi bi-box-arrow-left me-1"></i>Cerrar sesión
        </button>
      </div>
    </nav>
  `
})
export class SidebarComponent implements OnInit, OnDestroy {
  open = signal(false);
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

  esScrumMaster(): boolean {
    return this.auth.currentUser()?.role === 'scrum_master';
  }

  toggle(): void { this.open.update(v => !v); }
  close(): void  { this.open.set(false); }
}
