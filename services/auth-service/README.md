# FlashDrop Microservices Monorepo

Arquitectura de microservicios basada en **Spring Boot 3**, **Java 21** y **Gradle Multi-proyecto**.

## 🚀 Cómo Empezar (Guía para Desarrolladores)

### 1. Prerrequisitos
- **Java 21** instalado (`$env:JAVA_HOME` configurado correctamente).
- **Docker Desktop** o similar corriendo.

### 2. Variables de Entorno
Copia el archivo de ejemplo y configura tus valores locales:
```powershell
cp .env.example .env
```
*(Nota: El archivo `.env` está en el `.gitignore` para no subir secretos).*

### 3. Levantar la Base de Datos
El proyecto requiere PostgreSQL. Levántalo usando Docker Compose:
```powershell
docker compose up -d
```
Esto levantará la BD expuesta en el puerto `5432` con las credenciales de tu `.env`.

### 4. Ejecutar los Microservicios
Levanta los servicios que necesites. En consolas separadas:

**Auth Service (Migraciones, Login, Registro):**
```powershell
.\gradlew.bat :auth-service:bootRun
```
*Importante:* El `auth-service` es el **único** responsable de ejecutar Flyway (creación de tablas). Levántalo primero.

**Catalog Service (Productos, Categorías, Restaurantes):**
```powershell
.\gradlew.bat :catalog-service:bootRun
```
*Nota:* Por defecto el catalog arranca con el perfil `local` (datos en memoria). Para apuntarlo a la BD, levántalo así:
```powershell
.\gradlew.bat :catalog-service:bootRun --args="--spring.profiles.active=default"
```

## 🏗️ Arquitectura y Reglas

- **IDs:** Todos los microservicios usan `UUID` (no autoincrementales numéricos).
- **Seguridad:** Autenticación vía JWT (RS256). Solo `auth-service` emite tokens. El resto de los servicios solo los **validan** usando la Clave Pública.
- **Base de Datos Compartida:** Durante el desarrollo temprano, todos los servicios apuntan a `flashdrop_auth` pero a nivel de esquema actúan como si fueran separados. Las migraciones SQL se agregan en `auth-service/src/main/resources/db/migration/`.

---

## auth-service

### Endpoints

- `POST /auth/register` — registrar un usuario
- `POST /auth/login` — autenticarse y obtener tokens
- `POST /auth/refresh` — refrescar el access token
- `POST /auth/logout` — cerrar sesion
- `GET /auth/profile` — datos del usuario del token
- `GET /auth/.well-known/jwks.json` — clave publica para que el gateway valide los JWT
- `GET /health` — estado del servicio (lo consulta el gateway en cada ciclo)

Endpoints internos, consumidos por Orders y Delivery (MIGRATION_PLAN seccion 3.1):

- `GET /api/internal/users/{userId}`
- `GET /api/internal/users?ids=1,2,3` — variante batch, para que el consumidor no haga una llamada por registro al listar
- `GET /api/internal/users/{userId}/roles`

Todos los `/api/internal/**` exigen la cabecera `X-Internal-Api-Key`. Si la
variable `INTERNAL_API_KEY` no esta definida, el filtro responde 403 a todo:
falla cerrado a proposito.

### Base de datos

auth-service es dueño exclusivo de cinco tablas: `users`, `login`, `roles`,
`user_has_roles` y `refresh_tokens`. El esquema y el seed estan en
[`db/`](db/README.md) y se aplican a mano — no hay Flyway ni JPA, el acceso es
por PostgREST.

```bash
psql "$AUTH_DB_URL" -f db/01_schema.sql
```

Ninguna otra tabla del sistema pertenece a este servicio. Orders, Catalog y
Delivery obtienen los datos de usuario por `GET /api/internal/users/{id}`.

### Tests

```bash
./gradlew :auth-service:test
```

Cubre los minimos de MIGRATION_PLAN seccion 12.1: unitarios de registro,
autenticacion y refresh; integracion de los endpoints internos; el test de
contrato que fija los campos exactos de `/api/internal/users/{id}`; y un test
que arranca el contexto completo de Spring contra la cadena de filtros real.
