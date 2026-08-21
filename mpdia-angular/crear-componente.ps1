# Script para crear metrica-academica.component.ts
# Ejecutar: .\crear-componente.ps1

$template = @'
<app-shell [title]="metricaNombre">
  <div *ngIf="loading" class="text-center py-5">
    <div class="spinner-border"></div>
  </div>
  <div *ngIf="error" class="alert alert-danger">{{error}}</div>
  <div *ngIf="!loading && !error">
    <div *ngIf="sinParametrizacion" class="alert alert-warning">
      No existe parametrización aprobada.
      <button class="btn btn-sm btn-primary" (click)="irAParametrizacion()">Parametrizar</button>
    </div>
    <div *ngIf="parametrizacion" class="card mb-3">
      <div class="card-body">
        <div *ngFor="let v of variables" class="mb-3">
          <label>{{v.etiqueta}}</label>
          <input type="number" class="form-control" [(ngModel)]="valores[v.nombre]" min="0">
          <div *ngIf="mostrarErrores && !esValido(v.nombre)" class="text-danger small">Campo requerido</div>
        </div>
        <button class="btn btn-primary" [disabled]="ejecutando" (click)="ejecutar()">
          {{ejecutando ? 'Ejecutando...' : 'Ejecutar'}}
        </button>
      </div>
    </div>
    <div *ngIf="resultado" class="card mt-3">
      <div class="card-body">
        <h4>{{resultado.resultado}} {{resultado.unidad}}</h4>
        <button class="btn btn-sm btn-primary" [disabled]="interpretando" (click)="analizarConIA()">
          {{interpretando ? 'Analizando...' : 'Analizar con IA'}}
        </button>
        <div *ngIf="interpretacion" class="alert alert-info mt-3">{{interpretacion.interpretacion}}</div>
      </div>
    </div>
  </div>
</app-shell>
'@

$content = @"
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ShellComponent } from '../../layout/shell/shell.component';
import { MetricaAcademicaService } from '../../services/metrica-academica.service';
import { SprintService } from '../../services/sprint.service';
import { ParametrizacionAcademicaDto, EjecutarMetricaAcademicaRequest, ResultadoMetricaDto, InterpretacionIADto, VariableAcademica } from '../../models/metrica-academica.model';
import { SprintDto } from '../../models/sprint.model';
import { ProyectoDto } from '../../models/proyecto.model';

@Component({
  selector: 'app-metrica-academica',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: ``$template``,
  styles: []
})
export class MetricaAcademicaComponent implements OnInit {
  metricaId = '';
  metricaNombre = 'SIG-SC-02';
  loading = true;
  error = '';
  proyectoActivo: ProyectoDto | null = null;
  sprintActivo: SprintDto | null = null;
  parametrizacion: ParametrizacionAcademicaDto | null = null;
  sinParametrizacion = false;
  variables: VariableAcademica[] = [{
    nombre: 'problemas_reportados',
    etiqueta: 'Problemas reportados',
    tipo: 'INTEGER',
    unidad: 'problemas',
    requerida: true
  }];
  valores: any = {};
  mostrarErrores = false;
  ejecutando = false;
  resultado: ResultadoMetricaDto | null = null;
  historico: ResultadoMetricaDto[] = [];
  interpretando = false;
  interpretacion: InterpretacionIADto | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private metricaService: MetricaAcademicaService,
    private sprintService: SprintService
  ) {}

  ngOnInit(): void {
    this.metricaId = this.route.snapshot.paramMap.get('id') || '';
    this.cargarContexto();
  }

  cargarContexto(): void {
    const pStr = localStorage.getItem('mpdia_proyecto_activo');
    const sStr = localStorage.getItem('mpdia_sprint_activo');
    if (!pStr || !sStr) {
      this.error = 'No hay contexto';
      this.loading = false;
      return;
    }
    this.proyectoActivo = JSON.parse(pStr);
    this.sprintActivo = JSON.parse(sStr);
    this.cargarParametrizacion();
  }

  cargarParametrizacion(): void {
    this.loading = false;
    this.sinParametrizacion = true;
  }

  esValido(n: string): boolean {
    const v = this.valores[n];
    return v !== null && v !== undefined && v !== '' && v >= 0;
  }

  ejecutar(): void {
    this.mostrarErrores = true;
    if (!this.esValido('problemas_reportados')) return;
    if (!this.proyectoActivo || !this.sprintActivo) return;
    this.ejecutando = true;
    this.metricaService.ejecutar(this.metricaId, {
      proyectoId: this.proyectoActivo.id,
      sprintId: this.sprintActivo.id,
      valores: this.valores
    }).subscribe({
      next: (r) => {
        this.resultado = r;
        this.ejecutando = false;
      },
      error: () => {
        this.ejecutando = false;
      }
    });
  }

  analizarConIA(): void {
    if (!this.resultado) return;
    this.interpretando = true;
    this.metricaService.solicitarInterpretacion(this.resultado.resultadoId).subscribe({
      next: (i) => {
        this.interpretacion = i;
        this.interpretando = false;
      },
      error: () => {
        this.interpretando = false;
      }
    });
  }

  irAParametrizacion(): void {
    this.router.navigate(['/parametrizacion', this.metricaId]);
  }
}
"@

Remove-Item "src/app/pages/metrica-academica/metrica-academica.component.ts" -ErrorAction SilentlyContinue
$content | Out-File -FilePath "src/app/pages/metrica-academica/metrica-academica.component.ts" -Encoding utf8
Write-Host "Componente creado exitosamente" -ForegroundColor Green


# Agregar métodos faltantes
$addition = @"

  frecuenciaLabel(frecuencia: string): string {
    const labels: { [key: string]: string } = {
      'por_sprint': 'Por sprint',
      'semanal': 'Semanal',
      'diaria': 'Diaria',
      'ilimitada': 'Cuando ocurra el evento'
    };
    return labels[frecuencia] || frecuencia;
  }

  verDetalle(resultado: ResultadoMetricaDto): void {
    this.resultado = resultado;
    this.interpretacion = null;
  }
"@

$currentContent = Get-Content "src/app/pages/metrica-academica/metrica-academica.component.ts" -Raw
$newContent = $currentContent -replace '}\s*$', "$addition`r`n}"
$newContent | Out-File -FilePath "src/app/pages/metrica-academica/metrica-academica.component.ts" -Encoding utf8
Write-Host "Métodos agregados exitosamente" -ForegroundColor Green
