# Evaluación de Métricas y Análisis de Tendencias

Aprende a auditar cuantitativamente el rendimiento del equipo en iteraciones concluidas y en curso mediante el módulo de Evaluación en PRODOX V1.

---

## ¿Qué es la Fase de Evaluación en PRODOX V1?

La **Evaluación** (`/evaluacion`) constituye la tercera fase secuencial del ciclo operativo de PRODOX. Una vez capturadas las mediciones en los sprints, este módulo procesa la evidencia cuantitativa para:

- 🔬 Auditar el comportamiento matemático de las métricas en cada ciclo de trabajo.
- 📐 Calcular estadísticas intra-sprint (media, rango y dispersión/desviación estándar).
- 📊 Contrastar el desempeño entre iteraciones consecutivas mediante una matriz comparativa transversal.
- ⚠️ Emitir alertas automáticas ante patrones de alta variabilidad o desvíos atípicos.
- 🔄 Conectar directamente los hallazgos cuantitativos con la sesión de **Retrospectiva Inteligente**.

![Vista general del panel de Evaluación con datos reales del proyecto PRODOX](/images/evaluacion/evaluacion-general.png)

---

## Contexto de Datos y Selección de Sprints

En la cabecera superior de `/evaluacion`, la plataforma informa el universo de datos disponibles:

> **"5 sprint(s) con datos disponibles para análisis"**

A través del selector desplegable de sprint, el usuario puede aislar una iteración específica:
- **Sprint 3: 16/11/2026 - 29/11/2026 (Reabierto)**: Permite inspeccionar los datos recopilados en la iteración auditada.
- **Flexibilidad de Consulta**: El sistema permite evaluar las mediciones tanto en sprints finalizados como en sprints en estado `Reabierto` o `Activo`, facilitando auditorías intermedias antes del cierre definitivo del ciclo.

---

## Pestaña 1: Análisis de Tendencias Intra-Sprint

Al ingresar al módulo, la pestaña predeterminada es **"Tendencias"**, la cual despliega tarjetas analíticas por cada métrica con mediciones registradas en el sprint elegido:

### Caso Práctico en PRODOX: Métrica *Defectos* (Categoría Significado)
- **Valor Registrado**: `30` defectos detectados.
- **Promedio del Sprint**: `30.0`.
- **Rango**: `30 - 30`.
- **Desviación Estándar**: `0.0`.

> [!NOTE]
> **Requisito para Tendencias Intra-Sprint:**
> Para que el sistema trace una curva con pendiente interna dentro del mismo sprint, se requiere haber registrado al menos **dos capturas en fechas diferentes** durante la iteración. Con una sola lectura, el sistema presenta los valores puntuales y la media sin inducir falsas pendientes.

---

## Precondiciones Técnicas del Módulo de Evaluación

| Elemento Analítico | Precondición en Base de Datos | Comportamiento en PRODOX V1 |
|---|---|---|
| **Carga de Datos** | Al menos 1 registro en `/api/evaluacion/proyecto/{id}` | Activo: 5 sprints con registros disponibles |
| **Pendiente Intra-Sprint** | $\ge 2$ capturas en fechas distintas para el sprint | Informativo: 1 lectura registrada en Sprint 3 |
| **Comparación Multi-Sprint** | Mediciones en $\ge 2$ iteraciones consecutivas | Matriz histórica completa disponible |
| **Cálculo de Variabilidad** | Registros continuos en el proyecto | Alertas automáticas habilitadas en Estadísticas |

---

## Conexión con la Retrospectiva

En la cabecera del módulo se ubica el botón de acción principal **"Continuar con Retrospectiva"**. Al pulsarlo, el sistema te transfiere al asistente de [Retrospectiva con IA](/dashboard/reportes-ia), inyectando automáticamente los indicadores evaluados para enriquecer la dinámica del equipo.

---

## Cómo Continuar

Para profundizar en los análisis del módulo de Evaluación:

1. [Comparación Transversal de Métricas entre Sprints](/evaluacion/por-metrica) — Examina la matriz comparativa a lo largo de las 5 iteraciones.
2. [Estadísticas Globales y Detección de Patrones](/evaluacion/estadisticas) — Revisa las alertas de variabilidad y promedios históricos.
