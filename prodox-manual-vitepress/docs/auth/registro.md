# Registro de Nueva Cuenta de Usuario

Crea tu cuenta de acceso en PRODOX V1 para comenzar a liderar o colaborar en proyectos de desarrollo ágil.

---

## ¿Qué es el Registro en PRODOX V1?

El registro de usuario crea una identidad digital única en la base de datos de PRODOX. Al registrarte obtienes:

- 👤 **Identidad Verificada**: Capacidad de crear proyectos ágiles y convertirte automáticamente en su Scrum Master.
- 📨 **Recepción de Invitaciones**: Posibilidad de unirte como Product Owner o Scrum Member a proyectos existentes mediante códigos de invitación.
- 📊 **Historial Individual**: Trazabilidad de las mediciones y variables asignadas a tu cuenta en los módulos de Ejecución y Evaluación.

---

## Requisitos Previos

- ✅ Dirección de correo electrónico activa y de uso exclusivo (no se permiten duplicados).
- ✅ Definir una contraseña segura que cumpla con los estándares de robustez del sistema.
- ✅ Navegador web moderno con acceso a la red de PRODOX.

---

## Procedimiento de Registro Paso a Paso

### Paso 1: Acceder a la Pestaña de Registro

1. Ingresa a la pantalla de acceso de PRODOX (`/auth`).
2. En la parte inferior del formulario de inicio de sesión, localiza el enlace: **"¿No tienes una cuenta? Regístrate aquí"**.
3. Al hacer clic, la vista alternará de forma fluida hacia la pestaña de creación de cuenta:

![Formulario unificado de registro de nuevo usuario en PRODOX V1](/images/auth/registro-form.png)

### Paso 2: Completar los Campos Obligatorios

Diligencia los datos solicitados en el formulario:

| Campo | Descripción | Regla de Validación |
|---|---|---|
| **Nombre Completo** | Nombre y apellido para identificarte en el equipo | Mínimo 2 caracteres; no puede dejarse en blanco (`Ej: Cristian Martinez`). |
| **Correo Electrónico** | Tu dirección de contacto profesional o académica | Debe tener formato válido (`ejemplo@correo.com`) y ser único en el sistema. |
| **Contraseña** | Clave secreta para proteger el acceso | Mínimo **8 caracteres** (dispone de icono de ojo para alternar visibilidad). |
| **Rol Inicial** | Perfil inicial de cuenta para la plataforma | Selección en menú desplegable: **💻 Desarrollador** (`scrum_member`) o **🎯 Scrum Master** (`scrum_master`). |

> [!NOTE]
> La plataforma también permite el registro e inicio de sesión federado directo a través del botón **"Continuar con Google"**.

### Paso 3: Enviar la Solicitud de Registro

1. Haz clic en el botón principal **"Registrarse"**.
2. El cliente enviará la petición `POST /api/auth/register` al backend Spring Boot con los datos diligenciados.
3. El servidor encriptará la contraseña utilizando algoritmos seguros de hashing (*BCrypt*) y generará tu identificador de usuario único.

### Paso 4: Inicio Automático y Acceso a Proyectos

Tras la creación exitosa del usuario:
- El backend emite automáticamente tu token JWT de sesión (`mpdia_token`).
- La aplicación almacena la credencial en `localStorage` y te redirige directamente a la vista de [Listado de Proyectos](/proyectos/listado), donde podrás crear tu primer proyecto o unirte a uno existente.

---

## Errores Frecuentes al Registrarse

### ❌ El Correo Ya se Encuentra en Uso
Si intentas registrar un correo que ya existe en la base de datos, el sistema desplegará un mensaje de advertencia. En ese caso:
- Si ya eres el propietario de esa cuenta, dirígete al [Inicio de sesión](/auth/login).
- Si olvidaste tu contraseña, recurre al proceso de [Recuperación de contraseña](/auth/reset-password).

### ❌ Contraseña Débil
Si la contraseña ingresada no cumple con la longitud mínima o carece de mayúsculas/números, el formulario indicará los requisitos faltantes debajo del campo de texto.

---

## Cómo Continuar

- **¿Ya creaste tu cuenta?** 👉 Continúa directamente a la etapa de [Preparación del Proyecto y Listado](/proyectos/listado).
- **¿Prefieres ingresar con una cuenta existente?** 👉 Regresa a [Inicio de Sesión](/auth/login).
