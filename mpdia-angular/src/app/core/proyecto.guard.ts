// Autor: Cristian Santiago Martinez Cordoba — MPDIA
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

/**
 * Guard que verifica si hay un proyecto activo seleccionado.
 * Si no hay proyecto en localStorage, redirige a /proyectos.
 * Aplica a todas las rutas que requieren un proyecto activo:
 * selección, resumen, parametrización, verificación.
 */
export const proyectoGuard: CanActivateFn = () => {
  const router = inject(Router);
  const proyectoActivo = localStorage.getItem('mpdia_proyecto_activo');

  if (!proyectoActivo) {
    router.navigate(['/proyectos']);
    return false;
  }

  try {
    const p = JSON.parse(proyectoActivo);
    if (!p?.id || p?.estado !== 'activo') {
      localStorage.removeItem('mpdia_proyecto_activo');
      router.navigate(['/proyectos']);
      return false;
    }
  } catch {
    localStorage.removeItem('mpdia_proyecto_activo');
    router.navigate(['/proyectos']);
    return false;
  }

  return true;
};
