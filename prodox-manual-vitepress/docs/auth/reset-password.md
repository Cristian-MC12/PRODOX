# Recuperación de Contraseña

Aprende a restablecer de forma segura tu contraseña de acceso en caso de olvido o por políticas periódicas de seguridad.

---

## ¿Cuándo Utilizar Esta Función?

Utiliza el flujo de recuperación de contraseña cuando:
- Has olvidado tu contraseña actual.
- Tu cuenta fue bloqueada temporalmente tras múltiples intentos fallidos.
- Deseas renovar tu clave por motivos preventivos de seguridad.

---

## Procedimiento Paso a Paso

### Paso 1: Acceder a la Solicitud de Restablecimiento

1. En la pantalla principal de acceso (`/auth`), haz clic en el enlace **"¿Olvidaste tu contraseña?"** ubicado en la parte inferior del formulario.
2. La aplicación te redirigirá a la vista dedicada de recuperación (`/reset-password`):

![Formulario de solicitud de recuperación de contraseña con campo de email](/images/auth/reset-form.png)

### Paso 2: Ingresar tu Correo Electrónico

1. Escribe la dirección de correo asociada a tu cuenta de PRODOX V1 en el campo de texto.
2. Haz clic en el botón **"Enviar instrucciones"**.

> [!NOTE]
> Para prevenir la enumeración no autorizada de cuentas por motivos de ciberseguridad, el sistema muestra una notificación informativa genérica confirmando que se han despachado las instrucciones al correo indicado, independientemente de si la dirección existe o no en los registros.

### Paso 3: Abrir el Enlace de Restablecimiento

1. Abre tu bandeja de entrada de correo electrónico y localiza el mensaje enviado por PRODOX.
2. Haz clic en el botón o enlace seguro contenido en el mensaje (`/reset-password?token=...`).
3. El enlace incluye un token criptográfico de un solo uso que te conducirá a la pantalla para ingresar tu nueva clave:

![Formulario seguro para establecer la nueva contraseña en PRODOX V1](/images/auth/reset-password-form.png)

### Paso 4: Definir y Confirmar la Nueva Contraseña

1. **Nueva Contraseña**: Introduce tu nueva clave respetando los criterios de seguridad (mínimo 8 caracteres).
2. **Confirmar Contraseña**: Reitera la misma clave para validar que ambas coincidan.
3. Haz clic en el botón **"Guardar nueva contraseña"**.
4. El sistema actualizará la credencial en la base de datos y desplegará un mensaje de confirmación de éxito con el botón **"Ir a iniciar sesión"**.

---

## Cómo Continuar

Una vez restablecida tu contraseña con éxito:

👉 Regresa a la pantalla de [Inicio de Sesión](/auth/login) e ingresa a tu cuenta con la nueva clave para continuar hacia la [Preparación del Proyecto](/proyectos/listado).
