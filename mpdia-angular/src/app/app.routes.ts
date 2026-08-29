import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { proyectoGuard } from './core/proyecto.guard';

export const routes: Routes = [
  {
    path: 'auth',
    loadComponent: () => import('./pages/auth/auth.component').then(m => m.AuthComponent),
    title: 'Acceso — PRODOX AI'
  },
  {
    path: 'proyectos',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/proyectos/proyectos.component').then(m => m.ProyectosComponent),
    title: 'Proyectos — PRODOX AI'
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/proyectos/proyectos.component').then(m => m.ProyectosComponent),
    title: 'Proyectos — PRODOX AI'
  },
  // ── Selección de métricas ─────────────────────────────────────────────
  {
    path: 'seleccion',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/seleccion/seleccion.component').then(m => m.SeleccionComponent),
    title: 'Selección — PRODOX AI'
  },
  {
    path: 'resumen-seleccion',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/resumen-seleccion/resumen-seleccion.component').then(m => m.ResumenSeleccionComponent),
    title: 'Resumen de Selección — PRODOX AI'
  },
  {
    path: 'parametrizacion/:id',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/parametrizacion/parametrizacion.component').then(m => m.ParametrizacionComponent),
    title: 'Parametrización — PRODOX AI'
  },
  {
    path: 'captura-variables/:metricaId',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/captura-variables/captura-variables.component').then(m => m.CapturaVariablesComponent),
    title: 'Captura de Valores — PRODOX AI'
  },
  {
    path: 'verificacion',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/verificacion/verificacion.component').then(m => m.VerificacionComponent),
    title: 'Verificación — PRODOX AI'
  },
  {
    path: 'factores',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/factores/factores.component').then(m => m.FactoresComponent),
    title: 'Factores — PRODOX AI'
  },
  // ── Planeación ────────────────────────────────────────────────────────
  {
    path: 'planeacion',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/planeacion/planeacion.component').then(m => m.PlaneacionComponent),
    title: 'Planeación — PRODOX AI'
  },
  // FASE 15: "Crear métrica con IA" — puerta de entrada adicional en Planeación,
  // nunca reemplaza el flujo existente de selección/parametrización.
  {
    path: 'crear-metrica-ia',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/crear-metrica-ia/crear-metrica-ia.component').then(m => m.CrearMetricaIAComponent),
    title: 'Crear métrica con IA — PRODOX AI'
  },
  // ── Sprints ───────────────────────────────────────────────────────────
  {
    path: 'sprints',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/sprints/sprints.component').then(m => m.SprintsComponent),
    title: 'Sprints — PRODOX AI'
  },
  // ── Ejecución ─────────────────────────────────────────────────────────
  {
    path: 'ejecucion',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/ejecucion/ejecucion.component').then(m => m.EjecucionComponent),
    title: 'Ejecución — PRODOX AI'
  },
  // ── Métricas Académicas (Fase 16.9.2) ────────────────────────────────
  {
    path: 'metrica-academica/:id',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/metrica-academica/metrica-academica.component').then(m => m.MetricaAcademicaComponent),
    title: 'Métrica Académica — PRODOX AI'
  },
  // ── Evaluación ────────────────────────────────────────────────────────
  {
    path: 'evaluacion',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/evaluacion/evaluacion.component').then(m => m.EvaluacionComponent),
    title: 'Evaluación — PRODOX AI'
  },
  // ── Equipo ────────────────────────────────────────────────────────────
  {
    path: 'equipo',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/equipo/equipo.component').then(m => m.EquipoComponent),
    title: 'Equipo — PRODOX AI'
  },
  // ── Dashboard Inteligente ─────────────────────────────────────────────
  {
    path: 'dashboard',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent),
    title: 'Dashboard — PRODOX AI'
  },
  // ── AI Insights ───────────────────────────────────────────────────────
  {
    path: 'ai-insights',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/ai-insights/ai-insights.component').then(m => m.AIInsightsComponent),
    title: 'AI Insights — PRODOX AI'
  },
  // ── AI Reports & Retrospectives ───────────────────────────────────────
  {
    path: 'ai-report',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/ai-report/ai-report.component').then(m => m.AIReportComponent),
    title: 'Reporte Ejecutivo — PRODOX AI'
  },
  {
    path: 'ai-retrospective',
    canActivate: [authGuard, proyectoGuard],
    loadComponent: () => import('./pages/ai-retrospective/ai-retrospective.component').then(m => m.AIRetrospectiveComponent),
    title: 'Retrospectiva Inteligente — PRODOX AI'
  },
  // ── Configuración copiloto ────────────────────────────────────────────
  {
    path: 'configuracion',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/configuracion/configuracion.component').then(m => m.ConfiguracionComponent),
    title: 'Configuración — PRODOX AI'
  },
  { path: '**', redirectTo: '' }
];
