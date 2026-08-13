# Catalog Service

Microservicio de catalogo de Flash Drop Delivery construido con Java 21 y Spring Boot 3.

Puerto del servicio: `8082`.

## Endpoints

```text
GET  /health
GET  /catalog/products
POST /catalog/products
POST /catalog/products/validate
GET  /catalog/categories
GET  /catalog/restaurants
```

Ejemplo de validacion:

```json
{
  "productIds": [1, 2, 999]
}
```

## Perfiles

```text
local     Usa datos en memoria
supabase  Usa Supabase REST API con SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY
postgres  Usa JPA/PostgreSQL directo con POSTGRES_HOST, POSTGRES_PORT, POSTGRES_DB, POSTGRES_USER y POSTGRES_PASSWORD
```

## Levantar con Supabase

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=supabase"
```

Este es el modo recomendado para probar desde el equipo local, porque usa `SUPABASE_URL` y
`SUPABASE_SERVICE_ROLE_KEY`.

## Levantar local sin Supabase

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

## Docker

```powershell
docker compose up --build
```

Por defecto Docker levanta el servicio con el perfil `supabase`, usando la API REST del
Kong/PostgREST de la empresa.

El archivo `.env` real debe quedar en el servidor, no en GitHub:

```text
SPRING_PROFILES_ACTIVE=supabase
SUPABASE_URL=http://supabasekong-wymwq8rktid7ov678oe4va90.76.13.169.150.sslip.io
SUPABASE_SERVICE_ROLE_KEY=********
```

Luego se levanta con:

```powershell
docker compose up --build
```

Probar:

```text
http://localhost:8082/health
http://localhost:8082/catalog/products
http://localhost:8082/catalog/categories
http://localhost:8082/catalog/restaurants
```

## Alternativa PostgreSQL dentro del VPS

Si el microservicio corre dentro de la misma red Docker/VPS donde existe el contenedor
`db`, se puede usar PostgreSQL directo:

```text
SPRING_PROFILES_ACTIVE=postgres
POSTGRES_HOST=db
POSTGRES_PORT=5432
POSTGRES_DB=postgres
POSTGRES_USER=postgres
POSTGRES_PASSWORD=********
POSTGRES_SSLMODE=disable
```

Nota: `POSTGRES_HOST=db` funciona solo si este servicio corre dentro de la misma red Docker/VPS
donde existe el contenedor `db`. Desde el equipo local normalmente no resuelve.

## Seguridad de claves

No subir nunca el archivo `.env` real a GitHub. En el repositorio solo debe existir
`.env.example` con placeholders.

La `SUPABASE_SERVICE_ROLE_KEY` es una clave de backend. No debe ir en Flutter, React,
Next.js publico ni ningun frontend.

Para probar sin base real:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```
