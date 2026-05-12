// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ShellComponent } from '../../layout/shell/shell.component';
import { ObjetivoService } from '../../services/objetivo.service';
import { ObjetivoMedicion } from '../../models/objetivo.model';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-objetivos',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ShellComponent],
  template: `
    <app-shell title="Objetivos y Criterios de Medición">

      <p class="text-muted small mb-4">
        El <strong>Product Owner</strong> define los objetivos de medición del producto
        y los criterios de éxito asociados para el sprint actual.
      </p>

      @if (alertMsg) {
        <div class="alert py-2 small" [class]="alertClass">{{ alertMsg }}</div>
      }

      <div class="row g-4">
        <!-- Formulario -->
        <div class="col-lg-5">
          <div class="card">
            <div class="card-header fw-semibold small">
              {{ editingId ? 'Editar objetivo' : 'Nuevo objetivo de medición' }}
            </div>
            <div class="card-body">
              <form [formGroup]="form" (ngSubmit)="submit()">

                <div class="mb-3">
                  <label class="form-label small">Sprint</label>
                  <input type="text" class="form-control form-control-sm"
                         formControlName="sprintName"
                         [class.is-invalid]="f['sprintName'].invalid && f['sprintName'].touched">
                  <div class="invalid-feedback">Campo requerido.</div>
                </div>

                <div class="mb-3">
                  <label class="form-label small">Objetivo de medición del producto</label>
                  <textarea class="form-control form-control-sm" rows="3"
                            placeholder="Ej: Mejorar la velocidad del equipo en un 10% respecto al sprint anterior."
                            formControlName="objetivo"
                            [class.is-invalid]="f['objetivo'].invalid && f['objetivo'].touched">
                  </textarea>
                  <div class="invalid-feedback">Campo requerido.</div>
                </div>

                <div class="mb-3">
                  <label class="form-label small">Criterio de éxito</label>
                  <textarea class="form-control form-control-sm" rows="3"
                            placeholder="Ej: Velocidad ≥ 85 story points al cierre del sprint."
                            formControlName="criterioExito"
                            [class.is-invalid]="f['criterioExito'].invalid && f['criterioExito'].touched">
                  </textarea>
                  <div class="invalid-feedback">Campo requerido.</div>
                </div>

                <div class="d-flex gap-2">
                  <button type="submit" class="btn btn-primary btn-sm"
                          [disabled]="form.invalid">
                    <i class="bi bi-floppy me-1"></i>
                    {{ editingId ? 'Actualizar' : 'Guardar' }}
                  </button>
                  @if (editingId) {
                    <button type="button" class="btn btn-outline-secondary btn-sm"
                            (click)="cancelEdit()">
                      Cancelar
                    </button>
                  }
                </div>
              </form>
            </div>
          </div>
        </div>

        <!-- Lista de objetivos -->
        <div class="col-lg-7">
          <div class="card">
            <div class="card-header fw-semibold small">
              Objetivos definidos — Sprint Actual
            </div>
            <div class="card-body p-0">
              @if (objetivos.length === 0) {
                <div class="text-center text-muted py-5 small">
                  <i class="bi bi-clipboard-x fs-3 d-block mb-2"></i>
                  No hay objetivos definidos aún.
                </div>
              } @else {
                <ul class="list-group list-group-flush">
                  @for (obj of objetivos; track obj.id) {
                    <li class="list-group-item">
                      <div class="d-flex justify-content-between align-items-start">
                        <div class="flex-grow-1 me-3">
                          <div class="fw-semibold small mb-1">
                            <i class="bi bi-bullseye text-primary me-1"></i>
                            {{ obj.objetivo }}
                          </div>
                          <div class="text-muted small">
                            <i class="bi bi-check2-circle text-success me-1"></i>
                            <strong>Criterio:</strong> {{ obj.criterioExito }}
                          </div>
                          <div class="text-muted" style="font-size:0.7rem">
                            Sprint: {{ obj.sprintName }}
                            @if (obj.creadoEn) {
                              · {{ obj.creadoEn | date:'dd/MM/yy HH:mm' }}
                            }
                          </div>
                        </div>
                        <div class="d-flex gap-1 flex-shrink-0">
                          <button class="btn btn-sm btn-outline-secondary"
                                  (click)="edit(obj)" title="Editar">
                            <i class="bi bi-pencil"></i>
                          </button>
                          <button class="btn btn-sm btn-outline-danger"
                                  (click)="delete(obj.id!)" title="Eliminar">
                            <i class="bi bi-trash"></i>
                          </button>
                        </div>
                      </div>
                    </li>
                  }
                </ul>
              }
            </div>
          </div>

          <!-- Indicación RF05 -->
          <div class="alert alert-info small mt-3 mb-0">
            <i class="bi bi-info-circle me-1"></i>
            Una vez definidos los objetivos y criterios, el Scrum Master planifica el proceso
            de medición y el equipo procede a
            <a routerLink="/factores" class="alert-link">seleccionar los factores</a>.
          </div>
        </div>
      </div>
    </app-shell>
  `
})
export class ObjetivosComponent implements OnInit {
  form!: FormGroup;
  objetivos: ObjetivoMedicion[] = [];
  editingId: string | null = null;
  alertMsg   = '';
  alertClass = 'alert-success';

  constructor(
    private fb: FormBuilder,
    private objetivoService: ObjetivoService,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      sprintName:    ['Sprint Actual', Validators.required],
      objetivo:      ['', Validators.required],
      criterioExito: ['', Validators.required]
    });

    this.objetivoService.getAll().subscribe(list => {
      this.objetivos = list.filter(o => o.sprintName === 'Sprint Actual');
    });
  }

  get f() { return this.form.controls; }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const val = this.form.value as ObjetivoMedicion;
    if (this.editingId) val.id = this.editingId;
    val.creadoPor = this.auth.currentUser()?.userId;
    this.objetivoService.save(val);
    this.form.reset({ sprintName: 'Sprint Actual', objetivo: '', criterioExito: '' });
    this.editingId = null;
    this.showAlert(
      this.editingId ? 'Objetivo actualizado.' : 'Objetivo guardado.',
      'alert-success'
    );
  }

  edit(obj: ObjetivoMedicion): void {
    this.editingId = obj.id!;
    this.form.patchValue(obj);
  }

  cancelEdit(): void {
    this.editingId = null;
    this.form.reset({ sprintName: 'Sprint Actual', objetivo: '', criterioExito: '' });
  }

  delete(id: string): void {
    this.objetivoService.delete(id);
    this.showAlert('Objetivo eliminado.', 'alert-secondary');
  }

  private showAlert(msg: string, cls: string): void {
    this.alertMsg = msg; this.alertClass = cls;
    setTimeout(() => this.alertMsg = '', 3000);
  }
}
