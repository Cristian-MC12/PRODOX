# Inicio de Sesión y Autenticación Segura

Aprende a ingresar a PRODOX V1 utilizando tus credenciales de usuario para acceder a tus proyectos y herramientas analíticas.

---

## ¿Qué es la Autenticación en PRODOX V1?

La pantalla de acceso (`/auth`) es la puerta de entrada a la plataforma. Implementa un sistema de autenticación seguro basado en **JSON Web Tokens (JWT)**:

- 🛡️ **Seguridad Criptográfica**: Cada inicio de sesión exitoso emite un token JWT firmado por el backend Spring Boot.
- ⏱️ **Persistencia de Sesión (24 Horas)**: El token se conserva localmente en el navegador (`localStorage.getItem('mpdia_token')`), manteniendo tu sesión activa durante 24 horas continuas sin desconexiones imprevistas.
- 🔄 **Redirección Contextual**: Al autenticarte, el sistema te redirige de manera automática a la vista principal de proyectos (`/proyectos`).

---

## Requisitos Previos

- ✅ Contar con una cuenta registrada en PRODOX V1 (o haber sido invitado a un proyecto por tu Scrum Master).
- ✅ Navegador web compatible con cookies y almacenamiento local activado.
- ✅ Conectividad con el servidor de la aplicación.

---

## Procedimiento de Inicio de Sesión

### Paso 1: Acceder a la Pantalla de Acceso

Ingresa a la URL base de PRODOX (`/auth`). La interfaz presentará el formulario unificado de acceso. En la esquina superior derecha dispones del acceso directo a la **"Documentación"** para consultar este manual en cualquier momento:

![Formulario interactivo de inicio de sesión en PRODOX V1](/images/auth/login-formulario.png)

### Paso 2: Ingresar Credenciales

1. **Email**: Introduce la dirección de correo electrónico con la que te diste de alta en la plataforma.
2. **Contraseña**: Escribe tu contraseña de acceso. Puedes hacer clic en el icono del ojo en el extremo derecho del campo para alternar la visibilidad de los caracteres y verificar la escritura.

### Paso 3: Enviar el Formulario

Haz clic en el botón principal **"Iniciar Sesión"**. 

El cliente web valida en tiempo real:
- Que el formato del correo cumpla el estándar (`usuario@dominio.com`).
- Que el campo de contraseña no se encuentre vacío.
- Que el servidor confirme criptográficamente la validez de las credenciales.

### Paso 4: Redirección al Espacio de Trabajo

Al validar tus datos con éxito, el sistema almacena el token JWT de sesión (`mpdia_token`) y el objeto de perfil (`mpdia_user`) en `localStorage`, redirigiéndote de inmediato al catálogo de proyectos:

![Redirección exitosa a la pantalla de proyectos tras el inicio de sesión](/images/auth/login-exitoso.png)

---

## Manejo de Validaciones y Errores

### ❌ Credenciales Inválidas (Error 401)
Si la combinación de correo electrónico o contraseña no coincide con los registros del sistema, la aplicación desplegará una alerta en color rojo indicando el error de autenticación:

![Alerta de error al ingresar credenciales incorrectas en el formulario de login](/images/auth/login-error-credenciales.png)

> [!TIP]
> Si no recuerdas tu clave de acceso, no intentes adivinarla repetidamente. Utiliza el enlace inferior para [recuperar tu contraseña de forma segura](/auth/reset-password).

### ❌ Formato de Correo Electrónico Incorrecto
Si ingresas un texto que no corresponde a una estructura de correo válida, el campo se resaltará en rojo y el botón de inicio de sesión permanecerá inhabilitado hasta corregir la entrada.

---

## Cómo Continuar

Dependiendo de tu situación actual:

- **¿No tienes una cuenta aún?** 👉 Procede a [Registrar una nueva cuenta](/auth/registro).
- **¿Olvidaste tu contraseña?** 👉 Consulta el procedimiento de [Recuperación de contraseña](/auth/reset-password).
- **¿Ya iniciaste sesión con éxito?** 👉 Avanza a la siguiente etapa: [Preparación del Proyecto y Listado](/proyectos/listado).
