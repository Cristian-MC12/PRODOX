// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { SprintService } from '../../services/sprint.service';
import { ProyectoDto } from '../../models/proyecto.model';
import { SprintDto } from '../../models/sprint.model';
import { catchError, of } from 'rxjs';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, SidebarComponent],
  template: `
    <div class="d-flex">
      <app-sidebar #sidebar></app-sidebar>

      <div class="main-content w-100">
        <!-- Header -->
        <header class="page-header d-flex align-items-center gap-3">
          <button class="btn btn-sm btn-outline-secondary d-md-none"
                  (click)="sidebar.toggle()">
            <i class="bi bi-list"></i>
          </button>
          <h1>{{ title }}</h1>
        </header>

        <!-- Banner contextual: proyecto + sprint activo -->
        @if (showBanner && proyecto) {
          <div class="px-3 pt-2">
            <div class="alert alert-primary py-2 mb-0 d-flex align-items-center gap-3 flex-wrap"
                 style="font-size:0.82rem;border-radius:8px">
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
                <span class="text-muted">{{ proyecto.timeBoxSemanas }} sem/iteración</span>
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
  `
})
export class ShellComponent implements OnInit {
  @Input() title = '';
  @Input() showBanner = true;

  proyecto: ProyectoDto | null = null;
  sprint: SprintDto | null     = null;

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
}
