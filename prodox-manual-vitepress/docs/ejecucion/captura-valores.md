# Procedimiento de Captura, Edición e Historial de Mediciones

Aprende el procedimiento paso a paso para ingresar nuevas mediciones, modificar lecturas previas y auditar el historial de valores en PRODOX V1.

---

## Procedimiento de Registro Paso a Paso

### Paso 1: Localizar la Métrica en Ejecución
1. Asegúrate de tener el proyecto activo fijado en la barra superior y lateral.
2. Ingresa a la sección **Ejecución** (`/ejecucion`) desde el menú de navegación.
3. En el selector superior de iteraciones, verifica que esté seleccionado el sprint de trabajo (por ejemplo: *Sprint 3: 16/11 al 29/11/2026*).

### Paso 2: Abrir el Modal de Registro o Edición
- **Para una métrica pendiente** (ej. *Problemas Recurrentes* o *Problemas Reportados*): Haz clic en el botón azul **"Registrar valor"**.
- **Para actualizar una lectura ya registrada** (ej. *Defectos*): Haz clic en el icono de lápiz presente en la etiqueta de valor existente (`16/11/2026: 30`).

Se desplegará el cuadro de diálogo modal interactivo:

![Formulario modal interactivo para registro y edición de valores de métricas en PRODOX V1](/images/ejecucion/captura-valores.png)

### Paso 3: Completar los Campos del Registro
1. **Fecha de Medición**: Selecciona en el calendario la fecha correspondiente a la observación. Por defecto propone la fecha actual o la fecha del registro que se está editando.
2. **Valor Cuantitativo**: Introduce el valor observado según el tipo de dato definido en la parametrización (entero, decimal o booleano). En el ejemplo de *Defectos*, se ingresa el número `30`.
3. **Observaciones Contextuales (Opcional)**: Añade notas explicativas si la medición amerita justificación (ej. *"Incremento atribuible a pruebas exploratorias en módulo de pagos"*).

### Paso 4: Guardar y Validar la Información
1. Haz clic en el botón **"Guardar"**.
2. El sistema valida las reglas de integridad:
   - Que el valor numérico no exceda los rangos de la escala.
   - Que la fecha se encuentre dentro del intervalo del sprint (o corresponda a la fecha actual si el sprint está reabierto o activo).
3. Tras la confirmación:
   - El gráfico circular de **Progreso de Captura** incrementa su porcentaje.
   - La tarjeta de la métrica pasa a mostrar su etiqueta azul con la fecha y valor guardados.
   - La medición queda disponible de forma reactiva para los módulos de [Evaluación](/evaluacion/general) y [Dashboard](/dashboard/general).

---

## Consulta y Auditoría del Historial de Mediciones

Para revisar la evolución cronológica o verificar quién registró una medición:

1. En la tarjeta de la métrica correspondiente, haz clic en el botón **"Ver historial"**.
2. La vista desplegará la lista cronológica detallando:
   - Fecha y hora exacta de cada registro.
   - Usuario responsable del ingreso.
   - Valor numérico capturado y observaciones adjuntas.
3. El **Scrum Master** dispone de permisos para corregir o depurar lecturas erróneas directamente desde este panel.

> [!INFO]
> **Trazabilidad Inmutable:**
> Al modificar un valor en el historial, el motor analítico de PRODOX V1 recalcula inmediatamente las medias, desviaciones estándar y curvas de tendencia en los módulos subsiguientes.

---

## Cómo Continuar

Una vez capturadas las mediciones de tu sprint:

👉 Avanza a la Etapa 8: [Evaluación de Métricas y Análisis de Tendencias](/evaluacion/general) para auditar los resultados cuantitativos y comparar el desempeño histórico.
