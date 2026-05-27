// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../services/auth.service';

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

      <!-- Rol badge -->
      <div class="px-3 pb-2">
        <span class="badge w-100 py-1"
              [class]="esScrumMaster() ? 'bg-primary' : 'bg-secondary'">
          <i class="bi me-1" [class]="esScrumMaster() ? 'bi-shield-check' : 'bi-person'"></i>
          {{ esScrumMaster() ? 'Scrum Master' : 'Scrum Member' }}
        </span>
      </div>

      <!-- Nav links -->
      <ul class="nav flex-column mt-1 flex-grow-1">
        @for (item of navItemsFiltrados(); track item.route) {
          <li class="nav-item">
            <a class="nav-link"
               [routerLink]="item.route"
               routerLinkActive="active"
               [routerLinkActiveOptions]="{ exact: item.route === '/' }"
               (click)="close()">
              <i class="bi {{ item.icon }}"></i>
              {{ item.label }}
            </a>
          </li>
        }
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
export class SidebarComponent {
  open = signal(false);

  private readonly allNavItems = [
    { label: 'Selección',       icon: 'bi-layers',          route: '/seleccion',        roles: ['scrum_member', 'scrum_master'] },
    { label: 'Resumen',         icon: 'bi-table',           route: '/resumen-seleccion',roles: ['scrum_member', 'scrum_master'] },
    { label: 'Verificación SM', icon: 'bi-clipboard-check', route: '/verificacion',     roles: ['scrum_master'] },
    { label: 'Copiloto',        icon: 'bi-robot',           route: '/configuracion',    roles: ['scrum_member', 'scrum_master'] },
    { label: 'Equipo Scrum',    icon: 'bi-people',          route: '/equipo',           roles: ['scrum_member', 'scrum_master'] },
    { label: 'Sprints',         icon: 'bi-calendar3',       route: '/sprints',          roles: ['scrum_member', 'scrum_master'] },
  ];

  constructor(public auth: AuthService) {}

  esScrumMaster(): boolean {
    return this.auth.currentUser()?.role === 'scrum_master';
  }

  navItemsFiltrados() {
    const role = this.auth.currentUser()?.role ?? 'scrum_member';
    return this.allNavItems.filter(item => item.roles.includes(role));
  }

  toggle(): void { this.open.update(v => !v); }
  close(): void  { this.open.set(false); }
}
