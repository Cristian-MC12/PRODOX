# Comparación Transversal de Métricas entre Sprints

Aprende a analizar la evolución histórica de tus indicadores y el impacto de las mejoras del equipo a través de la matriz comparativa de PRODOX V1.

---

## La Matriz de Comparación entre Sprints

Al seleccionar la pestaña **"Comparación entre Sprints"** dentro de [Evaluación](/evaluacion/general), PRODOX despliega una matriz estructurada que correlaciona de forma horizontal los valores registrados en todas las iteraciones del proyecto:

![Matriz comparativa de métricas a través de las cinco iteraciones de PRODOX V1](/images/evaluacion/evaluacion-metricas.png)

---

## Análisis Detallado de la Métrica: *Defectos*

En el proyecto de referencia **PRODOX**, la métrica base *Defectos* (categoría *Significado*) presenta la siguiente trayectoria cuantitativa a lo largo de los 5 sprints:

| Iteración | Estado del Sprint | Valor Observado | Variación Porcentual | Interpretación Metodológica |
|---|---|:---:|:---:|---|
| **Sprint 1** | Finalizado | **85** | — | **Línea Base Inicial**: Volumen elevado de defectos tras las primeras versiones. |
| **Sprint 2** | Finalizado | **54** | 🟢 **-36.5%** | **Mejora Significativa**: Reducción neta de incidencias por correcciones tempranas. |
| **Sprint 3** | Reabierto | **30** | 🟢 **-44.4%** | **Consolidación de Calidad**: El sprint auditado muestra una marcada contracción de defectos. |
| **Sprint 4** | Reabierto | **50** | 🔴 **+66.7%** | **Repunte Temporal**: Incremento sustancial que activa una alerta de atención. |
| **Sprint 5** | Reabierto | **10** | 🟢 **-80.0%** | **Mínimo Histórico**: Mayor madurez y estabilización del código del producto. |

### Métricas Agregadas de la Serie:
- **Promedio Histórico Global**: `45.8` defectos por sprint.
- **Trayectoria General**: **Descendente** (reducción neta acumulada de 85 a 10 defectos, representando una mejora global del -88.2%).
- **Lectura Metodológica para el Equipo**: Aunque la tendencia macro es sumamente positiva, la fluctuación del Sprint 4 (+66.7%) demuestra la importancia de contar con mediciones continuas para evitar confiar ciegamente en impresiones subjetivas.

---

## Utilidad de la Matriz en la Reunión de Retrospectiva

El Scrum Master y el Product Owner pueden utilizar esta matriz durante la ceremonia de retrospectiva para:
1. **Analizar Causa Raíz del Sprint 4**: ¿El aumento a 50 defectos se debió a un cambio imprevisto de alcance, a la incorporación de un nuevo integrante o a una suite de pruebas más rigurosa?
2. **Reconocer Buenas Prácticas del Sprint 5**: ¿Qué prácticas permitieron alcanzar el mínimo de 10 defectos que deben mantenerse como estándar?

---

## Cómo Continuar

Para revisar el cálculo automático de dispersión y las alertas preventivas del motor analítico:

👉 Avanza a [Estadísticas Globales y Detección de Patrones](/evaluacion/estadisticas).
