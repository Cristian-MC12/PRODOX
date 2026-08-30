// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { ProjectMemberService } from '../../services/project-member.service';
import { ProyectoService } from '../../services/proyecto.service';
import { SprintService } from '../../services/sprint.service';

type Estado =
  | 'cargando'
  | 'necesita-login'
  | 'aceptando'
  | 'aceptada'
  | 'invalida'
  | 'expirada'
  | 'usada'
  | 'ya-miembro'
  | 'otro-correo'
  | 'no-autorizado';

@Component({
  selector: 'app-invitacion',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="d-flex align-items-center justify-content-center" style="min-height:100vh; background:#0f172a;">
      <div class="card shadow" style="max-width:440px; width:100%;">
        <div class="card-body p-4 text-center">

          @switch (estado) {
            @case ('cargando') {
              <span class="spinner-border text-primary mb-3"></span>
              <p class="text-muted mb-0">Cargando invitación...</p>
            }

            @case ('necesita-login') {
              <i class="bi bi-envelope-check text-primary" style="font-size:2.5rem"></i>
              <h5 class="fw-bold mt-3 mb-1">Invitación al proyecto</h5>
              <p class="text-muted small mb-4">
                Fuiste invitado a <strong>{{ proyectoNombre }}</strong>. Iniciá sesión o creá una cuenta para aceptarla.
              </p>
              <div class="d-grid gap-2">
                <button class="btn btn-primary btn-sm" (click)="irALogin()">Iniciar sesión</button>
                <button class="btn btn-outline-primary btn-sm" (click)="irARegistro()">Crear cuenta</button>
              </div>
            }

            @case ('aceptando') {
              <span class="spinner-border text-primary mb-3"></span>
              <p class="text-muted mb-0">Uniéndote a <strong>{{ proyectoNombre }}</strong>...</p>
            }

            @case ('aceptada') {
              <i class="bi bi-check-circle-fill text-success" style="font-size:2.5rem"></i>
              <h5 class="fw-bold mt-3 mb-1">¡Listo!</h5>
              <p class="text-muted small mb-0">Te uniste a <strong>{{ proyectoNombre }}</strong>. Entrando al proyecto...</p>
            }

            @case ('ya-miembro') {
              <i class="bi bi-info-circle text-primary" style="font-size:2.5rem"></i>
              <h5 class="fw-bold mt-3 mb-1">Ya sos parte de este proyecto</h5>
              <p class="text-muted small mb-4">Ya pertenecés a <strong>{{ proyectoNombre }}</strong>.</p>
              <button class="btn btn-primary btn-sm" (click)="entrarAlProyectoConocido()">Ir al proyecto</button>
            }

            @case ('expirada') {
              <i class="bi bi-clock-history text-warning" style="font-size:2.5rem"></i>
              <h5 class="fw-bold mt-3 mb-1">Invitación expirada</h5>
              <p class="text-muted small mb-4">Esta invitación ya venció. Pedile al Scrum Master que te envíe una nueva.</p>
              <button class="btn btn-outline-secondary btn-sm" (click)="irALogin()">Ir a inicio de sesión</button>
            }

            @case ('usada') {
              <i class="bi bi-slash-circle text-warning" style="font-size:2.5rem"></i>
              <h5 class="fw-bold mt-3 mb-1">Invitación ya utilizada</h5>
              <p class="text-muted small mb-4">Este código de invitación ya fue usado.</p>
              <button class="btn btn-outline-secondary btn-sm" (click)="irALogin()">Ir a inicio de sesión</button>
            }

            @case ('otro-correo') {
              <i class="bi bi-person-x text-danger" style="font-size:2.5rem"></i>
              <h5 class="fw-bold mt-3 mb-1">Esta invitación es para otro correo</h5>
              <p class="text-muted small mb-4">
                Iniciaste sesión con una cuenta distinta a la que recibió esta invitación.
                Iniciá sesión con la cuenta correcta para poder aceptarla.
              </p>
              <button class="btn btn-outline-secondary btn-sm" (click)="cambiarDeCuenta()">Cambiar de cuenta</button>
            }

            @case ('no-autorizado') {
              <i class="bi bi-shield-exclamation text-danger" style="font-size:2.5rem"></i>
              <h5 class="fw-bold mt-3 mb-1">No autorizado</h5>
              <p class="text-muted small mb-4">{{ errorMsg || 'No podés utilizar esta invitación con tu cuenta actual.' }}</p>
              <button class="btn btn-outline-secondary btn-sm" (click)="irALogin()">Ir a inicio de sesión</button>
            }

            @default {
              <i class="bi bi-exclamation-circle text-danger" style="font-size:2.5rem"></i>
              <h5 class="fw-bold mt-3 mb-1">Invitación inválida</h5>
              <p class="text-muted small mb-4">Este enlace de invitación no es válido. Verificá que lo copiaste completo.</p>
              <button class="btn btn-outline-secondary btn-sm" (click)="irALogin()">Ir a inicio de sesión</button>
            }
          }

        </div>
      </div>
    </div>
  `
})
export class InvitacionComponent implements OnInit {
  estado: Estado = 'cargando';
  codigo = '';
  proyectoNombre = '';
  proyectoIdConocido = '';
  errorMsg = '';

  constructor(
    private authService: AuthService,
    private memberService: ProjectMemberService,
    private proyectoService: ProyectoService,
    private sprintService: SprintService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.codigo = (params['codigo'] ?? '').toString().trim();
      if (!this.codigo) {
        this.estado = 'invalida';
        return;
      }
      this.consultarYContinuar();
    });
  }

  private consultarYContinuar(): void {
    this.estado = 'cargando';
    this.memberService.consultarInvitacion(this.codigo).pipe(
      catchError(() => of({ proyectoId: null, proyectoNombre: null, estado: 'no_existe' }))
    ).subscribe(res => {
      this.proyectoNombre = res.proyectoNombre ?? '';
      this.proyectoIdConocido = res.proyectoId ?? '';

      switch (res.estado) {
        case 'expirada':
          this.estado = 'expirada';
          return;
        case 'usada':
          this.estado = 'usada';
          return;
        case 'no_existe':
          this.estado = 'invalida';
          return;
        case 'valida':
          if (this.authService.isLoggedIn()) {
            this.aceptar();
          } else {
            // Se guarda para que login/registro/Google lo recuperen después
            // de autenticar (ver AuthComponent.redirectAfterAuth).
            this.authService.setInvitacionPendiente(this.codigo);
            this.estado = 'necesita-login';
          }
          return;
        default:
          this.estado = 'invalida';
      }
    });
  }

  private aceptar(): void {
    this.estado = 'aceptando';
    this.memberService.unirse(this.codigo).pipe(
      catchError(err => {
        const mensaje: string = err?.error?.error ?? '';
        if (mensaje.includes('Ya eres miembro')) {
          this.estado = 'ya-miembro';
        } else if (mensaje.includes('otro correo')) {
          // Validado en backend (ProjectMemberService.unirse): la cuenta
          // autenticada no es el correo al que se envió la invitación. No se
          // agregó al proyecto ni se consumió el código — sigue disponible
          // para que el correo correcto la use.
          this.estado = 'otro-correo';
        } else if (mensaje.includes('expiró')) {
          this.estado = 'expirada';
        } else if (mensaje.includes('inválido') || mensaje.includes('ya usado')) {
          this.estado = 'usada';
        } else {
          this.errorMsg = mensaje;
          this.estado = 'no-autorizado';
        }
        return of(null);
      })
    ).subscribe(miembro => {
      if (!miembro) return;
      this.authService.clearInvitacionPendiente();
      this.estado = 'aceptada';
      this.entrarAlProyecto(miembro.proyectoId);
    });
  }

  /** Reutiliza exactamente el mismo patrón que ProyectosComponent.seleccionarProyecto(). */
  private entrarAlProyecto(proyectoId: string): void {
    this.proyectoService.getById(proyectoId).pipe(
      catchError(() => of(null))
    ).subscribe(p => {
      if (!p) {
        this.router.navigate(['/proyectos']);
        return;
      }
      localStorage.setItem('mpdia_proyecto_activo', JSON.stringify(p));
      this.sprintService.getActivo(p.id).pipe(
        catchError(() => of(null))
      ).subscribe(sprint => {
        if (sprint) localStorage.setItem('mpdia_sprint_activo', JSON.stringify(sprint));
        this.router.navigate(['/planeacion']);
      });
    });
  }

  /** Para el estado "ya-miembro": no viene de unirse(), así que no tenemos
   *  el proyectoId todavía — hay que resolverlo antes de poder entrar. */
  entrarAlProyectoConocido(): void {
    if (this.proyectoIdConocido) {
      this.entrarAlProyecto(this.proyectoIdConocido);
      return;
    }
    this.router.navigate(['/proyectos']);
  }

  irALogin(): void {
    this.router.navigate(['/auth']);
  }

  /** El código de invitación permanece guardado (logout no lo toca) para
   *  reintentar automáticamente apenas inicie sesión con la cuenta correcta. */
  cambiarDeCuenta(): void {
    this.authService.setInvitacionPendiente(this.codigo);
    this.authService.logout();
  }

  irARegistro(): void {
    this.router.navigate(['/auth'], { queryParams: { tab: 'register' } });
  }
}
