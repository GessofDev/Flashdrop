# Catalog Service

Microservicio de catalogo de Flash Drop Delivery construido con Java 21 y Spring Boot 3.

Puerto del servicio: `8082`.

## Endpoints

```text
GET  /health
GET  /catalog/products
GET  /catalog/products?categoryId=1
GET  /catalog/products?restaurantId=1
POST /catalog/products
POST /catalog/products/validate
GET  /catalog/categories
GET  /catalog/restaurants
GET  /api/internal/products?ids=1,2,3
GET  /api/internal/restaurants/{restaurantId}
GET  /api/internal/restaurants?userId={userId}
```

Los `POST` publicos y todos los endpoints `/api/internal/**` requieren:

```text
X-Internal-Api-Key: valor-de-INTERNAL_API_KEY
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
postgres  Usa JPA/PostgreSQL directo con DB_URL, DB_USERNAME y DB_PASSWORD
supabase  Adapter legacy via Supabase REST API
```

## Levantar con Floci/PostgreSQL

```powershell
ssh -N -L 7001:127.0.0.1:7001 dev@76.13.168.23
```

En otra terminal, crea un `.env` local con:

```text
SPRING_PROFILES_ACTIVE=postgres
DB_URL=jdbc:postgresql://127.0.0.1:7001/flashdrop_catalog
DB_USERNAME=catalog_app
DB_PASSWORD=tu_password
INTERNAL_API_KEY=dev-key
CATALOG_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173,http://localhost:4200
```

Luego levanta el servicio:

```powershell
.\gradlew.bat bootRun
```

Flyway crea automaticamente las tablas propias de Catalog desde `src/main/resources/db/migration`.

## Levantar con Supabase legacy

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=supabase"
```

Ese perfil queda solo como compatibilidad. Para Floci/PostgreSQL usar `postgres`.

## Levantar local sin base real

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

## Docker

```powershell
docker compose up --build
```

Por defecto Docker levanta el servicio con el perfil configurado en `.env`.

El archivo `.env` real debe quedar en el servidor, no en GitHub:

```text
SPRING_PROFILES_ACTIVE=postgres
DB_URL=jdbc:postgresql://127.0.0.1:7001/flashdrop_catalog
DB_USERNAME=catalog_app
DB_PASSWORD=********
INTERNAL_API_KEY=********
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

## Seguridad de claves

No subir nunca el archivo `.env` real a GitHub. En el repositorio solo debe existir
`.env.example` con placeholders.

La `SUPABASE_SERVICE_ROLE_KEY` es una clave de backend. No debe ir en Flutter, React,
Next.js publico ni ningun frontend.

## Endpoints internos

Los endpoints bajo `/api/internal/**` son para comunicacion entre microservicios. No son
para la app mobile ni para el panel admin.

Todos requieren el header:

```text
X-Internal-Api-Key: valor-de-INTERNAL_API_KEY
```

Ejemplos:

```powershell
curl -H "X-Internal-Api-Key: dev-key" "http://localhost:8082/api/internal/products?ids=1,2,3"
curl -H "X-Internal-Api-Key: dev-key" "http://localhost:8082/api/internal/restaurants/1"
curl -H "X-Internal-Api-Key: dev-key" "http://localhost:8082/api/internal/restaurants?userId=2"
```

Las migraciones que preparan la base propia de Catalog estan en:

```text
src/main/resources/db/migration/V1__create_schema.sql
src/main/resources/db/migration/V2__seed_development.sql
```

Con el perfil `postgres`, Flyway las ejecuta automaticamente al levantar el servicio.

Para probar sin base real:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```
