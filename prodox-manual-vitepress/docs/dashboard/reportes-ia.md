# Reportes Ejecutivos Word, AI Insights y Retrospectivas

Aprende a utilizar las herramientas de síntesis ejecutiva y los asistentes de Inteligencia Artificial (Google Gemini) integrados en el Dashboard de PRODOX V1 para emitir reportes formales, diagnósticos predictivos y retrospectivas guiadas.

---

## Arquitectura de Inteligencia Artificial en PRODOX V1

PRODOX V1 integra modelos generativos avanzados (**Google Gemini**) a través de servicios backend especializados en Spring Boot. Esto garantiza:
- 🔒 **Seguridad y Privacidad**: La comunicación con el servicio de IA se gestiona en el servidor de forma transparente; el usuario no necesita configurar API keys ni tokens en el cliente web.
- 🎯 **Análisis Contextualizado**: Las peticiones incorporan automáticamente los datos reales del proyecto activo (métricas aprobadas, mediciones registradas, metas de sprint y promedios históricos).
- 📄 **Salidas Orientadas al Negocio**: Generación de informes en prosa ejecutiva listos para ser presentados ante comités directivos y exportados a documentos de Word (`.docx`).

---

## 1. Reportes Ejecutivos Narrativos (`/ai-report`)

El módulo de **Reporte Ejecutivo de Sprint** (`/ai-report`) convierte tablas numéricas dispersas en un documento analítico estructurado en lenguaje natural:

![Pantalla para generación de reportes ejecutivos narrativos con IA en PRODOX V1](/images/reportes/reportes-general.png)

### Procedimiento de Generación:
1. Accede desde el botón **"Reporte"** en la barra superior del Dashboard (o navega a `/ai-report`).
2. En la sección **"Seleccionar Sprint"**, despliega el selector y elige la iteración a evaluar (ej. *Sprint 3 - bajar el numero de defectos y hacer una correcta relacion de calidad de codigo*).
3. Haz clic en el botón morado **"Generar Reporte"**.
4. El sistema consultará los registros en el backend y procesará con Google Gemini el balance del ciclo.

### Secciones Estructuradas del Reporte:
La pantalla presenta el informe organizado en bloques analíticos:

1. **Encabezado y Metadatos**: Franja morada que especifica el Sprint Goal, el rango de fechas de la iteración (ej. *16/11 al 29/11/2026*), la fecha de generación y el botón de exportación **"Exportar a Word"**.
2. **Resumen Ejecutivo**: Diagnóstico en prosa que sintetiza la duración en días, el objetivo del sprint, el promedio observado de las variables clave frente al histórico de su categoría (ej. *30.00 vs 54.00*) y la evaluación de capacidad/velocidad requerida para el ciclo.
3. **Métricas del Sprint**: Tarjetas cuantitativas que reflejan los valores numéricos consolidados de las métricas evaluadas (ej. *Significado: 30.00*).
4. **Aspectos Destacados (⭐)**: Reconoce los logros del equipo, tales como el mantenimiento de bajas tasas de defectos, el cumplimiento del cronograma planificado y el seguimiento temprano de tendencias.
5. **Puntos de Atención (⚠️)**: Alertas que identifican variaciones con nivel de severidad (ej. *ALTA*), desvíos respecto a los promedios históricos o carencia de información sobre velocidad y puntos de historia entregados.
6. **Insights Relacionados**: Tarjetas analíticas vinculadas al sprint que explican anomalías observadas en las métricas.
7. **Recomendaciones**: Pautas de acción para tratar en la sesión de retrospectiva y sugerencias para ajustar la recolección de mediciones en futuros sprints.

### Exportación a Microsoft Word (`.docx`)
Al pulsar el botón **"Exportar a Word"** en la cabecera del reporte:
- El navegador genera y descarga de forma inmediata un archivo formal con extensión **`.docx`**.
- El documento respeta la jerarquía visual de títulos, tablas de soporte numérico y conclusiones para su archivo o entrega a comités de ingeniería.

---

## 2. Diagnóstico Predictivo: AI Insights (`/ai-insights`)

El módulo de **AI Insights — Análisis Agile** (`/ai-insights`) detecta de forma desatendida correlaciones, patrones atípicos y oportunidades de mejora a partir del histórico de mediciones del equipo:

![Listado de insights predictivos generados por el modelo de IA en PRODOX V1](/images/insights/insights-lista.png)

### Barra de Herramientas y Acciones:
En la cabecera del panel se ubican los controles de análisis:
- 🟣 **"Generar Insights"**: Invoca al motor de inteligencia artificial para procesar los datos de las iteraciones concluidas y emitir nuevos diagnósticos.
- 🔄 **"Actualizar"**: Recarga las tarjetas con la información más reciente de base de datos.
- 🟢 **"Exportar a Word"**: Compila todos los insights activos en un reporte Word (`.docx`) con tablas de evidencia y recomendaciones.

### Anatomía de una Tarjeta de Insight:
Cada tarjeta generada por el asistente desglosa un hallazgo estructurado:

- **Título y Clasificación**:
  - Enunciado claro del hallazgo (ej. *"Variación en la métrica Significado: reducción del 36,5% en dos sprints"*).
  - Insignia de **Severidad**: `Alto` (naranja), `Medio` o `Bajo`.
  - Insignia de **Tipo**: `Tendencia`, `Riesgo`, `Anomalía` u `Oportunidad`.
  - Insignia de **Confianza**: Nivel de fiabilidad estadística del modelo (`Confianza: Baja`, `Media` o `Alta`).
  - Controles de edición: Permite editar el texto del insight mediante el icono de lápiz o descartarlo mediante el icono de cierre (`✕`).
- **Observación**: Diagnóstico detallado que analiza el porcentaje de variación a lo largo de los sprints evaluados, su desviación estándar respecto a la media histórica y la interpretación cualitativa de cómo impacta el propósito y la dinámica del equipo ágil.
- **Evidencia**: Bloque de comprobación cuantitativa que expone:
  - `Valor actual` y `Valor anterior`.
  - `Promedio histórico` y `Variación porcentual` (ej. *-36.5%*).
  - `Tendencia` (`DOWN`, `UP` o `STABLE`) y `Cantidad de sprints analizados`.
- **Recomendación**: Recuadro lila con pautas metodológicas concretas para abordar el patrón identificado durante la reunión de retrospectiva.

> [!IMPORTANT]
> **Regla Comprobada en Backend:**
> La generación de AI Insights requiere estrictamente un **mínimo de 2 sprints finalizados formalmente** (`MINIMO_SPRINTS_FINALIZADOS_PARA_INSIGHTS = 2`). Si el proyecto no cuenta con este volumen de datos concluidos, el sistema mantendrá deshabilitado el botón para salvaguardar la validez estadística de los diagnósticos.

---

## 3. Retrospectivas Inteligentes Asistidas por IA (`/ai-retrospective`)

La funcionalidad de **Retrospectiva Inteligente** (`/ai-retrospective`) automatiza el análisis retrospectivo del sprint, correlacionando el cumplimiento de las metas del *Sprint Goal* con los datos cuantitativos capturados:

![Asistente interactivo de retrospectivas asistidas por IA en PRODOX V1](/images/retrospectivas/retrospectivas-general.png)

### Procedimiento:
1. Haz clic en el botón **"Retrospectiva"** en la barra superior del Dashboard (o navega a `/ai-retrospective`).
2. En la sección **"Seleccionar Sprint Finalizado"**, selecciona en el desplegable la iteración cerrada a auditar (ej. *Sprint 2 - bajar el numero de defectos y hacer una correcta relacion de calidad de codigo*).
3. Haz clic en el botón morado **"Generar Retrospectiva"**.
4. La plataforma desplegará la tarjeta verde del sprint con su identificación, metadatos y el botón **"Exportar a Word"**.

### Cuadrantes de Reflexión Continua de PRODOX V1:
El motor de PRODOX clasifica la retrospectiva en dos dimensiones ágiles con soporte cuantitativo real:

- 🟢 **¿Qué funcionó bien?** (icono de carita sonriente verde):
  - Contrasta los logros frente al *Sprint Goal*.
  - Ejemplifica con datos exactos: *"La métrica de Defectos / Significado registró una disminución pasando de 85,00 en el Sprint 1 a 54,00 en el Sprint 2 (una reducción del 36,47%), mostrando una tendencia orientada hacia la meta planteada en el Sprint Goal."*

- 🟠 **¿Qué podría mejorar?** (icono de flecha ascendente naranja):
  - Identifica vacíos de configuración metodológica (ej. *"La métrica analizada no tiene una dirección configurada en el sistema ('Dirección NO configurada'), lo que impide establecer formalmente si la variación cuantitativa cumple de forma automatizada con los estándares de calidad del proyecto"*).
  - Advierte sobre componentes del *Sprint Goal* sin métricas asociadas (ej. *"No se dispone de métricas cuantitativas adicionales en el reporte para evaluar explícitamente el segundo componente del Sprint Goal ('hacer una correcta relación de calidad de código')"*).

### Exportación a Microsoft Word
Al pulsar **"Exportar a Word"** en la franja verde superior, el sistema emite el archivo descargable `.docx` con los cuadrantes de evaluación y los datos de autoría del sprint.

---

## Cómo Continuar

Una vez consultados los reportes y tableros analíticos:

👉 Finaliza el manual con la Etapa 10: [Gestión de Sesión, Cuenta y Entorno](/configuracion/preferencias).
