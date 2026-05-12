// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../layout/shell/shell.component';
import { FactorService } from '../../services/factor.service';
import { MetricaPlanService } from '../../services/metrica-plan.service';
import { ObjetivoService } from '../../services/objetivo.service';
import { CopilotService } from '../../services/copilot.service';
import { CopilotConfig } from '../../models/copilot.model';

@Component({
  selector: 'app-inicio',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell title="Inicio">
      <div class="mb-4">
        <h2 class="h5 fw-semibold">Bienvenido al sistema MPDIA</h2>
        <p class="text-muted small">
          Sistema de Medición de Productividad para equipos Scrum.
          Actualmente en la <strong>Fase de Planeación</strong>: definí cómo vas a medir
          la productividad antes de iniciar el sprint.
        </p>
      </div>

      <!-- Progreso de la planeación -->
      <div class="card mb-4 border-primary">
        <div class="card-header bg-primary text-white fw-semibold small">
          <i class="bi bi-diagram-3 me-1"></i>Progreso — Fase de Planeación
        </div>
        <div class="card-body">
          <div class="row g-3">
            <div class="col-md-3 text-center">
              <div class="rounded-circle d-inline-flex align-items-center justify-content-center mb-2"
                   style="width:48px;height:48px"
                   [class]="objetivosCount > 0 ? 'bg-success text-white' : 'bg-light text-muted border'">
                <i class="bi bi-bullseye fs-5"></i>
              </div>
              <div class="small fw-semibold">1. Objetivos</div>
              <div class="text-muted" style="font-size:0.75rem">
                {{ objetivosCount > 0 ? objetivosCount + ' definido(s)' : 'Pendiente' }}
              </div>
            </div>
            <div class="col-md-3 text-center">
              <div class="rounded-circle d-inline-flex align-items-center justify-content-center mb-2"
                   style="width:48px;height:48px"
                   [class]="factoresCount > 0 ? 'bg-success text-white' : 'bg-light text-muted border'">
                <i class="bi bi-layers fs-5"></i>
              </div>
              <div class="small fw-semibold">2. Factores</div>
              <div class="text-muted" style="font-size:0.75rem">
                {{ factoresCount > 0 ? factoresCount + ' seleccionado(s)' : 'Pendiente' }}
              </div>
            </div>
            <div class="col-md-3 text-center">
              <div class="rounded-circle d-inline-flex align-items-center justify-content-center mb-2"
                   style="width:48px;height:48px"
                   [class]="metricasCount > 0 ? 'bg-success text-white' : 'bg-light text-muted border'">
                <i class="bi bi-bar-chart-line fs-5"></i>
              </div>
              <div class="small fw-semibold">3. Métricas</div>
              <div class="text-muted" style="font-size:0.75rem">
                {{ metricasCount > 0 ? metricasCount + ' definida(s)' : 'Pendiente' }}
              </div>
            </div>
            <div class="col-md-3 text-center">
              <div class="rounded-circle d-inline-flex align-items-center justify-content-center mb-2"
                   style="width:48px;height:48px"
                   [class]="copilot?.active ? 'bg-success text-white' : 'bg-light text-muted border'">
                <i class="bi bi-robot fs-5"></i>
              </div>
              <div class="small fw-semibold">4. Copiloto</div>
              <div class="text-muted" style="font-size:0.75rem">
                {{ copilot?.active ? 'Configurado' : 'Pendiente' }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Accesos rápidos -->
      <div class="row g-3">
        <div class="col-md-4">
          <div class="card h-100">
            <div class="card-body">
              <h6 class="card-title">
                <i class="bi bi-bullseye text-primary me-1"></i>Objetivos
              </h6>
              <p class="card-text text-muted small">
                Definí los objetivos de medición y criterios de éxito del sprint.
              </p>
              <a routerLink="/objetivos" class="btn btn-outline-primary btn-sm w-100">Ir a Objetivos</a>
            </div>
          </div>
        </div>
        <div class="col-md-4">
          <div class="card h-100">
            <div class="card-body">
              <h6 class="card-title">
                <i class="bi bi-layers text-primary me-1"></i>Factores
              </h6>
              <p class="card-text text-muted small">
                Seleccioná qué factores de productividad medirá el equipo en este sprint.
              </p>
              <a routerLink="/factores" class="btn btn-outline-primary btn-sm w-100">Ir a Factores</a>
            </div>
          </div>
        </div>
        <div class="col-md-4">
          <div class="card h-100">
            <div class="card-body">
              <h6 class="card-title">
                <i class="bi bi-bar-chart-line text-primary me-1"></i>Métricas
              </h6>
              <p class="card-text text-muted small">
                Definí cómo se medirá cada factor: unidad, valor meta y fuente de datos.
              </p>
              <a routerLink="/dashboard" class="btn btn-outline-primary btn-sm w-100">Ir a Métricas</a>
            </div>
          </div>
        </div>
        <div class="col-md-4">
          <div class="card h-100">
            <div class="card-body">
              <h6 class="card-title">
                <i class="bi bi-robot text-primary me-1"></i>Copiloto
              </h6>
              <p class="card-text text-muted small">
                Configurá Jira o GitHub para que el Copiloto recopile datos automáticamente.
              </p>
              <a routerLink="/configuracion" class="btn btn-outline-primary btn-sm w-100">Configurar</a>
            </div>
          </div>
        </div>
        <div class="col-md-4">
          <div class="card h-100">
            <div class="card-body">
              <h6 class="card-title">
                <i class="bi bi-clipboard-check text-primary me-1"></i>Verificación
              </h6>
              <p class="card-text text-muted small">
                El Scrum Master verifica que la planeación esté completa antes de ejecutar.
              </p>
              <a routerLink="/verificacion" class="btn btn-outline-primary btn-sm w-100">Verificar</a>
            </div>
          </div>
        </div>
      </div>
    </app-shell>
  `
})
export class InicioComponent implements OnInit {
  objetivosCount = 0;
  factoresCount  = 0;
  metricasCount  = 0;
  copilot: CopilotConfig | null = null;

  constructor(
    private factorService: FactorService,
    private metricaPlanService: MetricaPlanService,
    private objetivoService: ObjetivoService,
    private copilotService: CopilotService
  ) {}

  ngOnInit(): void {
    this.objetivoService.getAll().subscribe(list => {
      this.objetivosCount = list.filter(o => o.sprintName === 'Sprint Actual').length;
    });
    this.factorService.listSelections().subscribe(s => this.factoresCount = s.length);
    this.metricaPlanService.getAll().subscribe(list => {
      this.metricasCount = list.filter(m => m.sprintName === 'Sprint Actual').length;
    });
    this.copilotService.get().subscribe({
      next: c => this.copilot = c,
      error: () => {}
    });
  }
}
