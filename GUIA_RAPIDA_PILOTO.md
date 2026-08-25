# 🚀 GUÍA RÁPIDA — PILOTO E2E SIG-SC-02

## ⚡ INICIO RÁPIDO (5 minutos)

### 1️⃣ ABRIR pgAdmin
- Conectar a: `localhost:5433`
- Base de datos: `mpdia_db`
- Usuario: `mpdia_user`

### 2️⃣ EJECUTAR SQL
Copiar y pegar todo el contenido de: **`INSERT_PARAMETRIZACION_SIG_SC_02.sql`**

**Importante:** El script incluye verificación automática para evitar duplicados.

### 3️⃣ VERIFICAR RESULTADO
Debe mostrar 1 fila:
```
status = 'aprobada'
version = 1
factor_id = NULL ← Importante
formula_academica = 'Σ problemas_reportados'
tipo_operacion = 'SUMA'
```

### 4️⃣ ABRIR ANGULAR
URL: http://localhost:4200/metrica-academica/sig-sc-02

**Debe mostrar:**
- ✅ Badge verde "APROBADA"
- ✅ Fórmula: "Σ problemas_reportados"
- ✅ Input para capturar valor

### 5️⃣ EJECUTAR MÉTRICA
1. Ingresar: `7`
2. Click: "Ejecutar"
3. Verificar resultado: **7 problemas**

### 6️⃣ ANALIZAR CON IA
1. Click: "Analizar con IA"
2. Esperar respuesta de Gemini
3. Verificar interpretación contextual

---

## ✅ CRITERIO DE ÉXITO

Si ves:
- Parametrización APROBADA en Angular
- Resultado: 7 problemas
- Análisis IA funcionando

**→ PILOTO E2E COMPLETADO**

---

## 📋 DOCUMENTACIÓN COMPLETA

Para instrucciones detalladas paso a paso:
→ Ver `INSTRUCCIONES_PILOTO_E2E_SIG_SC_02.md`

Para entender el análisis técnico:
→ Ver `docs/FASE16_9_3_E2E_SIG_SC_02.md` (Sección 11)

Para resumen ejecutivo:
→ Ver `RESUMEN_FASE_16_9_3_C.md`

---

## 🔧 SI ALGO FALLA

### Problema: "No existe parametrización aprobada"
**Solución:**
1. Verificar en BD que existe el registro
2. Verificar que status = 'aprobada'
3. Recargar Angular (F5)
4. Verificar Network tab: GET /ultima-aprobada debe retornar 200

### Problema: "Error 500 al ejecutar"
**Solución:**
1. Verificar logs del backend
2. Verificar que parametrizacion_id existe
3. Verificar que campos académicos V24 están presentes

### Problema: "IA no responde"
**Solución:**
1. Verificar que el resultado ya fue calculado (7.0)
2. Verificar Gemini API key configurada
3. Ver logs del backend para error de API

---

## ⚠️ IMPORTANTE

**Esta parametrización fue creada mediante SQL directo porque:**
- El ParametrizacionController NO expone endpoints de crear/aprobar
- El ParametrizacionService tiene toda la lógica pero NO está expuesto vía REST
- Es una solución temporal para el piloto E2E
- El flujo completo UI → Backend se implementará en FASE 16.10+

**NO es el flujo definitivo del usuario.**

---

**Tiempo estimado:** 5 minutos  
**Prerequisito:** Backend y Frontend corriendo  
**Última actualización:** 2026-08-16 21:00
