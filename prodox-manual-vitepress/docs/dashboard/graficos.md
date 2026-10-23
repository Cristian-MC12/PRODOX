# Visualizaciones y Gráficos Analíticos Chart.js

Conoce las representaciones visuales interactivas que implementa el Dashboard de PRODOX V1 mediante la librería Chart.js para el seguimiento ágil de métricas y sprints.

---

## Catálogo de Visualizaciones Analíticas

En su estado operativo, el Dashboard de PRODOX V1 despliega gráficos dinámicos que correlacionan el avance del cronograma con las curvas de calidad:

![Visualizaciones gráficas de evolución, distribución y estado de sprints en PRODOX V1](/images/dashboard/dashboard-graficos.png)

---

## 1. Evolución Temporal de Métricas (`app-metrics-evolution-chart`)

- **Tipo de Gráfico**: Gráfico de líneas temporales (*Line Chart*) interactivo.
- **Eje Horizontal (X)**: Cronología de los 5 sprints (`Sprint 1` a `Sprint 5`).
- **Eje Vertical (Y)**: Valor numérico observado de la métrica.
- **Curva Observada en PRODOX (*Defectos*)**:
  - `Sprint 1`: 90 defectos (línea base).
  - `Sprint 2`: 55 defectos.
  - `Sprint 3`: 30 defectos (sprint bajo auditoría).
  - `Sprint 4`: 50 defectos (repunte analizado en retrospectiva).
  - `Sprint 5`: 10 defectos (estabilización).
- **Insignia de Tendencia**: Etiqueta destacada en color verde con el texto **"Descendente"**, que confirma matemáticamente la reducción general de defectos en el proyecto.
- **Interactividad**: Al posar el cursor (*hover tooltip*), el gráfico detalla la fecha y el valor puntual. Incluye selector para aislar métricas o comparar el conjunto.

---

## 2. Distribución por Categorías (`app-metrics-distribution-chart`)

- **Tipo de Gráfico**: Gráfico circular tipo dona (*Doughnut Chart*) que clasifica las métricas aprobadas según los ejes metodológicos de PRODOX.
- **Distribución en el Proyecto PRODOX**:
  - 🔵 **Significado** (`100%` - 3 métricas): Identificada con el color institucional (`#0E7C86`), agrupando las métricas *Defectos*, *Problemas Recurrentes* y *Problemas Reportados*.
- **Otras Categorías Soportadas por el Sistema**:
  - **Impacto** (`#5A96C4`): Aportación de valor y satisfacción.
  - **Flexibilidad** (`#1E8E5A`): Capacidad de adaptación y velocidad.
  - **Socio-Humano FSH** (`#C23B34`): Clima laboral y bienestar.
  - **Otros** (`#5B6B70`): Métricas técnicas auxiliares.

---

## 3. Panel de Riesgos Recientes

Módulo analítico que evalúa automáticamente la pendiente y estabilidad de las curvas:
- **Alerta Activa en PRODOX**: *"Significado en descenso sostenido"*.
- **Interpretación Metodológica**: Si bien la reducción de defectos es un logro positivo, el sistema recuerda al Scrum Master validar que la cobertura de pruebas automatizadas y de regresión se mantenga activa durante los sprints reabiertos.

---

## 4. Estado de los Sprints (`sprintsStatusCanvas`)

- **Tipo de Gráfico**: Gráfico de barras horizontales (*Horizontal Bar Chart*) que ilustra la distribución del ciclo de vida de los sprints del proyecto:
  - 🟢 **Finalizado** (`2 sprints`): Iteraciones cerradas formalmente (Sprint 1 y Sprint 2).
  - 🟡 **Reabierto** (`3 sprints`): Iteraciones en revisión o auditoría activa (Sprint 3, Sprint 4 y Sprint 5).
  - **Pendiente / En ejecución**: 0 sprints en estos estados actualmente.

---

## 5. Feed de Actividad Reciente (`app-activity-feed`)

Situado en la columna lateral derecha del Dashboard, proporciona una bitácora en tiempo real con las últimas transiciones de sprint, ejecuciones de evaluación y eventos de captura de mediciones efectuados por el equipo.

---

## Cómo Continuar

Para conocer cómo aprovechar la Inteligencia Artificial integrada y exportar reportes ejecutivos:

👉 Avanza a [Reportes Ejecutivos Word, AI Insights y Retrospectivas](/dashboard/reportes-ia).
