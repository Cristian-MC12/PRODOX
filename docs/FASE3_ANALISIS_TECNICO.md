# FASE 3: ANÁLISIS TÉCNICO - AI AGENT + FUNCTION CALLING

**Fecha:** 10 de Agosto, 2026  
**Fase:** 3 - AI Agent Service + Integration + Tool Use  
**Estado:** ANÁLISIS COMPLETADO

---

## 🔍 ANÁLISIS DE IMPLEMENTACIÓN ACTUAL

### GeminiService Existente

**Ubicación:** `com.mpdia.service.GeminiService`

**Capacidades actuales:**
- ✅ Integración básica con Gemini 2.5 Flash
- ✅ Envío de prompts simples
- ✅ Extracción de respuesta de texto
- ✅ Manejo básico de errores
- ✅ Usa RestClient de Spring

**Limitaciones identificadas:**
- ❌ **NO soporta function calling / tool use**
- ❌ NO envía definiciones de tools
- ❌ NO procesa function_call en respuesta
- ❌ NO maneja conversaciones multi-turn con tools
- ❌ NO gestiona system instructions
- ❌ NO maneja historial de mensajes

**Conclusión:** GeminiService actual **NO es suficiente** para function calling.

### Gemini 2.5 Flash vs OpenAI GPT-4

**Gemini 2.5 Flash:**
- ✅ Soporta function calling (con formato específico de Google)
- ✅ Ya está configurado en MPDIA
- ✅ API key ya existe
- ⚠️ Requiere refactorización significativa del servicio

**OpenAI GPT-4:**
- ✅ Function calling nativo y bien documentado
- ✅ Más maduro para tool use
- ❌ Requiere nueva API key
- ❌ Requiere nueva dependencia

**DECISIÓN:** Extender GeminiService para soportar function calling, manteniendo compatibilidad.

---

## 🏗️ ARQUITECTURA PROPUESTA

### Flujo Completo

```
Usuario autenticado (userId desde JWT)
    ↓
AICopilotService.chat(message, userId, proyectoId, sprintId?)
    ↓
1. Validar autorización (ProjectMemberService)
    ↓
2. Recuperar historial (AIChatMessageRepository)
    ↓
3. Construir contexto del proyecto/sprint
    ↓
AIAgentService.sendMessage(messages, tools, userId, proyectoId)
    ↓
4. Construir system instruction con reglas de MPDIA
    ↓
5. Registrar tools disponibles
    ↓
6. Llamar a Gemini con function declarations
    ↓
7. Procesar respuesta:
   - Si es texto → retornar
   - Si es function_call → ejecutar tool
        ↓
    CopilotToolsService.executeTool(toolName, args, userId, proyectoId)
        ↓
    8. Validar autorización del tool
        ↓
    9. Ejecutar tool (ej: AgileAnalyticsService.getSprintMetrics())
        ↓
    10. Retornar resultado a Gemini
        ↓
    11. Gemini genera respuesta final con el resultado
    ↓
12. Guardar mensajes en ai_chat_messages
    ↓
13. Retornar respuesta estructurada
```

### Capas de Abstracción

```
┌─────────────────────────────────────────┐
│      AICopilotService                   │
│  - Orquestación                         │
│  - Validación autorización              │
│  - Gestión historial                    │
│  - Guardado de mensajes                 │
└─────────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────────┐
│      AIAgentService                     │
│  - Gestión de conversación              │
│  - System prompt                        │
│  - Tool registration                    │
│  - Tool call processing                 │
│  - Provider abstraction                 │
└─────────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────────┐
│      GeminiService (extended)           │
│  - Function calling support             │
│  - Multi-turn conversations             │
│  - Tool declarations                    │
│  - Response parsing                     │
└─────────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────────┐
│      CopilotToolsService                │
│  - Tool execution                       │
│  - Authorization check                  │
│  - Parameter validation                 │
│  - Data retrieval                       │
└─────────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────────┐
│   AgileAnalyticsService (Fase 2)        │
│   EvaluacionService (Existente)         │
│   SprintService (Existente)             │
│   ProjectMemberService (Existente)      │
│   ProyectoService (Existente)           │
└─────────────────────────────────────────┘
```

---

## 🛠️ IMPLEMENTACIÓN TÉCNICA

### 1. Extender GeminiService

**Crear nuevo método:**
```java
public GeminiResponse generateWithTools(
    List<Message> messages,
    List<Tool> tools,
    String systemInstruction
)
```

**Formato de Gemini Function Calling:**
```json
{
  "contents": [
    {"role": "user", "parts": [{"text": "..."}]},
    {"role": "model", "parts": [{"text": "..."}]}
  ],
  "systemInstruction": {
    "parts": [{"text": "System prompt..."}]
  },
  "tools": [
    {
      "functionDeclarations": [
        {
          "name": "getSprintMetrics",
          "description": "Obtiene métricas de un sprint",
          "parameters": {
            "type": "OBJECT",
            "properties": {
              "sprintId": {"type": "STRING", "description": "UUID del sprint"}
            },
            "required": ["sprintId"]
          }
        }
      ]
    }
  ]
}
```

**Parseo de respuesta:**
```java
// Si Gemini llama una función:
{
  "candidates": [{
    "content": {
      "parts": [{
        "functionCall": {
          "name": "getSprintMetrics",
          "args": {"sprintId": "..."}
        }
      }]
    }
  }]
}
```

### 2. System Instruction de MPDIA

```
Eres el AI Agile Copilot de MPDIA, un sistema especializado en medición 
de productividad de equipos Agile.

CONTEXTO MPDIA:
- MPDIA permite a equipos Agile definir y medir métricas personalizadas
- Las métricas se agrupan en categorías: Calidad, Productividad, Cumplimiento, 
  Flexibilidad, Sociohumano
- Cada equipo configura sus propias variables de medición
- Los valores se registran durante los sprints

IMPORTANTE: MPDIA NO ES SCRUM TRADICIONAL
- NO tiene "story points" nativos (depende de configuración del equipo)
- NO rastrea items individuales con estados
- NO tiene Cycle Time tradicional
- NO tiene WIP tradicional
- Las métricas son adaptadas a cada proyecto

REGLAS ESTRICTAS:
1. NUNCA inventes datos, métricas, usuarios, proyectos o sprints
2. SIEMPRE usa tools para consultar información real
3. Diferencia HECHOS de INFERENCIAS claramente
4. Si una métrica retorna datosDisponibles=false, dilo explícitamente
5. NO presentes hipótesis como causas confirmadas
6. NO reveles información de proyectos sin autorización
7. Responde en ESPAÑOL
8. Usa lenguaje claro orientado a equipos Agile

ESTRUCTURA DE RESPUESTA:
- Resumen
- Datos relevantes (cita las métricas utilizadas)
- Hallazgos
- Posibles causas (márca las como hipótesis)
- Riesgos detectados
- Recomendaciones

Usuario actual: {userId}
Proyecto actual: {proyectoNombre}
Sprint actual: Sprint {sprintNumero} - {sprintGoal}
```

### 3. Tools a Implementar

#### Tool: getCurrentUser
```java
{
  "name": "getCurrentUser",
  "description": "Obtiene información del usuario autenticado actual",
  "parameters": {}
}
// Retorna: {userId, email, role}
```

#### Tool: getUserProjects
```java
{
  "name": "getUserProjects",
  "description": "Lista los proyectos del usuario autenticado",
  "parameters": {}
}
// Retorna: List<{proyectoId, nombre, metodo, estado}>
```

#### Tool: getProjectDetails
```java
{
  "name": "getProjectDetails",
  "description": "Obtiene detalles de un proyecto específico",
  "parameters": {
    "proyectoId": "UUID del proyecto"
  }
}
// Valida: usuario pertenece al proyecto
// Retorna: {id, nombre, metodo, timeBoxSemanas, numeroSprints, etc.}
```

#### Tool: getActiveSprintMetrics
```java
{
  "name": "getActiveSprintMetrics",
  "description": "Obtiene métricas del sprint activo del proyecto",
  "parameters": {
    "proyectoId": "UUID del proyecto"
  }
}
// Valida: usuario pertenece al proyecto
// Usa: AgileAnalyticsService.getSprintMetricsSummary()
// Retorna: SprintMetricsSummaryDto
```

#### Tool: getSprintMetrics
```java
{
  "name": "getSprintMetrics",
  "description": "Obtiene métricas de un sprint específico",
  "parameters": {
    "sprintId": "UUID del sprint"
  }
}
// Valida: usuario pertenece al proyecto del sprint
// Usa: AgileAnalyticsService.getSprintMetricsSummary()
```

#### Tool: getSprintHistory
```java
{
  "name": "getSprintHistory",
  "description": "Obtiene historial de sprints del proyecto",
  "parameters": {
    "proyectoId": "UUID del proyecto",
    "limit": "Número de sprints a retornar (opcional)"
  }
}
// Valida: usuario pertenece al proyecto
// Usa: SprintService.listarSprints()
```

#### Tool: compareSprints
```java
{
  "name": "compareSprints",
  "description": "Compara métricas entre dos sprints",
  "parameters": {
    "sprintId1": "UUID del primer sprint",
    "sprintId2": "UUID del segundo sprint"
  }
}
// Valida: usuario pertenece al proyecto de ambos sprints
// Valida: ambos sprints del mismo proyecto
// Usa: AgileAnalyticsService.compareSprints()
```

#### Tool: getProductivityTrends
```java
{
  "name": "getProductivityTrends",
  "description": "Analiza tendencias de productividad en últimos N sprints",
  "parameters": {
    "proyectoId": "UUID del proyecto",
    "numberOfSprints": "Número de sprints a analizar (opcional, default 5)"
  }
}
// Valida: usuario pertenece al proyecto
// Usa: AgileAnalyticsService.getSprintTrends(proyectoId, "Productividad", n)
```

#### Tool: detectRisks
```java
{
  "name": "detectRisks",
  "description": "Identifica riesgos en el proyecto basándose en señales objetivas",
  "parameters": {
    "proyectoId": "UUID del proyecto"
  }
}
// Valida: usuario pertenece al proyecto
// Usa: AgileAnalyticsService.identifyRisks()
```

#### Tool: getProjectOverview
```java
{
  "name": "getProjectOverview",
  "description": "Obtiene resumen general del proyecto con todas sus métricas",
  "parameters": {
    "proyectoId": "UUID del proyecto"
  }
}
// Valida: usuario pertenece al proyecto
// Usa: AgileAnalyticsService.getProjectOverview()
```

#### Tool: detectAnomalies
```java
{
  "name": "detectAnomalies",
  "description": "Detecta anomalías estadísticas en un sprint comparado con histórico",
  "parameters": {
    "sprintId": "UUID del sprint"
  }
}
// Valida: usuario pertenece al proyecto del sprint
// Usa: AgileAnalyticsService.detectAnomalies()
```

**Total tools a implementar:** 10 tools read-only

---

## 🔒 SEGURIDAD Y AUTORIZACIÓN

### Validación en Cada Tool

```java
// Patrón de validación
public Object executeTool(String toolName, Map<String, Object> args, String userId, UUID proyectoId) {
    // 1. Extraer proyectoId del argumento o parámetro
    UUID targetProyectoId = extractProyectoId(toolName, args, proyectoId);
    
    // 2. Validar autorización
    if (!projectMemberService.existsByProyectoIdAndUserId(targetProyectoId, userId)) {
        throw new SecurityException("Usuario no tiene acceso a este proyecto");
    }
    
    // 3. Ejecutar tool
    return switch (toolName) {
        case "getSprintMetrics" -> executeGetSprintMetrics(args);
        // ...
        default -> throw new IllegalArgumentException("Tool desconocido: " + toolName);
    };
}
```

### Matriz de Autorización

| Tool | Requiere ProyectoId | Validación |
|------|---------------------|------------|
| getCurrentUser | NO | Siempre autorizado |
| getUserProjects | NO | Retorna solo proyectos del usuario |
| getProjectDetails | SÍ | Validar membership |
| getActiveSprintMetrics | SÍ | Validar membership |
| getSprintMetrics | Indirecto (del sprint) | Validar que sprint pertenece a proyecto del usuario |
| getSprintHistory | SÍ | Validar membership |
| compareSprints | Indirecto (de ambos sprints) | Validar que ambos sprints pertenecen al proyecto del usuario |
| getProductivityTrends | SÍ | Validar membership |
| detectRisks | SÍ | Validar membership |
| getProjectOverview | SÍ | Validar membership |
| detectAnomalies | Indirecto (del sprint) | Validar que sprint pertenece a proyecto del usuario |

---

## 📝 DTOs NECESARIOS

### ChatRequest
```java
public record ChatRequest(
    String message,
    UUID proyectoId,
    UUID sprintId // opcional
) {}
```

### ChatResponse
```java
public record ChatResponse(
    String message,
    List<String> toolsUsed,
    Instant timestamp,
    Boolean hasData
) {}
```

### ToolResult (interno)
```java
public record ToolResult(
    String toolName,
    Object data,
    Boolean success,
    String error
) {}
```

---

## ⚙️ CONFIGURACIÓN

### application.properties.example

Agregar:
```properties
# AI Copilot
mpdia.ai.max-history-messages=10
mpdia.ai.timeout-seconds=30
mpdia.ai.system-instruction-template=classpath:prompts/system-instruction.txt
```

---

## ✅ PRÓXIMOS PASOS

1. Extender GeminiService con soporte de function calling
2. Crear AIAgentService
3. Crear AICopilotService
4. Crear CopilotToolsService
5. Crear DTOs necesarios
6. Implementar las 10 tools
7. Crear tests unitarios
8. Validar con tests de integración

**ESTADO:** Análisis completado, listo para implementación
