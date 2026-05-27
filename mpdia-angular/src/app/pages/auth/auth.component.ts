// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-auth',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="min-vh-100 d-flex align-items-center justify-content-center bg-light p-3">
      <div style="width:100%;max-width:420px">

        <!-- Brand -->
        <div class="text-center mb-4">
          <div class="d-inline-flex align-items-center justify-content-center
                      bg-primary text-white rounded-3 mb-2"
               style="width:48px;height:48px;font-size:1.4rem">
            <i class="bi bi-speedometer2"></i>
          </div>
          <h2 class="h5 fw-bold mb-0">MPDIA</h2>
          <p class="text-muted small">Sistema de Medición de Productividad Ágil</p>
        </div>

        <div class="card shadow-sm">
          <div class="card-body p-4">
            <h5 class="card-title mb-3">Acceso al sistema</h5>

            <!-- Tabs -->
            <ul class="nav nav-tabs mb-3" role="tablist">
              <li class="nav-item">
                <button class="nav-link" [class.active]="tab === 'login'"
                        (click)="switchTab('login')">Iniciar sesión</button>
              </li>
              <li class="nav-item">
                <button class="nav-link" [class.active]="tab === 'register'"
                        (click)="switchTab('register')">Registrarse</button>
              </li>
            </ul>

            @if (errorMsg) {
              <div class="alert alert-danger py-2 small">{{ errorMsg }}</div>
            }

            <form [formGroup]="form" (ngSubmit)="submit()">

              <div class="mb-3">
                <label class="form-label">Correo electrónico</label>
                <input type="email" class="form-control"
                       formControlName="email"
                       [class.is-invalid]="f['email'].invalid && f['email'].touched">
                <div class="invalid-feedback">Ingresá un correo válido.</div>
              </div>

              <div class="mb-3">
                <label class="form-label">
                  Contraseña {{ tab === 'register' ? '(mín. 8 caracteres)' : '' }}
                </label>
                <input type="password" class="form-control"
                       formControlName="password"
                       [class.is-invalid]="f['password'].invalid && f['password'].touched">
                <div class="invalid-feedback">Mínimo 8 caracteres.</div>
              </div>

              <!-- Rol: solo en registro -->
              @if (tab === 'register') {
                <div class="mb-3">
                  <label class="form-label">Rol en el equipo</label>
                  <select class="form-select" formControlName="role"
                          [class.is-invalid]="f['role'].invalid && f['role'].touched">
                    <option value="">Seleccionar rol...</option>
                    <option value="scrum_member">Scrum Member (Developer / PO / QA)</option>
                    <option value="scrum_master">Scrum Master</option>
                  </select>
                  <div class="invalid-feedback">Seleccioná un rol.</div>
                  <div class="form-text small text-muted">
                    El Scrum Master puede verificar y aprobar parametrizaciones.
                  </div>
                </div>
              }

              <button type="submit" class="btn btn-primary w-100" [disabled]="loading">
                @if (loading) {
                  <span class="spinner-border spinner-border-sm me-1"></span>
                }
                {{ tab === 'login' ? 'Ingresar' : 'Crear cuenta' }}
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AuthComponent {
  tab: 'login' | 'register' = 'login';
  form: FormGroup;
  loading  = false;
  errorMsg = '';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.form = this.buildForm('login');
  }

  get f() { return this.form.controls; }

  switchTab(t: 'login' | 'register'): void {
    this.tab = t;
    this.errorMsg = '';
    this.form = this.buildForm(t);
  }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading  = true;
    this.errorMsg = '';

    const { email, password, role } = this.form.value;
    const obs = this.tab === 'login'
      ? this.authService.login({ email, password })
      : this.authService.register({ email, password, role });

    obs.subscribe({
      next:  () => this.router.navigate(['/']),
      error: (err) => {
        this.errorMsg = err?.error?.error ?? 'Error al autenticar.';
        this.loading  = false;
      }
    });
  }

  private buildForm(tab: 'login' | 'register'): FormGroup {
    return this.fb.group({
      email:    ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      role:     [tab === 'register' ? '' : null,
                 tab === 'register' ? Validators.required : []]
    });
  }
}
