# FASE 2: ANÁLISIS DEL MODELO REAL DE MPDIA

**Fecha:** 10 de Agosto, 2026  
**Fase:** 2 - AgileAnalyticsService  
**Estado:** ANÁLISIS COMPLETADO

---

## 🔍 DATOS REALES ENCONTRADOS EN MPDIA

### Entidad: Sprint

**Campos disponibles:**
- `id` (UUID)
- `proyectoId` (UUID)
- `numero` (Integer) - Número secuencial del sprint
- `sprintGoal` (String) - Objetivo del sprint
- `estado` (String) - `pendiente` | `en_ejecucion` | `finalizado` | `reabierto`
- `fechaInicio` (LocalDate)
- `fechaFin` (LocalDate)
- `cerradoAt` (Instant) - Timestamp de cierre
- `createdAt` (Instant)

**NO EXISTEN:**
- ❌ Story points planificados
- ❌ Story points completados
- ❌ Historias/tareas/items individuales
- ❌ Estados de historias (TODO, IN_PROGRESS, DONE)
- ❌ Fechas de inicio/fin de items individuales
- ❌ WIP explícito
- ❌ Información de bloqueos de items

### Entidad: Variable

Una `Variable` representa una métrica configurable por el equipo dentro de un proyecto.

**Campos disponibles:**
- `id` (UUID)
- `proyectoId` (UUID)
- `metricaId` (UUID) - Referencia a métrica base del sistema
- `nombre` (String)
- `tipoAlcance` - `grupal` | `individual`
- `tipoIndicador` - `calidad` | `productividad` | `cumplimiento` | `flexibilidad` | `sociohumano`
- `frecuencia` - `diaria` | `semanal` | `por_sprint`
- `cardinalidad` - `unico` | `multiple`
- `tipoDato` - `numerico` | `texto` | `booleano` | `escala`
- `activa` (Boolean)
- `formulaTexto` (String)
- `formulaJson` (String/JSONB)
- `frecuenciaCaptura` (String)

**Métricas base del sistema (tabla `metricas`):**
- **Calidad:** Defectos, Errores por sprint, Problemas reportados, Impedimentos, TWQ
- **Productividad:** Satisfacción cliente, Comprensión de roles, Capacidad equipo, Capacidad trabajo, **Velocidad**
- **Cumplimiento:** Establecimiento de metas, Manejo de requisitos
- **Flexibilidad:** Mejorando proceso, Aprendizaje organizacional, Aprendiendo fracasos
- **Sociohumano:** Bienestar, Estado de ánimo

### Entidad: RegistroValor

Representa un valor capturado para una variable en un sprint específico.

**Campos disponibles:**
- `id` (UUID)
- `variableId` (UUID)
- `sprintId` (UUID)
- `userId` (String)
- `valorNum` (BigDecimal) - Valor numérico
- `valorTexto` (String) - Valor textual
- `valorBool` (Boolean) - Valor booleano
- `observacion` (String)
- `registradoAt` (Instant) - Timestamp de registro

**Características:**
- Un usuario puede registrar uno o múltiples valores según `frecuenciaCaptura`
- Un sprint puede tener múltiples registros de diferentes usuarios
- Los valores son de diferentes tipos (num, texto, bool)

### Servicio: EvaluacionService

**Ya calcula:**
- ✅ **Promedio** de valores numéricos por variable por sprint
- ✅ **Mínimo** de valores numéricos por variable por sprint
- ✅ **Máximo** de valores numéricos por variable por sprint
- ✅ **Total de registros** por variable por sprint
- ✅ Información de la fórmula utilizada

---

## 📊 MATRIZ DE VIABILIDAD DE MÉTRICAS AGILE

| Métrica | ¿Calculable? | Datos Utilizados | Limitaciones | Métrica Alternativa |
|---------|--------------|------------------|--------------|---------------------|
| **Velocity** | ✅ **PARCIAL** | Variable con código `PRD-VEL` (Velocidad) si existe y tiene registros | Depende de que el equipo haya configurado una variable de Velocidad y registre story points | Puede usarse cualquier variable de tipo `productividad` como proxy |
| **Throughput** | ✅ **SÍ** | Count de `RegistroValor` por sprint + variable específica | Throughput real depende de qué variables se midan (ej: items completados) | Puede calcularse como total de registros de variables de productividad |
| **Cycle Time** | ❌ **NO** | No existen fechas de inicio/fin de items individuales | MPDIA no rastrea items individuales con estados y timestamps | **NO CALCULABLE** con datos actuales |
| **Lead Time** | ❌ **NO** | No existen fechas de solicitud y entrega de items | MPDIA no rastrea el ciclo completo de items | **NO CALCULABLE** con datos actuales |
| **WIP** | ❌ **NO** | No existen items con estados IN_PROGRESS | MPDIA no rastrea items individuales en progreso | **NO CALCULABLE** con datos actuales |
| **Cumplimiento Sprint** | ✅ **PARCIAL** | Variables de categoría `cumplimiento` + comparación con metas | Depende de que existan variables configuradas para medir cumplimiento | Puede inferirse de variables CMP-EMG, CMP-MR |
| **Tendencias** | ✅ **SÍ** | Histórico de sprints + valores de variables | Requiere mínimo 2 sprints con datos | Comparación sprint N vs sprint N-1 |
| **Anomalías** | ✅ **SÍ** | Histórico de valores + desviación estándar | Requiere mínimo 3-4 sprints para cálculos estadísticos | Detección basada en desviación > 2σ |
| **Comparación Sprints** | ✅ **SÍ** | Datos de 2+ sprints del mismo proyecto | Requiere que ambos sprints tengan datos | Comparación directa de promedios |
| **Productividad Equipo** | ✅ **SÍ** | Variables de tipo `productividad` | Depende de configuración del equipo | Promedio de variables productividad |
| **Calidad Producto** | ✅ **SÍ** | Variables de tipo `calidad` (defectos, errores, TWQ) | Depende de configuración del equipo | Promedio de variables calidad |

---

## 💡 CONCLUSIONES DEL ANÁLISIS

### ✅ MÉTRICAS IMPLEMENTABLES

Las siguientes métricas **SÍ pueden calcularse** con los datos actuales de MPDIA:

1. **Velocity (Adaptada):**
   - Buscar variable con código `PRD-VEL` o cualquier variable de tipo `productividad`
   - Calcular promedio/suma de valores registrados por sprint
   - Comparar entre sprints para identificar tendencias

2. **Throughput (Adaptado):**
   - Contar registros de valores de variables de productividad por sprint
   - Representa "cantidad de trabajo medido" durante el sprint

3. **Promedio de Métricas por Categoría:**
   - Calidad: Promedio de defectos, errores, problemas, TWQ
   - Productividad: Promedio de satisfacción, capacidad, velocidad
   - Cumplimiento: Promedio de establecimiento metas y manejo requisitos
   - Sociohumano: Promedio de bienestar y estado ánimo

4. **Comparación entre Sprints:**
   - Sprint actual vs anterior
   - Variación absoluta y porcentual
   - Dirección de tendencia (UP/DOWN/STABLE)

5. **Tendencias Históricas:**
   - Evolución de una métrica a lo largo de N sprints
   - Identificación de mejoras o empeoramientos

6. **Detección de Anomalías:**
   - Valores significativamente alejados del promedio histórico
   - Basado en desviación estándar

7. **Análisis de Cumplimiento:**
   - Basado en variables de categoría `cumplimiento`
   - Comparación con sprint goal (cualitativo)

### ❌ MÉTRICAS NO IMPLEMENTABLES

Las siguientes métricas **NO pueden calcularse** con los datos actuales:

1. **Cycle Time Tradicional:**
   - **Falta:** Timestamps de inicio y fin de items individuales
   - **Falta:** Estados de items (TODO, IN PROGRESS, DONE)
   - **Alternativa:** Medir duración del sprint completo (fechaInicio - fechaFin)

2. **Lead Time Tradicional:**
   - **Falta:** Fecha de solicitud/creación de items
   - **Falta:** Fecha de entrega al cliente
   - **Alternativa:** NO CALCULABLE

3. **WIP (Work In Progress):**
   - **Falta:** Items individuales con estados
   - **Falta:** Información de qué está en progreso
   - **Alternativa:** NO CALCULABLE

4. **Burndown Chart:**
   - **Falta:** Story points restantes día a día
   - **Falta:** Trabajo planificado vs completado diariamente
   - **Alternativa:** NO CALCULABLE

### 🔄 ESTRATEGIA DE IMPLEMENTACIÓN

#### 1. Reutilizar EvaluacionService

El servicio existente ya calcula:
- Promedio, min, max por variable por sprint
- Agrupación por categoría

**Decisión:** AgileAnalyticsService **orquestará** llamadas a EvaluacionService y agregará:
- Comparaciones entre sprints
- Cálculo de tendencias
- Detección de anomalías
- Análisis de productividad por categoría

#### 2. Métricas Basadas en Variables Configurables

MPDIA es un sistema flexible donde cada equipo configura sus propias variables de medición.

**Implicación:** 
- No todos los proyectos tendrán "Velocidad" configurada
- AgileAnalyticsService debe ser robusto ante ausencia de variables
- Debe poder trabajar con cualquier combinación de variables

#### 3. Fórmulas Determinísticas

Todas las métricas se calcularán de forma determinística:

**Velocity (adaptada):**
```
Velocity = SUMA(valores numéricos de variable PRD-VEL en sprint) 
O bien
Velocity = PROMEDIO(variables de tipo 'productividad' en sprint)
```

**Throughput (adaptado):**
```
Throughput = COUNT(registros de valores de productividad en sprint)
```

**Tendencia:**
```
Tendencia = ((Valor_Sprint_N - Valor_Sprint_N-1) / Valor_Sprint_N-1) * 100
Dirección = UP si > 5%, DOWN si < -5%, STABLE si entre -5% y 5%
```

**Anomalía:**
```
Media = PROMEDIO(últimos N sprints)
StdDev = DESVIACIÓN_ESTÁNDAR(últimos N sprints)
Anomalía = |Valor - Media| > 2 * StdDev
```

#### 4. Manejo de Datos Faltantes

Todas las funciones deben manejar:
- Variables no configuradas → Retornar `Optional.empty()` o DTO con flag `dataAvailable=false`
- Sprints sin datos → Retornar explícitamente "Sin datos suficientes"
- División por cero → Validar denominador antes de dividir
- Primer sprint → No hay comparación previa, indicar explícitamente

---

## 📋 MÉTRICAS A IMPLEMENTAR (FINAL)

Basándome en el análisis, implementaré:

### 1. Métricas por Sprint

✅ **getSprintMetricsSummary(sprintId)**
- Promedio de variables por categoría (reutiliza EvaluacionService)
- Total de registros
- Duración del sprint (fechaInicio - fechaFin)
- Estado del sprint

✅ **getSprintProductivity(sprintId)**
- Variables de tipo `productividad`
- Velocidad si existe variable PRD-VEL
- Capacity metrics

✅ **getSprintQuality(sprintId)**
- Variables de tipo `calidad`
- Defectos, errores, TWQ si existen

### 2. Comparación y Tendencias

✅ **compareSprints(sprintId1, sprintId2)**
- Comparación lado a lado de todas las métricas
- Variación absoluta y porcentual
- Indicador de mejora/empeoramiento

✅ **getSprintTrends(proyectoId, numberOfSprints)**
- Tendencias de últimos N sprints
- Por cada categoría de métrica
- Dirección (UP/DOWN/STABLE)

✅ **getHistoricalData(proyectoId, metricCode)**
- Evolución histórica de una métrica específica
- Datos para gráficos

### 3. Detección de Problemas

✅ **detectAnomalies(sprintId)**
- Valores anómalos basados en desviación estándar
- Severidad de la anomalía
- Evidencia (valor actual vs promedio histórico)

✅ **identifyRisks(proyectoId)**
- Detección de señales de riesgo:
  - Tendencias negativas sostenidas
  - Valores de calidad deteriorados
  - Métricas de productividad disminuyendo
- Severidad: LOW, MEDIUM, HIGH, CRITICAL

### 4. Análisis Agregado

✅ **getProjectOverview(proyectoId)**
- Resumen del proyecto completo
- Todos los sprints finalizados
- Métricas promedio
- Sprint con mejor/peor desempeño

---

## 🚧 LIMITACIONES DOCUMENTADAS

1. **No hay Cycle Time real:**
   - MPDIA no rastrea items individuales con timestamps
   - Alternativa: Duración total del sprint

2. **No hay WIP:**
   - MPDIA no tiene concepto de items "en progreso"
   - Alternativa: NO CALCULABLE

3. **Velocity depende de configuración:**
   - Solo calculable si el equipo configuró una variable de velocidad
   - Fallback: Usar promedio de variables de productividad

4. **Throughput adaptado:**
   - No es "items completados" tradicional
   - Es "cantidad de mediciones de productividad registradas"

5. **Métricas dependientes del equipo:**
   - Cada proyecto define sus propias variables
   - Analytics debe ser flexible y robusto ante cualquier configuración

---

## ✅ PRÓXIMO PASO

**Implementar AgileAnalyticsService con las métricas viables identificadas.**

Cada método documentará claramente:
- Qué calcula
- Qué datos utiliza
- Qué limitaciones tiene
- Qué retorna cuando no hay datos suficientes
