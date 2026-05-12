import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: 'auth',
    loadComponent: () => import('./pages/auth/auth.component').then(m => m.AuthComponent),
    title: 'Acceso — MPDIA'
  },
  // Ruta raíz → va directo a selección de factores/métricas
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/seleccion/seleccion.component').then(m => m.SeleccionComponent),
    title: 'Selección — MPDIA'
  },
  {
    path: 'seleccion',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/seleccion/seleccion.component').then(m => m.SeleccionComponent),
    title: 'Selección de Factores y Métricas — MPDIA'
  },
  {
    path: 'resumen-seleccion',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/resumen-seleccion/resumen-seleccion.component').then(m => m.ResumenSeleccionComponent),
    title: 'Resumen de Selección — MPDIA'
  },
  {
    path: 'parametrizacion/:id',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/parametrizacion/parametrizacion.component').then(m => m.ParametrizacionComponent),
    title: 'Parametrización — MPDIA'
  },
  {
    path: 'verificacion',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/verificacion/verificacion.component').then(m => m.VerificacionComponent),
    title: 'Verificación Scrum Master — MPDIA'
  },
  {
    path: 'configuracion',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/configuracion/configuracion.component').then(m => m.ConfiguracionComponent),
    title: 'Configuración Copiloto — MPDIA'
  },
  {
    path: 'equipo',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/equipo/equipo.component').then(m => m.EquipoComponent),
    title: 'Equipo Scrum — MPDIA'
  },
  {
    path: 'sprints',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/sprints/sprints.component').then(m => m.SprintsComponent),
    title: 'Sprints — MPDIA'
  },
  { path: '**', redirectTo: '' }
];
