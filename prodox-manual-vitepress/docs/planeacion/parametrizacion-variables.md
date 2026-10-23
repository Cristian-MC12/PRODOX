# Parametrización de Métricas y Catálogo de Variables

Aprende a formular con precisión los objetivos, fórmulas, escalas y procedimientos de cálculo de cada métrica mediante el asistente GenAI y a gestionar las variables en PRODOX V1.

---

## ¿Qué es la Parametrización Técnica?

La parametrización transforma una definición teórica del catálogo en una **métrica operable y medible** en el proyecto:

- 🎯 **Objetivo Cuantitativo**: Qué comportamiento o mejora busca medir el equipo.
- 📐 **Procedimiento y Fórmula**: Reglas de cálculo y relación matemática entre variables.
- 🔢 **Identificación de Variables**: Variables concretas que se registrarán durante el sprint.
- 📊 **Escala y Rangos**: Valores mínimos, máximos, paso (*step*) y límites de tolerancia.
- 🤖 **Aceleración con GenAI**: Propuestas automáticas generadas por Inteligencia Artificial basadas en el contexto del proyecto y buenas prácticas ágiles.

---

## El Formulario de Parametrización

Al pulsar el botón estrella (**`✨`**) desde el [Resumen de Selección](/planeacion/seleccion-metricas), se despliega el formulario especializado de la métrica:

![Formulario de parametrización estructurada con asistente GenAI en PRODOX V1](/images/metricas/parametrizacion-formulario.png)

---

## Asistente con Inteligencia Artificial (GenAI)

El bloque superior **AI — Asistente de Parametrización** agiliza la formulación técnica:

1. Haz clic en el botón azul **"Generar parametrización con GenAI"**.
2. El cliente enviará los metadatos de la métrica al backend, donde el modelo de lenguaje de Google Gemini elabora una propuesta integral (objetivo, fórmula, variables y escala recomendada).
3. Los campos del formulario se completarán automáticamente con la propuesta generada.
4. Puedes aceptar la propuesta directamente o editar cualquier valor para adaptarlo a las particularidades de tu equipo.

---

## Campos Estructurados del Formulario

| Sección / Campo | Definición | Ejemplo Operativo |
|---|---|---|
| **Objetivo de Medición** | Qué se busca diagnosticar o mitigar en la iteración | *"Reducir la densidad de defectos en módulos críticos a menos de 5 por sprint"* |
| **Procedimiento / Fórmula** | Descripción paso a paso de la operación matemática | `Defectos = Sumatoria de incidencias cerradas con estado bug` |
| **Indicador y Variables** | Relación analítica y nombres técnicos | `DefectosDetectados` (Entero positivo) |
| **Tipo de Escala** | Numérica entera, decimal o porcentual | `Entero positivo` |
| **Rango de Medición** | Valores mínimos y máximos esperados | Mínimo: `0`, Máximo: `100`, Paso: `1` |
| **Frecuencia de Captura** | Momento en el que se debe registrar el dato | `Al finalizar el sprint` o `Semanal` |
| **Responsable de Captura** | Rol encargado del registro | `Scrum Master` o `Scrum Member` |

### Campos Complementarios de Calidad Académica
Para proyectos de investigación, tesis o auditorías formales, el formulario habilita campos complementarios:
- **Fórmula Académica Formal**: Expresión matemática normalizada.
- **Tipo de Operación**: `Sumatoria`, `Promedio ponderado`, `Ratio` o `Mediana`.
- **Unidad de Medida del Resultado**: `Porcentaje (%)`, `Puntos de historia`, `Defectos/KLOC` u `Horas`.
- **Fuente Bibliográfica**: Estándar de ingeniería de software o referencia académica de respaldo.

---

## Guardar y Consolidar la Parametrización

1. Una vez revisados los campos, haz clic en **"Guardar parametrización"**.
2. La métrica actualizará su estado a **"Completa"** en el [Resumen de Selección](/planeacion/seleccion-metricas).
3. Podrás continuar parametrizando las demás métricas del sprint hasta completar el lote requerido.

---

## El Catálogo de Variables en Planeación

Cuando las propuestas de parametrización son aprobadas por el Scrum Master, el sistema genera de forma automática las variables en la pestaña **Variables** de `/planeacion`:

![Tabla del catálogo de variables operativas generadas tras la aprobación](/images/planeacion/variables-tab.png)

### Atributos Clave de las Variables:
- **Alcance (`scope`)**:
  - `Individual`: Cada miembro del equipo de desarrollo puede ingresar sus propias mediciones desde su cuenta.
  - `Grupal`: La variable representa un consolidado del sprint y su captura está reservada al Scrum Master.
- **Tipo de Dato (`dataType`)**: Condiciona el control visual que se renderizará en el módulo de [Ejecución](/ejecucion/general) (campo numérico, caja de texto o selector booleano).

---

## Cómo Continuar

Una vez completadas y guardadas las parametrizaciones:

👉 Avanza al siguiente paso: [Verificación y Aprobación del Scrum Master](/planeacion/verificacion-scrum-master).
