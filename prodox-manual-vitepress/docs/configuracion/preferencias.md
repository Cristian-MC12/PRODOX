# Gestión de Sesión, Cuenta y Entorno

Conoce cómo PRODOX V1 gestiona la identidad del usuario, las preferencias visuales, la persistencia de sesión y el cierre seguro de cuenta.

---

## Filosofía de Entorno en PRODOX V1

PRODOX V1 está diseñado bajo el principio de **cero fricción operativa**: la plataforma no obliga al usuario a navegar por complejos paneles de configuración de preferencias, sino que adapta su comportamiento de forma desatendida a los estándares del sistema y navegador:

- 🌓 **Paleta Visual y Alto Contraste**: Interfaz optimizada con diseño responsivo oscuro y equilibrado que garantiza máxima legibilidad de tablas, métricas y gráficas tanto en entornos diurnos como nocturnos.
- 🌐 **Formatos Regionales Automáticos**: Las fechas (`dd/MM/yyyy`), separadores numéricos de miles y decimales en las métricas se formatean automáticamente respetando la configuración regional de tu navegador web.
- 📱 **Modo Adaptativo y Colapso del Menú**: Posibilidad de alternar el menú lateral entre modo expandido y modo colapsado para maximizar el área de trabajo en pantallas compactas o dispositivos móviles.

---

## Información de Cuenta y Rol en la Barra Lateral

En la parte inferior de la barra de navegación lateral izquierda (*sidebar*), PRODOX presenta tu tarjeta de identidad:

- **Avatar de Usuario**: Inicial de tu nombre en un círculo distintivo.
- **Nombre de Usuario**: Nombre registrado en tu cuenta (o correo electrónico de alta).
- **Rol en el Proyecto Activo**: Etiqueta dinámica (`Scrum Master`, `Product Owner` o `Scrum Member`) que indica tus permisos vigentes en el proyecto seleccionado.
- **Botón de Colapso (`<` / `>`)**: Ubicado en la parte superior del menú, permite replegar la barra lateral para dejar visibles únicamente los iconos de navegación. El estado elegido se guarda en `localStorage` (`mpdia_sidebar_collapsed`) para recordarlo en tus próximas sesiones.

---

## Persistencia de Sesión y Contexto de Trabajo

Para evitar recargas lentas o pérdidas accidentales de trabajo, PRODOX V1 gestiona dos elementos clave en el almacenamiento local del navegador (`localStorage`):

### 1. Token Criptográfico de Autenticación (`mpdia_token`)
- Al iniciar sesión con éxito en `/auth`, el servidor Spring Boot emite un JSON Web Token (JWT).
- La sesión permanece activa de manera continua durante **24 horas**, permitiéndote trabajar a lo largo de tu jornada sin necesidad de reautenticarte constantemente.

### 2. Contexto del Proyecto Activo (`mpdia_proyecto_activo`)
- Cuando seleccionas un proyecto pulsando **"Trabajar"** en el [Listado de Proyectos](/proyectos/listado), sus datos se retienen localmente.
- Este contexto acompaña tu navegación por todos los módulos del proyecto (Planeación, Sprints, Backlog, Ejecución, Evaluación y Dashboard).
- Para alternar de proyecto en cualquier momento, basta con regresar a **Proyectos** y hacer clic en **"Trabajar"** sobre otra tarjeta.

---

## Cierre de Sesión Seguro

Cuando concluyas tus labores en la plataforma o vayas a utilizar un equipo compartido:

1. Localiza tu tarjeta de usuario en la esquina inferior izquierda del menú lateral.
2. Haz clic en el botón **"Cerrar sesión"** (o en el icono de salida si el menú está colapsado).
3. La aplicación ejecutará el procedimiento seguro de desconexión:
   - Purgará el token de autenticación (`mpdia_token`) de `localStorage`.
   - Limpiará el contexto del proyecto activo (`mpdia_proyecto_activo`).
   - Te redirigirá de inmediato a la pantalla de acceso (`/auth`).

---

## Resumen del Flujo de Trabajo en PRODOX V1

Has completado el recorrido integral por todas las capacidades de PRODOX V1:

```
[ Acceso (/auth) ] 
       ↓
[ Preparación del Proyecto (/proyectos) ]
       ↓
[ Preparación del Equipo y Roles (/equipo) ]
       ↓
[ Backlog y Sprints (/backlog, /sprints) ]
       ↓
[ Planeación de Métricas (/planeacion) ]
       ↓
[ Ejecución y Captura (/ejecucion) ]
       ↓
[ Evaluación y Tendencias (/evaluacion) ]
       ↓
[ Dashboard e Informes Word / IA (/dashboard) ]
```

Para volver a consultar el mapa de flujo interactivo y sus requisitos:

👉 Regresa a la [Guía del Flujo Operativo Integral](/#flujo-operativo-integral) o al [Inicio de la Documentación](/).
