// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ShellComponent } from '../../layout/shell/shell.component';

interface TeamMember {
  name: string;
  role: string;
  initials: string;
}

@Component({
  selector: 'app-equipo',
  standalone: true,
  imports: [CommonModule, ShellComponent],
  template: `
    <app-shell title="Equipo Scrum">
      <div class="row g-3">
        @for (member of team; track member.name) {
          <div class="col-sm-6 col-lg-4">
            <div class="card h-100">
              <div class="card-body d-flex align-items-center gap-3">
                <div class="rounded-circle bg-primary text-white d-flex align-items-center
                            justify-content-center fw-bold flex-shrink-0"
                     style="width:44px;height:44px;font-size:1rem">
                  {{ member.initials }}
                </div>
                <div>
                  <div class="fw-semibold">{{ member.name }}</div>
                  <span class="badge bg-secondary">{{ member.role }}</span>
                </div>
              </div>
            </div>
          </div>
        }
      </div>
    </app-shell>
  `
})
export class EquipoComponent {
  team: TeamMember[] = [
    { name: 'Ana Torres',    role: 'Product Owner',  initials: 'AT' },
    { name: 'Luis Pérez',    role: 'Scrum Master',   initials: 'LP' },
    { name: 'María Gómez',   role: 'Developer',      initials: 'MG' },
    { name: 'Carlos Ruiz',   role: 'Developer',      initials: 'CR' },
    { name: 'Sofía Díaz',    role: 'QA',             initials: 'SD' },
  ];
}
