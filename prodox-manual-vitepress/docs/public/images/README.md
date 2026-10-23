# Convención de Nombres para Capturas de Pantalla — PRODOX V1

Este directorio contiene todas las capturas de pantalla reales de PRODOX V1 utilizadas en el manual.

---

## Estructura de Carpetas

```
images/
├── auth/              # Autenticación y acceso
├── proyectos/         # Gestión de proyectos
├── metricas/          # Selección y parametrización de métricas
├── planeacion/        # Planeación de sprints
├── backlog/           # Gestión de backlog e historias
├── sprints/           # Gestión de sprints
├── ejecucion/         # Ejecución y captura de valores
├── dashboard/         # Dashboard principal
├── evaluacion/        # Evaluación de métricas
├── ia/                # IA (Insights, Reports, Retrospectivas)
├── equipo/            # Gestión de equipo
├── configuracion/     # Configuración
└── general/           # Elementos generales (navbar, footer, etc.)
```

---

## Convención de Nombres de Archivos

### Formato General
```
{modulo}-{seccion}-{descripcion}.png
```

### Ejemplos por Módulo

#### auth/
- `auth-login-formulario.png`
- `auth-login-error-credenciales.png`
- `auth-registro-formulario.png`
- `auth-registro-exitoso.png`
- `auth-reset-password-solicitud.png`
- `auth-reset-password-formulario.png`

#### proyectos/
- `proyectos-listado-vacio.png`
- `proyectos-listado-con-proyectos.png`
- `proyectos-crear-formulario.png`
- `proyectos-detalle.png`
- `proyectos-codigo-invitacion.png`
- `proyectos-unirse-con-codigo.png`

#### metricas/
- `metricas-seleccion-catalogo.png`
- `metricas-seleccion-filtros.png`
- `metricas-seleccion-tarjeta.png`
- `metricas-resumen-lista.png`
- `metricas-parametrizacion-formulario.png`
- `metricas-parametrizacion-variables.png`
- `metricas-parametrizacion-formula.png`
- `metricas-parametrizacion-umbrales.png`
- `metricas-verificacion-completa.png`
- `metricas-factores-lista.png`

#### planeacion/
- `planeacion-vista-general.png`
- `planeacion-backlog-disponible.png`
- `planeacion-historias-seleccionadas.png`
- `planeacion-capacidad-equipo.png`
- `planeacion-objetivos.png`

#### backlog/
- `backlog-listado.png`
- `backlog-crear-historia.png`
- `backlog-editar-historia.png`
- `backlog-priorizar.png`
- `backlog-filtros.png`

#### sprints/
- `sprints-listado.png`
- `sprints-crear-formulario.png`
- `sprints-detalle.png`
- `sprints-iniciar-modal.png`
- `sprints-finalizar-modal.png`
- `sprints-estados.png`

#### ejecucion/
- `ejecucion-vista-general.png`
- `ejecucion-historias-sprint.png`
- `ejecucion-metricas-disponibles.png`
- `ejecucion-captura-valores-formulario.png`
- `ejecucion-captura-valores-resultado.png`
- `ejecucion-progreso.png`

#### dashboard/
- `dashboard-vista-completa.png`
- `dashboard-selectores.png`
- `dashboard-kpis.png`
- `dashboard-grafico-evolucion.png`
- `dashboard-grafico-distribucion.png`
- `dashboard-feed-actividad.png`
- `dashboard-acciones-rapidas.png`

#### evaluacion/
- `evaluacion-vista-general.png`
- `evaluacion-metricas-umbrales.png`
- `evaluacion-estadisticas.png`
- `evaluacion-tendencias.png`
- `evaluacion-metricas-criticas.png`
- `evaluacion-metrica-academica.png`

#### ia/
- `ia-insights-seleccion-sprints.png`
- `ia-insights-generando.png`
- `ia-insights-tendencias.png`
- `ia-insights-anomalias.png`
- `ia-insights-comparaciones.png`
- `ia-insights-graficos.png`
- `ia-report-seleccion-sprint.png`
- `ia-report-generando.png`
- `ia-report-vista-documento.png`
- `ia-report-descargar.png`
- `ia-retrospective-seleccion-sprint.png`
- `ia-retrospective-generando.png`
- `ia-retrospective-que-salio-bien.png`
- `ia-retrospective-que-salio-mal.png`
- `ia-retrospective-que-mejorar.png`
- `ia-retrospective-acciones.png`

#### equipo/
- `equipo-listado-miembros.png`
- `equipo-roles.png`
- `equipo-generar-codigo.png`
- `equipo-asignar-rol.png`
- `equipo-remover-miembro.png`

#### configuracion/
- `configuracion-vista-general.png`
- `configuracion-preferencias.png`

#### general/
- `navbar-completo.png`
- `navbar-usuario-menu.png`
- `footer.png`
- `loading-spinner.png`
- `mensaje-error.png`
- `mensaje-exito.png`

---

## Requisitos de las Capturas

### Técnicos
- **Formato**: PNG (preferido) o JPG
- **Resolución mínima**: 1280x720
- **Resolución recomendada**: 1920x1080
- **Tamaño máximo**: 2 MB por imagen
- **DPI**: 72-96 (web estándar)

### Contenido
- ✅ Proyecto de prueba debe llamarse **"PRODOX"** (exacto)
- ✅ Datos ficticios representativos
- ✅ UI completa y legible
- ✅ Sin información sensible (passwords, tokens, API keys)
- ✅ Sin datos personales reales
- ✅ Navegador en ventana consistente

### Estética
- ✅ Sin menús superpuestos que oculten información importante
- ✅ Sin mensajes temporales (toasts) que distraigan
- ✅ Ventana del navegador centrada
- ✅ Zoom al 100%
- ✅ Sin extensiones del navegador visibles

---

## Proceso de Captura

### Opción 1: Playwright (Automatizado)
```javascript
await page.screenshot({ 
  path: 'docs/public/images/modulo/archivo.png',
  fullPage: false,
  clip: { x: 0, y: 0, width: 1280, height: 720 }
});
```

### Opción 2: Manual
1. Navegar a la pantalla deseada
2. Esperar a que la UI esté completamente cargada
3. Usar Snipping Tool o herramienta similar
4. Guardar con el nombre convencional
5. Verificar que sea legible

---

## Uso en Markdown

### Sintaxis
```markdown
![Descripción alt](../public/images/modulo/archivo.png)
```

### Ejemplo
```markdown
![Formulario de login con campos de email y password](../public/images/auth/auth-login-formulario.png)
```

### Con Tamaño Ajustado
```markdown
<img src="../public/images/dashboard/dashboard-vista-completa.png" alt="Vista completa del dashboard" width="800">
```

---

## Optimización de Imágenes

Si las imágenes son muy grandes, considerar:

```bash
# Comprimir PNG (mantener calidad)
pngquant archivo.png --quality=65-80

# Convertir a WebP (mejor compresión)
cwebp -q 80 archivo.png -o archivo.webp
```

**Nota**: VitePress soporta PNG, JPG, SVG y WebP.

---

## Checklist de Captura

Antes de considerar una captura como "lista":

- [ ] Resolución adecuada (≥1280x720)
- [ ] Nombre sigue la convención
- [ ] Proyecto mostrado es "PRODOX"
- [ ] Datos son ficticios
- [ ] No hay información sensible
- [ ] UI está completamente cargada
- [ ] Texto es legible
- [ ] Colores se ven correctamente
- [ ] Sin elementos superpuestos problemáticos
- [ ] Guardada en la carpeta correcta

---

## Mantenimiento

### Agregar Nueva Captura
1. Tomar/generar la captura
2. Nombrar según convención
3. Guardar en carpeta correspondiente
4. Referenciar en archivo `.md` del manual
5. Verificar que se vea correctamente en preview

### Actualizar Captura Existente
1. Reemplazar archivo manteniendo el mismo nombre
2. Verificar referencias en documentación
3. Rebuild de VitePress si es necesario

### Eliminar Captura Obsoleta
1. Buscar todas las referencias en archivos `.md`
2. Eliminar o actualizar referencias
3. Eliminar archivo de imagen
4. Verificar que no queden enlaces rotos

---

## Estadísticas

**Total de capturas planificadas**: ~180 imágenes

**Distribución por módulo**:
- auth: 10-14 capturas
- proyectos: 11-15 capturas
- metricas: 17-26 capturas
- planeacion: 9-12 capturas
- backlog: 7-9 capturas
- sprints: 9-12 capturas
- ejecucion: 11-14 capturas
- dashboard: 16-21 capturas
- evaluacion: 14-18 capturas
- ia: 18-24 capturas
- equipo: 10-14 capturas
- configuracion: 8-11 capturas
- general: 10-15 capturas

---

**Última actualización**: 2026-10-19  
**Responsable**: Kiro AI  
**Proyecto**: PRODOX V1 Manual VitePress
