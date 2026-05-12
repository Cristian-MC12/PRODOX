// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = () => {
  const auth   = inject(AuthService);
  const router = inject(Router);

  const token = auth.getToken();
  if (!token) {
    router.navigate(['/auth']);
    return false;
  }

  // Verificar si el JWT expiró leyendo el payload
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    const expMs   = payload.exp * 1000;
    if (Date.now() >= expMs) {
      auth.logout();
      router.navigate(['/auth']);
      return false;
    }
  } catch {
    auth.logout();
    router.navigate(['/auth']);
    return false;
  }

  return true;
};
