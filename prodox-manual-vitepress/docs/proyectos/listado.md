# Listado y Selección de Proyecto Activo

Aprende a navegar por el catálogo de proyectos de PRODOX V1, a seleccionar tu proyecto de trabajo activo y a gestionar su ciclo de vida.

---

## ¿Qué es el Listado de Proyectos?

La vista de proyectos (`/proyectos`) es el centro de mando donde convergen todos los proyectos ágiles en los que participas. Desde este módulo puedes:

- 📂 Visualizar las tarjetas informativas de todos los proyectos en los que eres Scrum Master, Product Owner o Miembro.
- 🚀 **Fijar el Proyecto Activo**: La acción indispensable para desbloquear las rutas protegidas (`/planeacion`, `/sprints`, `/backlog`, `/ejecucion`, `/dashboard`, etc.).
- ➕ Desplegar el formulario de creación de nuevos proyectos ágiles.
- 🔗 Canjear códigos de invitación para unirte a proyectos colaborativos.
- 🗑️ Eliminar proyectos de forma definitiva (acción protegida reservada al Scrum Master).

---

## Acceso al Listado de Proyectos

Tras completar el [inicio de sesión](/auth/login), el sistema te redirige de manera automática a la ruta:

```
/proyectos
```

![Catálogo principal de proyectos ágiles en PRODOX V1](/images/proyectos/listado-inicial.png)

---

## Elementos y Anatomía de la Tarjeta de Proyecto

Cada proyecto se representa mediante una tarjeta interactiva con metadatos clave:

![Tarjeta individual de proyecto con atributos y acciones reales en PRODOX V1](/images/proyectos/tarjeta-proyecto.png)

| Atributo | Significado Metodológico | Ubicación / Visualización |
|---|---|---|
| **Nombre del Proyecto** | Título identificador principal (ej. *PRODOX*). | Encabezado superior de la tarjeta. |
| **Metodología Ágil** | Marco de trabajo: `Scrum` o `XP` (*Extreme Programming*). | Insignia azul/turquesa en la cabecera. |
| **Estado del Proyecto** | Condición operativa: `activo` (en ejecución) o `finalizado`. | Insignia verde (`activo`) o gris (`finalizado`). |
| **Timebox** | Duración configurada de cada iteración (ej. *14 días* o *2 semanas*). | Sección de parámetros de ciclo. |
| **Sprints Planificados** | Total de iteraciones estimadas para el horizonte del proyecto. | Contador numérico. |
| **Total de Miembros** | Número de colaboradores vinculados actualmente al proyecto. | Indicador con icono de personas. |
| **Product Goal** | Meta de alto nivel y visión de largo plazo del producto. | Párrafo descriptivo en el cuerpo central. |
| **Scrum Master** | Correo electrónico del usuario líder y creador del proyecto. | Pie de la tarjeta. |

---

## Acción Crítica: Seleccionar Proyecto con el Botón "Trabajar"

> [!IMPORTANT]
> **El mecanismo de activación de proyecto (`proyectoGuard`):**
> Los módulos internos de la plataforma (`/planeacion`, `/sprints`, `/backlog`, `/ejecucion`, `/evaluacion`, `/dashboard`, etc.) requieren conocer sobre qué proyecto se realizarán las operaciones.
> Para ingresar a ellos, **debes hacer clic en el botón "Trabajar"** (`-> Trabajar`) de la tarjeta correspondiente.

### ¿Qué ocurre al hacer clic en "Trabajar"?
1. La aplicación almacena la estructura completa del proyecto en el navegador (`localStorage.setItem('mpdia_proyecto_activo', JSON.stringify(proyecto))`).
2. El menú lateral izquierdo (*sidebar*) se sincroniza de inmediato:
   - Despliega la tarjeta del proyecto activo con su nombre, badge Scrum/XP, total de sprints y tu rol asignado.
   - Habilita las secciones de **Sprints**, **Equipo**, **Planeación**, **Backlog**, **Ejecución**, **Evaluación** y **Dashboard**.
3. El sistema te transfiere automáticamente a la pantalla de [Planeación del Proyecto](/planeacion/general).

### Cómo Cambiar de Proyecto de Trabajo
Si colaboras en más de un proyecto:
1. Haz clic en la opción **"Proyectos"** en el menú de navegación lateral.
2. Localiza la tarjeta del otro proyecto que deseas gestionar.
3. Haz clic en su botón **"Trabajar"**. El contexto del navegador y el menú lateral se actualizarán al instante.

---

## Eliminación de Proyectos (Acción de Scrum Master)

Si un proyecto ha concluido o fue creado como prueba, el usuario con rol de Scrum Master puede eliminarlo de forma definitiva:

1. Localiza la tarjeta del proyecto en el listado.
2. En la esquina inferior derecha de la tarjeta, haz clic en el botón con el icono de papelera roja (`bi bi-trash`).
3. El sistema abrirá el diálogo modal de seguridad para prevenir pérdidas accidentales de datos:

![Modal de confirmación para eliminación definitiva de proyecto](/images/proyectos/eliminar-proyecto-modal.png)

4. Si estás completamente seguro, confirma pulsando **"Sí, eliminar proyecto"**.
5. El backend purgará el proyecto, sus historias, sprints y mediciones asociadas. Si dicho proyecto era el que tenías activo, el contexto de sesión se limpiará automáticamente.

---

## Cómo Continuar

- **¿Deseas iniciar un proyecto nuevo desde cero?** 👉 Sigue a [Crear Proyecto](/proyectos/crear).
- **¿Ya seleccionaste tu proyecto activo y deseas gestionar a tus colaboradores?** 👉 Avanza a la Etapa 4: [Preparación del Equipo y Roles](/equipo/general).
