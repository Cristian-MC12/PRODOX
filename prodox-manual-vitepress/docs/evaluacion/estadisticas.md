# Estadísticas Globales y Detección de Patrones

Conoce cómo el motor analítico de PRODOX V1 sintetiza el desempeño histórico del proyecto, calcula medidas de dispersión y emite alertas automáticas de variabilidad.

---

## El Panel de Estadísticas del Proyecto

Al seleccionar la pestaña **"Estadísticas"** dentro del módulo de [Evaluación](/evaluacion/general), el sistema realiza un procesamiento matemático sobre todas las iteraciones que contienen registros:

![Pestaña de Estadísticas con métricas globales consolidadas y alertas de variabilidad](/images/evaluacion/evaluacion-estadisticas.png)

---

## Indicadores Estadísticos Globales

En la cabecera de la pestaña se presentan cuatro tarjetas cuantitativas que resumen el volumen histórico del proyecto:

| Indicador Estadístico | Valor en Proyecto PRODOX | Definición Metodológica |
|---|:---:|---|
| **Variables Medidas** | `1` | Total de métricas con al menos un registro numérico válido (*Defectos*). |
| **Sprints con Datos** | `5` | Total de iteraciones que poseen mediciones almacenadas en base de datos. |
| **Total de Registros** | `5` | Sumatoria total de eventos de captura registrados en el historial. |
| **Promedio General** | `45.8` | Media aritmética de todas las observaciones recopiladas en el proyecto. |

---

## Detección Automática de Patrones y Alertas

El motor de evaluación analiza las diferencias relativas y la dispersión entre iteraciones sucesivas para alertar al equipo sobre comportamientos que ameritan análisis:

### 1. Patrón de Tendencia Descendente (`1 métrica detectada`)
- **Variable**: *Defectos*.
- **Comportamiento Observado**: La métrica comenzó en 85 defectos (Sprint 1) y concluyó en 10 defectos (Sprint 5), representando una reducción sostenida del -88.2%.
- **Impacto**: Indicador positivo de estabilización del producto y consolidación de las prácticas de calidad del equipo.

### 2. Alerta de Alta Variabilidad (`1 métrica detectada`)
- **Variable**: *Defectos*.
- **Comportamiento Observado**: A pesar de la tendencia descendente global, el salto de 30 defectos en el Sprint 3 a 50 defectos en el Sprint 4 (+66.7%) supera el umbral de estabilidad matemática.
- **Acción Recomendada**: El Scrum Master debe examinar en retrospectiva las causas de esta dispersión para prevenir desvíos en futuras iteraciones.

---

## Precondiciones Técnicas

- **Mínimo de Datos Requerido**: Se necesitan lecturas en al menos **dos sprints** para habilitar los cálculos de dispersión y la detección de variabilidad.
- **Recálculo Reactivo**: Al modificar o agregar nuevas capturas desde el módulo de [Ejecución](/ejecucion/captura-valores), los valores estadísticos se actualizan automáticamente sin recargar la página.

---

## Cómo Continuar

Una vez evaluados los resultados cuantitativos:

👉 Avanza a la Etapa 9: [Dashboard Ejecutivo e Informes IA](/dashboard/general) para consultar los KPIs gerenciales, visualizaciones Chart.js y reportes descargables en Word.
