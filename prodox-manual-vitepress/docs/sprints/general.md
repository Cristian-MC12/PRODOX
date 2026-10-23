# Gestión, Creación y Estados de Sprints

Aprende a planificar los ciclos de desarrollo de tu equipo ágil, a transicionar los estados de las iteraciones y a asignar el alcance del sprint en PRODOX V1.

---

## ¿Qué es la Gestión de Sprints en PRODOX V1?

El módulo de **Sprints** (`/sprints`) administra los bloques temporales de trabajo (*Timeboxes*) del proyecto. Desde esta vista el equipo puede:

- 🗓️ Supervisar el cronograma general de iteraciones con sus fechas de inicio, término y metas asociadas (*Sprint Goals*).
- 🔄 Controlar el ciclo de vida de los sprints (`Planificado`, `Activo`, `Finalizado`, `Reabierto`).
- 📋 Asignar historias del [Product Backlog](/backlog/general) a la iteración en curso.
- ⚡ Habilitar la captura de mediciones en el módulo de [Ejecución](/ejecucion/general).

![Listado de sprints planificados, en ejecución y finalizados en PRODOX V1](/images/sprints/listado-sprints.png)

---

## Ciclo de Vida y Estados del Sprint

El motor metodológico de PRODOX V1 administra las iteraciones bajo cuatro estados formales:

```
[ PENDIENTE ] ──> [ EN EJECUCIÓN ] ──> [ FINALIZADO ]
                         ▲                   │
                         └─── [ REABIERTO ] ◄┘
```

| Estado | Significado Operativo | Comportamiento en la Plataforma |
|---|---|---|
| **Pendiente** (`pendiente`) | Iteración proyectada en el horizonte del proyecto que aún no ha iniciado. | Permite planificar metas e historias del backlog. Las mediciones en Ejecución se activan al iniciarlo. |
| **En ejecución** (`en_ejecucion`) | Sprint en curso activo del equipo. | **Regla estricta:** Solo puede existir **1 sprint en ejecución a la vez**. Desbloquea la captura periódica de datos en `/ejecucion`. |
| **Finalizado** (`finalizado`) | Sprint cerrado formalmente tras la entrega de incrementos. | Congela las mediciones y consolida la información para [Evaluación](/evaluacion/general), [AI Insights](/dashboard/reportes-ia) y exportación de reportes. |
| **Reabierto** (`reabierto`) | Sprint concluido puesto en revisión metodológica o auditoría. | Permite al Scrum Master auditar y ajustar valores de captura en `/ejecucion` antes de consolidar el historial definitivo. |

---

## Generación y Horizonte de Sprints

En PRODOX V1, **los sprints se generan automáticamente al crear el proyecto** según el número de iteraciones parametrizadas (de 1 a 20 sprints). 

- El **Sprint 1** inicia de manera predeterminada en estado **En ejecución**.
- Los sprints subsiguientes (Sprint 2, 3...) quedan en estado **Pendiente**, esperando su activación ordenada.

![Listado de sprints planificados, en ejecución y finalizados en PRODOX V1](/images/sprints/listado-sprints.png)

---

## Procedimiento 1: Finalizar Sprint Activo e Iniciar Siguiente

Solo el usuario con rol de **Scrum Master** está facultado para transicionar los estados de iteración:

### Si existen sprints pendientes en el proyecto:
1. Ingresa a `/sprints` con tu proyecto activo.
2. En la parte superior verás el panel: **"Finalizar Sprint N e iniciar Sprint N+1"**.
3. **Sprint Goal del Sprint N+1**: Redacta la meta concreta que el equipo se compromete a alcanzar durante la siguiente iteración (campo obligatorio).
4. Haz clic en el botón amarillo **"Finalizar Sprint N e iniciar Sprint N+1"**.
5. Se abrirá el modal de confirmación. Pulsa **"Sí, confirmar"**. El Sprint N pasará a `finalizado` y el Sprint N+1 quedará `en_ejecucion`.

### Si se trata del último sprint del proyecto:
1. El panel superior indicará automáticamente: **"Cerrar Sprint N (último sprint del proyecto)"**.
2. Haz clic en **"Cerrar Sprint N"** y confirma en el modal. El sprint quedará en estado `finalizado`.

---

## Procedimiento 2: Reapertura y Cierre de Auditoría

En la tabla del **Historial de sprints**, el Scrum Master cuenta con acciones dedicadas por fila:

### Para Reabrir un Sprint Finalizado:
1. Localiza el sprint finalizado en la tabla de `/sprints`.
2. En la columna de acciones, haz clic en el botón **"Reabrir"** (`bi bi-arrow-counterclockwise`).
3. Confirma en el diálogo modal. El estado cambiará a **Reabierto**, habilitando la edición de mediciones en `/ejecucion`.

### Para Concluir un Sprint Reabierto:
1. Una vez realizadas las correcciones de datos, localiza el sprint reabierto.
2. Haz clic en el botón **"Finalizar"** (`bi bi-check2-circle`).
3. Confirma en el modal. El sprint regresará a estado **Finalizado**, consolidando la estadística definitiva.

---

## Cómo Continuar

Con el horizonte de sprints y el backlog definidos:

👉 Avanza a la Etapa 6: [Planeación de Métricas y Factores de Evaluación](/planeacion/general) para seleccionar los indicadores que medirás durante la ejecución.
