# Selección y Resumen de Métricas para el Sprint

Aprende a utilizar el selector dual de transferencia para incorporar métricas a la iteración y a gestionar su avance en la mesa de trabajo de Resumen de Selección en PRODOX V1.

---

## El Flujo de Selección de Métricas

La preparación de las mediciones del sprint sigue un flujo de dos pasos complementarios:

```
[ Catálogo de Factores ] ──> [ Selector Dual (/seleccion) ] ──> [ Resumen de Selección (/resumen-seleccion) ]
```

1. **Selección Dual (`/seleccion`)**: Transfiere métricas desde el catálogo general hacia el conjunto de la iteración.
2. **Resumen de Selección (`/resumen-seleccion`)**: Mesa de control donde se supervisa qué métricas están sin parametrizar, cuáles están completas y se despachan al Scrum Master para su aprobación.

---

## Paso 1: Selección de Métricas mediante el Selector Dual

En la pantalla de **Selección de Métricas** (`/seleccion`), la interfaz implementa una lista de transferencia dual (*transfer list*):

![Selector dual interactivo con métricas disponibles y filtros por factor](/images/metricas/seleccion-metricas.png)

### Procedimiento:
1. **Filtrar por Factor**: En el menú desplegable superior, selecciona la dimensión a evaluar (*Calidad*, *Productividad*, *Sociohumano* o *Cumplimiento*). El panel izquierdo (*Métricas disponibles*) cargará las opciones correspondientes.
2. **Transferir Métricas Individuales**: Haz clic sobre la fila de la métrica en el panel izquierdo. Verás cómo se traslada al panel derecho (*Métricas seleccionadas*) con una marca verde de confirmación.
3. **Transferencia Masiva**: Pulsa el botón de flecha hacia la derecha (**`>`**) para transferir todas las métricas del factor seleccionado simultáneamente.
4. **Remover Métricas**: Para descartar una métrica del sprint, haz clic en el botón rojo **`✕`** de su fila en el panel derecho, o utiliza la flecha hacia la izquierda (**`<`**) para vaciar la selección.

![Métricas transferidas al panel derecho de selección activa](/images/metricas/seleccion-con-metricas.png)

5. **Confirmar la Selección**: Cuando tengas las métricas requeridas en el panel derecho, haz clic en el botón morado **"Seleccionar →"** al pie de la tabla. El sistema guardará la selección y te transferirá de inmediato al Resumen de Selección.

---

## Paso 2: Mesa de Control del Resumen de Selección

La pantalla de **Resumen de Selección** (`/resumen-seleccion`) consolida las métricas elegidas para el sprint y su grado de preparación técnica:

### Estado Inicial (Sin Métricas)
Si ingresas a esta vista sin haber transferido métricas desde el selector, el sistema presentará una notificación vacía orientándote a utilizar el selector:

![Pantalla de Resumen de Selección en estado inicial sin métricas elegidas](/images/metricas/resumen-vacio.png)

### Tabla de Trabajo con Métricas Seleccionadas
Una vez incorporadas las métricas, la tabla muestra su estado de configuración:

![Mesa de trabajo del Resumen de Selección con estados de avance y botón de envío](/images/metricas/resumen-seleccion.png)

### Columnas y Estados de la Tabla:
- **Métrica**: Nombre del indicador y etiqueta de categoría (*badge* de color). Si ya fue parametrizada, muestra un extracto de su objetivo.
- **Estado de Parametrización**:
  - 🔴 **Sin parametrizar**: Requiere definir fórmulas, escalas y procedimientos.
  - 🟡 **Parcial**: Cuenta con algunos datos pero le faltan campos obligatorios.
  - 🟢 **Completa**: Parametrización técnica finalizada y lista para revisión.
- **Acciones**:
  - ✨ **Parametrizar con GenAI**: Abre el formulario interactivo de parametrización.
  - 👁️ **Ver detalle**: Permite consultar los parámetros vigentes si la métrica ya fue guardada.
  - 🗑️ **Quitar**: Descarta la métrica de este sprint.

---

## Enviar Propuestas al Scrum Master

1. Por cada métrica en estado **"Sin parametrizar"** (rojo), pulsa el botón estrella (**`✨`**) y completa su parametrización técnica.
2. Al guardar cada una, su estado cambiará a **"Completa"** (verde).
3. Cuando todas las métricas del sprint estén completas, el botón inferior **"Enviar al Scrum Master"** se habilitará automáticamente.
4. Haz clic en **"Enviar al Scrum Master"** para remitir las propuestas a la bandeja de [Verificación](/planeacion/verificacion-scrum-master).

---

## Cómo Continuar

Para conocer en detalle cómo estructurar los campos y utilizar el asistente de IA:

👉 Continúa con [Parametrización de Métricas y Catálogo de Variables](/planeacion/parametrizacion-variables).
