# Ejecución de Métricas y Seguimiento del Sprint

Aprende a supervisar el avance de la captura periódica de datos en el sprint activo, a auditar las variables registradas y a mantener el seguimiento del equipo en PRODOX V1.

---

## ¿Qué es la Fase de Ejecución en PRODOX V1?

La **Ejecución** (`/ejecucion`) es la segunda fase metodológica formal de PRODOX. Durante el transcurso del sprint, los miembros del equipo y el Scrum Master utilizan este módulo para recolectar las mediciones cuantitativas que respaldarán la analítica del proyecto:

- ⏱️ **Registro en Tiempo Real**: Captura periódica de variables conforme se desarrollan las actividades del sprint.
- 🎯 **Monitoreo del Avance**: Gráfico dinámico tipo dona que expone el porcentaje de recolección de métricas alcanzado frente al total planificado.
- 👥 **Control de Alcance por Perfil**:
  - **Variables Individuales**: Cualquier integrante del equipo puede ingresar y editar sus mediciones asignadas.
  - **Variables Grupales**: Mediciones agregadas del equipo reservadas exclusivamente al **Scrum Master**.
- 🔍 **Detección Preventiva de Desvíos**: Identificación temprana de valores atípicos (por ejemplo, incrementos en defectos) durante las reuniones diarias (*Daily Standups*).

![Panel principal de Ejecución con seguimiento en tiempo real y Sprint 3 activo](/images/ejecucion/ejecucion-general.png)

---

## Componentes del Panel de Ejecución

Al ingresar a `/ejecucion` con el proyecto activo, la pantalla presenta los siguientes bloques operativos:

### 1. Selector de Iteración (Sprint)
En la barra superior se ubica el selector desplegable con los sprints del proyecto. Al seleccionar el **Sprint 3: 16/11 al 29/11/2026 (Reabierto)**, la interfaz carga al instante las métricas aprobadas para dicha iteración.

### 2. Tarjeta de Progreso de Recolección (Gráfico de Dona)
Sintetiza el estado de captura del sprint actual:
- **Métricas Capturadas**: Proporción completada (ej. `1 de 3 Métricas capturadas - 33% completado`).
- **Métricas Pendientes**: Contador de variables que todavía no han registrado su lectura en el ciclo (ej. `2 pendientes`).

### 3. Tarjetas de Métricas del Sprint
Cada métrica formalmente aprobada se presenta en una tarjeta interactiva con su estado:

| Métrica | Categoría | Estado Actual | Visualización en Pantalla | Acciones Disponibles |
|---|---|---|---|---|
| **Defectos** | Significado | **Capturada** | Etiqueta azul con fecha y valor (`16/11/2026: 30`) | Icono de lápiz (editar) y botón **"Ver historial"** |
| **Problemas Recurrentes** | Significado | **Pendiente** | Insignia ámbar `Pendiente` | Botón azul **"Registrar valor"** |
| **Problemas Reportados** | Significado | **Pendiente** | Insignia ámbar `Pendiente` | Botón azul **"Registrar valor"** |

---

## Seguimiento Diario y Buenas Prácticas

- **Revisión en Daily Standups**: Se aconseja proyectar el panel de Ejecución al inicio de cada reunión diaria para corroborar que ninguna métrica quede desactualizada.
- **Preparación para la Evaluación**: Asegurar que todas las métricas del sprint pasen de estado `Pendiente` a `Capturada` garantiza que las fórmulas de dispersión del módulo de [Evaluación](/evaluacion/general) y las curvas del [Dashboard](/dashboard/general) se calculen con máxima exactitud.

---

## Cómo Continuar

Para consultar el procedimiento detallado para registrar una nueva lectura, modificar valores existentes o revisar el historial cronológico:

👉 Avanza a [Procedimiento de Captura, Edición e Historial de Mediciones](/ejecucion/captura-valores).
