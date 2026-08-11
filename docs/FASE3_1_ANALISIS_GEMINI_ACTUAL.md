# FASE 3.1: ANÁLISIS DE GeminiService ACTUAL

**Fecha:** 10 de Agosto, 2026  
**Sub-Fase:** 3.1 - Extender GeminiService + AIAgentService básico  
**Estado:** ANÁLISIS PREVIO

---

## 🔍 ANÁLISIS DE GeminiService ACTUAL

### Código Actual

```java
@Service
public class GeminiService {
    @Value("${mpdia.gemini.api-key}")
    private String apiKey;

    @Value("${mpdia.gemini.api-url}")
    private String apiUrl;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    public String generate(String prompt) {
        Map<String, Object> body = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(Map.of("text", prompt)))
            )
        );
        
        // Llamada a Gemini API
        // Parseo de respuesta simple
    }
}
```

### Capacidades Actuales

✅ **LO QUE HACE:**
1. Envía un prompt simple a Gemini 2.5 Flash
2. Recibe una respuesta de texto
3. Parsea el JSON de respuesta
4. Extrae el texto del último part
5. Maneja errores básicos de HTTP

### Limitaciones Identificadas

❌ **LO QUE NO HACE:**
1. **NO soporta conversaciones multi-turn** (solo envía un prompt)
2. **NO soporta system instructions**
3. **NO soporta function calling / tool use**
4. **NO envía definiciones de tools**
5. **NO procesa `functionCall` en la respuesta**
6. **NO maneja historial de mensajes**
7. **NO permite configurar parámetros (temperature, max_tokens)**

---

## 🚧 LIMITACIÓN CRÍTICA: Function Calling

### Formato Actual (Sin Tools)

```json
{
  "contents": [
    {
      "parts": [{"text": "¿Cómo estuvo el último sprint?"}]
    }
  ]
}
```

**Respuesta:**
```json
{
  "candidates": [{
    "content": {
      "parts": [{"text": "El sprint..."}]
    }
  }]
}
```

### Formato Requerido (Con Tools)

**Request con tools:**
```json
{
  "contents": [
    {"role": "user", "parts": [{"text": "¿Cómo estuvo el último sprint?"}]}
  ],
  "systemInstruction": {
    "parts": [{"text": "Eres el AI Agile Copilot..."}]
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
              "sprintId": {
                "type": "STRING",
                "description": "UUID del sprint"
              }
            },
            "required": ["sprintId"]
          }
        }
      ]
    }
  ]
}
```

**Respuesta con function call:**
```json
{
  "candidates": [{
    "content": {
      "parts": [{
        "functionCall": {
          "name": "getSprintMetrics",
          "args": {
            "sprintId": "uuid-123"
          }
        }
      }]
    }
  }]
}
```

**Segundo request con result:**
```json
{
  "contents": [
    {"role": "user", "parts": [{"text": "¿Cómo estuvo el último sprint?"}]},
    {"role": "model", "parts": [{
      "functionCall": {
        "name": "getSprintMetrics",
        "args": {"sprintId": "uuid-123"}
      }
    }]},
    {"role": "function", "parts": [{
      "functionResponse": {
        "name": "getSprintMetrics",
        "response": {
          "sprintNumero": 3,
          "promediosPorCategoria": {"Productividad": 82}
        }
      }
    }]}
  ],
  "tools": [...]
}
```

**Respuesta final:**
```json
{
  "candidates": [{
    "content": {
      "parts": [{"text": "El Sprint 3 tuvo una productividad de 82..."}]
    }
  }]
}
```

---

## 🏗️ ARQUITECTURA PROPUESTA

### Estrategia: Extender Sin Romper

**Principio:** Mantener compatibilidad con uso actual mientras agregamos nuevas capacidades.

### Nuevo Diseño de Clases

```
┌──────────────────────────────────────────────────┐
│           GeminiService (Extended)               │
│                                                  │
│  Método existente:                               │
│  + String generate(String prompt)               │
│    └→ Mantiene compatibilidad                   │
│                                                  │
│  Nuevos métodos:                                 │
│  + GeminiResponse chat(GeminiRequest request)   │
│    └→ Soporta multi-turn + tools                │
│                                                  │
│  + GeminiResponse chatWithTools(               │
│      List<Message> messages,                    │
│      List<Tool> tools,                          │
│      String systemInstruction                   │
│    )                                             │
│    └→ Function calling completo                 │
│                                                  │
│  Métodos privados helper:                       │
│  - buildRequestBody()                           │
│  - parseResponse()                              │
│  - extractText()                                │
│  - extractFunctionCall()                        │
└──────────────────────────────────────────────────┘
```

### Nuevas Clases de Datos

```
┌────────────────────────────────────┐
│       GeminiRequest                │
│  - List<Message> messages          │
│  - List<Tool> tools                │
│  - String systemInstruction        │
│  - GenerationConfig config         │
└────────────────────────────────────┘

┌────────────────────────────────────┐
│       GeminiResponse               │
│  - String text                     │
│  - FunctionCall functionCall       │
│  - Boolean isTextResponse          │
│  - Boolean isFunctionCall          │
└────────────────────────────────────┘

┌────────────────────────────────────┐
│       Message                      │
│  - String role (user/model/func)   │
│  - List<Part> parts                │
└────────────────────────────────────┘

┌────────────────────────────────────┐
│       Part                         │
│  - String text                     │
│  - FunctionCall functionCall       │
│  - FunctionResponse funcResponse   │
└────────────────────────────────────┘

┌────────────────────────────────────┐
│       Tool                         │
│  - List<FunctionDeclaration> funcs │
└────────────────────────────────────┘

┌────────────────────────────────────┐
│   FunctionDeclaration              │
│  - String name                     │
│  - String description              │
│  - Map<String,Object> parameters   │
└────────────────────────────────────┘

┌────────────────────────────────────┐
│       FunctionCall                 │
│  - String name                     │
│  - Map<String,Object> args         │
└────────────────────────────────────┘
```

### AIAgentService (Básico)

```
┌──────────────────────────────────────────────────┐
│           AIAgentService                         │
│                                                  │
│  Constructor:                                    │
│  - GeminiService geminiService                  │
│                                                  │
│  Métodos:                                        │
│  + AgentResponse processMessage(                │
│      String userMessage,                        │
│      List<Tool> availableTools,                 │
│      String systemInstruction,                  │
│      ToolExecutor toolExecutor                  │
│    )                                             │
│    └→ Orquesta el flujo completo                │
│                                                  │
│  Lógica interna:                                 │
│  1. Construir mensaje inicial                   │
│  2. Llamar a Gemini con tools                   │
│  3. Si es texto → retornar                      │
│  4. Si es function_call:                        │
│     a. Ejecutar tool via toolExecutor           │
│     b. Agregar resultado a conversación         │
│     c. Llamar nuevamente a Gemini               │
│     d. Retornar respuesta final                 │
│  5. Registrar tools utilizadas                  │
└──────────────────────────────────────────────────┘
```

### ToolExecutor (Interface)

```java
@FunctionalInterface
public interface ToolExecutor {
    Object execute(String toolName, Map<String, Object> args);
}
```

**Razón:** Permite que AIAgentService sea agnóstico de la lógica de tools.

---

## 📝 ARCHIVOS A CREAR/MODIFICAR

### Archivos a CREAR (13 archivos)

**DTOs para Gemini:**
1. `mpdia-springboot/src/main/java/com/mpdia/dto/ai/gemini/GeminiRequest.java`
2. `mpdia-springboot/src/main/java/com/mpdia/dto/ai/gemini/GeminiResponse.java`
3. `mpdia-springboot/src/main/java/com/mpdia/dto/ai/gemini/Message.java`
4. `mpdia-springboot/src/main/java/com/mpdia/dto/ai/gemini/Part.java`
5. `mpdia-springboot/src/main/java/com/mpdia/dto/ai/gemini/Tool.java`
6. `mpdia-springboot/src/main/java/com/mpdia/dto/ai/gemini/FunctionDeclaration.java`
7. `mpdia-springboot/src/main/java/com/mpdia/dto/ai/gemini/FunctionCall.java`
8. `mpdia-springboot/src/main/java/com/mpdia/dto/ai/gemini/FunctionResponse.java`

**Servicios:**
9. `mpdia-springboot/src/main/java/com/mpdia/service/AIAgentService.java`

**Interfaces:**
10. `mpdia-springboot/src/main/java/com/mpdia/service/ToolExecutor.java`

**DTO de Agent:**
11. `mpdia-springboot/src/main/java/com/mpdia/dto/ai/AgentResponse.java`

**Tests:**
12. `mpdia-springboot/src/test/java/com/mpdia/service/GeminiServiceFunctionCallingTest.java`
13. `mpdia-springboot/src/test/java/com/mpdia/service/AIAgentServiceTest.java`

### Archivos a MODIFICAR (1 archivo)

1. `mpdia-springboot/src/main/java/com/mpdia/service/GeminiService.java`
   - ✅ Mantener método `generate(String prompt)` intacto
   - ➕ Agregar método `chatWithTools(...)`
   - ➕ Agregar métodos helper privados
   - ➕ Agregar manejo de function calling

---

## 🧪 TOOL DE PRUEBA

### Tool Simple: getProjectOverview

**Razón de elección:**
- ✅ Read-only
- ✅ Usa AgileAnalyticsService (validamos integración Fase 2)
- ✅ No requiere parámetros complejos
- ✅ Retorna datos estructurados

**Implementación:**
```java
// En AIAgentServiceTest o clase de prueba
ToolExecutor simpleExecutor = (toolName, args) -> {
    if ("getProjectOverview".equals(toolName)) {
        UUID proyectoId = UUID.fromString((String) args.get("proyectoId"));
        return agileAnalyticsService.getProjectOverview(proyectoId);
    }
    throw new IllegalArgumentException("Tool desconocida: " + toolName);
};
```

**Function Declaration:**
```java
FunctionDeclaration getProjectOverviewTool = new FunctionDeclaration(
    "getProjectOverview",
    "Obtiene resumen general del proyecto con todas sus métricas agregadas",
    Map.of(
        "type", "OBJECT",
        "properties", Map.of(
            "proyectoId", Map.of(
                "type", "STRING",
                "description", "UUID del proyecto"
            )
        ),
        "required", List.of("proyectoId")
    )
);
```

---

## ✅ CRITERIOS DE ACEPTACIÓN FASE 3.1

- [ ] GeminiService extendido sin romper funcionalidad existente
- [ ] Método `generate(String)` sigue funcionando
- [ ] Nuevo método `chatWithTools()` implementado
- [ ] DTOs de Gemini creados (8 clases)
- [ ] AIAgentService básico implementado
- [ ] ToolExecutor interface creada
- [ ] Tool de prueba funciona (`getProjectOverview`)
- [ ] Tests unitarios creados y pasan
- [ ] Tests existentes siguen pasando
- [ ] No se rompe nada del sistema actual
- [ ] BUILD SUCCESS
- [ ] Documentación de arquitectura completa

---

## 🚀 PRÓXIMO PASO

**Proceder con implementación de FASE 3.1 según esta arquitectura.**

Después de implementar, **DETENERSE** y no continuar a FASE 3.2 automáticamente.
