# Creación de un Nuevo Proyecto Ágil

Aprende a configurar un nuevo proyecto ágil en PRODOX V1, definiendo su metodología de trabajo, horizonte de sprints y objetivo estratégico del producto (*Product Goal*).

---

## ¿Qué Implica Crear un Proyecto en PRODOX V1?

Al registrar un nuevo proyecto en PRODOX V1:
- Se inicializa un espacio de trabajo metodológico aislado en la base de datos.
- **Te conviertes automáticamente en el Scrum Master** y líder administrativo del proyecto.
- El sistema proyecta el calendario de iteraciones basándose en el timebox y la fecha de inicio configurados.
- Se habilita el catálogo de factores, historias de backlog y herramientas de analítica con Inteligencia Artificial.

---

## Requisitos Previos

- ✅ Haber iniciado sesión en la plataforma.
- ✅ Tener clara la metodología de trabajo del equipo (`Scrum` o `XP`).
- ✅ Conocer la duración estimada de cada ciclo de trabajo (timebox) y la meta estratégica (*Product Goal*).

---

## Procedimiento de Creación Paso a Paso

### Paso 1: Abrir el Formulario de Creación

1. Dirígete a la sección de [Listado de Proyectos](/proyectos/listado) (`/proyectos`).
2. En la cabecera superior derecha, haz clic en el botón azul **"+ Nuevo Proyecto"**.
3. La interfaz desplegará el panel del formulario directamente sobre el listado:

![Formulario de creación de proyecto abierto y listo para diligenciar](/images/proyectos/crear-formulario.png)

### Paso 2: Diligenciar los Parámetros del Proyecto

Completa cada uno de los campos requeridos en el formulario:

![Formulario de creación de proyecto diligenciado con datos reales en PRODOX V1](/images/proyectos/crear-formulario-lleno.png)

| Campo | Propósito Metodológico | Regla de Validación | Obligatorio |
|---|---|---|---|
| **Nombre del Proyecto** | Título público con el que se identificará el proyecto en los tableros | Longitud entre **3 y 100 caracteres** | ✅ Sí |
| **Método Ágil** | Marco de trabajo: `Scrum` o `XP` (*Extreme Programming*) | Selección obligatoria de una opción | ✅ Sí |
| **Descripción** | Contexto general, alcance o antecedentes del sistema | Texto libre descriptivo | ⚪ Opcional |
| **Timebox (Duración)** | Duración de cada iteración | Valor entero mayor a 0 | ✅ Sí |
| **Unidad de Timebox** | Escala temporal: `Horas`, `Días` o `Semanas` | Selección de unidad | ✅ Sí |
| **Hora de Inicio** | Hora específica de arranque del primer sprint | Obligatorio **solo** si la unidad es `Horas` (`HH:mm`) | ⏱️ Condicional |
| **Número de Sprints** | Total de iteraciones proyectadas para el proyecto | Entero entre **1 y 20** sprints | ✅ Sí |
| **Fecha de Inicio** | Fecha en la que comenzará a correr el Sprint 1 | Fecha válida en formato de calendario | ✅ Sí |
| **Product Goal** | Objetivo de largo plazo y valor final que entregará el producto | Declaración concisa del propósito estratégico | ✅ Sí |

> [!NOTE]
> **Definición del Product Goal y Timebox en Horas:**
> En PRODOX V1, el Product Goal sustituye formularios genéricos de objetivos. Representa el norte estratégico que orientará la creación de historias en el [Backlog](/backlog/general) y la selección de métricas en la [Planeación](/planeacion/general). Si seleccionas `Horas` como unidad de timebox, el formulario desplegará automáticamente la casilla para definir la hora exacta de inicio del primer sprint.

### Paso 3: Guardar y Crear el Proyecto

1. Verifica que los campos obligatorios cumplan con las reglas de validación.
2. Haz clic en el botón **"Crear Proyecto"**.
3. El cliente despacha la petición `POST /api/proyectos` al backend.
4. Al recibir la confirmación:
   - El formulario se repliega.
   - La nueva tarjeta de proyecto se incorpora a la cuadrícula del listado.
   - El sistema calcula y crea internamente los registros de sprints planificados de acuerdo con el timebox seleccionado.

---

## Validaciones y Reglas Comprobadas

- **Nombre único por usuario**: Evita nombres excesivamente cortos (< 3 caracteres).
- **Rango de Sprints**: PRODOX V1 admite un máximo de 20 iteraciones por proyecto para mantener la coherencia analítica en las curvas de tendencia.
- **Asignación de Rol Automática**: Tu usuario queda registrado de forma inmediata con el rol `scrum_master` en la entidad `ProjectMember`, otorgándote los máximos privilegios administrativos sobre el proyecto recién creado.

---

## Cómo Continuar

Una vez creado el proyecto:

1. Localiza su tarjeta en el [Listado de Proyectos](/proyectos/listado) y haz clic en **"Trabajar"** para fijarlo como proyecto activo.
2. Continúa con la Etapa 4: [Invitar a los miembros de tu equipo](/equipo/general) y definir sus [roles y permisos](/equipo/roles-permisos).
3. A continuación, podrás [estructurar el backlog y planificar los sprints](/sprints/general).
