// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

function passwordsIguales(control: AbstractControl): ValidationErrors | null {
  const password = control.get('newPassword')?.value;
  const confirm  = control.get('confirmPassword')?.value;
  return password && confirm && password !== confirm ? { passwordsNoCoinciden: true } : null;
}

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="d-flex align-items-center justify-content-center" style="min-height:100vh; background:#0f172a;">
      <div class="card shadow" style="max-width:420px; width:100%;">
        <div class="card-body p-4">
          <h4 class="fw-bold mb-1">Nueva contraseña</h4>
          <p class="text-muted small mb-4">Establecé una nueva contraseña para tu cuenta.</p>

          @if (!token) {
            <div class="alert alert-danger small">
              El enlace de recuperación no es válido: falta el token. Solicitá uno nuevo desde la pantalla de inicio de sesión.
            </div>
            <button type="button" class="btn btn-outline-secondary btn-sm w-100" (click)="irALogin()">Volver a iniciar sesión</button>
          } @else if (exito) {
            <div class="alert alert-success small">
              {{ mensaje }}
            </div>
            <button type="button" class="btn btn-primary btn-sm w-100" (click)="irALogin()">Ir a iniciar sesión</button>
          } @else {
            @if (errorMsg) {
              <div class="alert alert-danger small">{{ errorMsg }}</div>
            }

            <form [formGroup]="form" (ngSubmit)="submit()">
              <div class="mb-3">
                <label class="form-label small fw-semibold">Nueva contraseña</label>
                <input type="password" class="form-control form-control-sm" formControlName="newPassword" placeholder="Mínimo 8 caracteres">
                @if (f['newPassword'].touched && f['newPassword'].errors?.['required']) {
                  <div class="text-danger small mt-1">La contraseña es obligatoria.</div>
                }
                @if (f['newPassword'].touched && f['newPassword'].errors?.['minlength']) {
                  <div class="text-danger small mt-1">Debe tener al menos 8 caracteres.</div>
                }
              </div>

              <div class="mb-3">
                <label class="form-label small fw-semibold">Confirmar contraseña</label>
                <input type="password" class="form-control form-control-sm" formControlName="confirmPassword" placeholder="Repetí la contraseña">
                @if (form.touched && form.errors?.['passwordsNoCoinciden']) {
                  <div class="text-danger small mt-1">Las contraseñas no coinciden.</div>
                }
              </div>

              <button type="submit" class="btn btn-primary btn-sm w-100" [disabled]="loading">
                @if (loading) {
                  <span class="spinner-border spinner-border-sm me-1"></span>Guardando...
                } @else {
                  Guardar nueva contraseña
                }
              </button>
            </form>
          }
        </div>
      </div>
    </div>
  `
})
export class ResetPasswordComponent implements OnInit {
  token = '';
  form: FormGroup;
  loading = false;
  exito = false;
  mensaje = '';
  errorMsg = '';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {
    this.form = this.fb.group({
      newPassword:     ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', [Validators.required]]
    }, { validators: passwordsIguales });
  }

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.token = params['token'] ?? '';
    });
  }

  get f() { return this.form.controls; }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    if (!this.token) return;

    this.loading  = true;
    this.errorMsg = '';

    const { newPassword } = this.form.value;
    this.authService.resetPassword(this.token, newPassword).subscribe({
      next: (res) => {
        this.exito   = true;
        this.mensaje = res.message;
        this.loading = false;
      },
      error: (err) => {
        // Distingue token inválido / expirado / ya utilizado según el
        // mensaje que devuelve PasswordResetService (ver GlobalExceptionHandler).
        this.errorMsg = err?.error?.error ?? 'No se pudo actualizar la contraseña. Intenta nuevamente.';
        this.loading  = false;
      }
    });
  }

  irALogin(): void {
    this.router.navigate(['/auth']);
  }
}
