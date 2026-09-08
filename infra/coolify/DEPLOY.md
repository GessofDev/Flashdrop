# Deploy del ambiente FlashDrop en Coolify

Guía operativa para levantar el ambiente de desarrollo de FlashDrop Backend en un VPS con Coolify. El estado objetivo es dev, con carga de usuarios ~0, por lo que las prioridades son: simplicidad operativa, redes internas, y mínimos privilegios.

## Stack

| Componente | Imagen | Puerto interno | Función |
|---|---|---|---|
| PostgreSQL 16 | `postgres:16-alpine` | 5432 | 4 bases de datos (auth/catalog/orders/delivery) |
| Redis 7 | `redis:7-alpine` | 6379 | Rate-limit del gateway |
| API Gateway | Build local (Fastify/TS) | 3000 | Reverse proxy + middleware |
| Auth Service | Build local (Spring Boot) | 8081 | Identidad y autenticación |
| Catalog Service | Build local (Spring Boot) | 8082 | Productos, categorías, restaurantes |
| Orders Service | Build local (Spring Boot) | 8083 | Pedidos |
| Delivery Service | Build local (Spring Boot) | 8084 | Repartidores y rutas |

**Red interna**: Coolify resuelve los nombres de recursos vía DNS interno. Los servicios se llaman entre sí por `http://flashdrop-<servicio>:<puerto>`, nunca por `localhost`.

**Persistencia**: un único volumen Postgres contiene las 4 bases. Cada servicio se conecta con un usuario distinto (least-privilege) que solo tiene acceso a su BD.

## Pre-requisitos

- VPS con Coolify instalado y accesible vía UI web.
- Acceso de lectura al repositorio `GessofDev/Flashdrop` en GitHub.
- `psql` instalado en la máquina local (para los tests de conexión).
- `openssl` disponible (para generar el `INTERNAL_API_KEY`).

## Fase 1 — Levantar PostgreSQL

### 1.1 Crear el recurso

En Coolify: **New Resource → Database → PostgreSQL 16**.

| Campo | Valor |
|---|---|
| Name | `flashdrop-postgres` |
| Image Tag | `16-alpine` |
| Memory Reservation | 256 MB |
| Memory Limit | 512 MB |
| Volume | `/var/lib/postgresql/data` (default) |
| Public port | **Deshabilitado** (solo red interna) |

Coolify autogenera `POSTGRES_USER` y `POSTGRES_PASSWORD`. **Anotarlos** en un password manager; son las credenciales del superusuario (admin).

### 1.2 Ejecutar el init script

Dos opciones en Coolify:

**Opción A** — Pegar el contenido de `infra/coolify/01-postgres-init.sql` en el campo "Post Init Commands" del recurso Postgres (si Coolify lo soporta en tu versión).

**Opción B** — Montar el archivo como volumen:

- Volume host: `D:\desarrollo\2027\flashdrop_backend\infra\coolify\01-postgres-init.sql` (o la ruta que use Coolify para archivos locales)
- Volume container: `/docker-entrypoint-initdb.d/01-init.sql`
- Read-only: sí

Postgres ejecuta automáticamente cualquier archivo en `/docker-entrypoint-initdb.d/` la primera vez que arranca con un volumen vacío. Si el volumen ya tiene datos (segunda ejecución), el script se ignora — está OK.

### 1.3 Reemplazar passwords del init script

Si ejecutaste la Opción A, primero generá las passwords reales:

```bash
openssl rand -base64 24
openssl rand -base64 24
openssl rand -base64 24
openssl rand -base64 24
```

Reemplazá los placeholders `REEMPLAZAR_*_PASS` del script por esos valores antes de ejecutarlo. Si ya lo ejecutaste con placeholders, podés cambiar las passwords después con:

```sql
ALTER USER auth_svc WITH PASSWORD 'valor_nuevo';
```

### 1.4 Verificar

Desde la máquina local, usando el proxy de Coolify o un container temporal:

```bash
# Opción A: via proxy de Coolify (si está habilitado)
PGPASSWORD="<POSTGRES_PASSWORD>" psql -h <vps_host> -U <POSTGRES_USER> -d auth_db -c "SELECT current_database(), current_user;"

# Opción B: container temporal en Coolify (más seguro, no abre puertos)
# Crear un container alpine con psql, conectado a la red interna de Coolify
```

Respuesta esperada: `auth_db | auth_svc`. Si obtenés ese resultado, las 4 bases y los 4 usuarios están listos.

## Fase 2 — Levantar Redis

### 2.1 Crear el recurso

En Coolify: **New Resource → Database → Redis 7**.

| Campo | Valor |
|---|---|
| Name | `flashdrop-redis` |
| Image Tag | `7-alpine` |
| Memory Limit | 128 MB |
| Public port | Deshabilitado |

Coolify autogenera (o deja vacía) la password. Para dev, sin password está bien. Para producción, agregar `--requirepass`.

### 2.2 Anotar URL interna

La URL que los servicios usan para conectarse es:

```
redis://flashdrop-redis:6379
```

Si decidiste usar password:

```
redis://:<password>@flashdrop-redis:6379
```

## Fase 3 — Variables de entorno compartidas

Antes de deployar cualquier servicio, copiá los valores de `infra/coolify/env.shared.template` a las variables de entorno de Coolify. Los valores sensibles (`POSTGRES_PASSWORD`, `INTERNAL_API_KEY`, las 4 passwords de servicio) son los que generaste en Fase 1 y Fase 2.

### 3.1 Generar INTERNAL_API_KEY

```bash
openssl rand -hex 32
```

Este valor se usa en el header `X-Internal-Api-Key` que el plan de migración menciona para proteger los endpoints `/api/internal/*` entre microservicios. **Tiene que ser exactamente el mismo en los 4 servicios y en el gateway**.

### 3.2 Estrategia en Coolify

Coolify no tiene un "secret store" compartido nativo para múltiples resources, así que las opciones son:

| Opción | Pro | Contra |
|---|---|---|
| Pegar las variables en cada resource | Simple, sin setup extra | Hay que mantenerlas en 5 lugares |
| Usar "Service" de Coolify con env vars compartidas | Single source of truth | Requiere Coolify 4.x o superior |
| Externalizar a un `.env` montado por volumen | Versionable en repo | No es seguro commitear; el archivo en el VPS debe tener `chmod 600` |

**Recomendación para dev**: pegar las variables en cada resource. Cuando pases a producción, considerar Doppler o un Vault.

## Fase 4 — Testear el ambiente

Antes de deployar servicios, validar que la red interna de Coolify funciona.

### 4.1 Test de Postgres

Crear un container temporal en Coolify:

- Image: `postgres:16-alpine`
- Command: `sleep infinity`
- Network: la misma red que `flashdrop-postgres`

Una vez corriendo, abrir la consola del container y ejecutar:

```bash
psql "postgresql://auth_svc:REEMPLAZAR_CON_AUTH_SVC_PASS@flashdrop-postgres:5432/auth_db" -c "SELECT current_database(), current_user;"
```

Si responde `auth_db | auth_svc`, la red interna y las credenciales están OK.

### 4.2 Test de Redis

Desde el mismo container temporal o uno nuevo:

```bash
apk add redis
redis-cli -h flashdrop-redis ping
```

Respuesta esperada: `PONG`.

## Fase 5 — Deployar microservicios

Esta fase se ejecuta **después** de que los juniors hayan consolidado su código en `services/<nombre>-service/` y commiteado los Dockerfiles en el branch `feat/services-integration`.

### 5.1 Pre-requisitos por servicio

Cada servicio necesita en su branch:

- `services/<nombre>-service/Dockerfile` (template: `services/delivery-service/Dockerfile`)
- `services/<nombre>-service/src/` con el código fuente completo
- `services/<nombre>-service/build.gradle.kts` con las dependencias
- `services/<nombre>-service/gradle/wrapper/gradle-wrapper.jar` (a veces no se commitea; verificar)
- `services/<nombre>-service/.env.example` con las variables esperadas

### 5.2 Crear el recurso en Coolify

Para cada servicio (usando Delivery como ejemplo):

**New Resource → Application**:

| Campo | Valor |
|---|---|
| Name | `flashdrop-delivery` |
| Source | GitHub → `GessofDev/Flashdrop` |
| Branch | `main` |
| Build Pack | Dockerfile |
| Dockerfile Path | `services/delivery-service/Dockerfile` |
| Port | `8084` |
| Memory Reservation | 256 MB |
| Memory Limit | 512 MB |
| Restart Policy | `unless-stopped` |
| Healthcheck | `GET /actuator/health` (si Spring Actuator está configurado) |

### 5.2.1 Rutas de Dockerfile por servicio

Después de la consolidación `juniors/ → services/`, las rutas son:

| Servicio | Branch | Dockerfile Path | Port |
|---|---|---|---|
| Auth | `main` | `services/auth-service/Dockerfile` | 8081 |
| Catalog | `main` | `services/catalog-service/Dockerfile` | 8082 |
| Orders | `main` | `services/orders-service/Dockerfile` | 8083 |
| Delivery | `main` | `services/delivery-service/Dockerfile` | 8084 |

> **Nota sobre Auth**: `services/auth-service/` requiere acceso a `services/shared-observability/` (módulo Gradle hermano). Coolify construye desde la raíz de `services/`, no desde `auth-service/`, para que Gradle resuelva la dependencia. Ajustar el Dockerfile Context de Coolify a `services/` y usar `./gradlew :auth-service:bootJar` en lugar de `gradle bootJar`.

### 5.3 Variables de entorno del servicio

Pegar en Coolify las variables específicas del servicio (las de la sección 16 del plan de migración). Ejemplo para Delivery:

```
SPRING_PROFILES_ACTIVE=delivery
SPRING_DATASOURCE_URL=jdbc:postgresql://flashdrop-postgres:5432/delivery_db
SPRING_DATASOURCE_USERNAME=delivery_svc
SPRING_DATASOURCE_PASSWORD=<delivery_svc_password>
INTERNAL_API_KEY=<mismo_que_env_shared>
CATALOG_SERVICE_URL=http://flashdrop-catalog:8082
ORDERS_SERVICE_URL=http://flashdrop-orders:8083
JAVA_OPTS=-Xmx384m
```

> **Perfil `delivery`**: delivery-service usa `SPRING_PROFILES_ACTIVE=delivery` para activar `application-delivery.yml`, que configura el DataSource contra `delivery_db` (schema `internal`) via JPA + Flyway. No usar `production` ni `local` para este servicio.

`JAVA_OPTS=-Xmx384m` es **obligatorio** en VPS de 2-4 GB. Sin él, 4 JVMs revientan la RAM disponible.

### 5.4 Repetir para los 4 servicios

Orden recomendado (de menor a mayor dependencia):

1. **Auth** (8081) — sin dependencias internas, validable solo.
2. **Catalog** (8082) — sin dependencias internas, validable solo.
3. **Delivery** (8084) — depende de Auth (vía JWT) y Catalog.
4. **Orders** (8083) — depende de los tres anteriores.

### 5.5 Deployar el gateway

**New Resource → Application**:

| Campo | Valor |
|---|---|
| Name | `flashdrop-gateway` |
| Source | GitHub → `GessofDev/Flashdrop` |
| Branch | `feat/services-integration` (o donde esté el gateway) |
| Build Pack | Dockerfile |
| Dockerfile Path | `gateway/docker/Dockerfile` |
| Port | `3000` |
| Memory Limit | 256 MB |

Variables de entorno:

```
NODE_ENV=production
PORT=3000
HOST=0.0.0.0
CONFIG_PATH=/app/config/gateway.yaml
REDIS_URL=redis://flashdrop-redis:6379
```

El `gateway.yaml` que se monta en `/app/config/` debe tener las rutas (`routes[]`) apuntando a los nombres internos de Coolify (`flashdrop-auth:8081`, etc.).

### 5.6 Test end-to-end

Una vez los 5 containers estén corriendo (4 servicios + gateway), desde la máquina local:

```bash
curl http://<vps_host>:3000/health
```

El endpoint `/health` del gateway agrega los health checks de los 4 servicios. Status 200 = todo OK. Status 503 = alguno caído.

Después, un test real de la API pública:

```bash
curl -X POST http://<vps_host>:3000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Test","email":"test@example.com","password":"password123"}'
```

## Comandos útiles

### Conectarse a Postgres desde la máquina local (vía proxy Coolify)

```bash
PGPASSWORD="<POSTGRES_PASSWORD>" psql -h <vps_host> -p <puerto_proxy> -U postgres -d auth_db
```

### Ver logs de un container en Coolify

UI → Resource → `flashdrop-<servicio>` → Logs.

O desde el VPS por SSH:

```bash
docker logs -f flashdrop-delivery
docker logs -f flashdrop-postgres
```

### Reiniciar un servicio sin perder datos

```bash
docker restart flashdrop-delivery
```

### Backup de las 4 bases

```bash
docker exec flashdrop-postgres pg_dump -U postgres auth_db     > backup_auth_$(date +%F).sql
docker exec flashdrop-postgres pg_dump -U postgres catalog_db  > backup_catalog_$(date +%F).sql
docker exec flashdrop-postgres pg_dump -U postgres orders_db   > backup_orders_$(date +%F).sql
docker exec flashdrop-postgres pg_dump -U postgres delivery_db > backup_delivery_$(date +%F).sql
```

### Test de conexión entre servicios

```bash
# Desde el container del gateway, validar que llega a delivery
docker exec flashdrop-gateway wget -qO- http://flashdrop-delivery:8084/actuator/health
```

## Troubleshooting

### El container no arranca

**Síntoma**: Coolify muestra "Restarting" en loop.

**Causa común 1**: Falta `gradle-wrapper.jar` en el commit. Verificar:

```bash
git ls-tree -r feat/services-integration services/delivery-service/gradle/wrapper/
```

Si no aparece `gradle-wrapper.jar`, los juniors lo tienen en `.gitignore` local. Agregar a `.gitignore` solo `*.jar` que NO sea `gradle-wrapper.jar` (el .gitignore actual del servicio ya lo hace, pero hay que confirmar el commit).

**Causa común 2**: `application.yml` referencia una variable de entorno que no está definida en Coolify. Verificar logs:

```bash
docker logs flashdrop-delivery | head -50
```

Spring Boot falla con `Could not resolve placeholder 'X'` cuando falta una env var.

### Error de conexión a Postgres

**Síntoma**: Servicio cae con `FATAL: password authentication failed for user "auth_svc"`.

**Causa**: La password en Coolify no coincide con la del init script. Confirmar que el valor de `SPRING_DATASOURCE_PASSWORD` en Coolify es exactamente el mismo que se usó en `01-postgres-init.sql`.

### Health check siempre rojo

**Síntoma**: `/health` retorna 503 aunque los 4 servicios están "running".

**Causa**: El gateway no puede alcanzar los servicios por su nombre interno. Verificar:

```bash
docker exec flashdrop-gateway ping -c 1 flashpost-delivery
```

Si falla, el servicio no está en la misma red de Coolify que el gateway. Re-deployar el gateway con la red correcta (en Coolify, al crear el resource, sección "Networks").

### Memoria insuficiente (OOMKilled)

**Síntoma**: Container crashea con `exit code 137` en logs.

**Causa**: El VPS se quedó sin RAM. Con 4 JVMs Spring Boot sin `JAVA_OPTS=-Xmx384m`, 4 × 512MB = 2GB solo de JVMs, más Postgres, Redis, gateway, sistema = OOM.

**Fix**: Verificar que cada servicio tiene `JAVA_OPTS=-Xmx384m` en sus env vars. Considerar reducir a `-Xmx256m` si sigue habiendo problemas.

## Próximos pasos (post-MVP)

Cuando el ambiente esté estable y validado:

1. **HTTPS**: Configurar dominio + certificados en Coolify (Let's Encrypt automático).
2. **Migrar adaptadores SQL → HTTP** (Fase 2 del plan de migración). Cada servicio reemplaza sus accesos a tablas ajenas por HTTP clients a `/api/internal/*`.
3. **Separar BDs** (Fase 3 del plan). Solo si el tráfico crece o la aislación física se vuelve necesaria. El approach actual de 1 instancia + 4 bases es equivalente a nivel de seguridad.
4. **Observabilidad**: El `docker-compose.stack.yml` del gateway ya tiene Dozzle, Prometheus y Grafana. Replicar en Coolify cuando se quiera monitoring real.
5. **CI/CD**: El repo ya tiene `.github/workflows/auth-service-ci.yml`. Extender el patrón a los otros 3 servicios + gateway. Los deploys a Coolify pueden dispararse con webhooks de GitHub Actions.
