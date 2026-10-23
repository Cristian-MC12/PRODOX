# Gestión de Integrantes e Invitaciones al Equipo

Aprende a gestionar los miembros de tu equipo ágil, a generar códigos de invitación y a incorporar colaboradores a tus proyectos en PRODOX V1.

---

## ¿Qué es el Módulo de Equipo?

El módulo de **Equipo** (`/equipo`) administra la nómina de colaboradores asignados al proyecto activo y su distribución de responsabilidades. Desde esta pantalla puedes:

- 👥 Visualizar a todos los integrantes vinculados al proyecto con su nombre, correo y rol actual.
- 🔑 **Generar códigos de invitación** protegidos para sumar nuevos miembros.
- 🔄 Reasignar roles entre integrantes (`Scrum Master`, `Product Owner`, `Scrum Member`).
- 🎟️ Canjear un código de invitación para unirte a un proyecto al que fuiste invitado.

---

## Acceso al Módulo de Equipo

En el menú lateral izquierdo, haz clic en **"Equipo"**:
- **Si tienes un proyecto activo seleccionado**: Verás la nómina de integrantes del proyecto y las herramientas de gestión.
- **Si no tienes un proyecto activo**: La pantalla mostrará el panel para ingresar un código de invitación y vincularte a un proyecto nuevo.

![Panel de gestión de equipo con listado de miembros y roles en PRODOX V1](/images/equipo/equipo-general.png)

---

## Procedimiento 1: Invitar a un Nuevo Colaborador

Solo el **Scrum Master** del proyecto tiene autorización para emitir códigos de invitación.

### Paso 1: Localizar el Panel de Invitación
1. Con tu proyecto activo seleccionado, ingresa a la vista de `/equipo`.
2. El Scrum Master verá el panel superior: **"Invitar miembro a este proyecto"**.

### Paso 2: Ingresar Correo y Seleccionar Rol
1. **Correo electrónico**: Digita la dirección del colaborador a quien invitas (`nombre@ejemplo.com`).
2. **Rol en el proyecto**: Elige entre:
   - **Scrum Member**: Para desarrolladores, evaluadores o colaboradores técnicos.
   - **Product Owner**: Para el responsable de la visión de producto y backlog.

> [!IMPORTANT]
> **Regla de Product Owner Único:**
> Si el proyecto ya cuenta con un Product Owner vinculado, el selector de rol solo permitirá invitar como **Scrum Member** y desplegará la advertencia:
> *"Este proyecto ya tiene un Product Owner."*

### Paso 3: Generar y Compartir el Código
1. Haz clic en el botón azul **"Generar código"**.
2. La pantalla desplegará la tarjeta informativa **"Código generado"** con el identificador único (ej. `PRJ-ABC123`).
3. Haz clic en el icono de copiar (`bi bi-clipboard`) para enviarlo de forma segura al miembro invitado.

---

## Procedimiento 2: Unirse a un Proyecto Mediante Código de Invitación

Si recibiste un código de invitación de tu Scrum Master, puedes unirte al proyecto siguiendo estos pasos:

### Opción A: Desde el Módulo de Equipo (`/equipo`)
1. Inicia sesión en PRODOX con tu cuenta.
2. Si no tienes proyecto activo, ingresa a **"Equipo"** en la barra lateral.
3. En el campo **"Código de invitación"**, escribe o pega el código recibido.
4. Haz clic en **"Unirse al proyecto"**.

### Opción B: Desde el Enlace Directo de Invitación (`/invitacion?codigo=...`)
1. Haz clic en el enlace compartido por tu Scrum Master (ej. `/invitacion?codigo=PRJ-PRODOX`).
2. La plataforma reconocerá la invitación y desplegará la pantalla de confirmación:

![Pantalla de aceptación de invitación directa al proyecto](/images/auth/invitacion-form.png)

3. Si ya posees credenciales en PRODOX, haz clic en **"Iniciar sesión"**; si eres un usuario nuevo, haz clic en **"Crear cuenta"**.
4. Al autenticarte, el sistema vinculará automáticamente tu cuenta al proyecto con el rol asignado por el Scrum Master y te transferirá de inmediato al espacio de trabajo.

---

## Procedimiento 3: Reasignar Roles y Gestionar Miembros

En la tabla **"Miembros del proyecto"**, el Scrum Master cuenta con controles directos en cada fila:

1. **Email y Rol Actual**: Muestra el correo del integrante con su insignia de rol correspondiente (los Scrum Master lucen un distintivo azul `SM`).
2. **Cambiar Rol**: En la columna correspondiente, selecciona el nuevo rol en el menú desplegable (`Scrum Member` o `Product Owner`). El cambio se persiste de inmediato en la base de datos.
3. **Eliminar Miembro**: Al hacer clic en el botón de papelera roja (`bi bi-trash`), el sistema solicita confirmación mediante un diálogo modal antes de desvincular al usuario del proyecto.

> [!WARNING]
> **Regla de Scrum Master Único:**
> PRODOX V1 aplica la regla metodológica de un único Scrum Master por proyecto. El Scrum Master líder es quien creó el proyecto y no puede ser removido por otros integrantes.

---

## Cómo Continuar

- Para conocer en detalle qué acciones puede realizar cada rol en los distintos módulos, consulta la [Matriz de Roles y Permisos (RBAC)](/equipo/roles-permisos).
- Si tu equipo ya está conformado, continúa con la Etapa 5: [Preparación del Backlog y los Sprints](/backlog/general).
