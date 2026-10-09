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
POST /api/catalog/my/products
GET  /api/catalog/my/products
PUT  /api/catalog/my/products/{productId}
DELETE /api/catalog/my/products/{productId}
POST /api/catalog/my/products/image
GET  /catalog/images/products/{year}/{month}/{filename}
```

Los `POST` publicos y todos los endpoints `/api/internal/**` requieren:

```text
X-Internal-Api-Key: valor-de-INTERNAL_API_KEY
```

Todos los endpoints `/api/catalog/my/**` requieren un Bearer JWT emitido por Auth
con el rol `Restaurante`. El claim `roles` es una lista y se mapean todos sus
valores como authorities de Spring.

El CRUD owner deriva el restaurante desde el `sub` del JWT: nunca confia en un
`restaurantId` enviado por la app. `DELETE` es un soft delete que marca el
producto como no disponible; el listado owner incluye inactivos y el catalogo
publico solo devuelve productos activos.

El upload acepta JPEG, PNG o WebP de hasta 5 MB y retorna:

```json
{
  "objectKey": "products/2026/09/00000000-0000-0000-0000-000000000001.webp",
  "url": "/catalog/images/products/2026/09/00000000-0000-0000-0000-000000000001.webp"
}
```

Persist `objectKey` in `products.image`. Public product responses turn new S3
keys into relative gateway paths while preserving legacy `assets/img/*`
references. Flutter prepends the same backend base URL it uses for the API.

Required deployment variables:

- `AUTH_SERVICE_JWKS_URI`
- `AUTH_SERVICE_ISSUER` (`flashdrop-auth`)
- `S3_ENDPOINT`, `S3_BUCKET`, `S3_REGION`
- `S3_ACCESS_KEY`, `S3_SECRET_KEY`
- `S3_PUBLIC_URL_BASE` (default: `/catalog/images`, not a secret)

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
INTERNAL_API_KEY=una-clave-interna-segura
CATALOG_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173,http://localhost:4200
```

Luego levanta el servicio:

```powershell
.\gradlew.bat bootRun
```

Flyway crea automaticamente las tablas propias de Catalog desde `src/main/resources/db/migration`. Los datos de desarrollo viven fuera de las migraciones, en `src/main/resources/db/seed`, y se cargan con el perfil `seed`.

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
src/main/resources/db/seed/V2__seed_development.sql
```

El perfil `postgres` ejecuta el esquema. Para incluir los datos de desarrollo,
usar `postgres,seed`; Flyway ejecuta entonces tambien `V2__seed_development.sql`.

## Pruebas de filtros y persistencia

Desde `services/catalog-service`, ejecutar la suite completa:

```powershell
.\gradlew.bat test
```

`PublicCatalogControllerTest` comprueba que `categoryId` y `restaurantId` no
numericos, decimales o fuera del rango Long devuelven 400 con `BAD_REQUEST`.
Los filtros numericos validos conservan el contrato existente.

`JpaProductRepositoryAdapterIT` usa PostgreSQL 16 con Testcontainers y las
migraciones reales. Comprueba las consultas activas generales, por categoria,
por restaurante y combinadas, ademas del GET publico y la desactivacion de un
producto sin perderlo en la consulta del dueno. Cada caso se revierte mediante
la transaccion del test. No usa la base de FloCI ni las credenciales del `.env`.

Para ejecutar solo esta integracion, iniciar Docker y luego:

```powershell
.\gradlew.bat test --tests '*JpaProductRepositoryAdapterIT' --rerun-tasks
```

Si Docker no esta disponible, esta clase se omite localmente; eso no equivale
a aprobar sus casos. El workflow de Catalog exige que el reporte de esta clase
exista y no tenga casos omitidos, fallidos ni errores. El reporte completo queda
en `build/reports/tests/test/index.html`.

Para probar sin base real:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```
