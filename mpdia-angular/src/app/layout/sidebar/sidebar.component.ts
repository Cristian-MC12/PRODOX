import { Component, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../services/auth.service';

interface NavItem {
  label: string;
  icon: string;
  route: string;
}

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

      <!-- Nav links -->
      <ul class="nav flex-column mt-2 flex-grow-1">
        @for (item of navItems; track item.route) {
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

  navItems: NavItem[] = [
    { label: 'Selección',        icon: 'bi-layers',          route: '/seleccion'         },
    { label: 'Resumen',          icon: 'bi-table',           route: '/resumen-seleccion' },
    { label: 'Verificación SM',  icon: 'bi-clipboard-check', route: '/verificacion'      },
    { label: 'Copiloto',         icon: 'bi-robot',           route: '/configuracion'     },
    { label: 'Equipo Scrum',     icon: 'bi-people',          route: '/equipo'            },
    { label: 'Sprints',          icon: 'bi-calendar3',       route: '/sprints'           },
  ];

  constructor(public auth: AuthService) {}

  toggle(): void { this.open.update(v => !v); }
  close(): void  { this.open.set(false); }
}
