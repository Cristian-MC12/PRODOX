import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { proyectoGuard } from './core/proyecto.guard';

export const routes: Routes = [
  {
    path: 'auth',
    loadComponent: () => import('./pages/auth/auth.component').then(m => m.AuthComponent),
    title: 'Acceso — MPDIA'
  },
  {
    path: 'proyectos',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/proyectos/proyectos.component').then(m => m.ProyectosComponent),
    title: 'Proyectos — MPDIA'
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/proyectos/proyectos.component').then(m => m.ProyectosComponent),
    title: 'Proyectos — MPDIA'
  },
  // ── Selección de métricas ─────────────────────────────────────────────
  {
    path: 'seleccion',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/seleccion/seleccion.component').then(m => m.SeleccionComponent),
    title: 'Selección — MPDIA'
  },
  {
    path: 'resumen-seleccion',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/resumen-seleccion/resumen-seleccion.component').then(m => m.ResumenSeleccionComponent),
    title: 'Resumen Selección — MPDIA'
  },
  {
    path: 'parametrizacion/:id',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/parametrizacion/parametrizacion.component').then(m => m.ParametrizacionComponent),
    title: 'Parametrización — MPDIA'
  },
  {
    path: 'verificacion',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/verificacion/verificacion.component').then(m => m.VerificacionComponent),
    title: 'Verificación — MPDIA'
  },
  {
    path: 'factores',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/factores/factores.component').then(m => m.FactoresComponent),
    title: 'Factores — MPDIA'
  },
  // ── Planeación ────────────────────────────────────────────────────────
  {
    path: 'planeacion',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/planeacion/planeacion.component').then(m => m.PlaneacionComponent),
    title: 'Planeación — MPDIA'
  },
  // ── Ejecución ─────────────────────────────────────────────────────────
  {
    path: 'ejecucion',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/ejecucion/ejecucion.component').then(m => m.EjecucionComponent),
    title: 'Ejecución — MPDIA'
  },
  // ── Evaluación ────────────────────────────────────────────────────────
  {
    path: 'evaluacion',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/evaluacion/evaluacion.component').then(m => m.EvaluacionComponent),
    title: 'Evaluación — MPDIA'
  },
  // ── Equipo ────────────────────────────────────────────────────────────
  {
    path: 'equipo',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/equipo/equipo.component').then(m => m.EquipoComponent),
    title: 'Equipo — MPDIA'
  },
  // ── Configuración copiloto ────────────────────────────────────────────
  {
    path: 'configuracion',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/configuracion/configuracion.component').then(m => m.ConfiguracionComponent),
    title: 'Configuración — MPDIA'
  },
  { path: '**', redirectTo: '' }
];
