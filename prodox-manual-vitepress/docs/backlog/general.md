# Gestión del Backlog e Historias de Usuario

Aprende a estructurar el trabajo funcional de tu proyecto ágil, a redactar y estimar historias de usuario y a priorizar el Product Backlog en PRODOX V1.

---

## ¿Qué es el Product Backlog en PRODOX V1?

El **Product Backlog** (`/backlog`) es el inventario centralizado y priorizado de requerimientos funcionales del proyecto. En la arquitectura de PRODOX V1:

- 📌 **Ubicación Metodológica**: En el menú lateral se presenta como un sub-ítem funcional dentro de la fase de Planeación, ya que el backlog define el alcance que respaldará la selección de métricas del proyecto.
- 🎯 **Propiedad del Product Owner**: Es el único rol autorizado para crear (`+ Nueva historia`), editar, eliminar y priorizar los elementos del backlog.
- 👓 **Transparencia para el Equipo**: El Scrum Master y los Miembros de desarrollo tienen acceso permanente de lectura para consultar criterios de aceptación y detalles técnicos.

![Listado del Product Backlog con filtros por estado e historias registradas](/images/backlog/listado-historias.png)

---

## Procedimiento: Crear una Historia de Usuario

Para dar de alta una historia en el backlog (requiere rol de **Product Owner**):

### Paso 1: Abrir el Formulario
1. Con tu proyecto activo seleccionado, haz clic en **"Backlog"** en la barra de navegación lateral.
2. Haz clic en el botón superior azul **"Nueva historia"**.
3. Se desplegará el panel de captura de historias: **"Nueva historia de usuario"**.

> [!NOTE]
> Si ingresas con rol de Scrum Master o Scrum Member, la pantalla mostrará el aviso informativo:
> *"Vista de solo lectura — la gestión del backlog es responsabilidad del Product Owner"*, y el botón de creación no se mostrará.

### Paso 2: Completar los Campos Estructurados

| Campo | Definición | Regla o Formato Recomendado | Obligatorio |
|---|---|---|:---:|
| **Título** | Resumen ágil de la funcionalidad requerida | Formato sugerido: <br>`Como [rol], quiero [funcionalidad] para [beneficio]` | ✅ Sí |
| **Prioridad** | Nivel de urgencia o valor de entrega | Menú desplegable: `Alta`, `Media` (predeterminada) o `Baja` | ✅ Sí |
| **Descripción** | Especificación técnica o contexto de negocio | Área de texto para detalles del requerimiento | ⚪ Opcional |
| **Criterios de Aceptación** | Condiciones que determinan el "Done" | Validaciones requeridas (`Uno por línea`) | ⚪ Opcional |

### Paso 3: Guardar y Asignar la Historia
1. Haz clic en el botón **"Crear historia"**.
2. La historia se insertará en el inventario con estado inicial **Pendiente** y clasificada como **Sin sprint (backlog)**.
3. Desde la tarjeta de cada historia, el Product Owner dispone de selectores directos para:
   - **Modificar Prioridad**: `Alta`, `Media` o `Baja`.
   - **Transicionar Estado**: `Pendiente`, `En progreso` o `Completada`.
   - **Asignar a Sprint**: Vincularla a `Sprint 1`, `Sprint 2`... o devolverla a `Sin sprint (backlog)`.
   - **Editar / Eliminar**: Botones dedicados para modificar la redacción en un modal o remover la historia.

---

## Estimación con Story Points (Escala de Fibonacci)

Para mantener la consistencia entre proyectos y facilitar el cálculo de velocidad en el [Dashboard](/dashboard/general), se recomienda seguir este estándar de estimación:

- **1 - 2 Puntos (Trivial / Simple)**: Ajustes menores de interfaz, cambios de texto o validaciones sencillas (1 a 4 horas).
- **3 Puntos (Medio)**: Funcionalidad estándar bien delimitada con impacto en base de datos (1 día de desarrollo).
- **5 Puntos (Complejo)**: Flujo de negocio completo con integración entre frontend y backend (2 a 3 días).
- **8 Puntos (Muy Complejo)**: Componente arquitectónico crítico o con incertidumbre técnica (1 semana).
- **13+ Puntos (Épica)**: Tarea demasiado extensa. **Recomendación ágil**: Dividirla en dos o más historias independientes.

---

## Filtros de Estado del Backlog

La cabecera del módulo incluye botones de filtrado rápido para organizar la vista del equipo:

- **Todas**: Muestra el inventario histórico completo.
- **Pendiente / Backlog**: Historias listas para ser planificadas en futuros sprints.
- **En Progreso**: Historias que han sido asignadas al sprint activo actual.
- **Completada**: Historias que han superado los criterios de aceptación y se encuentran finalizadas.

---

## Cómo Continuar

Una vez estructurado el backlog:

👉 Avanza a la [Gestión y Ciclo de Vida de Sprints](/sprints/general) para organizar las historias dentro de las iteraciones del proyecto.
