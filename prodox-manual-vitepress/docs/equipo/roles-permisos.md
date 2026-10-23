# Matriz de Roles y Control de Acceso (RBAC)

Conoce el modelo de permisos y las capacidades operativas de cada rol en PRODOX V1 según el marco de control de acceso basado en roles (*Role-Based Access Control*).

---

## Arquitectura de Roles: Rol por Proyecto (`miRol`)

En PRODOX V1, el control de acceso opera con una distinción fundamental respecto a otros sistemas:

> [!IMPORTANT]
> **El rol pertenece al contexto del proyecto, no a la cuenta global:**
> Un mismo usuario puede desempeñar el rol de **Scrum Master** en un proyecto que lidera, y simultáneamente participar como **Scrum Member** (desarrollador) o **Product Owner** en otros proyectos colaborativos.
> La interfaz y las protecciones de ruta leen la propiedad `miRol` devuelta por el backend para el proyecto activo (`mpdia_proyecto_activo`), garantizando que solo se desplieguen los controles autorizados.

![Listado de integrantes con sus roles distintivos en el proyecto PRODOX](/images/equipo/equipo-general.png)

---

## Perfiles y Responsabilidades

### 🔵 SCRUM_MASTER (Líder y Administrador Metodológico)
Es el guardián del marco ágil y de la calidad de las mediciones:
- **Gestión del Proyecto**: Crea el proyecto, define su horizonte de sprints y tiene autorización exclusiva para eliminarlo.
- **Verificación de Métricas**: Es el **único rol** con acceso a la bandeja de [Verificación](/planeacion/verificacion-scrum-master) para aprobar o rechazar parametrizaciones. Cada aprobación crea una versión formal de la métrica.
- **Gestión de Sprints**: Inicia, finaliza y reabre iteraciones en el módulo de [Sprints](/sprints/general).
- **Gestión de Equipo**: Genera códigos de invitación, revoca accesos y reasigna roles entre miembros.
- **Captura de Métricas Grupales**: Registra las variables cuyo alcance metodológico es de equipo/grupal.

### 🟢 PRODUCT_OWNER (Gestor del Backlog y del Valor)
Es la voz del negocio y el responsable del alcance funcional:
- **Control Exclusivo del Backlog**: Es el **único rol** que puede crear nuevas historias de usuario (`+ Nueva historia`), editarlas, asignar Story Points (Fibonacci) y definir su orden de prioridad.
- **Definición Estratégica**: Supervisa el cumplimiento del *Product Goal* del proyecto.
- **Propuesta de Métricas**: Puede explorar el catálogo de métricas, proponer parametrizaciones técnicas con GenAI y enviarlas a revisión del Scrum Master.
- **Analítica de Entrega**: Consulta tableros de velocidad, reportes narrativos y proyecciones de entrega.

### ⚪ SCRUM_MEMBER (Equipo de Desarrollo e Ingeniería)
Son los profesionales técnicos que construyen el producto y generan la evidencia diaria:
- **Lectura del Backlog**: Consulta las historias de usuario y sus criterios de aceptación para planificar el desarrollo.
- **Captura de Variables Individuales**: En el módulo de [Ejecución](/ejecucion/general), registra periódicamente los valores de las variables de medición asignadas (individuales).
- **Consulta de Evaluación**: Analiza el impacto de sus entregas a través de las gráficas de tendencias y matrices históricas.
- **Participación en Retrospectivas**: Utiliza la evidencia analítica y los reportes de IA para retroalimentar la dinámica del equipo.

---

## Matriz Comparativa de Permisos por Módulo

| Módulo / Acción Operativa | Scrum Master | Product Owner | Scrum Member |
|---|:---:|:---:|:---:|
| **Crear proyectos ágiles** | ✅ | ✅ | ✅ |
| **Eliminar proyecto activo** | ✅ | ❌ | ❌ |
| **Invitar colaboradores y generar códigos** | ✅ | ❌ | ❌ |
| **Reasignar roles de integrantes** | ✅ | ❌ | ❌ |
| **Crear / Editar / Priorizar historias en Backlog** | ❌ *(Solo lectura)* | ✅ *(Control total)* | ❌ *(Solo lectura)* |
| **Crear y transicionar estados de Sprints** | ✅ | ❌ *(Solo lectura)* | ❌ *(Solo lectura)* |
| **Seleccionar y parametrizar métricas** | ✅ | ✅ | ❌ *(Solo lectura)* |
| **Aprobar / Rechazar parametrizaciones (Verificación)** | ✅ | ❌ *(Sin acceso)* | ❌ *(Sin acceso)* |
| **Formular métricas personalizadas con IA** | ✅ | ✅ | ❌ *(Sin acceso)* |
| **Capturar mediciones de alcance individual** | ✅ | ✅ | ✅ |
| **Capturar mediciones de alcance grupal** | ✅ | ❌ | ❌ |
| **Consultar tendencias y estadísticas en Evaluación** | ✅ | ✅ | ✅ |
| **Visualizar KPIs y gráficos en Dashboard** | ✅ | ✅ | ✅ |
| **Generar AI Insights y Reportes Word (.docx)** | ✅ | ✅ | ✅ *(Lectura)* |
| **Ejecutar Retrospectivas guiadas con IA** | ✅ | ✅ | ✅ |

---

## Cómo Continuar

Con los roles y responsabilidades claramente definidos:

👉 Avanza a la Etapa 5: [Preparación del Backlog y los Sprints](/backlog/general).
