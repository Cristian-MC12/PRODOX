# 🚀 Guía Completa de Despliegue en Railway - PRODOX

Esta guía te llevará paso a paso por todo el proceso de despliegue de tu aplicación PRODOX en Railway, desde cero hasta tenerla completamente funcional en producción.

---

## 📚 Tabla de Contenidos

1. [Pre-requisitos](#-pre-requisitos)
2. [Arquitectura del Despliegue](#️-arquitectura-del-despliegue)
3. [Paso 1: Preparar Credenciales Externas](#-paso-1-preparar-credenciales-externas)
4. [Paso 2: Configurar Railway](#-paso-2-configurar-railway)
5. [Paso 3: Desplegar Backend](#️-paso-3-desplegar-backend-spring-boot)
6. [Paso 4: Desplegar Frontend](#-paso-4-desplegar-frontend-angular)
7. [Paso 5: Configuración Final](#-paso-5-configuración-final)
8. [Paso 6: Verificación](#-paso-6-verificación)
9. [Troubleshooting](#-troubleshooting)
10. [Variables de Entorno - Referencia Completa](#-variables-de-entorno---referencia-completa)

---

## 🎯 Pre-requisitos

Antes de comenzar, asegúrate de tener:

### Cuentas Necesarias

- [ ] **Cuenta de GitHub** (donde está tu código)
- [ ] **Cuenta de Railway** (plataforma de despliegue) - [railway.app](https://railway.app)
- [ ] **Cuenta de Google Cloud** (para OAuth y Gemini AI) - [console.cloud.google.com](https://console.cloud.google.com)
- [ ] **Cuenta de Gmail** (para envío de emails, puede ser la misma de Google Cloud)
- [ ] **(Opcional) Cuenta de Vercel** - Si quieres desplegar el frontend en Vercel en lugar de Railway

### Conocimientos Básicos

- Git y GitHub
- Variables de entorno
- Navegación en interfaces web

### Tiempo Estimado

⏱️ **45-60 minutos** (primera vez)

---

## 🏗️ Arquitectura del Despliegue

Tu aplicación PRODOX se desplegará con la siguiente arquitectura:

```
┌─────────────────────────────────────────────────────────────┐
│                         RAILWAY                             │
│                                                             │
│  ┌─────────────────┐         ┌──────────────────┐         │
│  │  PostgreSQL DB  │◄────────│  Spring Boot API │         │
│  │   (Railway)     │         │    (Backend)     │         │
│  └─────────────────┘         └──────────────────┘         │
│                                       │                     │
│                                       │ API REST            │
└───────────────────────────────────────┼─────────────────────┘
                                        │
                                        ▼
                    ┌──────────────────────────────┐
                    │   Angular Frontend           │
                    │   (Railway o Vercel)         │
                    └──────────────────────────────┘
                                        │
                    ┌───────────────────┼───────────────────┐
                    │                   │                   │
                    ▼                   ▼                   ▼
           ┌────────────────┐  ┌────────────────┐ ┌────────────────┐
           │ Google OAuth   │  │  Gmail SMTP    │ │  Gemini AI     │
           │   (Login)      │  │   (Emails)     │ │  (Copilot)     │
           └────────────────┘  └────────────────┘ └────────────────┘
```

**Componentes:**
1. **Backend Spring Boot** - API REST en Railway
2. **Frontend Angular** - Interfaz web en Railway o Vercel
3. **PostgreSQL** - Base de datos en Railway
4. **Google OAuth** - Autenticación con Google
5. **Gmail SMTP** - Envío de emails (invitaciones, recuperación de contraseña)
6. **Gemini AI** - Asistente inteligente (AI Copilot)

---

## 🔑 Paso 1: Preparar Credenciales Externas

Antes de desplegar en Railway, necesitas obtener las credenciales de servicios externos.

### 1.1 Google Gemini AI (API Key)

**¿Para qué?** El AI Copilot que asiste en la gestión de proyectos.

**Pasos:**

1. Ve a [Google AI Studio](https://aistudio.google.com/app/apikey)
2. Inicia sesión con tu cuenta de Google
3. Click en **"Create API Key"** o **"Crear clave de API"**
4. Selecciona un proyecto existente o crea uno nuevo
5. Copia la clave generada (empieza con `AIza...`)
6. **Guárdala en un lugar seguro** (la necesitarás después)

**Ejemplo:** `AIzaSyC8xxxxxxxxxxxxxxxxxxxxxxxxxxx`

---

### 1.2 Google OAuth 2.0 (Client ID y Client Secret)

**¿Para qué?** Permitir a los usuarios iniciar sesión con su cuenta de Google.

**Pasos detallados:**

#### A. Crear Proyecto en Google Cloud Console

1. Ve a [Google Cloud Console](https://console.cloud.google.com)
2. En la barra superior, haz click en el selector de proyectos
3. Click en **"New Project"** / **"Nuevo Proyecto"**
4. Nombre del proyecto: `PRODOX` (o el que prefieras)
5. Click en **"Create"** / **"Crear"**
6. Espera a que se cree el proyecto (puede tardar unos segundos)

#### B. Habilitar Google+ API

1. En el menú lateral, ve a **"APIs & Services"** > **"Library"**
2. Busca **"Google+ API"** o **"People API"**
3. Click en el resultado
4. Click en **"Enable"** / **"Habilitar"**

#### C. Configurar Pantalla de Consentimiento OAuth

1. En el menú lateral, ve a **"APIs & Services"** > **"OAuth consent screen"**
2. Selecciona **"External"** (usuarios externos)
3. Click en **"Create"** / **"Crear"**

**Información de la aplicación:**
- **App name:** PRODOX
- **User support email:** tu-email@gmail.com
- **App logo:** (opcional, puedes dejarlo vacío por ahora)
- **Application home page:** Déjalo vacío por ahora (lo actualizarás después)
- **Application privacy policy:** Déjalo vacío por ahora
- **Application terms of service:** Déjalo vacío por ahora
- **Authorized domains:** (déjalo vacío por ahora)
- **Developer contact information:** tu-email@gmail.com

4. Click en **"Save and Continue"** / **"Guardar y continuar"**

**Scopes (alcances):**
5. Click en **"Add or Remove Scopes"**
6. Selecciona:
   - `.../auth/userinfo.email`
   - `.../auth/userinfo.profile`
7. Click en **"Update"** / **"Actualizar"**
8. Click en **"Save and Continue"** / **"Guardar y continuar"**

**Test users:** (opcional)
9. Puedes agregar usuarios de prueba o dejarlo vacío
10. Click en **"Save and Continue"** / **"Guardar y continuar"**

**Resumen:**
11. Revisa que todo esté correcto
12. Click en **"Back to Dashboard"** / **"Volver al panel"**

#### D. Crear Credenciales OAuth

1. En el menú lateral, ve a **"APIs & Services"** > **"Credentials"**
2. Click en **"Create Credentials"** / **"Crear credenciales"**
3. Selecciona **"OAuth client ID"** / **"ID de cliente de OAuth"**
4. **Application type:** Web application
5. **Name:** PRODOX Backend

**Authorized JavaScript origins:**
6. Click en **"Add URI"** / **"Agregar URI"**
7. Agrega: `http://localhost:8080` (para desarrollo local)

**Authorized redirect URIs:**
8. Click en **"Add URI"** / **"Agregar URI"**
9. Por ahora, agrega solo esta URI de desarrollo:
   ```
   http://localhost:8080/login/oauth2/code/google
   ```
   
   ⚠️ **NOTA:** Después de desplegar en Railway, volverás aquí para agregar la URI de producción.

10. Click en **"Create"** / **"Crear"**

**Guardar Credenciales:**
11. Se abrirá un modal con tus credenciales
12. **Copia y guarda:**
    - **Client ID:** (algo como `123456789-abcdefg.apps.googleusercontent.com`)
    - **Client Secret:** (algo como `GOCSPX-abc123xyz`)
13. Click en **"OK"**

---

### 1.3 Gmail SMTP (Contraseña de Aplicación)

**¿Para qué?** Enviar emails de invitación a proyectos y recuperación de contraseña.

**Pasos:**

#### A. Habilitar Verificación en 2 Pasos

1. Ve a [Cuenta de Google](https://myaccount.google.com)
2. En el menú lateral, ve a **"Seguridad"** / **"Security"**
3. Busca **"Verificación en 2 pasos"** / **"2-Step Verification"**
4. Si no está habilitada, haz click en **"Comenzar"** / **"Get Started"**
5. Sigue los pasos para habilitar la verificación en 2 pasos

#### B. Generar Contraseña de Aplicación

1. Una vez habilitada la verificación en 2 pasos, ve a [Contraseñas de aplicación](https://myaccount.google.com/apppasswords)
2. Puede que te pida tu contraseña de Google nuevamente
3. En **"Seleccionar app"** / **"Select app"**: elige **"Otro (nombre personalizado)"** / **"Other (custom name)"**
4. Escribe: `PRODOX Backend`
5. Click en **"Generar"** / **"Generate"**
6. Google te mostrará una contraseña de 16 caracteres (sin espacios)
7. **Copia y guarda esta contraseña** (no la podrás ver de nuevo)

**Ejemplo:** `abcd efgh ijkl mnop` → Guárdala sin espacios: `abcdefghijklmnop`

---

### 1.4 Generar JWT Secret

Para la seguridad de los tokens JWT, necesitas una clave secreta aleatoria.

**En PowerShell (Windows):**
```powershell
[Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 }))
```

Copia el resultado, se verá algo así:
```
MjE3IDE4NyA5MSAyMzQgMTIxIDIwNSAxNDMgMjA5IDE5MyAyMjEgMTQ2IDI0...
```

---

### 📝 Resumen de Credenciales Obtenidas

Crea un archivo temporal en tu computadora (⚠️ **NO lo subas a Git**) con estas credenciales:

```
# ================================
# CREDENCIALES PRODOX - NO COMPARTIR
# ================================

## Gemini AI
PRODOX_GEMINI_API_KEY=AIzaSy...

## Google OAuth
GOOGLE_CLIENT_ID=123456789-abc...apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-abc123...

## Gmail SMTP
GMAIL_ADDRESS=tu-email@gmail.com
GMAIL_APP_PASSWORD=abcdefghijklmnop

## JWT Secret
PRODOX_JWT_SECRET=<resultado-del-comando-powershell>
```

---

## 🚂 Paso 2: Configurar Railway

### 2.1 Crear Cuenta en Railway

1. Ve a [railway.app](https://railway.app)
2. Click en **"Login"** en la esquina superior derecha
3. Selecciona **"Login with GitHub"**
4. Autoriza Railway para acceder a tu cuenta de GitHub
5. Completa tu perfil si es necesario

**Plan Gratuito:** Railway ofrece $5 USD de crédito gratis cada mes, suficiente para desarrollo y pruebas.

---

### 2.2 Crear Nuevo Proyecto

1. Una vez logueado, click en **"New Project"**
2. Selecciona **"Deploy from GitHub repo"**
3. Si es la primera vez, Railway pedirá permisos para acceder a tus repositorios
4. Click en **"Configure GitHub App"**
5. Selecciona **"All repositories"** o **"Only select repositories"**
6. Si eliges "Only select repositories", busca y selecciona `MPDIA-SM` (o el nombre de tu repositorio)
7. Click en **"Save"** / **"Guardar"**
8. Vuelve a Railway y ahora deberías ver tu repositorio en la lista
9. **NO hagas click en el repositorio todavía** (primero agregaremos la base de datos)

---

### 2.3 Configuración Inicial del Proyecto

1. En lugar de hacer click en el repositorio, click en **"New Project"** nuevamente
2. Esta vez selecciona **"Empty Project"** / **"Proyecto vacío"**
3. Railway creará un proyecto vacío
4. Puedes renombrar el proyecto haciendo click en el nombre (arriba a la izquierda)
5. Nómbralo: `PRODOX` o `PRODOX Production`

---

## 🗄️ Paso 3: Desplegar Backend (Spring Boot)

### 3.1 Agregar PostgreSQL

1. En tu proyecto vacío de Railway, click en **"+ New"** (botón morado en la esquina superior derecha)
2. Selecciona **"Database"**
3. Selecciona **"Add PostgreSQL"**
4. Railway creará automáticamente una base de datos PostgreSQL
5. Espera unos segundos a que se provisione (verás un ícono de PostgreSQL en el canvas)

**Variables automáticas creadas:**
Railway crea automáticamente estas variables en el servicio de PostgreSQL:
- `PGHOST`
- `PGPORT`
- `PGUSER`
- `PGPASSWORD`
- `PGDATABASE`
- `DATABASE_URL`

✅ Estas variables estarán disponibles para cualquier servicio en el mismo proyecto.

---

### 3.2 Crear Servicio del Backend

1. Click de nuevo en **"+ New"**
2. Selecciona **"GitHub Repo"**
3. Selecciona tu repositorio `MPDIA-SM`
4. Railway empezará a analizar el repositorio

**Configurar el Root Directory:**
5. Railway creará un servicio, haz click en él (debería aparecer una tarjeta en el canvas)
6. Ve a **"Settings"** (⚙️ en la barra lateral o pestaña superior)
7. Scroll hacia abajo hasta encontrar **"Root Directory"**
8. Click en el campo y cambialo a: `prodox-springboot`
9. Los cambios se guardan automáticamente

---

### 3.3 Configurar Variables de Entorno del Backend

Ahora viene la parte más importante: configurar todas las variables de entorno.

1. En el servicio del backend (tarjeta de Spring Boot), ve a la pestaña **"Variables"** (en la barra superior)
2. Vas a agregar variables una por una

⚠️ **IMPORTANTE:** Reemplaza los valores entre `< >` con tus credenciales reales que guardaste en el Paso 1.

#### A. Activar Perfil de Producción

```
Nombre: SPRING_PROFILES_ACTIVE
Valor: prod
```

#### B. Variables de Base de Datos

Railway usa una sintaxis especial `${{NombreServicio.VARIABLE}}` para referenciar variables de otros servicios.

El nombre del servicio de PostgreSQL puede variar. Para verificarlo:
- Mira el nombre de la tarjeta de PostgreSQL en el canvas
- Si se llama "Postgres", usa `${{Postgres.VARIABLE}}`
- Si tiene otro nombre, ajusta en consecuencia

```
Nombre: SPRING_DATASOURCE_URL
Valor: jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
```

```
Nombre: SPRING_DATASOURCE_USERNAME
Valor: ${{Postgres.PGUSER}}
```

```
Nombre: SPRING_DATASOURCE_PASSWORD
Valor: ${{Postgres.PGPASSWORD}}
```

```
Nombre: SPRING_DATASOURCE_DRIVER_CLASS_NAME
Valor: org.postgresql.Driver
```

#### C. Variables de JPA

```
Nombre: SPRING_JPA_HIBERNATE_DDL_AUTO
Valor: none
```

```
Nombre: SPRING_JPA_SHOW_SQL
Valor: false
```

```
Nombre: SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT
Valor: org.hibernate.dialect.PostgreSQLDialect
```

#### D. Variables de Flyway

```
Nombre: SPRING_FLYWAY_BASELINE_ON_MIGRATE
Valor: true
```

```
Nombre: SPRING_FLYWAY_LOCATIONS
Valor: classpath:db/migration
```

```
Nombre: SPRING_FLYWAY_REPAIR_ON_MIGRATE
Valor: true
```

```
Nombre: SPRING_FLYWAY_VALIDATE_ON_MIGRATE
Valor: false
```

#### E. Variables de JWT

```
Nombre: PRODOX_JWT_SECRET
Valor: <tu-jwt-secret-generado-en-paso-1.4>
```

```
Nombre: PRODOX_JWT_EXPIRATION_MS
Valor: 86400000
```

#### F. Variables de CORS

Por ahora, usa un wildcard temporal (lo cambiaremos después cuando tengas la URL del frontend):

```
Nombre: PRODOX_CORS_ALLOWED_ORIGINS
Valor: *
```

⚠️ **NOTA:** Esto permitirá solicitudes desde cualquier origen. Lo restringiremos más adelante.

#### G. Variables de Gemini AI

```
Nombre: PRODOX_GEMINI_API_KEY
Valor: <tu-gemini-api-key-del-paso-1.1>
```

```
Nombre: PRODOX_GEMINI_API_URL
Valor: https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent
```

#### H. Variables de Google OAuth

```
Nombre: SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID
Valor: <tu-client-id-del-paso-1.2>
```

```
Nombre: SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET
Valor: <tu-client-secret-del-paso-1.2>
```

```
Nombre: SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_SCOPE
Valor: email,profile
```

```
Nombre: SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_REDIRECT_URI
Valor: {baseUrl}/login/oauth2/code/google
```

⚠️ **NOTA:** `{baseUrl}` es un placeholder que Spring Security reemplaza automáticamente con la URL de tu aplicación.

#### I. Variables de Email (Gmail SMTP)

```
Nombre: SPRING_MAIL_HOST
Valor: smtp.gmail.com
```

```
Nombre: SPRING_MAIL_PORT
Valor: 587
```

```
Nombre: SPRING_MAIL_USERNAME
Valor: <tu-email-gmail-del-paso-1.3>
```

```
Nombre: SPRING_MAIL_PASSWORD
Valor: <tu-password-de-aplicacion-del-paso-1.3>
```

```
Nombre: SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH
Valor: true
```

```
Nombre: SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE
Valor: true
```

Por ahora, usa un placeholder para la URL de la app (lo cambiaremos después):

```
Nombre: PRODOX_APP_URL
Valor: https://prodox-app.com
```

#### J. Variables de Rate Limiting y Configuración

```
Nombre: PRODOX_AI_RATE_LIMIT_REQUESTS_PER_MINUTE
Valor: 10
```

```
Nombre: PRODOX_AI_RATE_LIMIT_WINDOW_SECONDS
Valor: 60
```

```
Nombre: PRODOX_AI_MAX_HISTORY_MESSAGES
Valor: 10
```

```
Nombre: PRODOX_PASSWORD_RESET_EXPIRATION_MINUTES
Valor: 30
```

```
Nombre: PRODOX_INVITACION_EXPIRATION_DAYS
Valor: 7
```

---

### 3.4 Configurar el Build y Start Command

1. Ve a **"Settings"** del servicio backend
2. Scroll hasta la sección **"Build"**

**Build Command:**
Railway debería detectarlo automáticamente, pero verifica que sea similar a:
```bash
mvn clean package -DskipTests
```

Si está vacío o diferente, configúralo manualmente.

**Start Command:**
```bash
java -jar target/prodox-backend-0.0.1-SNAPSHOT.jar
```

⚠️ **NOTA:** El nombre del JAR debe coincidir con lo que está en tu `pom.xml`:
- `<artifactId>prodox-backend</artifactId>`
- `<version>0.0.1-SNAPSHOT</version>`
- Resultado: `prodox-backend-0.0.1-SNAPSHOT.jar`

Si tu JAR tiene otro nombre, ajusta el comando.

---

### 3.5 Generar Dominio Público

1. Aún en **"Settings"** del servicio backend
2. Scroll hasta la sección **"Networking"**
3. Click en **"Generate Domain"**
4. Railway generará una URL pública como: 
   ```
   https://prodox-backend-production-abc123.up.railway.app
   ```
5. **🔖 Copia esta URL y guárdala** (la necesitarás para el frontend)

---

### 3.6 Desplegar el Backend

¡Ahora sí, es hora de desplegar!

1. Ve a la pestaña **"Deployments"** (en la barra superior del servicio)
2. Railway debería haber empezado a desplegar automáticamente cuando agregaste las variables
3. Si no hay despliegue en curso, haz un pequeño cambio en las variables (agrega un espacio y bórralo) para forzar un redespliegue

**El proceso tomará varios minutos (5-10 min la primera vez):**

Podrás ver el progreso en tiempo real:
- ⏳ **Cloning repository** - Clonando el código
- ⏳ **Installing Maven** - Instalando herramientas de build
- ⏳ **Resolving dependencies** - Descargando librerías de Spring Boot
- ⏳ **Compiling** - Compilando código Java
- ⏳ **Packaging JAR** - Creando archivo ejecutable
- ⏳ **Starting application** - Iniciando Spring Boot
- ⏳ **Running Flyway migrations** - Creando tablas en la base de datos
- ✅ **Application started** - ¡Aplicación corriendo!

**Ver logs en tiempo real:**
- Click en el despliegue activo (el que tiene el indicador verde o amarillo)
- Los logs se mostrarán automáticamente
- Busca este mensaje de éxito:
  ```
  Started ProdoxBackendApplication in X.XXX seconds
  ```

---

### 3.7 Verificar el Backend

Una vez que el despliegue esté completo y veas **"Active"** o **"Running"**:

1. Copia la URL de tu backend (la que generaste en el paso 3.5)
2. Ábrela en el navegador:
   ```
   https://tu-backend.up.railway.app
   ```

3. **Comportamiento esperado:**
   - Verás una "Whitelabel Error Page" de Spring Boot
   - Esto es **NORMAL** - significa que el backend está corriendo
   - Solo no hay ningún endpoint en la raíz `/`

4. **Prueba un endpoint real:**
   Abre esta URL en el navegador:
   ```
   https://tu-backend.up.railway.app/api/auth/test
   ```
   
   - Si ves **cualquier respuesta** (incluso un error 401 o 403), ¡está funcionando!
   - Si ves un error de conexión o timeout, hay un problema

**Si algo sale mal:**
- Ve a **"Deployments"** > click en el último despliegue
- Revisa los logs buscando errores (palabras clave: `ERROR`, `Exception`, `Failed`)
- Ve a la sección [Troubleshooting](#-troubleshooting) más abajo

---

## 🎨 Paso 4: Desplegar Frontend (Angular)

Tienes dos opciones para desplegar el frontend: **Vercel** (más simple) o **Railway** (todo en un solo lugar).

---

### Opción A: Desplegar en Vercel (Recomendado ⭐)

Vercel está optimizado para aplicaciones frontend como Angular y el despliegue es más simple.

#### 4A.1 Crear Cuenta en Vercel

1. Ve a [vercel.com](https://vercel.com)
2. Click en **"Sign Up"**
3. Selecciona **"Continue with GitHub"**
4. Autoriza Vercel para acceder a tu cuenta de GitHub

---

#### 4A.2 Importar Proyecto

1. En el dashboard de Vercel, click en **"Add New..."** > **"Project"**
2. Busca tu repositorio `MPDIA-SM` en la lista
3. Click en **"Import"** junto al nombre del repositorio

**Configurar el proyecto:**

4. **Framework Preset:** Vercel debería detectar automáticamente "Angular" - déjalo así
5. **Root Directory:** 
   - Click en **"Edit"** junto a "Root Directory"
   - Selecciona `prodox-angular` de la lista
   - Click en **"Continue"**

6. **Build and Output Settings:**
   - **Build Command:** (debería estar pre-llenado)
     ```bash
     npm run build
     ```
   - **Output Directory:** (debería estar pre-llenado)
     ```bash
     dist/prodox-angular/browser
     ```
   - **Install Command:** (debería estar pre-llenado)
     ```bash
     npm install
     ```

---

#### 4A.3 Configurar API URL

Antes de desplegar, necesitas que el frontend sepa dónde está el backend.

**Verifica tu código Angular:**

Busca archivos de entorno en tu código:
- `src/environments/environment.ts`
- `src/environments/environment.prod.ts`

**Si existe `environment.prod.ts`:**

1. Abre el archivo en tu editor local
2. Busca la propiedad `apiUrl` o similar
3. Si usa una variable de entorno de Node (ejemplo: `process.env['API_URL']`), configúrala en Vercel:

   En la configuración de Vercel, en **"Environment Variables"**:
   ```
   Key: API_URL
   Value: https://tu-backend.up.railway.app
   ```

**Si NO usa variables de entorno:**

Necesitas actualizar el código antes de desplegar:

1. En tu computadora local, edita `src/environments/environment.prod.ts`
2. Cambia la URL del API:
   ```typescript
   export const environment = {
     production: true,
     apiUrl: 'https://tu-backend.up.railway.app'
   };
   ```
3. Guarda el archivo
4. Haz commit y push:
   ```bash
   git add src/environments/environment.prod.ts
   git commit -m "Configurar URL del backend para producción"
   git push origin main
   ```

---

#### 4A.4 Desplegar en Vercel

1. Una vez configurado todo, click en **"Deploy"**
2. Vercel comenzará a construir tu aplicación
3. El proceso toma 2-5 minutos:
   - ⏳ Clonando repositorio
   - ⏳ Instalando dependencias (npm install)
   - ⏳ Construyendo aplicación Angular (ng build)
   - ⏳ Optimizando assets
   - ✅ Desplegado

4. Al finalizar, verás una pantalla de celebración 🎉
5. Click en **"Visit"** para ver tu aplicación
6. **🔖 Copia la URL** (será algo como `https://mpdia-sm-username.vercel.app`)

---

#### 4A.5 Configurar Dominio Custom (Opcional)

Si tienes un dominio propio (ej: `prodox.com`):

1. En el dashboard del proyecto en Vercel, ve a **"Settings"** > **"Domains"**
2. Agrega tu dominio
3. Sigue las instrucciones de Vercel para configurar los registros DNS

---

### Opción B: Desplegar en Railway (Alternativa)

Si prefieres tener todo en Railway:

#### 4B.1 Crear Servicio del Frontend

1. En tu proyecto de Railway, click en **"+ New"**
2. Selecciona **"GitHub Repo"**
3. Selecciona tu repositorio `MPDIA-SM` nuevamente
4. Railway creará otro servicio

**Configurar Root Directory:**
5. Click en el nuevo servicio
6. Ve a **"Settings"**
7. En **"Root Directory"**, cámbialo a: `prodox-angular`

---

#### 4B.2 Configurar Variables de Entorno

En la pestaña **"Variables"** del servicio frontend:

```
Nombre: API_URL
Valor: ${{prodox-springboot.RAILWAY_PUBLIC_DOMAIN}}
```

O simplemente:
```
Nombre: API_URL
Valor: https://tu-backend.up.railway.app
```

---

#### 4B.3 Configurar Build

En **"Settings"** > **"Build"**:

**Build Command:**
```bash
npm install && npm run build
```

**Start Command:**
```bash
npx http-server dist/prodox-angular/browser -p $PORT
```

⚠️ **NOTA:** Necesitas agregar `http-server` como dependencia:

En tu `package.json` local, agrega:
```json
"dependencies": {
  ...
  "http-server": "^14.1.1"
}
```

Luego haz commit y push.

---

#### 4B.4 Generar Dominio y Desplegar

1. En **"Settings"** > **"Networking"**, click en **"Generate Domain"**
2. Railway generará una URL pública
3. El despliegue comenzará automáticamente
4. Espera 3-5 minutos
5. Visita la URL generada

---

## 🔧 Paso 5: Configuración Final

Ahora que tanto backend como frontend están desplegados, necesitas completar algunas configuraciones.

### 5.1 Actualizar CORS en el Backend

El backend necesita saber desde qué dominio recibirá solicitudes.

1. Ve al servicio del backend en Railway
2. Ve a **"Variables"**
3. Encuentra `PRODOX_CORS_ALLOWED_ORIGINS`
4. Cámbiala a:
   ```
   https://tu-frontend.vercel.app
   ```
   
   O si usaste Railway para el frontend:
   ```
   https://tu-frontend.up.railway.app
   ```

5. Si quieres permitir múltiples orígenes (frontend + localhost para desarrollo):
   ```
   https://tu-frontend.vercel.app,http://localhost:4200
   ```

6. Los cambios se guardan automáticamente y el backend se redesp legará

---

### 5.2 Actualizar PRODOX_APP_URL

Esta variable se usa en los emails para crear enlaces que redirijan a tu frontend.

1. En el backend de Railway, ve a **"Variables"**
2. Encuentra `PRODOX_APP_URL`
3. Cámbiala a la URL de tu frontend:
   ```
   https://tu-frontend.vercel.app
   ```

---

### 5.3 Actualizar Google OAuth Redirect URI

Ahora que tienes la URL real del backend, necesitas agregarla a Google Cloud Console.

1. Ve a [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
2. Selecciona tu proyecto PRODOX
3. En **"Credentials"**, click en tu OAuth Client ID
4. En **"Authorized redirect URIs"**, click en **"Add URI"**
5. Agrega:
   ```
   https://tu-backend.up.railway.app/login/oauth2/code/google
   ```
6. Click en **"Save"**

---

### 5.4 Actualizar Google Cloud Console - Authorized Domains

1. Ve a **"OAuth consent screen"**
2. Click en **"Edit App"**
3. En **"Authorized domains"**, agrega:
   ```
   railway.app
   vercel.app
   ```
   
   (O tu dominio custom si lo configuraste)

4. En **"Application home page"**, agrega:
   ```
   https://tu-frontend.vercel.app
   ```

5. Click en **"Save and Continue"** en cada sección

---

## ✅ Paso 6: Verificación

Ahora vamos a probar que todo funcione correctamente.

### 6.1 Verificar Backend

Abre estas URLs en tu navegador (reemplaza con tu URL real):

**Health Check básico:**
```
https://tu-backend.up.railway.app
```
Debería mostrar el "Whitelabel Error Page" de Spring Boot ✅

**Endpoint de autenticación:**
```
https://tu-backend.up.railway.app/api/auth/test
```
Puede dar error 401, pero si responde, ¡funciona! ✅

---

### 6.2 Verificar Base de Datos y Migraciones

1. En Railway, click en el servicio de **PostgreSQL**
2. Ve a la pestaña **"Data"** (o **"Query"**)
3. Ejecuta esta consulta:
   ```sql
   SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
   ```

4. Deberías ver las migraciones de Flyway ejecutadas ✅
5. Verifica que la última migración sea `V38__...` (o la última que tengas)

**Verificar tablas creadas:**
```sql
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_name;
```

Deberías ver tablas como:
- `app_users`
- `proyectos`
- `sprints`
- `metricas`
- etc.

---

### 6.3 Verificar Frontend

1. Abre la URL de tu frontend en el navegador
2. Deberías ver la pantalla de login de PRODOX ✅
3. Verifica que no haya errores en la Consola del navegador (F12 > Console)

**Si ves errores de CORS:**
- Ve al paso 5.1 y verifica que configuraste correctamente PRODOX_CORS_ALLOWED_ORIGINS

**Si ves "ERR_CONNECTION_REFUSED" o "Network Error":**
- Verifica que la URL del backend en el frontend sea correcta
- Ve al paso 4A.3 o 4B.2

---

### 6.4 Probar Funcionalidades

#### A. Registro de Usuario

1. En el frontend, ve a **"Registrarse"**
2. Ingresa:
   - Nombre
   - Email
   - Contraseña
3. Click en **"Registrar"**
4. Si funciona, deberías ser redirigido al dashboard ✅

#### B. Login con Google OAuth

1. En el frontend, click en **"Iniciar sesión con Google"**
2. Serás redirigido a Google
3. Selecciona tu cuenta
4. Autoriza la aplicación
5. Deberías ser redirigido de vuelta al dashboard de PRODOX ✅

**Si Google muestra "Error 400: redirect_uri_mismatch":**
- Ve al paso 5.3 y verifica que agregaste la URI correcta

#### C. Crear Proyecto

1. En el dashboard, click en **"Nuevo Proyecto"**
2. Ingresa:
   - Nombre del proyecto
   - Descripción
   - Fecha de inicio
3. Click en **"Crear"**
4. El proyecto debería aparecer en la lista ✅

#### D. Probar AI Copilot

1. Entra a un proyecto
2. Busca el botón del AI Copilot (ícono de robot o mensaje)
3. Escribe una pregunta como: "¿Cómo puedo mejorar la velocidad de mi equipo?"
4. Deberías recibir una respuesta del AI ✅

**Si da error:**
- Verifica que tu PRODOX_GEMINI_API_KEY sea correcta
- Revisa los logs del backend en Railway

#### E. Invitación a Proyecto (Email)

1. En un proyecto, click en **"Invitar miembro"**
2. Ingresa un email
3. Click en **"Enviar invitación"**
4. Revisa la bandeja de entrada del email invitado ✅

**Si no llega el email:**
- Revisa la carpeta de spam
- Verifica SPRING_MAIL_USERNAME y SPRING_MAIL_PASSWORD en Railway
- Revisa los logs del backend buscando "mail" o "smtp"

---

## 🐛 Troubleshooting

### Error: "Could not connect to database"

**Síntomas:**
- El backend no inicia
- Logs muestran: `Connection refused` o `Unknown database`

**Solución:**

1. Ve al servicio del backend en Railway > **"Variables"**
2. Verifica que `SPRING_DATASOURCE_URL` use la sintaxis correcta:
   ```
   jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
   ```
3. Verifica que el nombre del servicio sea correcto (`Postgres` puede variar)
4. Verifica que `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` estén referenciando correctamente al servicio de PostgreSQL

---

### Error: "Flyway migration failed"

**Síntomas:**
- El backend inicia pero falla al correr migraciones
- Logs muestran: `FlywayException` o `Migration V1__... failed`

**Solución 1: Baseline**

1. Asegúrate de que `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`
2. Redesplegar

**Solución 2: Repair**

1. Conéctate a la base de datos en Railway (pestaña "Query")
2. Ejecuta:
   ```sql
   DELETE FROM flyway_schema_history WHERE success = false;
   ```
3. Redesplegar

**Solución 3: Empezar desde cero (⚠️ perderás datos)**

1. En Railway, elimina el servicio de PostgreSQL
2. Crea uno nuevo
3. Actualiza las referencias en las variables del backend
4. Redesplegar

---

### Error: "CORS policy blocked"

**Síntomas:**
- El frontend no puede hacer requests al backend
- Consola del navegador muestra: `Access to XMLHttpRequest... has been blocked by CORS policy`

**Solución:**

1. Ve al backend en Railway > **"Variables"**
2. Verifica `PRODOX_CORS_ALLOWED_ORIGINS`:
   ```
   https://tu-frontend.vercel.app
   ```
3. Asegúrate de:
   - NO incluir barra final (`/`)
   - Incluir el protocolo (`https://`)
   - Usar la URL exacta (no wildcards `*` en producción)

---

### Error: "Google OAuth - redirect_uri_mismatch"

**Síntomas:**
- Al intentar login con Google, ves: "Error 400: redirect_uri_mismatch"

**Solución:**

1. Ve a [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
2. Edita tu OAuth Client ID
3. En **"Authorized redirect URIs"**, asegúrate de tener:
   ```
   https://tu-backend.up.railway.app/login/oauth2/code/google
   ```
4. La URI debe coincidir EXACTAMENTE con la que Spring Security usa
5. Espera 5 minutos (los cambios en Google pueden tardar)

---

### Error: "Gmail SMTP - Authentication failed"

**Síntomas:**
- Los emails no se envían
- Logs muestran: `AuthenticationFailedException` o `535 authentication failed`

**Solución:**

1. Verifica que estés usando una **contraseña de aplicación**, NO tu contraseña de Gmail
2. Ve a [Contraseñas de aplicación](https://myaccount.google.com/apppasswords) y genera una nueva
3. Asegúrate de NO incluir espacios en la contraseña al copiarla
4. Verifica que la verificación en 2 pasos esté habilitada en tu cuenta de Google

---

### Error: "Gemini AI - 403 Forbidden"

**Síntomas:**
- El AI Copilot no responde
- Logs muestran: `403 Forbidden` o `API key not valid`

**Solución:**

1. Ve a [Google AI Studio](https://aistudio.google.com/app/apikey)
2. Verifica que tu API key esté activa
3. Genera una nueva si es necesario
4. Actualiza `PRODOX_GEMINI_API_KEY` en Railway
5. Verifica que no haya espacios extra al copiar la key

---

### Error: "Application failed to start" (genérico)

**Síntomas:**
- El backend no inicia en absoluto
- Logs muestran un stack trace grande

**Solución:**

1. Lee los logs cuidadosamente desde el inicio
2. Busca la **primera** palabra `ERROR` o `Exception`
3. Busca mensajes como:
   - `required a bean of type '...' that could not be found` → falta una configuración
   - `Failed to configure a DataSource` → problema con la base de datos
   - `Caused by:` → indica la causa raíz del error

4. Si no puedes resolverlo, copia el error completo y búscalo en Google
5. O comparte los logs en foros de Spring Boot / Railway

---

### Frontend muestra "Connection Refused"

**Síntomas:**
- El frontend carga pero no puede conectarse al backend
- Consola del navegador: `ERR_CONNECTION_REFUSED` o `Network Error`

**Solución:**

1. Verifica que el backend esté corriendo (ve a **"Deployments"** en Railway)
2. Verifica que la URL del backend en el frontend sea correcta:
   - Revisa `environment.prod.ts` o la variable `API_URL`
   - Debe ser `https://` (NO `http://`)
   - NO debe tener barra final (`/`)
3. Prueba acceder directamente al backend en el navegador
4. Si el backend no responde, revisa los logs del backend

---

### Railway dice "Quota Exceeded"

**Síntomas:**
- El despliegue se detiene
- Mensaje: "You have exceeded your plan's quota"

**Solución:**

1. Railway gratis tiene $5 USD al mes
2. Cada servicio (backend, frontend, DB) consume del crédito
3. Opciones:
   - Usa Vercel para el frontend (gratis ilimitado para frontend)
   - Pausa servicios cuando no los uses (botón "Pause" en cada servicio)
   - Actualiza a Railway Pro ($5/mes + usage)

---

### Backend se reinicia constantemente

**Síntomas:**
- El backend muestra "Running" por unos segundos y luego "Building" de nuevo
- Ciclo infinito de restarts

**Solución:**

1. Hay un error al iniciar que causa el restart
2. Lee los logs completos del último despliegue
3. Busca `ERROR` o `Exception` justo antes de que se reinicie
4. Común: 
   - Puerto incorrecto (debe ser el puerto que Railway asigna)
   - Falta una variable de entorno requerida
   - Migraciones de Flyway fallan

---

## 📋 Variables de Entorno - Referencia Completa

Para copiar y pegar fácilmente, aquí están todas las variables organizadas:

### Backend - Spring Boot (Railway)

```bash
# === PERFIL ===
SPRING_PROFILES_ACTIVE=prod

# === BASE DE DATOS ===
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver

# === JPA ===
SPRING_JPA_HIBERNATE_DDL_AUTO=none
SPRING_JPA_SHOW_SQL=false
SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect

# === FLYWAY ===
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true
SPRING_FLYWAY_LOCATIONS=classpath:db/migration
SPRING_FLYWAY_REPAIR_ON_MIGRATE=true
SPRING_FLYWAY_VALIDATE_ON_MIGRATE=false

# === JWT ===
PRODOX_JWT_SECRET=<tu-jwt-secret>
PRODOX_JWT_EXPIRATION_MS=86400000

# === CORS ===
PRODOX_CORS_ALLOWED_ORIGINS=https://tu-frontend.vercel.app

# === GEMINI AI ===
PRODOX_GEMINI_API_KEY=<tu-gemini-api-key>
PRODOX_GEMINI_API_URL=https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent

# === GOOGLE OAUTH ===
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=<tu-client-id>
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=<tu-client-secret>
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_SCOPE=email,profile
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_REDIRECT_URI={baseUrl}/login/oauth2/code/google

# === EMAIL (GMAIL SMTP) ===
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<tu-email@gmail.com>
SPRING_MAIL_PASSWORD=<tu-password-de-aplicacion>
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
PRODOX_APP_URL=https://tu-frontend.vercel.app

# === RATE LIMITING Y OTROS ===
PRODOX_AI_RATE_LIMIT_REQUESTS_PER_MINUTE=10
PRODOX_AI_RATE_LIMIT_WINDOW_SECONDS=60
PRODOX_AI_MAX_HISTORY_MESSAGES=10
PRODOX_PASSWORD_RESET_EXPIRATION_MINUTES=30
PRODOX_INVITACION_EXPIRATION_DAYS=7
```

### Frontend - Angular (Vercel)

```bash
# === API ===
API_URL=https://tu-backend.up.railway.app
```

---

## 🎉 ¡Listo!

Si llegaste hasta aquí y todo funciona, ¡felicitaciones! 🎊

Tu aplicación PRODOX está completamente desplegada en la nube y lista para usarse.

### Próximos Pasos Recomendados

1. **Configurar un dominio custom** en Vercel y Railway
2. **Configurar SSL certificates** (Vercel y Railway lo hacen automáticamente)
3. **Monitorear uso** en Railway para no exceder el crédito gratis
4. **Backups de base de datos** (Railway Pro ofrece backups automáticos)
5. **Configurar CI/CD** para despliegues automáticos al hacer push
6. **Agregar monitoreo** con herramientas como Sentry o LogRocket

### Recursos Útiles

- [Documentación de Railway](https://docs.railway.app)
- [Documentación de Vercel](https://vercel.com/docs)
- [Spring Boot Deployment](https://spring.io/guides/gs/spring-boot-docker)
- [Angular Deployment](https://angular.io/guide/deployment)

---

## 📞 Soporte

Si tienes problemas no cubiertos en esta guía:

1. Revisa los logs detalladamente
2. Busca el error específico en Google
3. Consulta la documentación de Railway/Vercel
4. Pregunta en:
   - [Railway Discord](https://discord.gg/railway)
   - [Stack Overflow](https://stackoverflow.com)
   - Comunidades de Spring Boot y Angular

---

**¡Éxito con tu proyecto PRODOX! 🚀**
