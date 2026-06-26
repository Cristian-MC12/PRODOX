// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ShellComponent } from '../../layout/shell/shell.component';
import { AuthService } from '../../services/auth.service';
import { ProyectoService } from '../../services/proyecto.service';
import { SprintService } from '../../services/sprint.service';
import { ProjectMemberService } from '../../services/project-member.service';
import { ProyectoDto } from '../../models/proyecto.model';

@Component({
  selector: 'app-proyectos',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, ShellComponent],
  template: `
    <app-shell title="Proyectos" [showBanner]="false">

      @if (alertMsg) {
        <div class="alert py-2 small" [class]="alertClass">{{ alertMsg }}</div>
      }

      <!-- Acciones superiores -->
      <div class="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-2">
        <p class="text-muted small mb-0">
          {{ esScrumMaster ? 'Gestioná tus proyectos y seleccioná uno para trabajar.' : 'Seleccioná un proyecto para comenzar a trabajar.' }}
        </p>
        <div class="d-flex gap-2">
          <!-- Unirse con código (Scrum Member) -->
          @if (!esScrumMaster) {
            <button class="btn btn-outline-success btn-sm" (click)="mostrarUnirse = !mostrarUnirse">
              <i class="bi bi-key me-1"></i>Unirse con código
            </button>
          }
          <!-- Crear proyecto (Scrum Master) -->
          @if (esScrumMaster) {
            <button class="btn btn-primary btn-sm" (click)="mostrarFormulario = !mostrarFormulario">
              <i class="bi bi-plus-lg me-1"></i>Nuevo proyecto
            </button>
          }
        </div>
      </div>

      <!-- Panel unirse con código -->
      @if (mostrarUnirse && !esScrumMaster) {
        <div class="card mb-4 border-success">
          <div class="card-header fw-semibold small bg-success text-white">
            <i class="bi bi-key me-1"></i>Unirse a un proyecto
          </div>
          <div class="card-body">
            <p class="text-muted small">Ingresá el código que te compartió el Scrum Master.</p>
            <div class="input-group input-group-sm" style="max-width:320px">
              <input type="text" class="form-control"
                     placeholder="Ej: PRJ-ABC123"
                     [(ngModel)]="codigoUnirse"
                     style="text-transform:uppercase;letter-spacing:2px">
              <button class="btn btn-success"
                      [disabled]="!codigoUnirse.trim() || uniendose"
                      (click)="unirseConCodigo()">
                @if (uniendose) {
                  <span class="spinner-border spinner-border-sm"></span>
                } @else {
                  Unirse
                }
              </button>
            </div>
          </div>
        </div>
      }

      <!-- Formulario crear proyecto (SM) -->
      @if (mostrarFormulario && esScrumMaster) {
        <div class="card mb-4 border-primary">
          <div class="card-header fw-semibold small bg-primary text-white">
            <i class="bi bi-folder-plus me-1"></i>Nuevo proyecto
          </div>
          <div class="card-body">
            <form [formGroup]="form" (ngSubmit)="crearProyecto()">
              <div class="row g-3">
                <div class="col-md-8">
                  <label class="form-label small fw-semibold">Nombre <span class="text-danger">*</span></label>
                  <input type="text" class="form-control form-control-sm"
                         placeholder="Ej: Sistema de gestión de inventarios"
                         formControlName="nombre"
                         [class.is-invalid]="f['nombre'].invalid && f['nombre'].touched">
                  <div class="invalid-feedback">Requerido.</div>
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold">Método ágil <span class="text-danger">*</span></label>
                  <select class="form-select form-select-sm" formControlName="metodo"
                          [class.is-invalid]="f['metodo'].invalid && f['metodo'].touched">
                    <option value="">Seleccionar...</option>
                    <option value="scrum">Scrum</option>
                    <option value="xp">XP (Extreme Programming)</option>
                  </select>
                  <div class="invalid-feedback">Requerido.</div>
                </div>
                <div class="col-12">
                  <label class="form-label small fw-semibold">Descripción</label>
                  <input type="text" class="form-control form-control-sm"
                         placeholder="Breve descripción..." formControlName="descripcion">
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold">Time Box <span class="text-danger">*</span></label>
                  <select class="form-select form-select-sm" formControlName="timeBoxSemanas"
                          [class.is-invalid]="f['timeBoxSemanas'].invalid && f['timeBoxSemanas'].touched">
                    <option value="">Seleccionar...</option>
                    <option [value]="1">1 semana</option>
                    <option [value]="2">2 semanas</option>
                    <option [value]="3">3 semanas</option>
                    <option [value]="4">4 semanas</option>
                  </select>
                  <div class="invalid-feedback">Requerido.</div>
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold">Número de Sprints <span class="text-danger">*</span></label>
                  <input type="number" class="form-control form-control-sm" min="1" max="20"
                         placeholder="Ej: 4" formControlName="numeroSprints"
                         [class.is-invalid]="f['numeroSprints'].invalid && f['numeroSprints'].touched">
                  <div class="invalid-feedback">Requerido (1-20).</div>
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold">Fecha de Inicio <span class="text-danger">*</span></label>
                  <input type="date" class="form-control form-control-sm"
                         formControlName="fechaInicio"
                         [class.is-invalid]="f['fechaInicio'].invalid && f['fechaInicio'].touched">
                  <div class="invalid-feedback">Requerido.</div>
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold">Product Goal <span class="text-danger">*</span></label>
                  <textarea class="form-control form-control-sm" rows="2"
                            placeholder="¿Qué se quiere lograr con el producto a largo plazo?"
                            formControlName="productGoal"
                            [class.is-invalid]="f['productGoal'].invalid && f['productGoal'].touched"></textarea>
                  <div class="invalid-feedback">Requerido.</div>
                </div>
              </div>
              <div class="d-flex gap-2 mt-3">
                <button type="submit" class="btn btn-primary btn-sm" [disabled]="form.invalid || creando">
                  @if (creando) { <span class="spinner-border spinner-border-sm me-1"></span> }
                  @else { <i class="bi bi-floppy me-1"></i> }
                  Crear proyecto
                </button>
                <button type="button" class="btn btn-outline-secondary btn-sm"
                        (click)="mostrarFormulario = false">Cancelar</button>
              </div>
            </form>
          </div>
        </div>
      }

      <!-- Lista de proyectos -->
      @if (cargando) {
        <div class="text-center py-5 text-muted">
          <span class="spinner-border me-2"></span>Cargando proyectos...
        </div>
      } @else if (proyectos.length === 0) {
        <div class="text-center py-5 text-muted">
          <i class="bi bi-folder-x fs-1 d-block mb-3 opacity-25"></i>
          @if (esScrumMaster) {
            <p>No tenés proyectos aún. Creá uno para comenzar.</p>
            <button class="btn btn-primary btn-sm" (click)="mostrarFormulario = true">
              <i class="bi bi-plus-lg me-1"></i>Crear primer proyecto
            </button>
          } @else {
            <p>No estás en ningún proyecto todavía.</p>
            <p class="small text-muted">Tu Scrum Master debe invitarte con un código de proyecto.</p>
            <button class="btn btn-outline-success btn-sm" (click)="mostrarUnirse = true">
              <i class="bi bi-key me-1"></i>Ingresar código de invitación
            </button>
          }
        </div>
      } @else {
        <div class="row g-3">
          @for (p of proyectos; track p.id) {
            <div class="col-md-6 col-lg-4">
              <div class="card h-100"
                   [class.border-primary]="p.estado === 'activo'"
                   [class.border-secondary]="p.estado === 'finalizado'">
                <div class="card-header d-flex justify-content-between align-items-center py-2">
                  <span class="fw-semibold small text-truncate me-2">{{ p.nombre }}</span>
                  <div class="d-flex gap-1 flex-shrink-0">
                    <span class="badge"
                          [class]="p.metodo === 'scrum' ? 'bg-primary' : 'bg-info text-dark'"
                          style="font-size:0.65rem">
                      {{ p.metodo === 'scrum' ? 'Scrum' : 'XP' }}
                    </span>
                    <span class="badge"
                          [class]="p.estado === 'activo' ? 'bg-success' : 'bg-secondary'"
                          style="font-size:0.65rem">
                      {{ p.estado }}
                    </span>
                  </div>
                </div>
                <div class="card-body py-2">
                  <div class="small mb-1 text-muted">
                    <i class="bi bi-clock me-1"></i>Time Box: <strong>{{ p.timeBoxSemanas }} sem</strong>
                    <span class="ms-2"><i class="bi bi-people me-1"></i>{{ p.totalMiembros }} miembro(s)</span>
                  </div>
                  <div class="small mb-1">
                    <div class="text-muted fw-semibold">Product Goal:</div>
                    <div>{{ p.productGoal | slice:0:100 }}{{ p.productGoal.length > 100 ? '...' : '' }}</div>
                  </div>
                </div>
                <div class="card-footer py-2 d-flex justify-content-between align-items-center">
                  <small class="text-muted">{{ p.scrumMasterEmail }}</small>
                  @if (p.estado === 'activo') {
                    <button class="btn btn-primary btn-sm py-0 px-2" (click)="seleccionarProyecto(p)">
                      <i class="bi bi-arrow-right me-1"></i>Trabajar
                    </button>
                  }
                </div>
              </div>
            </div>
          }
        </div>
      }

    </app-shell>
  `
})
export class ProyectosComponent implements OnInit {
  proyectos: ProyectoDto[] = [];
  form!: FormGroup;
  cargando         = true;
  creando          = false;
  uniendose        = false;
  mostrarFormulario = false;
  mostrarUnirse    = false;
  codigoUnirse     = '';
  alertMsg   = '';
  alertClass = 'alert-success';

  constructor(
    public  auth: AuthService,
    private proyectoService: ProyectoService,
    private sprintService: SprintService,
    private memberService: ProjectMemberService,
    private fb: FormBuilder,
    private router: Router
  ) {}

  get esScrumMaster(): boolean {
    return this.auth.currentUser()?.role === 'scrum_master';
  }

  get f() { return this.form.controls; }

  ngOnInit(): void {
    this.form = this.fb.group({
      nombre:         ['', Validators.required],
      descripcion:    [''],
      metodo:         ['', Validators.required],
      timeBoxSemanas: ['', Validators.required],
      numeroSprints:  [3, [Validators.required, Validators.min(1), Validators.max(20)]],
      fechaInicio:    ['', Validators.required],
      productGoal:    ['', Validators.required]
    });
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.proyectoService.getMisProyectos().pipe(
      catchError(() => of([]))
    ).subscribe(p => {
      this.proyectos = p;
      this.cargando  = false;
    });
  }

  crearProyecto(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.creando = true;
    const val = this.form.value;
    this.proyectoService.crear({
      nombre:         val.nombre,
      descripcion:    val.descripcion ?? '',
      metodo:         val.metodo,
      timeBoxSemanas: Number(val.timeBoxSemanas),
      numeroSprints:  Number(val.numeroSprints),
      fechaInicio:    val.fechaInicio,
      productGoal:    val.productGoal
    }).pipe(
      catchError(err => {
        this.showAlert(err?.error?.error ?? 'Error al crear el proyecto.', 'alert-danger');
        this.creando = false;
        return of(null);
      })
    ).subscribe(p => {
      if (p) {
        this.proyectos = [p, ...this.proyectos];
        this.mostrarFormulario = false;
        this.form.reset();
        this.showAlert('Proyecto creado exitosamente.', 'alert-success');
      }
      this.creando = false;
    });
  }

  unirseConCodigo(): void {
    if (!this.codigoUnirse.trim()) return;
    this.uniendose = true;
    this.memberService.unirse(this.codigoUnirse.toUpperCase()).pipe(
      catchError(err => {
        this.showAlert(err?.error?.error ?? 'Código inválido.', 'alert-danger');
        this.uniendose = false;
        return of(null);
      })
    ).subscribe(m => {
      if (m) {
        this.codigoUnirse = '';
        this.mostrarUnirse = false;
        this.showAlert('Te uniste al proyecto exitosamente.', 'alert-success');
        this.cargar();
      }
      this.uniendose = false;
    });
  }

  seleccionarProyecto(p: ProyectoDto): void {
    if (p.estado !== 'activo') return;
    localStorage.setItem('mpdia_proyecto_activo', JSON.stringify(p));
    this.sprintService.getActivo(p.id).pipe(
      catchError(() => of(null))
    ).subscribe(sprint => {
      if (sprint) localStorage.setItem('mpdia_sprint_activo', JSON.stringify(sprint));
      this.router.navigate(['/planeacion']);
    });
  }

  private showAlert(msg: string, cls: string): void {
    this.alertMsg = msg; this.alertClass = cls;
    setTimeout(() => this.alertMsg = '', 5000);
  }
}
