# PLAN DE IMPLEMENTACIÓN: AI AGILE COPILOT PARA MPDIA

**Autor:** Análisis realizado por Kiro AI Assistant  
**Fecha:** Enero 2025  
**Proyecto:** MPDIA - Sistema de Medición de Productividad para Equipos Ágiles  
**Objetivo:** Transformar MPDIA en una plataforma inteligente de análisis de productividad Agile con AI Copilot

---

## 📋 DIAGNÓSTICO DE ARQUITECTURA ACTUAL

### Stack Tecnológico Identificado

**Backend:**
- Spring Boot 3.2.5
- Java 17
- PostgreSQL + Flyway migrations
- Spring Security + JWT (HS256)
- Spring Data JPA
- Lombok
- Ya existe integración con Google Gemini AI (`GeminiService`)

**Frontend:**
- Angular 17
- Bootstrap 5 + Bootstrap Icons
- RxJS
- Standalone Components

**Base de Datos:**
- PostgreSQL (puerto 5433)
- Esquema gestionado con Flyway

### Entidades Principales Identificadas

```
AppUser (usuarios con roles: scrum_master, scrum_member)
  ↓
Proyecto (proyectos ágiles con método, timeBox, goals)
  ↓
ProjectMember (membresía por proyecto - autorización)
  ↓
Sprint (múltiples sprints con estados: pendiente, en_ejecucion, finalizado)
  ↓
Variable (métricas configuradas con fórmulas)
  ↓
RegistroValor (valores capturados durante sprints)
```

**Relaciones Clave:**
- Un `Proyecto` tiene múltiples `Sprint`
- Un `Proyecto` tiene múltiples `ProjectMember`
- Un `Sprint` pertenece a un `Proyecto`
- Una `Variable` pertenece a un `Proyecto` y a una `Metrica`
- Los `RegistroValor` están asociados a `Variable`, `Sprint` y `userId`

### Controladores Existentes

```
✅ AuthController          → Autenticación JWT
✅ ProyectoController      → CRUD proyectos
✅ ProjectMemberController → Gestión de equipos por proyecto
✅ SprintController        → Gestión de sprints
✅ MetricaController       → Métricas base del sistema
✅ VariableController      → Variables de métricas
✅ EjecucionController     → Registro de valores
✅ EvaluacionController    → Evaluación de sprints
✅ CopilotController       → Configuración de copiloto (Jira/GitHub)
✅ GeminiService           → Ya existe integración con Gemini AI
```

### Servicios Existentes que Reutilizaremos

```java
✅ SprintService           → listarSprints(), getSprintActivo(), cerrarEIniciarSiguiente()
✅ EvaluacionService       → evaluar(), evaluarSprint() 
✅ EjecucionService        → listarPorSprint(), listarPorVariable()
✅ ProjectMemberService    → proyectosDelUsuario(), listarMiembros()
✅ ProyectoService         → getProyecto()
✅ GeminiService           → generate() - llama a Gemini API
```

### Sistema de Autorización Actual

- JWT con `userId`, `email`, `role` en el token
- Roles: `scrum_master` y `scrum_member`
- Membresía por proyecto mediante `ProjectMember`
- **IMPORTANTE:** Validar que usuario pertenezca al proyecto antes de dar acceso a datos

### Métricas Actualmente Calculadas

El sistema actualmente calcula (ver `EvaluacionService.evaluar()`):
- ✅ **Promedio** por variable por sprint
- ✅ **Mínimo** por variable por sprint
- ✅ **Máximo** por variable por sprint
- ✅ **Total de registros** por variable por sprint
- ✅ Información de **fórmula** utilizada
- ✅ **Frecuencia de captura** de datos

**LIMITACIONES ACTUALES:**
- ❌ No calcula métricas Agile estándar (velocity, cycle time, WIP, throughput)
- ❌ No detecta tendencias ni anomalías
- ❌ No identifica riesgos automáticamente
- ❌ No compara sprints
- ❌ No genera insights ni recomendaciones
- ❌ La interfaz solo muestra datos tabulares, sin análisis inteligente

---

## 🎯 ARQUITECTURA OBJETIVO

### Flujo de Datos con AI Copilot

```
┌─────────────────────────────────────────────────────────────────┐
│                      ANGULAR FRONTEND                            │
│                                                                   │
│  ┌─────────────┐  ┌──────────────────┐  ┌──────────────────┐   │
│  │  Dashboard  │  │  AI Copilot Chat │  │  AI Insights     │   │
│  │  + Metrics  │  │  Component       │  │  Cards           │   │
│  └─────────────┘  └──────────────────┘  └──────────────────┘   │
│         │                  │                      │              │
└─────────┼──────────────────┼──────────────────────┼──────────────┘
          │                  │                      │
          ▼                  ▼                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                    SPRING BOOT BACKEND                           │
│                                                                   │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │          AI AGILE COPILOT CONTROLLER                      │  │
│  │  /api/ai-copilot/chat                                     │  │
│  │  /api/ai-copilot/analyze-sprint                           │  │
│  │  /api/ai-copilot/insights                                 │  │
│  │  /api/ai-copilot/report                                   │  │
│  └───────────────────────────────────────────────────────────┘  │
│                           │                                      │
│                           ▼                                      │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │        AI AGILE COPILOT SERVICE                           │  │
│  │  - Gestiona contexto del usuario                          │  │
│  │  - Valida autorizaciones                                  │  │
│  │  - Orquesta llamadas a servicios existentes               │  │
│  │  - Estructura prompts para la IA                          │  │
│  │  - Maneja conversaciones y historial                      │  │
│  └───────────────────────────────────────────────────────────┘  │
│         │                                                         │
│         ▼                                                         │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │        OPENAI / GEMINI SERVICE                            │  │
│  │  - Integración con OpenAI GPT-4o (preferido)             │  │
│  │  - O continuar con Gemini 2.5 Flash (existente)          │  │
│  │  - Implementa Function Calling / Tool Use                 │  │
│  └───────────────────────────────────────────────────────────┘  │
│         │                                                         │
│         ▼                                                         │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │        AGILE ANALYTICS SERVICE (NUEVO)                    │  │
│  │  Calcula métricas Agile determinísticas:                  │  │
│  │  - Velocity (story points por sprint)                     │  │
│  │  - Throughput (items completados)                         │  │
│  │  - Cycle Time (tiempo de completar items)                 │  │
│  │  - Lead Time                                              │  │
│  │  - WIP (work in progress)                                 │  │
│  │  - Cumplimiento de sprint (%)                             │  │
│  │  - Tendencias (comparación entre sprints)                 │  │
│  │  - Detección de anomalías básicas                         │  │
│  └───────────────────────────────────────────────────────────┘  │
│         │                                                         │
│         ▼                                                         │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │     SERVICIOS EXISTENTES REUTILIZADOS                     │  │
│  │  - SprintService                                          │  │
│  │  - EvaluacionService                                      │  │
│  │  - EjecucionService                                       │  │
│  │  - VariableService                                        │  │
│  │  - ProjectMemberService                                   │  │
│  │  - ProyectoService                                        │  │
│  └───────────────────────────────────────────────────────────┘  │
│         │                                                         │
└─────────┼─────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    POSTGRESQL DATABASE                           │
│  - app_users, proyectos, project_members                         │
│  - sprints, variables, registro_valores                          │
│  - metricas, metric_parametrizacion                              │
│  - Nueva tabla: ai_conversations (historial)                     │
│  - Nueva tabla: ai_insights (insights generados)                 │
└─────────────────────────────────────────────────────────────────┘
```

### Principios de Diseño

1. **Separación de Responsabilidades:**
   - Backend calcula métricas determinísticas
   - IA interpreta resultados y genera recomendaciones
   - IA NO accede directamente a la BD

2. **Autorización Estricta:**
   - Todas las herramientas validan membresía del usuario
   - Usuario solo ve datos de proyectos a los que pertenece

3. **Reducción de Alucinaciones:**
   - IA solo recibe datos estructurados del backend
   - Backend proporciona hechos, IA proporciona interpretación
   - Instrucciones explícitas: "No inventes datos"

4. **Extensibilidad:**
   - Arquitectura preparada para agregar actions futuras
   - Posibilidad de integrar RAG posteriormente
   - Tools escalables y modulares

---

## 📂 ARCHIVOS A CREAR/MODIFICAR

### FASE 1: Backend - Nuevas Entidades y Repositorios

#### Crear Nuevas Entidades

```
✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/entity/AIChatMessage.java
   - Historial de mensajes del chat con la IA
   - Campos: id, userId, proyectoId, sprintId, role (user/assistant), content, timestamp

✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/entity/AIInsight.java
   - Insights generados automáticamente por la IA
   - Campos: id, proyectoId, sprintId, tipo, titulo, descripcion, impacto, confianza, 
             recomendacion, dismissed, createdAt

✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/repository/AIChatMessageRepository.java
✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/repository/AIInsightRepository.java
```

#### Migración de Base de Datos

```
✨ CREAR: mpdia-springboot/src/main/resources/db/migration/V17__ai_copilot.sql
   - Tabla: ai_chat_messages
   - Tabla: ai_insights
   - Índices apropiados
```

### FASE 2: Backend - Servicios de Análisis

#### Nuevo Servicio de Analytics Agile

```
✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/service/AgileAnalyticsService.java
   Métodos:
   - calcularVelocity(proyectoId, numberOfSprints)
   - calcularThroughput(sprintId)
   - calcularCycleTime(sprintId)
   - calcularWIP(sprintId)
   - compararSprints(sprintId1, sprintId2)
   - detectarTendencias(proyectoId, numberOfSprints)
   - detectarAnomalias(sprintId)
   - calcularCumplimientoSprint(sprintId)
   - obtenerHistorialSprints(proyectoId, limit)
```

#### Nuevo Servicio de IA

```
✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/service/AIAgentService.java
   Responsabilidades:
   - Integración con OpenAI API (function calling)
   - Gestión de contexto de conversación
   - Registro de herramientas (tools)
   - Ejecución de herramientas y devolución de resultados

✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/service/AICopilotService.java
   Responsabilidades:
   - Orquesta interacciones del usuario con la IA
   - Valida autorizaciones antes de cada tool call
   - Mantiene historial de conversaciones
   - Genera insights automáticos
   - Formatea respuestas estructuradas
```

#### Servicio de Tools/Functions

```
✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/service/copilot/CopilotToolsService.java
   Herramientas disponibles para la IA:
   - getCurrentUser()
   - getUserProjects()  
   - getProjectDetails(proyectoId)
   - getTeamMembers(proyectoId)
   - getActiveSprintMetrics(proyectoId)
   - getSprintMetrics(sprintId)
   - getSprintHistory(proyectoId, numberOfSprints)
   - compareSprints(sprintId1, sprintId2)
   - getVelocityTrend(proyectoId, numberOfSprints)
   - getThroughputData(proyectoId, numberOfSprints)
   - getCycleTimeAnalysis(sprintId)
   - getVariableValues(sprintId, variableId)
   - detectRisks(proyectoId)
   - generateSprintReport(sprintId)
   - getRetrospectiveInsights(sprintId)
```

### FASE 3: Backend - DTOs

```
✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/dto/ai/ChatRequest.java
✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/dto/ai/ChatResponse.java
✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/dto/ai/ChatMessageDto.java
✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/dto/ai/InsightDto.java
✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/dto/ai/AnalyzeSprintRequest.java
✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/dto/ai/SprintAnalysisResponse.java
✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/dto/ai/GenerateReportRequest.java
✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/dto/ai/ReportResponse.java

✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/dto/analytics/VelocityDto.java
✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/dto/analytics/ThroughputDto.java
✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/dto/analytics/CycleTimeDto.java
✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/dto/analytics/SprintComparisonDto.java
✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/dto/analytics/TrendAnalysisDto.java
✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/dto/analytics/RiskDto.java
```

### FASE 4: Backend - Controladores

```
✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/controller/AICopilotController.java
   Endpoints:
   POST   /api/ai-copilot/chat                 → Enviar mensaje al copilot
   GET    /api/ai-copilot/history              → Obtener historial de chat
   DELETE /api/ai-copilot/history              → Limpiar historial
   POST   /api/ai-copilot/analyze-sprint       → Análisis profundo de sprint
   GET    /api/ai-copilot/insights             → Insights automáticos
   POST   /api/ai-copilot/insights/{id}/dismiss → Descartar insight
   POST   /api/ai-copilot/report/sprint        → Generar reporte de sprint
   POST   /api/ai-copilot/report/retrospective → Generar reporte retrospectiva

✨ CREAR: mpdia-springboot/src/main/java/com/mpdia/controller/AgileAnalyticsController.java
   Endpoints (para debug y visualización directa):
   GET /api/analytics/velocity/{proyectoId}
   GET /api/analytics/throughput/{sprintId}
   GET /api/analytics/cycle-time/{sprintId}
   GET /api/analytics/compare/{sprintId1}/{sprintId2}
   GET /api/analytics/trends/{proyectoId}
```

### FASE 5: Backend - Configuración

```
✨ MODIFICAR: mpdia-springboot/src/main/resources/application.properties.example
   Agregar:
   # OpenAI Configuration (preferido)
   mpdia.openai.api-key=sk-...
   mpdia.openai.api-url=https://api.openai.com/v1/chat/completions
   mpdia.openai.model=gpt-4o
   mpdia.openai.max-tokens=4000
   mpdia.openai.temperature=0.7
   
   # O continuar usando Gemini (ya configurado)
   mpdia.gemini.api-key=...
   mpdia.gemini.model=gemini-2.5-flash

   # AI Copilot Settings
   mpdia.ai.provider=openai   # openai | gemini
   mpdia.ai.max-history-messages=20
   mpdia.ai.enable-insights=true
```

### FASE 6: Frontend - Modelos

```
✨ CREAR: mpdia-angular/src/app/models/ai-copilot.model.ts
   Interfaces:
   - ChatMessage
   - ChatRequest
   - ChatResponse
   - AIInsight
   - SprintAnalysis
   - ReportResponse
```

### FASE 7: Frontend - Servicios

```
✨ CREAR: mpdia-angular/src/app/services/ai-copilot.service.ts
   Métodos:
   - sendMessage(message, proyectoId, sprintId?)
   - getHistory(proyectoId)
   - clearHistory()
   - analyzeSprint(sprintId)
   - getInsights(proyectoId)
   - dismissInsight(insightId)
   - generateSprintReport(sprintId)
   - generateRetrospectiveReport(sprintId)

✨ CREAR: mpdia-angular/src/app/services/agile-analytics.service.ts
   Métodos:
   - getVelocity(proyectoId)
   - getThroughput(sprintId)
   - getCycleTime(sprintId)
   - compareSprints(sprintId1, sprintId2)
   - getTrends(proyectoId)
```

### FASE 8: Frontend - Componentes

```
✨ CREAR: mpdia-angular/src/app/components/ai-copilot/
   ├── ai-copilot-button.component.ts       → Botón flotante para abrir copilot
   ├── ai-copilot-button.component.html
   ├── ai-copilot-panel.component.ts        → Panel/modal del chat
   ├── ai-copilot-panel.component.html
   ├── ai-copilot-panel.component.css
   ├── ai-message.component.ts              → Componente para mensaje individual
   ├── ai-message.component.html
   └── ai-insight-card.component.ts         → Card de insight con botones
       └── ai-insight-card.component.html

✨ CREAR: mpdia-angular/src/app/pages/ai-insights/
   ├── ai-insights.component.ts             → Página de insights del proyecto
   └── ai-insights.component.html
```

### FASE 9: Frontend - Integración con Componentes Existentes

```
✨ MODIFICAR: mpdia-angular/src/app/layout/shell/shell.component.ts
   - Agregar <app-ai-copilot-button> en el template
   - Visible en todas las páginas cuando haya proyecto activo

✨ MODIFICAR: mpdia-angular/src/app/app.routes.ts
   - Agregar ruta: /ai-insights

✨ MODIFICAR: mpdia-angular/src/app/layout/sidebar/sidebar.component.ts
   - Agregar enlace a "AI Insights" en el menú

✨ MODIFICAR (OPCIONAL): mpdia-angular/src/app/pages/dashboard/dashboard.component.ts
   - Si existe dashboard, mostrar tarjetas de AI Insights
   - Botones "Analizar con IA" en secciones relevantes

✨ MODIFICAR: mpdia-angular/src/app/pages/evaluacion/evaluacion.component.ts
   - Agregar botón "Generar Reporte con IA" en la evaluación de sprint
```

### FASE 10: Testing

```
✨ CREAR: mpdia-springboot/src/test/java/com/mpdia/service/AgileAnalyticsServiceTest.java
✨ CREAR: mpdia-springboot/src/test/java/com/mpdia/service/AICopilotServiceTest.java
✨ CREAR: mpdia-angular/src/app/services/ai-copilot.service.spec.ts
✨ CREAR: mpdia-angular/src/app/components/ai-copilot/ai-copilot-panel.component.spec.ts
```

---

## 🛠️ IMPLEMENTACIÓN DETALLADA POR FASE

### FASE 1: Migración de Base de Datos y Entidades

**Objetivo:** Crear las tablas necesarias para almacenar historial de chat e insights

**Archivos:**
1. V17__ai_copilot.sql
2. AIChatMessage.java
3. AIInsight.java
4. AIChatMessageRepository.java
5. AIInsightRepository.java

**Dependencias:** Ninguna

---

### FASE 2: Servicio de Analytics Agile

**Objetivo:** Implementar el cálculo determinístico de métricas Agile

**Archivos:**
1. AgileAnalyticsService.java
2. DTOs de analytics (VelocityDto, ThroughputDto, etc.)

**Reutiliza:**
- SprintService (para obtener sprints)
- EvaluacionService (para obtener valores de variables)
- VariableRepository, RegistroValorRepository

**Lógica Clave:**

Para calcular métricas Agile, el servicio debe:
- Obtener todos los sprints finalizados del proyecto
- Calcular variables agregadas por sprint
- Detectar patrones y anomalías

Ejemplo: **Velocity** = Promedio de "story points completados" de los últimos N sprints

---

### FASE 3: Integración con OpenAI/Gemini + Function Calling

**Objetivo:** Implementar el servicio de IA con capacidad de llamar tools

**Archivos:**
1. AIAgentService.java
2. AICopilotService.java
3. CopilotToolsService.java

**Decisión Técnica:**
- **Opción A (Recomendada):** Usar OpenAI GPT-4o con function calling nativo
- **Opción B:** Extender GeminiService existente para soportar function calling

**Flujo de Conversación:**
```
Usuario → AICopilotService.chat(mensaje, userId, proyectoId)
    ↓
Validar autorización (ProjectMemberService)
    ↓
Construir contexto de sistema (system prompt)
    ↓
AIAgentService.sendMessage(mensajes, tools)
    ↓
OpenAI devuelve respuesta O function_call
    ↓
Si function_call: CopilotToolsService.executeTool(toolName, args)
    ↓
Devolver resultado a OpenAI
    ↓
Respuesta final al usuario
    ↓
Guardar historial en ai_chat_messages
```

**System Prompt Base:**
```
Eres un Agile Coach experto especializado en análisis de productividad de equipos Scrum/XP.

Tu objetivo es ayudar al equipo a:
- Analizar métricas de sprints
- Identificar problemas y riesgos
- Generar recomendaciones basadas en datos
- Preparar retrospectivas efectivas

REGLAS CRÍTICAS:
1. NUNCA inventes datos. Solo usa información de las herramientas.
2. Diferencia hechos de inferencias.
3. Si no tienes suficiente información, dilo explícitamente.
4. Cita las métricas que usaste en tu análisis.
5. Responde en español.
6. Estructura tus respuestas: Resumen → Datos → Hallazgos → Recomendaciones

El usuario actual pertenece al proyecto: {proyectoNombre}
Sprint activo: Sprint {numeroSprint} - {sprintGoal}
```

---

### FASE 4: Controllers + DTOs

**Objetivo:** Exponer endpoints REST para el frontend

**Endpoints Principales:**

#### POST /api/ai-copilot/chat
```json
Request:
{
  "message": "¿Cómo estuvo el último sprint?",
  "proyectoId": "uuid",
  "sprintId": "uuid" (opcional)
}

Response:
{
  "message": "El Sprint 3 mostró una disminución del 15% en velocity...",
  "timestamp": "2025-01-15T10:30:00Z",
  "toolsUsed": ["getSprintMetrics", "compareSprints"],
  "sources": [
    {
      "tool": "getSprintMetrics",
      "data": { "sprintId": "...", "velocity": 25 }
    }
  ]
}
```

#### GET /api/ai-copilot/insights?proyectoId=uuid
```json
Response:
[
  {
    "id": "uuid",
    "tipo": "RIESGO",
    "titulo": "Cycle Time en aumento",
    "descripcion": "El cycle time aumentó 23% en los últimos 3 sprints",
    "impacto": "MEDIO",
    "confianza": "ALTA",
    "recomendacion": "Revisar elementos bloqueados y reducir WIP",
    "dismissed": false,
    "createdAt": "2025-01-15T09:00:00Z"
  }
]
```

---

### FASE 5-9: Frontend (Resumen Técnico)

**Componentes Clave:**

#### 1. AI Copilot Button (Botón Flotante)
- Posición: fixed, bottom-right
- Icono: robot o sparkles
- Badge con contador de insights no leídos
- Visible solo si usuario tiene proyecto activo

#### 2. AI Copilot Panel (Chat)
- Modal o sidebar deslizable
- Historial de mensajes con scroll
- Input con botón enviar
- Indicador "IA está escribiendo..."
- Botones rápidos: "Analizar sprint actual", "Generar reporte"
- Opción de limpiar historial

#### 3. AI Insight Card
- Card Bootstrap con ícono según tipo
- Colores según impacto (success/warning/danger)
- Badge de confianza
- Botón "Descartar"
- Botón "Analizar con IA" → Abre copilot con contexto

#### 4. Página AI Insights
- Lista filtrable de insights
- Filtros: tipo, impacto, dismissed
- Posibilidad de exportar
- Gráfico de tendencias (opcional)

**Integración en Shell:**
```typescript
// shell.component.ts
showAICopilot: boolean = false;
proyectoActual: string | null = null;

ngOnInit() {
  // Detectar si hay proyecto activo
  this.proyectoService.proyectoActual$.subscribe(p => {
    this.proyectoActual = p?.id || null;
    this.showAICopilot = !!this.proyectoActual;
  });
}
```

```html
<!-- shell.component.html -->
<app-ai-copilot-button 
  *ngIf="showAICopilot" 
  [proyectoId]="proyectoActual">
</app-ai-copilot-button>
```

---

## 🔐 SEGURIDAD Y MEJORES PRÁCTICAS

### Protección de API Keys

```properties
# ❌ NUNCA en application.properties committed
mpdia.openai.api-key=sk-actual-key

# ✅ Solo en application.properties.example
mpdia.openai.api-key=sk-YOUR_OPENAI_KEY_HERE

# ✅ Usar variables de entorno en producción
mpdia.openai.api-key=${OPENAI_API_KEY}
```

### Autorización en Tools

```java
// SIEMPRE validar antes de ejecutar tool
public Object executeTool(String toolName, Map<String, Object> args, String userId) {
    UUID proyectoId = UUID.fromString((String) args.get("proyectoId"));
    
    // Validar que usuario pertenece al proyecto
    if (!projectMemberRepo.existsByProyectoIdAndUserId(proyectoId, userId)) {
        throw new SecurityException("No tienes acceso a este proyecto");
    }
    
    // Ejecutar tool
    return switch (toolName) {
        case "getSprintMetrics" -> getSprintMetrics(args);
        // ...
        default -> throw new IllegalArgumentException("Tool desconocido");
    };
}
```

### Rate Limiting

```java
// Implementar límite de requests por usuario
@Service
public class RateLimitService {
    private final Map<String, AtomicInteger> userRequests = new ConcurrentHashMap<>();
    
    public void checkRateLimit(String userId) {
        int count = userRequests.computeIfAbsent(userId, k -> new AtomicInteger(0))
                                .incrementAndGet();
        if (count > 50) { // 50 mensajes por hora
            throw new TooManyRequestsException("Límite de requests excedido");
        }
    }
    
    @Scheduled(fixedRate = 3600000) // 1 hora
    public void resetCounters() {
        userRequests.clear();
    }
}
```

### Sanitización de Inputs

```java
public String sanitizeInput(String userMessage) {
    if (userMessage == null || userMessage.isBlank()) {
        throw new IllegalArgumentException("Mensaje vacío");
    }
    
    // Limitar longitud
    if (userMessage.length() > 2000) {
        throw new IllegalArgumentException("Mensaje demasiado largo");
    }
    
    // Eliminar caracteres potencialmente peligrosos
    return userMessage.trim()
                     .replaceAll("[<>]", "")
                     .substring(0, Math.min(2000, userMessage.length()));
}
```

---

## 📊 OBSERVABILIDAD Y LOGGING

### Logging Estructurado

```java
@Slf4j
@Service
public class AICopilotService {
    
    public ChatResponse chat(ChatRequest req, String userId) {
        log.info("AI Chat Request - User: {}, Proyecto: {}, Message length: {}", 
                 userId, req.proyectoId(), req.message().length());
        
        Instant start = Instant.now();
        
        try {
            ChatResponse response = processChat(req, userId);
            
            long duration = Duration.between(start, Instant.now()).toMillis();
            log.info("AI Chat Success - Duration: {}ms, Tools used: {}", 
                     duration, response.toolsUsed());
            
            return response;
            
        } catch (Exception e) {
            log.error("AI Chat Error - User: {}, Proyecto: {}, Error: {}", 
                     userId, req.proyectoId(), e.getMessage(), e);
            throw e;
        }
    }
}
```

### Métricas de Uso

```java
// Tabla opcional para analytics
@Entity
@Table(name = "ai_usage_metrics")
public class AIUsageMetric {
    @Id @GeneratedValue
    private UUID id;
    
    private String userId;
    private UUID proyectoId;
    private String operation; // chat | analyze | report
    private Long durationMs;
    private Integer tokensUsed;
    private Boolean success;
    private Instant timestamp;
}
```

---

## 🧪 ESTRATEGIA DE TESTING

### Tests Unitarios - Backend

```java
@SpringBootTest
class AICopilotServiceTest {
    
    @Mock private AIAgentService agentService;
    @Mock private ProjectMemberService memberService;
    @Mock private CopilotToolsService toolsService;
    
    @InjectMocks private AICopilotService copilotService;
    
    @Test
    void chat_DebeValidarAutorizacion() {
        // Given
        String userId = "user-123";
        UUID proyectoId = UUID.randomUUID();
        when(memberService.isMember(proyectoId, userId)).thenReturn(false);
        
        // When & Then
        assertThrows(SecurityException.class, () -> 
            copilotService.chat(new ChatRequest("test", proyectoId, null), userId)
        );
    }
    
    @Test
    void chat_DebeGuardarHistorial() {
        // Given
        String userId = "user-123";
        UUID proyectoId = UUID.randomUUID();
        when(memberService.isMember(proyectoId, userId)).thenReturn(true);
        when(agentService.sendMessage(any(), any())).thenReturn("Respuesta IA");
        
        // When
        ChatResponse response = copilotService.chat(
            new ChatRequest("Analiza el sprint", proyectoId, null), 
            userId
        );
        
        // Then
        assertNotNull(response.message());
        verify(chatMessageRepo, times(2)).save(any()); // user + assistant
    }
}
```

### Tests de Integración - API

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AICopilotControllerIntegrationTest {
    
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ProjectMemberRepository memberRepo;
    
    private String authToken;
    
    @BeforeEach
    void setup() {
        // Setup test data and auth token
        authToken = getAuthToken("test@mpdia.com", "password");
    }
    
    @Test
    void chat_ConTokenValido_DebeRetornarRespuesta() {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        ChatRequest request = new ChatRequest("Hola", testProyectoId, null);
        HttpEntity<ChatRequest> entity = new HttpEntity<>(request, headers);
        
        // When
        ResponseEntity<ChatResponse> response = restTemplate.exchange(
            "/api/ai-copilot/chat",
            HttpMethod.POST,
            entity,
            ChatResponse.class
        );
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().message());
    }
}
```

### Tests E2E - Frontend

```typescript
// ai-copilot-panel.component.spec.ts
describe('AICopilotPanelComponent', () => {
  let component: AICopilotPanelComponent;
  let fixture: ComponentFixture<AICopilotPanelComponent>;
  let copilotService: jasmine.SpyObj<AiCopilotService>;
  
  beforeEach(() => {
    const spy = jasmine.createSpyObj('AiCopilotService', ['sendMessage', 'getHistory']);
    
    TestBed.configureTestingModule({
      imports: [AICopilotPanelComponent],
      providers: [
        { provide: AiCopilotService, useValue: spy }
      ]
    });
    
    copilotService = TestBed.inject(AiCopilotService) as jasmine.SpyObj<AiCopilotService>;
    fixture = TestBed.createComponent(AICopilotPanelComponent);
    component = fixture.componentInstance;
  });
  
  it('debe enviar mensaje y mostrar respuesta', fakeAsync(() => {
    // Given
    const mockResponse: ChatResponse = {
      message: 'Respuesta de prueba',
      timestamp: new Date().toISOString(),
      toolsUsed: []
    };
    copilotService.sendMessage.and.returnValue(of(mockResponse));
    
    // When
    component.userMessage = 'Hola';
    component.sendMessage();
    tick();
    
    // Then
    expect(component.messages.length).toBe(2); // user + assistant
    expect(component.messages[1].content).toBe('Respuesta de prueba');
    expect(component.isLoading).toBeFalse();
  }));
});
```

---

## 🚀 PLAN DE DESPLIEGUE

### Checklist Pre-Deployment

```
Backend:
☐ Configurar OPENAI_API_KEY en variables de entorno
☐ Ejecutar migraciones (V17__ai_copilot.sql)
☐ Configurar rate limiting en producción
☐ Configurar logs (nivel INFO)
☐ Verificar que application.properties NO contiene keys hardcodeadas
☐ Tests pasan (mvn test)

Frontend:
☐ Build de producción (ng build --configuration production)
☐ Verificar que environment.prod.ts apunta a API correcta
☐ Tests pasan (ng test --watch=false)

Database:
☐ Backup antes de migrar
☐ Verificar espacio en disco para nuevas tablas
☐ Índices creados correctamente

Monitoreo:
☐ Logs configurados (CloudWatch, Datadog, etc.)
☐ Alertas para errores críticos
☐ Dashboard de uso de AI (opcional)
```

### Rollback Plan

```sql
-- Si necesitas revertir la migración V17
-- CUIDADO: perderás el historial de chat e insights

DROP TABLE IF EXISTS ai_insights;
DROP TABLE IF EXISTS ai_chat_messages;

-- Revertir en Flyway metadata
DELETE FROM flyway_schema_history WHERE version = '17';
```

---

## 📈 ROADMAP FUTURO

### Fase 2 - Funcionalidades Avanzadas

1. **RAG (Retrieval Augmented Generation)**
   - Integrar vector database (Pinecone, Weaviate)
   - Indexar documentación interna del equipo
   - Indexar conocimiento Agile (Scrum Guide, etc.)
   - Tool: `searchKnowledgeBase(query)`

2. **Actions (Modificar Datos)**
   - `createTask(titulo, descripcion, sprintId)`
   - `updateSprintGoal(sprintId, newGoal)`
   - `createRetrospectiveItem(sprintId, tipo, descripcion)`
   - **Importante:** Requiere confirmación explícita del usuario

3. **Integraciones Externas**
   - Tool: `getJiraIssues(sprintId)` via API
   - Tool: `getGitHubCommits(repoId, dateRange)`
   - Tool: `getSlackMessages(channelId, dateRange)`
   - Correlacionar actividad de desarrollo con métricas

4. **Alertas Proactivas**
   - Scheduler diario que ejecuta análisis
   - Genera insights automáticamente
   - Envía notificaciones (email, Slack)
   - Ejemplo: "Sprint en riesgo: WIP alto detectado"

5. **Dashboards Inteligentes**
   - Gráficos generados por IA
   - Comparaciones automáticas
   - Predicciones de velocity
   - Recomendaciones visuales

6. **Multi-idioma**
   - Detectar idioma del usuario
   - Responder en el idioma correspondiente
   - Soporte para equipos internacionales

---

## 🎯 CRITERIOS DE ACEPTACIÓN FINAL

Marca cuando cada criterio esté cumplido:

### Funcionalidad Core
- [ ] Usuario autenticado puede abrir AI Copilot desde cualquier página
- [ ] Usuario puede enviar mensaje y recibir respuesta de IA
- [ ] IA puede consultar datos reales de MPDIA mediante tools
- [ ] IA respeta permisos (solo datos del proyecto del usuario)
- [ ] Historial de chat se persiste y recupera correctamente

### Análisis de Sprints
- [ ] IA puede analizar sprint actual: "Analiza el sprint actual"
- [ ] IA puede comparar sprints: "Compara los últimos 3 sprints"
- [ ] IA identifica tendencias: "¿Cómo ha evolucionado la velocity?"
- [ ] IA detecta problemas: "¿Qué problemas ves en el equipo?"
- [ ] IA genera recomendaciones basadas en datos reales

### Reportes y Retrospectivas
- [ ] IA genera reporte de sprint en formato estructurado
- [ ] IA genera preguntas para retrospectiva
- [ ] IA identifica áreas de mejora con evidencia
- [ ] Reportes se pueden copiar o exportar

### Insights Automáticos
- [ ] Sistema genera insights automáticamente (diario/semanal)
- [ ] Insights se muestran como cards en interfaz
- [ ] Usuario puede descartar insights
- [ ] Usuario puede "Analizar con IA" desde un insight

### Seguridad
- [ ] API key NO está expuesta en frontend
- [ ] API key NO está en código versionado (Git)
- [ ] Autorización validada en cada tool call
- [ ] Usuario solo ve datos de sus proyectos
- [ ] Rate limiting funciona correctamente

### Calidad de IA
- [ ] IA NO inventa datos
- [ ] IA diferencia hechos de inferencias
- [ ] IA cita fuentes/métricas usadas
- [ ] IA responde en español
- [ ] IA dice "no tengo suficiente información" cuando corresponde

### Performance
- [ ] Respuesta de IA en menos de 10 segundos (promedio)
- [ ] Interfaz no se congela durante procesamiento
- [ ] Historial carga rápido (< 2 segundos)
- [ ] Métricas se calculan eficientemente

### UX/UI
- [ ] Botón flotante bien posicionado y visible
- [ ] Panel de chat usable y responsive
- [ ] Indicador "IA escribiendo..." funciona
- [ ] Mensajes formateados correctamente (Markdown)
- [ ] Errores se muestran amigablemente

### Testing
- [ ] Tests unitarios backend (>70% coverage)
- [ ] Tests unitarios frontend (>60% coverage)
- [ ] Tests de integración para endpoints críticos
- [ ] Tests E2E para flujo principal de chat

### Documentación
- [ ] README con instrucciones de configuración
- [ ] Documentación de API (endpoints)
- [ ] Guía de usuario básica
- [ ] Este plan de implementación completo

---

## 📝 NOTAS FINALES

### Estimaciones de Esfuerzo

| Fase | Descripción | Tiempo Estimado |
|------|-------------|-----------------|
| 1 | BD + Entidades | 30 min |
| 2 | AgileAnalyticsService | 2 horas |
| 3 | Integración OpenAI + Tools | 3 horas |
| 4 | Controllers + DTOs | 1.5 horas |
| 5 | Config + Properties | 15 min |
| 6 | Frontend: Modelos | 20 min |
| 7 | Frontend: Servicios | 40 min |
| 8 | Frontend: Componentes Chat | 3 horas |
| 9 | Frontend: Integración Shell | 1 hora |
| 10 | Testing | 2 horas |
| 11 | Ajustes y Debugging | 2 horas |
| **TOTAL** | | **~16 horas** |

### Dependencias Externas

```xml
<!-- pom.xml - Agregar si usas OpenAI Java SDK -->
<dependency>
    <groupId>com.theokanning.openai-gpt3-java</groupId>
    <artifactId>service</artifactId>
    <version>0.18.2</version>
</dependency>

<!-- O simplemente usar RestClient (ya incluido en Spring Boot) -->
```

### Configuración de Desarrollo

```properties
# application-dev.properties
mpdia.ai.provider=gemini
mpdia.ai.enable-insights=false
mpdia.ai.max-history-messages=10

logging.level.com.mpdia.service.AICopilotService=DEBUG
```

### Mejores Prácticas Identificadas

1. **Siempre validar autorización antes de ejecutar tools**
2. **Logs estructurados para debugging**
3. **DTOs separados para AI vs Analytics**
4. **Tools pequeños y específicos (mejor que tools grandes)**
5. **Cachear resultados costosos (velocity, trends)**
6. **Timeouts en llamadas a API de IA (30s máx)**
7. **Manejo graceful de errores de IA**
8. **Feedback visual inmediato en UI**

---

## ✅ SIGUIENTE PASO

Una vez revisado y aprobado este plan, proceder con:

**FASE 1: Implementación de BD y Entidades**

Ejecutar comando:
```bash
# Backend
cd mpdia-springboot
# Crear archivos según especificación FASE 1
```

¿Estás listo para comenzar la implementación? 🚀
