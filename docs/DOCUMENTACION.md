# MPDIA — Sistema de Medición de Productividad para Equipos Ágiles

**Autor:** Cristian Santiago Martinez Cordoba  
**Versión:** 1.0.0  
**Stack:** Angular 17 + Spring Boot 3.2.5 + PostgreSQL

---

## Tabla de Contenidos

1. [Descripción General](#descripción-general)
2. [Arquitectura](#arquitectura)
3. [Historias de Usuario](#historias-de-usuario)
4. [Módulos del Sistema](#módulos-del-sistema)
5. [API REST — Endpoints](#api-rest--endpoints)
6. [Modelos de Datos](#modelos-de-datos)
7. [Seguridad](#seguridad)
8. [Configuración y Ejecución](#configuración-y-ejecución)

---

## Descripción General

MPDIA es un sistema web que apoya la **fase de planeación** de equipos ágiles (Scrum / XP),
permitiendo seleccionar factores de productividad, definir métricas, parametrizarlas con apoyo
de Inteligencia Artificial (GenAI) y verificarlas antes de ejecutar un sprint.

---

## Arquitectura

```
mpdia-angular/          → Frontend (Angular 17, Bootstrap 5)
mpdia-springboot/       → Backend  (Spring Boot 3.2.5, Spring Security, JPA)
PostgreSQL (puerto 5433) → Base de datos relacional
```

**Flujo general:**

```
Usuario → Angular SPA → HTTP + JWT → Spring Boot API → PostgreSQL
```

---

## Historias de Usuario

| Id   | Descripción                                                                                                                                                      | Prioridad | Complejidad |
|------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|-------------|
| HU1  | Como integrante del *Scrum Team*, quiero seleccionar un factor de medición de productividad, para obtener datos durante la ejecución del *sprint*                | 100       | Media       |
| HU2  | Como herramienta MPDIA, quiero generar una métrica o indicador de productividad de un factor, para que un integrante del *Scrum Team* lo pueda aprobar           | 100       | Alta        |
| HU3  | Como copiloto, quiero configurar la herramienta de gestión de proyectos, para recopilar los datos que requiere una métrica durante un *sprint*                   | 100       | Alta        |
| HU4  | Como usuario, quiero registrarme con correo, contraseña y rol, para acceder según mi perfil (Scrum Master o Scrum Member)                                        | 90        | Baja        |
| HU5  | Como usuario registrado, quiero iniciar sesión, para acceder a mis proyectos y continuar la planeación                                                           | 90        | Baja        |
| HU6  | Como *Scrum Master*, quiero crear un proyecto con método ágil, time box, product goal y sprint goal, para gestionar la medición estructuradamente                | 100       | Media       |
| HU7  | Como *Scrum Master*, quiero invitar miembros a un proyecto específico con un código, para que cada proyecto tenga su propio equipo                               | 100       | Media       |
| HU8  | Como *Scrum Member*, quiero unirme a un proyecto con un código de invitación, para acceder a las métricas de ese proyecto                                        | 100       | Baja        |
| HU9  | Como integrante del *Scrum Team*, quiero ver los miembros del proyecto activo, para conocer quiénes participan en la medición                                    | 80        | Baja        |
| HU10 | Como integrante del *Scrum Team*, quiero parametrizar una métrica (objetivo, procedimiento, indicador, escala), para que el SM pueda verificarla                 | 100       | Alta        |
| HU11 | Como integrante del *Scrum Team*, quiero usar GenAI para generar propuestas de parametrización, para agilizar la definición del proceso de medición              | 90        | Alta        |
| HU12 | Como *Scrum Master*, quiero aprobar o rechazar parametrizaciones del equipo, para asegurar la calidad antes de ejecutar el sprint                                | 100       | Media       |
| HU13 | Como *Scrum Master*, quiero cerrar un sprint e iniciar el siguiente con un nuevo sprint goal, para mantener el historial de iteraciones                          | 90        | Media       |
| HU14 | Como integrante del *Scrum Team*, quiero ver el historial de sprints del proyecto, para hacer seguimiento de las iteraciones completadas                         | 70        | Baja        |
| HU15 | Como integrante del *Scrum Team*, quiero ver las parametrizaciones más usadas como referencia, para aprovechar buenas prácticas al definir una métrica           | 80        | Media       |

---

## Módulos del Sistema

### Autenticación (`/api/auth`)
Registro e inicio de sesión con JWT. Los roles posibles son `scrum_master` y `scrum_member`.

### Proyectos (`/api/proyectos`)
El Scrum Master crea proyectos. Cada proyecto tiene método ágil, time box, product goal y sprint goal.
Al crearse, se genera automáticamente el Sprint 1 y el SM queda registrado como miembro.

### Equipo por Proyecto (`/api/project-members`)
La membresía es **por proyecto**. El SM invita con un código único `PRJ-XXXXXX`.
Los Scrum Members se unen ingresando el código. Cada proyecto tiene su propio equipo independiente.

### Sprints (`/api/sprints`)
Cada proyecto tiene un sprint activo. El SM puede cerrar el sprint actual e iniciar el siguiente
con un nuevo sprint goal. Se guarda el historial completo.

### Factores y Métricas (`/api/factors`)
El equipo selecciona factores de productividad para el sprint (Productividad, Calidad, Cumplimiento, Sociohumano).
Cada factor tiene métricas asociadas.

### Parametrización (`/api/metric-ranking/parametrizacion`)
Cada miembro parametriza las métricas seleccionadas: objetivo de medición, procedimiento/fórmula,
indicador/variables y escala. Puede apoyarse en GenAI o en parametrizaciones previas del ranking.

### Verificación (`/api/metric-ranking/verificar`)
El Scrum Master aprueba o rechaza las parametrizaciones enviadas por el equipo antes de ejecutar el sprint.

### Copiloto (`/api/copilot`)
Configuración de integración con Jira o GitHub para recopilación automática de datos de métricas.

---

## API REST — Endpoints

### Autenticación

| Método | Endpoint              | Descripción              | Acceso   |
|--------|-----------------------|--------------------------|----------|
| POST   | `/api/auth/register`  | Registrar usuario        | Público  |
| POST   | `/api/auth/login`     | Iniciar sesión           | Público  |

### Proyectos

| Método | Endpoint                        | Descripción                        | Rol requerido  |
|--------|---------------------------------|------------------------------------|----------------|
| POST   | `/api/proyectos`                | Crear proyecto                     | scrum_master   |
| GET    | `/api/proyectos/mios`           | Listar proyectos del usuario       | Autenticado    |
| GET    | `/api/proyectos/{id}`           | Obtener proyecto por ID            | Autenticado    |
| PATCH  | `/api/proyectos/{id}/finalizar` | Finalizar proyecto                 | scrum_master   |

### Equipo por Proyecto

| Método | Endpoint                                    | Descripción                        | Rol requerido  |
|--------|---------------------------------------------|------------------------------------|----------------|
| GET    | `/api/project-members/{proyectoId}`         | Listar miembros del proyecto       | Autenticado    |
| POST   | `/api/project-members/{proyectoId}/invitar` | Generar código de invitación       | scrum_master   |
| POST   | `/api/project-members/unirse`               | Unirse con código                  | Autenticado    |

### Sprints

| Método | Endpoint                             | Descripción                        | Rol requerido  |
|--------|--------------------------------------|------------------------------------|----------------|
| GET    | `/api/sprints/{proyectoId}/activo`   | Obtener sprint activo              | Autenticado    |
| GET    | `/api/sprints/{proyectoId}`          | Listar sprints del proyecto        | Autenticado    |
| POST   | `/api/sprints/{proyectoId}/siguiente`| Cerrar sprint e iniciar siguiente  | scrum_master   |

### Factores

| Método | Endpoint                          | Descripción                           | Rol requerido |
|--------|-----------------------------------|---------------------------------------|---------------|
| GET    | `/api/factors`                    | Listar todos los factores             | Autenticado   |
| GET    | `/api/factors/selections`         | Selecciones del sprint actual         | Autenticado   |
| POST   | `/api/factors/selections`         | Seleccionar factor para sprint        | Autenticado   |
| DELETE | `/api/factors/selections/{id}`    | Quitar factor del sprint              | Autenticado   |

### Métricas y Parametrización

| Método | Endpoint                                        | Descripción                               | Rol requerido  |
|--------|-------------------------------------------------|-------------------------------------------|----------------|
| GET    | `/api/metric-ranking`                           | Top 5 métricas más usadas                 | Autenticado    |
| GET    | `/api/metric-ranking/{factorId}/top3`           | Top 3 parametrizaciones del factor        | Autenticado    |
| GET    | `/api/metric-ranking/{factorId}/base`           | Parametrización base de referencia        | Autenticado    |
| POST   | `/api/metric-ranking/{factorId}/uso`            | Incrementar uso al seleccionar            | Autenticado    |
| POST   | `/api/metric-ranking/parametrizacion`           | Guardar parametrización                   | Autenticado    |
| GET    | `/api/metric-ranking/pendientes`                | Parametrizaciones pendientes de revisión  | scrum_master   |
| POST   | `/api/metric-ranking/verificar`                 | Aprobar o rechazar parametrización        | scrum_master   |

---

## Modelos de Datos

### AppUser
| Campo         | Tipo      | Descripción                          |
|---------------|-----------|--------------------------------------|
| id            | UUID      | Identificador único                  |
| email         | String    | Correo electrónico (único)           |
| passwordHash  | String    | Contraseña hasheada (BCrypt)         |
| role          | String    | `scrum_master` / `scrum_member`      |
| createdAt     | Instant   | Fecha de registro                    |

### Proyecto
| Campo          | Tipo      | Descripción                          |
|----------------|-----------|--------------------------------------|
| id             | UUID      | Identificador único                  |
| nombre         | String    | Nombre del proyecto                  |
| descripcion    | String    | Descripción opcional                 |
| metodo         | String    | `scrum` / `xp`                       |
| timeBoxSemanas | Integer   | Duración del sprint (1-4 semanas)    |
| productGoal    | String    | Objetivo del producto                |
| sprintGoal     | String    | Objetivo del primer sprint           |
| estado         | String    | `activo` / `finalizado`              |
| scrumMasterId  | String    | UUID del Scrum Master                |
| createdAt      | Instant   | Fecha de creación                    |

### ProjectMember
| Campo      | Tipo      | Descripción                              |
|------------|-----------|------------------------------------------|
| proyectoId | UUID      | Referencia al proyecto (PK compuesta)    |
| userId     | String    | ID del usuario (PK compuesta)            |
| userEmail  | String    | Email del miembro                        |
| rol        | String    | `scrum_master` / `scrum_member`          |
| joinedAt   | Instant   | Fecha de incorporación                   |

### Sprint
| Campo       | Tipo       | Descripción                          |
|-------------|------------|--------------------------------------|
| id          | UUID       | Identificador único                  |
| proyectoId  | UUID       | Proyecto al que pertenece            |
| numero      | Integer    | Número secuencial del sprint         |
| sprintGoal  | String     | Objetivo del sprint                  |
| estado      | String     | `activo` / `finalizado`              |
| fechaInicio | LocalDate  | Fecha de inicio                      |
| fechaFin    | LocalDate  | Fecha de fin                         |

---

## Seguridad

- Autenticación basada en **JWT (HS256)**
- Todos los endpoints excepto `/api/auth/**` requieren el header:  
  `Authorization: Bearer <token>`
- El token contiene: `userId` (subject), `email`, `role`
- Expiración configurable vía `mpdia.jwt.expiration-ms`

---

## Configuración y Ejecución

### Backend

```bash
cd mpdia-springboot
mvn clean spring-boot:run
```

Variables en `application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/mpdia_db
spring.datasource.username=postgres
spring.datasource.password=tu_password
mpdia.jwt.secret=clave_secreta_minimo_32_chars
mpdia.jwt.expiration-ms=86400000
mpdia.app.url=http://localhost:4200
```

### Frontend

```bash
cd mpdia-angular
npm install
ng serve
```

Accedé en: `http://localhost:4200`
