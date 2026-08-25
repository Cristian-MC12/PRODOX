// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const auth   = inject(AuthService);
  const router = inject(Router);
  const token  = auth.getToken();

  if (token) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    });
  }

  return next(req).pipe(
    catchError(err => {
      // Solo 401 (sin autenticar / token inválido, ver SecurityConfig.
      // authenticationEntryPoint) significa "la sesión no es válida" —
      // acá es donde corresponde cerrar sesión. Un 403 significa que el
      // usuario SÍ está autenticado pero no tiene acceso a ESE recurso
      // puntual (ej. un proyecto del que no es miembro) — eso es un error
      // de ese request, no de la sesión, y varios componentes ya manejan
      // su propio mensaje para ese caso (ver ai-insights, parametrizacion,
      // ejecucion, etc.). Cerrar sesión también en 403 hacía que una sesión
      // recién iniciada, válida, se cerrara sola apenas algún dato viejo
      // (ej. un proyecto activo en localStorage ya inexistente) disparaba
      // un 403 de autorización — indistinguible de una sesión inválida
      // antes de que existiera este entry point.
      if (err.status === 401 && !req.url.includes('/auth/')) {
        auth.logout();
        router.navigate(['/auth']);
      }
      return throwError(() => err);
    })
  );
};
