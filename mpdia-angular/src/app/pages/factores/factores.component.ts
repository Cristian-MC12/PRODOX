// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ShellComponent } from '../../layout/shell/shell.component';
import { FactorService } from '../../services/factor.service';
import { Factor, SprintSelection } from '../../models/factor.model';

@Component({
  selector: 'app-factores',
  standalone: true,
  imports: [CommonModule, ShellComponent],
  template: `
    <app-shell title="Selección de Factores">
      <p class="text-muted small mb-4">
        Elige los factores que el equipo medirá durante el sprint actual.
        Cada factor genera indicadores que se aprueban en el Dashboard.
      </p>

      <!-- Alert -->
      @if (alertMsg) {
        <div class="alert" [class]="alertClass + ' py-2 small'">{{ alertMsg }}</div>
      }

      @if (loading) {
        <div class="text-center py-5">
          <div class="spinner-border text-primary"></div>
        </div>
      } @else {
        <div class="row g-3">
          @for (factor of factors; track factor.id) {
            <div class="col-sm-6 col-lg-4">
              <div class="card factor-card h-100" [class.selected]="isSelected(factor.id)">
                <div class="card-body">
                  <div class="d-flex justify-content-between align-items-start mb-2">
                    <h6 class="card-title mb-0">{{ factor.name }}</h6>
                    <span class="badge" [class]="categoryBadge(factor.category)">
                      {{ factor.category }}
                    </span>
                  </div>
                  <p class="card-text text-muted small">{{ factor.description }}</p>
                  <button class="btn btn-sm w-100 mt-2"
                          [class]="isSelected(factor.id) ? 'btn-outline-primary' : 'btn-primary'"
                          [disabled]="busy"
                          (click)="toggle(factor)">
                    @if (isSelected(factor.id)) {
                      <i class="bi bi-check-lg me-1"></i>Seleccionado — quitar
                    } @else {
                      <i class="bi bi-plus-lg me-1"></i>Seleccionar para el Sprint
                    }
                  </button>
                </div>
              </div>
            </div>
          }
        </div>
      }
    </app-shell>
  `
})
export class FactoresComponent implements OnInit {
  factors: Factor[]          = [];
  selections: SprintSelection[] = [];
  loading = true;
  busy    = false;
  alertMsg   = '';
  alertClass = 'alert-success';

  constructor(private factorService: FactorService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.factorService.list().subscribe(f => {
      this.factors = f;
      this.factorService.listSelections().subscribe(s => {
        this.selections = s;
        this.loading = false;
      });
    });
  }

  isSelected(id: string): boolean {
    return this.selections.some(s => s.factorId === id);
  }

  toggle(factor: Factor): void {
    this.busy = true;
    if (this.isSelected(factor.id)) {
      this.factorService.unselect(factor.id).subscribe({
        next: () => { this.showAlert('Factor removido del sprint.', 'alert-secondary'); this.load(); this.busy = false; },
        error: () => { this.showAlert('Error al remover el factor.', 'alert-danger'); this.busy = false; }
      });
    } else {
      this.factorService.select({ factorId: factor.id, sprintName: 'Sprint Actual' }).subscribe({
        next: () => { this.showAlert('Factor agregado al sprint.', 'alert-success'); this.load(); this.busy = false; },
        error: () => { this.showAlert('Error al seleccionar el factor.', 'alert-danger'); this.busy = false; }
      });
    }
  }

  categoryBadge(cat: string): string {
    const map: Record<string, string> = {
      'Productividad': 'bg-primary',
      'Calidad':       'bg-warning text-dark',
      'Cumplimiento':  'bg-success',
      'Sociohumano':   'bg-info text-dark'
    };
    return map[cat] ?? 'bg-secondary';
  }

  private showAlert(msg: string, cls: string): void {
    this.alertMsg   = msg;
    this.alertClass = cls;
    setTimeout(() => this.alertMsg = '', 3000);
  }
}
