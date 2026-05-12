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
      if ((err.status === 401 || err.status === 403) && !req.url.includes('/auth/')) {
        auth.logout();
        router.navigate(['/auth']);
      }
      return throwError(() => err);
    })
  );
};
