# Creación Asistida de Métricas con IA

Aprende a formular métricas personalizadas para tu proyecto ágil utilizando el asistente de Inteligencia Artificial integrado en PRODOX V1.

---

## ¿Qué es la Creación de Métricas con IA?

La funcionalidad **Crear Métrica con IA** (`/crear-metrica-ia`) es una puerta de entrada especializada dentro de la fase de Planeación. Está concebida para aquellos escenarios donde el catálogo estándar de PRODOX V1 no incluye un indicador adaptado a las necesidades específicas de tu equipo:

- 🤖 **Formulación a partir de Lenguaje Natural**: Describes en tus propias palabras qué deseas diagnosticar o medir y el asistente estructurará una métrica profesional completa.
- 🎯 **Alineación con Buenas Prácticas Ágiles**: El motor propone objetivos SMART, fórmulas normalizadas, variables operativas y escalas de medición coherentes.
- 🔄 **Integración sin Rupturas**: La métrica generada se incorpora al catálogo del proyecto activo para seguir el mismo flujo regular de parametrización y aprobación por el Scrum Master.

![Modal interactivo de creación de métrica con IA en PRODOX V1](/images/planeacion/crear-metrica-ia.png)

---

## Requisitos de Operación

- ✅ Proyecto activo seleccionado en estado `'activo'`.
- ✅ Rol de **Scrum Master** o **Product Owner** en el proyecto.
- ✅ Conectividad activa con el backend de Spring Boot (que gestiona de forma segura el servicio de Google Gemini).

---

## Procedimiento Paso a Paso

### Paso 1: Acceder al Asistente de Creación
1. Ingresa a la sección de [Planeación](/planeacion/general) (`/planeacion`).
2. En la barra de herramientas superior, haz clic en el botón **"Crear métrica con IA"** (`<i class="bi bi-robot"></i>`).
3. El sistema te transferirá a la vista guiada `/crear-metrica-ia`.

### Paso 2: Redactar la Necesidad en Lenguaje Natural
En la tarjeta **"¿Qué necesitás medir?"**, describe en tus propias palabras qué deseas cuantificar:

> **Ejemplo de Prompt:**
> *"Quiero medir el tiempo promedio de resolución de defectos bloqueantes por sprint."*

1. Haz clic en el botón azul **"Generar propuesta"**.
2. El cliente invocará al backend y al servicio de Google Gemini, presentando un indicador de carga (*"Generando propuesta..."*).

### Paso 3: Revisión y Detección de Duplicados
La plataforma presentará el formulario estructurado **"Propuesta de métrica"**:

> [!NOTE]
> **Principio de Diseño: La IA propone, el Scrum Master decide**
> La propuesta de la IA nunca se guarda en la base de datos de forma automática. Permanece en memoria para tu revisión y validación previa.

Campos disponibles para ajuste manual:
- **Nombre** y **Categoría** (*Significado*, *Impacto*, *Flexibilidad* o *Socio-Humano FSH*).
- **Descripción** y **Objetivo**.
- **Qué pretende medir** y **Variables sugeridas**.
- **Tipo de operación sugerido**, **Fórmula sugerida**, **Unidad de resultado** y **Fuente sugerida**.

**Validación Inteligente contra Duplicados:**
- Si la métrica ya existe en el catálogo, el sistema muestra el aviso: *"Esta métrica ya existe en el catálogo"* con el botón **"Usar la métrica existente"** para reutilizarla sin duplicar registros.
- Si existen métricas similares, permite elegir entre **"Reutilizar existente"** o **"Crear como métrica diferente"**.

### Paso 4: Confirmar y Usar la Métrica
1. Una vez revisados los campos, haz clic en el botón verde **"Usar esta propuesta"**.
2. El sistema creará la métrica en la base de datos, la asociará a tu proyecto y te conducirá directamente a la fase de [Parametrización de Variables](/planeacion/parametrizacion-variables).

---

## Cómo Continuar

Una vez creadas y verificadas las métricas de tu proyecto:

👉 Avanza a la Etapa 7: [Ejecución de Métricas y Seguimiento del Sprint](/ejecucion/general).
