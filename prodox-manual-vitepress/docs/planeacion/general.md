# Módulo de Planeación y Catálogo de Factores

Conoce cómo PRODOX V1 organiza la etapa de planeación, cómo se estructuran sus pestañas internas y cómo utilizar el catálogo de factores para orientar la medición del proyecto.

---

## ¿Qué es la Fase de Planeación en PRODOX V1?

La **Planeación** (`/planeacion`) es la primera de las tres fases operativas formales de PRODOX. Su propósito es definir con rigor metodológico qué variables se observarán durante los sprints para garantizar que el equipo no mida datos al azar, sino indicadores alineados con los objetivos del producto.

Desde este módulo el equipo puede:
- 📑 Explorar el catálogo de métricas organizadas por categorías de calidad y desempeño.
- 🎯 Seleccionar los **Factores de Evaluación** prioritarios para la iteración activa.
- 📐 Consultar el inventario de **Variables de Medición** generadas a partir de métricas aprobadas.
- 🗓️ Supervisar el **Calendario de Sprints** y la distribución temporal de las metas.
- 🤖 Desplegar el asistente para formular métricas personalizadas con Inteligencia Artificial.

![Vista general del módulo de Planeación en PRODOX V1](/images/planeacion/planeacion-general.png)

---

## Las Tres Pestañas Internas de Planeación

En la vista principal de `/planeacion`, la interfaz organiza las herramientas en tres pestañas de trabajo:

### 1. Pestaña "Métricas"
Es la vista predeterminada. Despliega el catálogo de métricas agrupadas por categorías metodológicas:
- **Significado**: Métricas base de defectos, incidencias y estabilidad del producto.
- **Flexibilidad**: Capacidad de adaptación, variabilidad del backlog y respuesta ante cambios.
- **Impacto**: Aportación de valor al usuario y cumplimiento de entregables clave.
- **Socio-Humano (FSH)**: Clima del equipo, bienestar, balance de carga y cohesión colaborativa.

Permite buscar métricas por nombre, consultar su definición y acceder directamente a la [Selección de Métricas](/planeacion/seleccion-metricas) y a [Crear Métrica con IA](/planeacion/crear-metrica-ia).

### 2. Pestaña "Variables"
Muestra la tabla técnica de variables operativas que han sido aprobadas por el Scrum Master:

![Pestaña de Variables técnicas generadas para medición en Planeación](/images/planeacion/variables-tab.png)

Cada registro detalla:
- **Nombre de la Variable e Indicador asociado**.
- **Categoría metodológica**.
- **Alcance**: `Individual` (captura por cualquier miembro) o `Grupal` (captura por el Scrum Master).
- **Tipo de Dato**: `Numérico entero`, `Decimal`, `Texto` o `Booleano`.
- **Frecuencia de Captura**: Periodicidad recomendada para el registro.

### 3. Pestaña "Calendario de Sprints"
Presenta la línea de tiempo (*timeline*) y la tabla sincronizada de todas las iteraciones planificadas:

![Pestaña de Calendario de Sprints con cronograma y estados](/images/planeacion/calendario-sprints.png)

Permite verificar que las fechas de cada sprint no se solapen y que las metas de cada ciclo correspondan con la capacidad del equipo.

---

## Catálogo y Selección de Factores de Evaluación

Los **Factores de Evaluación** (`/factores`) son dimensiones que agrupan los aspectos prioritarios del sprint:

![Catálogo interactivo de factores ágiles en PRODOX V1](/images/metricas/factores-catalogo.png)

### Procedimiento para Seleccionar Factores del Sprint
1. En el menú de navegación o desde Planeación, accede a **Factores** (`/factores`).
2. Examina las tarjetas de los 14 factores predefinidos (ej. *Bienestar del Equipo*, *Calidad de Código*, *Predictibilidad*, *Estabilidad Técnica*).
3. Haz clic en el botón azul **"Seleccionar para el Sprint"** en aquellos factores que tu equipo priorizará en la iteración.
4. El botón cambiará su estado a **"Seleccionado — quitar"**, registrando la elección en el backend.
5. Estos factores actuarán como filtros automáticos en el selector de métricas.

---

## Cómo Continuar

Una vez establecidos los factores y revisada la vista general de planeación:

👉 Avanza al siguiente paso: [Selección y Resumen de Métricas para el Sprint](/planeacion/seleccion-metricas).
