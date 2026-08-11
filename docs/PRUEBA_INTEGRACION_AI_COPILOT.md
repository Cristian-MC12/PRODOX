# PRUEBA DE INTEGRACIÓN REAL DEL AI COPILOT

**Objetivo:** Verificar el flujo completo del AI Copilot contra Gemini API con datos reales de MPDIA

---

## ⚠️ REQUISITOS PREVIOS

### 1. API Key de Gemini

1. Obtén tu API key en: https://aistudio.google.com/app/apikey
2. Configúrala en `mpdia-springboot/src/main/resources/application.properties`:
   ```properties
   mpdia.gemini.api-key=TU_API_KEY_AQUI
   ```
3. **NUNCA** subas este archivo a Git (ya está en .gitignore)

### 2. Base de Datos con Datos

La prueba requiere:
- Al menos 1 proyecto en la BD
- Al menos 1 usuario miembro de ese proyecto
- Al menos 1 sprint (preferiblemente en estado "ejecucion")

---

## 📝 EJECUCIÓN DE LA PRUEBA

### Opción 1: Desde IDE (Recomendado)

1. Abrir `mpdia-springboot/src/test/java/com/mpdia/integration/AICopilotIntegrationTest.java`
2. **QUITAR** temporalmente la anotación `@Disabled`
3. Ejecutar el test desde el IDE (click derecho > Run Test)
4. Ver resultados en consola
5. **VOLVER A AGREGAR** `@Disabled` cuando termines

### Opción 2: Desde Maven

```bash
cd mpdia-springboot

# Quitar @Disabled del archivo AICopilotIntegrationTest.java

# Ejecutar solo este test
mvn test -Dtest=AICopilotIntegrationTest

# Volver a agregar @Disabled
```

---

## 🔍 QUÉ VERIFICA LA PRUEBA

### Flujo Completo

```
Usuario
  ↓
[1] AICopilotService.chat()
  ↓
[2] AIAgentService.processAgent()
  ↓
[3] Gemini API (llamada REAL)
  ↓
[4] Gemini solicita function call
  ↓
[5] CopilotToolsService.executeTool()
  ↓
[6] validateProjectAccess() - Autorización
  ↓
[7] AgileAnalyticsService.getSprintMetricsSummary()
  ↓
[8] Datos reales de MPDIA desde BD
  ↓
[9] Function Response a Gemini
  ↓
[10] Respuesta final de Gemini
```

### Validaciones

✅ **Gemini Connection** - La API responde correctamente  
✅ **Function Calling** - Gemini solicita y ejecuta tools  
✅ **Tool Execution** - getActiveSprintMetrics funciona  
✅ **Authorization** - Solo usuarios autorizados acceden al proyecto  
✅ **Real Data** - Datos reales desde PostgreSQL  
✅ **No Modifications** - Solo operaciones READ-ONLY  
✅ **Security** - No expone datos de otros proyectos  

---

## 📊 RESULTADO ESPERADO

### Consola Output

```
================================================================================
INICIANDO PRUEBA DE INTEGRACIÓN DEL AI COPILOT
================================================================================

[1/9] Verificando datos reales en BD...
✓ Proyecto encontrado: Proyecto Test (ID: uuid-aqui)
✓ Usuario miembro encontrado: user-id-aqui
✓ Sprint encontrado: Sprint 1 (Estado: ejecucion)

[2/9] Preparando consulta al AI Copilot...
✓ Consulta preparada

[3/9] Ejecutando consulta contra Gemini API REAL...
NOTA: Esta llamada va a Gemini API con tu API key real
✓ Gemini respondió exitosamente

[4/9] Verificando function calling...
✓ Function calling parece haber funcionado (respuesta contiene datos relevantes)

[5/9] Verificando autorización...
✓ Autorización funcionó (no hubo SecurityException)

[6-9] Respuesta final de Gemini:
────────────────────────────────────────────────────────────────────────────────
El sprint activo del proyecto tiene las siguientes métricas principales:
- Nombre: Sprint 1
- Estado: En ejecución
- Fecha inicio: 2024-01-15
- Fecha fin: 2024-01-29
- Métricas de calidad: {...}
- Productividad: {...}
────────────────────────────────────────────────────────────────────────────────

================================================================================
RESULTADOS DE LA PRUEBA DE INTEGRACIÓN
================================================================================

### Gemini
Status: CONNECTED

### Function Calling
Status: WORKING

### Tool
Tool ejecutada: getActiveSprintMetrics

### Authorization
Status: PASSED

### Real MPDIA data
Status: AVAILABLE

### Final response
Respuesta:
────────────────────────────────────────────────────────────────────────────────
El sprint activo del proyecto tiene las siguientes métricas principales:
[...respuesta de Gemini...]
────────────────────────────────────────────────────────────────────────────────

### Errores
Ninguno

### Duración
2456ms (2.456s)

### Conclusión
INTEGRATION TEST: PASSED

================================================================================
```

---

## ❌ POSIBLES ERRORES

### Error: No hay proyectos en la base de datos

**Solución:** Crear al menos un proyecto con usuario miembro y sprint

### Error: API key inválida

**Solución:** Verificar que `mpdia.gemini.api-key` en application.properties es correcta

### Error: SecurityException

**Solución:** Verificar que el usuario es miembro del proyecto

### Error: Connection refused

**Solución:** Verificar conectividad a internet y a Gemini API

---

## 🔒 SEGURIDAD

### ✅ LO QUE HACE LA PRUEBA

- Lee datos de proyectos existentes
- Valida autorización de usuarios
- Ejecuta tools READ-ONLY
- Registra logs sin secretos

### ❌ LO QUE NO HACE

- NO modifica datos
- NO expone API keys en logs
- NO accede a proyectos no autorizados
- NO crea/actualiza/elimina datos

---

## 📝 NOTAS

1. **Costo:** Cada ejecución consume tu cuota de Gemini API (muy bajo costo)
2. **Duración:** La prueba tarda 2-5 segundos normalmente
3. **Datos:** Usa datos reales de tu BD local
4. **Conexión:** Requiere internet para conectar a Gemini
5. **@Disabled:** La prueba NO se ejecuta en `mvn test` automático

---

## ✅ CHECKLIST PRE-EJECUCIÓN

- [ ] API key configurada en application.properties
- [ ] Base de datos tiene al menos 1 proyecto
- [ ] Base de datos tiene al menos 1 usuario miembro
- [ ] Base de datos tiene al menos 1 sprint
- [ ] @Disabled removido temporalmente
- [ ] Internet disponible
- [ ] PostgreSQL corriendo

---

## 🎯 PRÓXIMOS PASOS

Si la prueba pasa exitosamente:

✅ **Gemini Integration:** Funcionando  
✅ **Function Calling:** Operativo  
✅ **Tools:** Validadas  
✅ **Security:** Verificada  

**Listo para:**
- Implementar Controllers REST (Fase 4)
- Crear Frontend Angular (Fase 5)
- Agregar más AI Tools
- Testing end-to-end completo

---

**Autor:** Cristian Santiago Martinez Cordoba  
**Proyecto:** MPDIA - AI Agile Copilot  
**Fecha:** 10 de Agosto, 2026
