import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'PRODOX V1',
  description: 'Manual de Usuario - Plataforma de Métricas de Desarrollo Ágil Oximetrada',
  lang: 'es-ES',
  
  themeConfig: {
    logo: '/logo.svg',
    
    nav: [
      { text: 'Inicio', link: '/' },
      { text: 'Guía de Flujo', link: '/#flujo-operativo-integral' },
      { text: 'Comenzar', link: '/inicio/bienvenida' },
      { text: 'Dashboard', link: '/dashboard/general' }
    ],

    sidebar: [
      {
        text: '1. Inicio y Orientación',
        collapsed: false,
        items: [
          { text: 'Guía del Flujo Operativo', link: '/' },
          { text: 'Bienvenida y Modelo de Trabajo', link: '/inicio/bienvenida' },
          { text: 'Requisitos del Sistema', link: '/inicio/requisitos' }
        ]
      },
      {
        text: '2. Acceso a la Plataforma',
        collapsed: false,
        items: [
          { text: 'Inicio de Sesión', link: '/auth/login' },
          { text: 'Registro de Cuenta', link: '/auth/registro' },
          { text: 'Recuperar Contraseña', link: '/auth/reset-password' }
        ]
      },
      {
        text: '3. Preparación del Proyecto',
        collapsed: false,
        items: [
          { text: 'Listado y Selección de Proyecto', link: '/proyectos/listado' },
          { text: 'Crear Nuevo Proyecto Ágil', link: '/proyectos/crear' }
        ]
      },
      {
        text: '4. Preparación del Equipo',
        collapsed: false,
        items: [
          { text: 'Gestión de Integrantes e Invitaciones', link: '/equipo/general' },
          { text: 'Matriz de Roles y Permisos (RBAC)', link: '/equipo/roles-permisos' }
        ]
      },
      {
        text: '5. Preparación del Backlog y Sprints',
        collapsed: false,
        items: [
          { text: 'Gestión del Backlog de Historias', link: '/backlog/general' },
          { text: 'Gestión y Ciclo de Sprints', link: '/sprints/general' }
        ]
      },
      {
        text: '6. Planeación',
        collapsed: false,
        items: [
          { text: 'Módulo de Planeación y Factores', link: '/planeacion/general' },
          { text: 'Selección y Resumen de Métricas', link: '/planeacion/seleccion-metricas' },
          { text: 'Parametrización y Variables', link: '/planeacion/parametrizacion-variables' },
          { text: 'Verificación del Scrum Master', link: '/planeacion/verificacion-scrum-master' },
          { text: 'Crear Métrica con IA', link: '/planeacion/crear-metrica-ia' }
        ]
      },
      {
        text: '7. Ejecución',
        collapsed: false,
        items: [
          { text: 'Ejecución y Seguimiento del Sprint', link: '/ejecucion/general' },
          { text: 'Captura y Registro de Valores', link: '/ejecucion/captura-valores' }
        ]
      },
      {
        text: '8. Evaluación',
        collapsed: false,
        items: [
          { text: 'Evaluación y Tendencias Intra-Sprint', link: '/evaluacion/general' },
          { text: 'Comparación entre Sprints', link: '/evaluacion/por-metrica' },
          { text: 'Estadísticas y Patrones Globales', link: '/evaluacion/estadisticas' }
        ]
      },
      {
        text: '9. Dashboard e Informes',
        collapsed: false,
        items: [
          { text: 'Dashboard Ejecutivo y KPIs', link: '/dashboard/general' },
          { text: 'Gráficos Analíticos Chart.js', link: '/dashboard/graficos' },
          { text: 'Informes Word, Insights y Retro IA', link: '/dashboard/reportes-ia' }
        ]
      },
      {
        text: '10. Cuenta y Preferencias',
        collapsed: false,
        items: [
          { text: 'Gestión de Sesión y Entorno', link: '/configuracion/preferencias' }
        ]
      }
    ],

    socialLinks: [
      { icon: 'github', link: 'https://github.com/Cristian-MC12/PRODOX' }
    ],

    search: {
      provider: 'local',
      options: {
        locales: {
          root: {
            translations: {
              button: {
                buttonText: 'Buscar',
                buttonAriaLabel: 'Buscar en la documentación'
              },
              modal: {
                noResultsText: 'No se encontraron resultados',
                resetButtonTitle: 'Limpiar búsqueda',
                footer: {
                  selectText: 'Seleccionar',
                  navigateText: 'Navegar',
                  closeText: 'Cerrar'
                }
              }
            }
          }
        }
      }
    },

    footer: {
      message: 'PRODOX V1 - Documentación Oficial',
      copyright: 'Copyright © 2026 Cristian Santiago Martinez Cordoba'
    },

    docFooter: {
      prev: 'Etapa anterior',
      next: 'Siguiente etapa'
    },

    darkModeSwitchLabel: 'Tema',
    sidebarMenuLabel: 'Menú',
    returnToTopLabel: 'Volver arriba',
    langMenuLabel: 'Cambiar idioma',
    
    outline: {
      label: 'En esta página',
      level: [2, 3]
    }
  }
})
