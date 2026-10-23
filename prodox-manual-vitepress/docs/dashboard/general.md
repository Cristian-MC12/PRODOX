# Dashboard Ejecutivo y Analítica en Tiempo Real

Aprende a interpretar el centro de mando de PRODOX V1, sus tarjetas de indicadores clave (KPIs), acciones ejecutivas y herramientas de supervisión en tiempo real.

---

## ¿Qué es el Dashboard de PRODOX V1?

El **Dashboard** (`/dashboard`) es el centro de control analítico y gerencial de PRODOX V1. Consolida en una sola pantalla panorámica la información generada en las etapas de Planeación, Ejecución y Evaluación:

- 📊 **Resumen de Alto Nivel**: Visión sintética del estado del proyecto para el Scrum Master, Product Owner y directivos.
- 🚦 **Monitoreo de Salud del Proyecto**: Indicadores clave de desempeño (KPIs) y detección automática de riesgos.
- ⚡ **Acciones Rápidas Ejecutivas**: Acceso directo a diagnósticos de Inteligencia Artificial, retrospectivas y exportación inmediata a Word (`.docx`).
- 📈 **Visualizaciones Interactivas**: Curvas de evolución temporal y distribución de categorías renderizadas con Chart.js.

![Dashboard ejecutivo consolidado en estado operativo con datos reales del proyecto PRODOX](/images/dashboard/dashboard-general.png)

---

## Barra de Acciones Rápidas y Filtros

En la parte superior del Dashboard se despliega el menú de acciones inmediatas:

- 🤖 **Insights IA**: Abre el asistente de diagnóstico predictivo y análisis de patrones con Google Gemini (`/ai-insights`).
- 🔄 **Retrospectiva**: Conduce al panel de retrospectiva guiada basada en evidencia de sprint (`/ai-retrospective`).
- 📊 **Reporte**: Permite generar y revisar el resumen narrativo del estado del proyecto (`/ai-report`).
- 📄 **Reporte General**: Genera y **descarga directamente en formato Word (`.docx`)** el expediente analítico e histórico completo del proyecto para comités de ingeniería o stakeholders.
- 🔍 **Selector de Métrica Individual**: Menú desplegable que permite aislar una métrica específica (ej. *Defectos*) o visualizar el agregado de todas las variables activas.

> [!NOTE]
> **Permisos de Ejecución (RBAC):**
> Las 4 acciones de generación y exportación (*Insights IA*, *Retrospectiva*, *Reporte* y *Reporte General*) son **exclusivas del Scrum Master del proyecto activo** (`esScrumMasterDelProyecto`). Para los integrantes con rol de desarrollador (*Scrum Member*), estos botones se ocultan automáticamente para salvaguardar la gobernanza de las evaluaciones y el uso de cuota de IA.

---

## Tarjetas de Indicadores Clave de Desempeño (KPIs)

En la franja superior se resumen cuatro indicadores clave de control:

| Indicador KPI | Valor Actual en PRODOX | Interpretación Metodológica |
|---|:---:|---|
| **Métricas Activas** | **3** | Total de métricas parametrizadas y aprobadas en el proyecto (*Defectos*, *Problemas Recurrentes*, *Problemas Reportados*). |
| **Riesgos Detectados** | **1** | Alerta preventiva activa: *"Significado en descenso sostenido"*. Señala la necesidad de verificar que no decaiga la cobertura de pruebas. |
| **Sprint Activo** | **Sin sprint activo** | Notifica que no existe una iteración en estado `Activo` en curso (S1 y S2 finalizados; S3, S4 y S5 en estado reabierto). |
| **Miembros del Equipo** | **1** | Número de colaboradores con acceso y credenciales activas en el proyecto. |

> [!INFO]
> **Condición de Evaluación: "Sin cumplimiento combinado"**
> En la tarjeta de evaluación de cumplimiento se visualiza la nota:
> > *"1 métrica analizada - sin cumplimiento combinado"*
> 
> Esta condición es el comportamiento normal y documentado del sistema cuando las métricas capturadas pertenecen a la categoría base y aún no se ha ponderado un objetivo compuesto para el sprint actual. La plataforma refleja con fidelidad los datos sin inducir valores artificiales.

---

## Cómo Continuar

Para profundizar en las herramientas analíticas del Dashboard:

1. [Visualizaciones y Gráficos Analíticos](/dashboard/graficos) — Detalle de las curvas Chart.js, distribución por categorías y feed de actividad.
2. [Reportes Ejecutivos Word, AI Insights y Retrospectivas](/dashboard/reportes-ia) — Generación de informes en prosa y análisis inteligente asistido por IA.
