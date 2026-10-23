# Verificación y Aprobación del Scrum Master

Aprende cómo el Scrum Master revisa, edita, aprueba o rechaza las parametrizaciones de métricas remitidas por el equipo, consolidando las versiones formales para la ejecución.

---

## ¿Qué es la Verificación en PRODOX V1?

La pantalla de **Verificación del Scrum Master** (`/verificacion`) es el punto de control de calidad metodológica donde se validan las definiciones antes de que entren a la fase de captura diaria:

- 🛡️ **Acceso Exclusivo por Rol**: Solo los usuarios con rol `scrum_master` en el proyecto activo pueden ingresar a este módulo. Si un usuario con otro rol intenta acceder, el guard funcional despliega una advertencia y deniega la acción.
- 📋 **Control Formal de Versiones**: Cada aprobación consolida una versión inmutable de la métrica (v1, v2, etc.).
- ⚙️ **Activación de Variables para Ejecución**: Al aprobar una propuesta, el backend genera y activa automáticamente las variables en el módulo de [Ejecución](/ejecucion/general).

![Bandeja de verificación del Scrum Master con tarjetas KPI y propuestas pendientes](/images/metricas/verificacion-scrum-master.png)

---

## Panel de Indicadores (KPIs de Verificación)

En la cabecera del módulo se resume el estado de las propuestas en el proyecto activo:

| Tarjeta KPI | Color Distintivo | Significado Operativo |
|---|---|---|
| **Pendientes** | Amarillo / Ámbar | Cantidad de propuestas de métricas enviadas por el equipo que esperan revisión. |
| **Aprobadas** | Verde | Total de métricas vigentes activas para captura y cálculo en los sprints. |
| **Rechazadas** | Rojo | Propuestas devueltas al equipo para corrección de fórmulas o escalas. |

---

## Reglas de Versionado de Métricas

> [!IMPORTANT]
> **Consecuencias de aprobar una nueva versión de una métrica:**
> - Cuando el Scrum Master aprueba una nueva parametrización para una métrica previamente existente, la versión anterior deja de estar vigente de forma inmediata.
> - El módulo de **Ejecución** pasa a utilizar exclusivamente la nueva definición y su nueva escala de captura.
> - Las mediciones capturadas bajo versiones anteriores **no se eliminan ni se pierden**: quedan preservadas en la base de datos vinculadas a su versión histórica respectiva para mantener la fidelidad de auditoría en el módulo de [Evaluación](/evaluacion/general).

---

## Procedimiento: Acciones del Scrum Master

Por cada propuesta pendiente listada en la tabla, el Scrum Master dispone de las siguientes opciones:

### 1. Inspeccionar Detalles
Haz clic en la fila de la propuesta para examinar el objetivo de medición propuesto, la fórmula de cálculo, los nombres de variables, la escala y el usuario que remitió la solicitud.

### 2. Editar la Propuesta
Si la formulación técnica requiere un ajuste menor:
1. Haz clic en el botón azul **"Editar"**.
2. Modifica el objetivo, los límites de la escala o la frecuencia de captura en el diálogo modal.
3. Guarda los cambios; la propuesta quedará corregida y lista para ser aprobada sin obligar al equipo a rehacer el envío.

### 3. Aprobar Parametrización
1. Haz clic en el botón verde **"Aprobar"**.
2. El sistema creará la versión formal en la base de datos, incrementará el contador de métricas aprobadas y habilitará los campos de captura en `/ejecucion`.

### 4. Rechazar con Motivo Metodológico
1. Si la propuesta no se alinea con los objetivos del proyecto, haz clic en **"Rechazar"**.
2. Se desplegará un formulario emergente donde deberás ingresar el **motivo de rechazo** (ej. *"La escala no contempla casos negativos"* o *"La frecuencia debe ser por sprint, no diaria"*).
3. La métrica regresará al estado editable en el [Resumen de Selección](/planeacion/seleccion-metricas) del remitente con el motivo indicado.

---

## Cómo Continuar

Una vez verificadas y aprobadas las métricas del proyecto:

- Si necesitas formular una métrica especializada con ayuda de IA, consulta [Crear Métrica con IA](/planeacion/crear-metrica-ia).
- Si tu catálogo ya está listo y aprobado, avanza a la Etapa 7: [Ejecución de Métricas y Seguimiento del Sprint](/ejecucion/general).
