# INSTRUCCIONES PARA EJECUTAR PILOTO E2E SIG-SC-02
**Fase 16.9.3-C**

## CONTEXTO
- Backend corriendo: ✅ puerto 8080
- Frontend corriendo: ✅ puerto 4200
- Usuario autenticado: ✅ sm9130109@gmail.com
- Proyecto: ✅ "Trabajo 1" (fce0340c-74f2-4219-a727-5bae4d842496)
- Métrica SIG-SC-02: ✅ existe en BD

## PREREQUISITO: ACCESO A BASE DE DATOS

**Opción A - pgAdmin:**
1. Abrir pgAdmin
2. Conectar a servidor PostgreSQL (localhost:5433)
3. Base de datos: `mpdia_db`
4. Usuario: `mpdia_user`
5. Contraseña: `mpdia_pass`

**Opción B - psql (línea de comandos):**
```bash
"C:\Program Files\PostgreSQL\16\bin\psql.exe" -h localhost -p 5433 -U mpdia_user -d mpdia_db
```

---

## PASO 1: VERIFICAR SI YA EXISTE PARAMETRIZACIÓN

**Ejecutar en pgAdmin o psql:**

```sql
SELECT 
    id,
    metrica_id,
    proyecto_id,
    factor_id,
    status,
    version,
    formula_academica,
    tipo_operacion,
    unidad_resultado,
    created_at
FROM metric_parametrizaciones
WHERE metrica_id = '2ba0cf34-0bec-4e7d-8dc5-40795f050ec9'
  AND proyecto_id = 'fce0340c-74f2-4219-a727-5bae4d842496'
ORDER BY created_at DESC;
```

**Resultado esperado:**
- ❌ **Si retorna vacío** → Continuar con PASO 2
- ✅ **Si retorna status='aprobada'** → Saltar a PASO 3 (usar existente)
- ⚠️ **Si retorna status='propuesta' o 'inactiva'** → Revisar versionado

---

## PASO 2: CREAR PARAMETRIZACIÓN (SOLO SI NO EXISTE)

**Ejecutar en pgAdmin o psql:**

```sql
INSERT INTO metric_parametrizaciones (
    id,
    version,
    factor_id,
    user_id,
    user_email,
    objetivo,
    procedimiento,
    indicador_variable,
    escala,
    metrica_id,
    status,
    proyecto_id,
    frecuencia_captura,
    fuente_academica,
    formula_academica,
    tipo_operacion,
    unidad_resultado,
    created_at
) VALUES (
    gen_random_uuid(),
    1,
    NULL,
    'sm9130109@gmail.com',
    'sm9130109@gmail.com',
    'Medir la cantidad de problemas reportados por el cliente durante el sprint',
    'Al finalizar el sprint, contar el número total de problemas reportados por el cliente. Fórmula: Σ problemas_reportados',
    'problemas_reportados',
    'Número entero >= 0',
    '2ba0cf34-0bec-4e7d-8dc5-40795f050ec9',
    'aprobada',
    'fce0340c-74f2-4219-a727-5bae4d842496',
    'por_sprint',
    'Guerrero-Calvache & Hernández (2024)',
    'Σ problemas_reportados',
    'SUMA',
    'problemas',
    NOW()
)
RETURNING id, metrica_id, status, version;
```

**Verificar resultado:**
- ✅ Debe devolver 1 fila con el ID generado
- ✅ status = 'aprobada'
- ✅ version = 1

---

## PASO 3: VERIFICAR EN BD

**Ejecutar:**

```sql
SELECT 
    p.id,
    p.metrica_id,
    m.nombre AS metrica_nombre,
    m.codigo AS metrica_codigo,
    p.status,
    p.version,
    p.factor_id,
    p.fuente_academica,
    p.formula_academica,
    p.tipo_operacion,
    p.unidad_resultado,
    p.indicador_variable,
    p.created_at
FROM metric_parametrizaciones p
JOIN metricas m ON p.metrica_id = m.id
WHERE p.metrica_id = '2ba0cf34-0bec-4e7d-8dc5-40795f050ec9'
  AND p.proyecto_id = 'fce0340c-74f2-4219-a727-5bae4d842496'
  AND p.status = 'aprobada'
ORDER BY p.created_at DESC
LIMIT 1;
```

**Confirmar:**
- ✅ metrica_codigo = 'SIG-SC-02'
- ✅ status = 'aprobada'
- ✅ factor_id = NULL
- ✅ formula_academica = 'Σ problemas_reportados'
- ✅ tipo_operacion = 'SUMA'
- ✅ unidad_resultado = 'problemas'
- ✅ fuente_academica = 'Guerrero-Calvache & Hernández (2024)'

---

## PASO 4: VERIFICAR API REST

**En el navegador (con sesión activa en localhost:4200):**

Abrir Developer Tools (F12) → Console → Ejecutar:

```javascript
fetch('http://localhost:8080/api/parametrizacion/ultima-aprobada?metricaId=2ba0cf34-0bec-4e7d-8dc5-40795f050ec9&proyectoId=fce0340c-74f2-4219-a727-5bae4d842496', {
  headers: {
    'Authorization': 'Bearer ' + localStorage.getItem('mpdia_token')
  }
})
.then(r => r.json())
.then(data => console.log('Parametrización:', data))
.catch(err => console.error('Error:', err));
```

**Resultado esperado:**
```json
{
  "id": "...",
  "metricaId": "2ba0cf34-0bec-4e7d-8dc5-40795f050ec9",
  "status": "aprobada",
  "version": 1,
  "formulaAcademica": "Σ problemas_reportados",
  "tipoOperacion": "SUMA",
  "unidadResultado": "problemas",
  "fuenteAcademica": "Guerrero-Calvache & Hernández (2024)",
  ...
}
```

**Si retorna 204 No Content o 404:**
- ❌ La parametrización NO fue creada correctamente
- ⏸️ NO continuar, revisar PASO 2

---

## PASO 5: VERIFICAR EN ANGULAR

1. **Navegar a:** http://localhost:4200/metrica-academica/sig-sc-02
2. **Recargar la página** (F5)

**Debe mostrar:**
- ✅ Título: "SIG-SC-02"
- ✅ Estado: badge verde "APROBADA"
- ✅ Versión: 1
- ✅ Fuente: "Guerrero-Calvache & Hernández (2024)"
- ✅ Fórmula: "Σ problemas_reportados"
- ✅ Operación: "SUMA"
- ✅ Variable: "problemas_reportados" (INTEGER)
- ✅ Unidad: "problemas"
- ✅ Input para capturar valor

**NO debe mostrar:**
- ❌ "No existe una parametrización aprobada"
- ❌ "Error al cargar"
- ❌ Botón "Ir a Planeación"

---

## PASO 6: CAPTURAR VARIABLE

1. **En el input "problemas_reportados"**, ingresar: `7`
2. **Verificar:**
   - ✅ El valor es aceptado
   - ✅ NO aparece error de validación
   - ✅ Botón "Ejecutar" queda habilitado

---

## PASO 7: EJECUTAR MÉTRICA

1. **Click en botón "Ejecutar"**
2. **Abrir Developer Tools (F12) → Network**
3. **Filtrar:** "ejecutar"

**Verificar request:**
```
POST http://localhost:8080/api/metricas-academicas/sig-sc-02/ejecutar
Content-Type: application/json

Body:
{
  "proyectoId": "fce0340c-74f2-4219-a727-5bae4d842496",
  "sprintId": "...",
  "valores": {
    "problemas_reportados": 7
  }
}
```

**Verificar response (Status 200):**
```json
{
  "resultadoId": "...",
  "metricaId": "sig-sc-02",
  "metricaNombre": "SIG-SC-02",
  "proyectoId": "...",
  "sprintId": "...",
  "parametrizacionId": "...",
  "parametrizacionVersion": 1,
  "tipoCalculo": "SUMA",
  "expresion": "Σ problemas_reportados",
  "valoresUtilizados": "{\"problemas_reportados\":7}",
  "resultado": 7.0,
  "unidad": "problemas",
  "estado": "calculado",
  "calculadoAt": "..."
}
```

**Verificar en UI:**
- ✅ Card de resultado aparece
- ✅ Resultado: **7 problemas**
- ✅ Fórmula: "Σ problemas_reportados"
- ✅ Valores: "problemas_reportados: 7"
- ✅ Estado: "calculado"
- ✅ Botón "Analizar con IA" visible

---

## PASO 8: VERIFICAR RESULTADO EN BD

**Ejecutar en BD:**

```sql
SELECT 
    id,
    metrica_id,
    proyecto_id,
    sprint_id,
    parametrizacion_id,
    parametrizacion_version,
    tipo_calculo,
    expresion,
    valores_utilizados,
    resultado,
    unidad,
    estado,
    calculado_at
FROM resultado_metrica_academica
WHERE metrica_id = 'sig-sc-02'
ORDER BY calculado_at DESC
LIMIT 1;
```

**Confirmar:**
- ✅ resultado = 7 (numeric)
- ✅ tipo_calculo = 'SUMA'
- ✅ expresion = 'Σ problemas_reportados'
- ✅ valores_utilizados contiene: `{"problemas_reportados": 7}`
- ✅ unidad = 'problemas'
- ✅ estado = 'calculado'
- ✅ parametrizacion_version = 1

---

## PASO 9: VERIFICAR HISTÓRICO

**En el navegador (Console):**

```javascript
fetch('http://localhost:8080/api/metricas-academicas/sig-sc-02/historico?proyectoId=fce0340c-74f2-4219-a727-5bae4d842496', {
  headers: {
    'Authorization': 'Bearer ' + localStorage.getItem('mpdia_token')
  }
})
.then(r => r.json())
.then(data => console.log('Histórico:', data))
.catch(err => console.error('Error:', err));
```

**Verificar:**
- ✅ Array con al menos 1 elemento
- ✅ Elemento contiene: resultado=7, metricaId='sig-sc-02'

---

## PASO 10: ANALIZAR CON IA

1. **Click en botón "Analizar con IA"**
2. **Verificar en Network:**
   - ✅ POST `/api/metricas-academicas/resultados/{resultadoId}/interpretar`
   - ✅ Response incluye interpretación de Gemini

3. **Verificar en UI:**
   - ✅ Sección de análisis IA aparece
   - ✅ Muestra interpretación contextual del resultado
   - ✅ **NO muestra cálculo** (Gemini NO calcula, solo interpreta)

**IMPORTANTE:**
- El resultado 7 fue calculado por el backend
- Gemini solo proporciona contexto/interpretación

---

## PASO 11: VERIFICAR NO-AUTOMATISMO IA

1. **Recargar la página** (F5)
2. **Observar Network tab** (F12)
3. **NO pulsar "Analizar con IA"**

**Confirmar:**
- ✅ NO existe request a `/interpretar`
- ✅ La IA NO se ejecuta automáticamente
- ✅ Solo se ejecuta después del click manual

---

## PASO 12: VALIDACIÓN NEGATIVA

1. **Cambiar valor a:** `-1`
2. **Click "Ejecutar"**

**Verificar:**
- ✅ Validación en frontend bloquea el valor negativo
- **O** backend responde con 400 Bad Request
- ✅ NO se genera resultado en BD

---

## RESULTADO FINAL

Si TODOS los pasos anteriores funcionaron:

✅ **FASE 16.9.3-C — PILOTO E2E SIG-SC-02 = COMPLETADA**

**Evidencia requerida:**
1. Screenshot de BD con parametrización creada
2. Screenshot de API devolviendo parametrización
3. Screenshot de Angular mostrando "APROBADA"
4. Screenshot del formulario con valor 7
5. Screenshot de Network con POST /ejecutar
6. Screenshot del resultado 7 en UI
7. Screenshot de BD con resultado
8. Screenshot de histórico
9. Screenshot de POST /interpretar
10. Screenshot demostrando ausencia de request IA automático

---

## NOTAS IMPORTANTES

**Sobre la parametrización:**
> La parametrización del piloto fue preparada mediante SQL directo debido a que el ParametrizacionController actualmente no expone los endpoints de generar/guardar/aprobar. Este NO es el flujo definitivo del usuario. El ParametrizacionService contiene la lógica completa, pero falta exponerla vía REST.

**Próximas fases:**
- FASE 16.10: Exponer ParametrizacionService completo vía REST
- FASE 16.11: Conectar frontend con endpoints de parametrización
- FASE 16.12: Implementar métricas FSH (Socio-Humano)

**NO implementar todavía:**
- Otras métricas académicas
- Componente genérico de métricas
- Endpoints nuevos de parametrización

---

**Última actualización:** 2026-08-16 20:45  
**Responsable:** Fase 16.9.3-C Piloto E2E
