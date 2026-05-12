import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SidebarComponent } from '../sidebar/sidebar.component';

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
          <!-- Mobile toggle -->
          <button class="btn btn-sm btn-outline-secondary d-md-none"
                  (click)="sidebar.toggle()">
            <i class="bi bi-list"></i>
          </button>
          <h1>{{ title }}</h1>
        </header>

        <!-- Content -->
        <main class="page-body">
          <ng-content></ng-content>
        </main>
      </div>
    </div>
  `
})
export class ShellComponent {
  @Input() title = '';
}
