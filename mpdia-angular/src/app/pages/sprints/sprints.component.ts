// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ShellComponent } from '../../layout/shell/shell.component';

interface Sprint {
  name: string;
  state: 'Activo' | 'Cerrado';
  from: string;
  to: string;
}

@Component({
  selector: 'app-sprints',
  standalone: true,
  imports: [CommonModule, ShellComponent],
  template: `
    <app-shell title="Sprints">
      <div class="row g-3">
        @for (sprint of sprints; track sprint.name) {
          <div class="col-sm-6 col-lg-4">
            <div class="card h-100">
              <div class="card-body">
                <div class="d-flex justify-content-between align-items-center mb-2">
                  <h6 class="card-title mb-0">{{ sprint.name }}</h6>
                  <span class="badge" [class]="sprint.state === 'Activo' ? 'bg-success' : 'bg-secondary'">
                    {{ sprint.state }}
                  </span>
                </div>
                <p class="card-text text-muted small mb-0">
                  <i class="bi bi-calendar3 me-1"></i>{{ sprint.from }} → {{ sprint.to }}
                </p>
              </div>
            </div>
          </div>
        }
      </div>
    </app-shell>
  `
})
export class SprintsComponent {
  sprints: Sprint[] = [
    { name: 'Sprint Actual', state: 'Activo',  from: '01/05', to: '14/05' },
    { name: 'Sprint 12',     state: 'Cerrado', from: '17/04', to: '30/04' },
    { name: 'Sprint 11',     state: 'Cerrado', from: '03/04', to: '16/04' },
  ];
}
