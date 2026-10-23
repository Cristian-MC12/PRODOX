# Requisitos del Sistema y Entorno de Trabajo

Antes de comenzar a operar en PRODOX V1, verifica que tu equipo y entorno de red cumplan con los siguientes requisitos técnicos y operativos.

---

## Requisitos Técnicos del Cliente

PRODOX V1 es una aplicación web de última generación construida en Angular 17+ que opera de forma fluida en clientes ligeros sin necesidad de instalar plugins o extensiones locales:

### 1. Navegadores Web Compatibles
La plataforma está optimizada para navegadores modernos con soporte completo para estándares ECMAScript 2022+ y renderizado HTML5 Canvas (requerido para las gráficas interactivas de Chart.js):

- ✅ **Google Chrome**: Versión 100 o superior (Recomendado).
- ✅ **Mozilla Firefox**: Versión 100 o superior.
- ✅ **Microsoft Edge**: Versión 100 o superior (basado en Chromium).
- ✅ **Apple Safari**: Versión 15 o superior en macOS/iOS.

> [!NOTE]
> Es indispensable mantener habilitada la ejecución de JavaScript y el acceso a `localStorage` para permitir la retención del token JWT de sesión y el contexto del proyecto activo.

### 2. Resolución de Pantalla y Visualización
La interfaz incluye un diseño completamente adaptativo con barra lateral colapsable:

- **Resolución Mínima**: `1280 x 720` píxeles (HD estándar).
- **Resolución Recomendada**: `1920 x 1080` píxeles (Full HD) para una visualización panorámica óptima de las matrices comparativas de métricas y los dashboards analíticos.
- **Dispositivos Móviles y Tablets**: El menú de navegación incorpora colapso automático y modo drawer para consultas y captura de valores en campo.

### 3. Conectividad y Red
- **Conexión a Internet**: Acceso estable con ancho de banda mínimo sugerido de 2 Mbps para la descarga de bundles web y respuesta en tiempo real de consultas REST.
- **Acceso a Endpoints del Backend**: El cortafuegos o proxy corporativo debe permitir peticiones HTTP/HTTPS hacia la API de Spring Boot (`/api/**`) y habilitar los encabezados de autorización estándar (`Authorization: Bearer <token>`).

---

## Requisitos de Conocimiento Metodológico

Para aprovechar al máximo las capacidades analíticas de la plataforma, es conveniente contar con familiaridad en:

1. **Fundamentos de Scrum y Marcos Ágiles**:
   - Concepto de iteración de tiempo fijo (*Timebox / Sprint*).
   - Roles ágiles (Scrum Master, Product Owner, Miembros del Equipo de Desarrollo).
   - Eventos ágiles (Planeación, Daily Standup, Revisión y Retrospectiva).
2. **Estimación y Gestión del Backlog**:
   - Redacción de Historias de Usuario bajo la estructura estándar (*Como / Quiero / Para*).
   - Asignación de esfuerzo relativo en Story Points utilizando la escala de Fibonacci.
3. **Métricas de Software e Indicadores**:
   - Conceptos de velocidad, defectos, deuda técnica y factores sociohumanos.

---

## Cómo Continuar

Una vez verificado tu entorno de trabajo:

👉 Continúa con el siguiente paso: [Acceso a la Plataforma e Inicio de Sesión](/auth/login).
