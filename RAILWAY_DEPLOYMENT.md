# Guía de Despliegue en Railway - PRODOX

## 📋 Variables de Entorno Requeridas

### 🔐 Base de Datos PostgreSQL
Railway creará automáticamente una base de datos PostgreSQL. Necesitas configurar:

```env
# PostgreSQL (Railway lo proporciona automáticamente)
DATABASE_URL=postgresql://user:password@host:port/database
PGHOST=<hostname>
PGPORT=5432
PGDATABASE=<database_name>
PGUSER=<username>
PGPASSWORD=<password>

# Spring Boot las necesita en este formato:
SPRING_DATASOURCE_URL=jdbc:postgresql://${PGHOST}:${PGPORT}/${PGDATABASE}
SPRING_DATASOURCE_USERNAME=${PGUSER}
SPRING_DATASOURCE_PASSWORD=${PGPASSWORD}
```

### 🔑 Seguridad y JWT
```env
# JWT Secret (genera una clave segura de al menos 256 bits)
PRODOX_JWT_SECRET=tu-clave-super-secreta-minimo-256-bits-cambiar-en-produccion
PRODOX_JWT_EXPIRATION_MS=86400000
```

### 🌐 CORS
```env
# Permitir acceso desde tu frontend desplegado
PRODOX_CORS_ALLOWED_ORIGINS=https://tu-app-angular.vercel.app,https://tu-dominio.com
```

### 🤖 Gemini AI
```env
# API Key de Google Gemini (https://aistudio.google.com/app/apikey)
PRODOX_GEMINI_API_KEY=tu-api-key-de-gemini-aqui
PRODOX_GEMINI_API_URL=https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent
```

### 🔐 Google OAuth 2.0
```env
# Credenciales OAuth (https://console.cloud.google.com/apis/credentials)
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=tu-client-id.apps.googleusercontent.com
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=tu-client-secret
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_REDIRECT_URI={baseUrl}/login/oauth2/code/google
```

### 📧 Email (Gmail SMTP)
```env
# Gmail SMTP para invitaciones y recuperación de contraseña
SPRING_MAIL_USERNAME=tu-email@gmail.com
SPRING_MAIL_PASSWORD=tu-password-de-aplicacion-gmail
PRODOX_APP_URL=https://tu-app-angular.vercel.app
```

### ⚙️ Otras Configuraciones
```env
# Rate Limiting
PRODOX_AI_RATE_LIMIT_REQUESTS_PER_MINUTE=10
PRODOX_AI_RATE_LIMIT_WINDOW_SECONDS=60
PRODOX_AI_MAX_HISTORY_MESSAGES=10

# Recuperación de contraseña
PRODOX_PASSWORD_RESET_EXPIRATION_MINUTES=30

# Invitaciones
PRODOX_INVITACION_EXPIRATION_DAYS=7

# Puerto (Railway lo asigna automáticamente)
PORT=8080
```

---

## 🚀 Paso a Paso para Desplegar en Railway

### 1️⃣ Preparar el Repositorio

```bash
# Asegúrate de que todo esté commiteado
git add .
git commit -m "Preparar para despliegue en Railway"
git push origin main
```

### 2️⃣ Crear Cuenta en Railway

1. Ve a [railway.app](https://railway.app)
2. Regístrate con GitHub
3. Autoriza Railway para acceder a tus repositorios

### 3️⃣ Crear Nuevo Proyecto

1. Click en **"New Project"**
2. Selecciona **"Deploy from GitHub repo"**
3. Busca y selecciona el repositorio `PRODOX-SM`
4. Railway detectará automáticamente que es un proyecto Spring Boot

### 4️⃣ Agregar Base de Datos PostgreSQL

1. En tu proyecto de Railway, click en **"New"** → **"Database"** → **"Add PostgreSQL"**
2. Railway creará automáticamente la base de datos y generará las credenciales
3. Las variables `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` se crearán automáticamente

### 5️⃣ Configurar Variables de Entorno del Backend

1. Click en el servicio de **Spring Boot** (prodox-springboot)
2. Ve a la pestaña **"Variables"**
3. **IMPORTANTE:** Agrega primero esta variable para activar el perfil de producción:
   ```
   SPRING_PROFILES_ACTIVE=prod
   ```

4. Agrega todas las variables una por una:

**⚠️ NOTA IMPORTANTE:** 
- El archivo `application.properties` tiene valores de desarrollo local (localhost)
- Al configurar `SPRING_PROFILES_ACTIVE=prod`, Spring Boot usará `application-prod.properties`
- Este archivo usa variables de entorno `${VARIABLE}` que Railway proporciona automáticamente
- **NO necesitas modificar ningún archivo .properties** - todo se configura con variables de entorno

**Base de Datos:**
```
SPRING_DATASOURCE_URL=jdbc:postgresql://${PGHOST}:${PGPORT}/${PGDATABASE}
SPRING_DATASOURCE_USERNAME=${PGUSER}
SPRING_DATASOURCE_PASSWORD=${PGPASSWORD}
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
```

**JPA y Flyway:**
```
SPRING_JPA_HIBERNATE_DDL_AUTO=none
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true
SPRING_FLYWAY_LOCATIONS=classpath:db/migration
SPRING_FLYWAY_REPAIR_ON_MIGRATE=true
SPRING_FLYWAY_VALIDATE_ON_MIGRATE=false
SPRING_JPA_SHOW_SQL=false
SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect
```

**JWT:**
```
PRODOX_JWT_SECRET=genera-una-clave-segura-de-al-menos-256-bits-aqui
PRODOX_JWT_EXPIRATION_MS=86400000
```

**CORS (actualiza con tu dominio de frontend):**
```
PRODOX_CORS_ALLOWED_ORIGINS=https://tu-app-angular.vercel.app
```

**Gemini AI:**
```
PRODOX_GEMINI_API_KEY=tu-api-key-de-gemini
PRODOX_GEMINI_API_URL=https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent
```

**Google OAuth:**
```
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=tu-client-id
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=tu-client-secret
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_SCOPE=email,profile
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_REDIRECT_URI={baseUrl}/login/oauth2/code/google
```

**Email:**
```
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=tu-email@gmail.com
SPRING_MAIL_PASSWORD=tu-password-de-aplicacion
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
PRODOX_APP_URL=https://tu-app-angular.vercel.app
```

**Rate Limiting y otros:**
```
PRODOX_AI_RATE_LIMIT_REQUESTS_PER_MINUTE=10
PRODOX_AI_RATE_LIMIT_WINDOW_SECONDS=60
PRODOX_AI_MAX_HISTORY_MESSAGES=10
PRODOX_PASSWORD_RESET_EXPIRATION_MINUTES=30
PRODOX_INVITACION_EXPIRATION_DAYS=7
```

### 6️⃣ Configurar el Build

Railway debería detectar automáticamente el proyecto Maven. Si no:

1. Ve a **"Settings"** del servicio
2. En **"Build Command"** asegúrate que sea:
   ```bash
   cd prodox-springboot && ./mvnw clean package -DskipTests
   ```
3. En **"Start Command"**:
   ```bash
   java -jar prodox-springboot/target/*.jar
   ```

### 7️⃣ Desplegar

1. Railway empezará a construir automáticamente
2. Espera a que termine el build (puede tardar 5-10 minutos la primera vez)
3. Una vez completado, Railway te dará una URL pública (ej: `https://tu-app.railway.app`)

### 8️⃣ Verificar el Despliegue

1. Abre la URL de Railway en el navegador
2. Verifica que la API esté corriendo: `https://tu-app.railway.app/api/auth/test`
3. Revisa los logs en Railway si hay errores

### 9️⃣ Desplegar el Frontend (Angular)

**Opción A: Vercel**

1. Ve a [vercel.com](https://vercel.com)
2. Importa el repositorio
3. Configura:
   - **Framework Preset:** Angular
   - **Root Directory:** `prodox-angular`
   - **Build Command:** `npm run build`
   - **Output Directory:** `dist/prodox-angular/browser`

4. Agrega variable de entorno:
   ```
   API_URL=https://tu-app.railway.app
   ```

**Opción B: Railway (Frontend también)**

1. En tu proyecto Railway, click **"New"** → **"GitHub Repo"**
2. Configura:
   - **Root Directory:** `prodox-angular`
   - **Build Command:** `npm install && npm run build`
   - **Start Command:** `npx http-server dist/prodox-angular/browser -p $PORT`

### 🔟 Actualizar CORS y OAuth Redirects

Una vez que tengas las URLs de producción:

1. **Actualiza CORS en Railway:**
   - Variable `PRODOX_CORS_ALLOWED_ORIGINS` con la URL de tu frontend

2. **Actualiza Google OAuth:**
   - Ve a [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
   - Agrega la URI de redirección: `https://tu-backend.railway.app/login/oauth2/code/google`

3. **Actualiza Email URLs:**
   - Variable `PRODOX_APP_URL` con la URL de tu frontend

---

## ✅ Checklist Final

- [ ] Base de datos PostgreSQL creada en Railway
- [ ] Todas las variables de entorno configuradas
- [ ] Build exitoso del backend
- [ ] API funcionando (verificar endpoint `/api/auth/test`)
- [ ] Frontend desplegado (Vercel o Railway)
- [ ] CORS configurado correctamente
- [ ] Google OAuth redirects actualizados
- [ ] Email SMTP funcionando
- [ ] Prueba de login con Google
- [ ] Prueba de registro de usuario
- [ ] Prueba de creación de proyecto

---

## 🐛 Troubleshooting

### Error: "Could not connect to database"
- Verifica que las variables `SPRING_DATASOURCE_*` estén correctas
- Asegúrate de usar `${PGHOST}` en lugar de valores hardcodeados

### Error: "CORS policy blocked"
- Verifica `PRODOX_CORS_ALLOWED_ORIGINS`
- Asegúrate de incluir el protocolo `https://`

### Error: "Google OAuth failed"
- Verifica las credenciales OAuth en Google Cloud Console
- Asegúrate de agregar la URI de redirección correcta

### Error: "Flyway migration failed"
- Revisa los logs de Railway
- Puede que necesites hacer `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`

---

## 📞 Contacto

Si tienes problemas, revisa los logs en Railway:
- Click en el servicio
- Ve a la pestaña **"Deployments"**
- Click en el despliegue más reciente
- Revisa los logs en tiempo real
